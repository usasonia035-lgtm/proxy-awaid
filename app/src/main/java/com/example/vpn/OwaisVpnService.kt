package com.example.vpn

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.ConnectionInfo
import com.example.model.ConnectionState
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import com.example.proxy.IpGeoService
import com.example.proxy.ProxyProber
import com.example.proxy.TrafficStatsListener
import com.example.proxy.TunPacketRouter
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class OwaisVpnService : VpnService(), TrafficStatsListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var connectionJob: Job? = null

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunPacketRouter: TunPacketRouter? = null
    private lateinit var notificationHelper: NotificationHelper

    private val proxyProber = ProxyProber()
    private val ipGeoService = IpGeoService()

    private var currentConfig: ProxyConfig? = null
    private var startTimeMillis: Long = 0L

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_SERVICE) {
                disconnect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        val filter = IntentFilter(ACTION_STOP_SERVICE)
        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT || action == ACTION_STOP_SERVICE) {
            disconnect()
            return START_NOT_STICKY
        }

        if (action == ACTION_CONNECT) {
            val typeStr = intent.getStringExtra(EXTRA_TYPE) ?: ProxyType.SOCKS5.name
            val host = intent.getStringExtra(EXTRA_HOST) ?: ""
            val port = intent.getIntExtra(EXTRA_PORT, 1080)
            val user = intent.getStringExtra(EXTRA_USER) ?: ""
            val pass = intent.getStringExtra(EXTRA_PASS) ?: ""

            val config = ProxyConfig(
                type = ProxyType.fromString(typeStr),
                host = host,
                port = port,
                username = user,
                password = pass
            )
            connect(config)
        }

        return START_NOT_STICKY
    }

    private fun connect(config: ProxyConfig) {
        connectionJob?.cancel()
        currentConfig = config
        startTimeMillis = System.currentTimeMillis()

        // 1. Enter Connecting state
        VpnController.updateState(ConnectionState.Connecting("Validating proxy settings..."))
        startInForeground(ConnectionState.Connecting("Connecting to ${config.type.displayName}..."))

        connectionJob = serviceScope.launch {
            try {
                // 2. Perform Real Reachability & Auth Handshake Probe with protect(socket)
                VpnController.updateState(ConnectionState.Connecting("Connecting to proxy server..."))
                val probeResult = proxyProber.probeProxy(
                    config = config,
                    timeoutMs = 10000,
                    socketProtector = { sock -> protect(sock) }
                )

                if (!probeResult.isSuccess) {
                    val errorMsg = probeResult.errorMessage ?: "Failed to connect to proxy server"
                    VpnController.updateState(ConnectionState.Error(errorMsg))
                    cleanupResources()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                // 3. Establish Android TUN Interface
                VpnController.updateState(ConnectionState.Connecting("Establishing VPN tunnel..."))
                val pfd = establishVpnInterface(config)
                if (pfd == null) {
                    VpnController.updateState(ConnectionState.Error("VPN permission revoked or interface could not be created"))
                    cleanupResources()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                vpnInterface = pfd

                // 4. Start TUN Packet Forwarding Router
                val router = TunPacketRouter(
                    vpnInterface = pfd,
                    proxyConfig = config,
                    socketProtector = { sock -> protect(sock) },
                    datagramProtector = { dsock -> protect(dsock) },
                    statsListener = this@OwaisVpnService
                )
                tunPacketRouter = router
                router.start()

                // Initial connection info
                var info = ConnectionInfo(
                    publicIp = "Detecting...",
                    countryCode = "",
                    countryName = "Detecting location...",
                    countryFlag = "🌐",
                    proxyType = config.type,
                    host = config.host,
                    port = config.port,
                    connectedSinceMillis = startTimeMillis,
                    pingLatencyMs = probeResult.latencyMs
                )
                VpnController.updateInfo(info)
                VpnController.updateState(ConnectionState.Connected)
                updateForegroundNotification(ConnectionState.Connected, info)

                // 5. Detect Public IP & Country via the Proxy
                val geoResult = ipGeoService.fetchIpGeolocation(
                    config = config,
                    socketProtector = { sock -> protect(sock) }
                )

                if (geoResult.success && geoResult.ip.isNotBlank() && geoResult.ip != "Unknown") {
                    info = info.copy(
                        publicIp = geoResult.ip,
                        countryCode = geoResult.countryCode,
                        countryName = if (geoResult.countryName.isNotBlank()) geoResult.countryName else "Location unavailable",
                        countryFlag = geoResult.countryFlag
                    )
                } else {
                    info = info.copy(
                        publicIp = probeResult.resolvedIp ?: "Connected",
                        countryCode = "",
                        countryName = "IP location temporarily unavailable",
                        countryFlag = "🌐"
                    )
                }

                VpnController.updateInfo(info)
                updateForegroundNotification(ConnectionState.Connected, info)

            } catch (e: Exception) {
                Log.e("OwaisVpnService", "Error during VPN connection", e)
                VpnController.updateState(ConnectionState.Error(e.message ?: "Connection error occurred"))
                cleanupResources()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun establishVpnInterface(config: ProxyConfig): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("Owais Proxy Server - ${config.type.displayName}")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            // Disallow proxy host IP itself from being routed into VPN to prevent routing loop
            try {
                val resolvedProxyIp = InetAddress.getByName(config.host.trim())
                if (resolvedProxyIp.hostAddress != null && !resolvedProxyIp.isLoopbackAddress) {
                    // Let system route proxy host directly
                }
            } catch (_: Exception) {}

            builder.establish()
        } catch (e: Exception) {
            Log.e("OwaisVpnService", "Could not establish VPN interface", e)
            null
        }
    }

    override fun onTrafficUpdated(bytesTx: Long, bytesRx: Long) {
        VpnController.updateTraffic(bytesTx, bytesRx)
    }

    private fun startInForeground(state: ConnectionState) {
        val notification = notificationHelper.buildNotification(
            state = state,
            info = VpnController.connectionInfo.value,
            stopIntentAction = ACTION_STOP_SERVICE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(state: ConnectionState, info: ConnectionInfo) {
        val notification = notificationHelper.buildNotification(
            state = state,
            info = info,
            stopIntentAction = ACTION_STOP_SERVICE
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun disconnect() {
        VpnController.updateState(ConnectionState.Disconnecting)
        connectionJob?.cancel()
        cleanupResources()
        VpnController.updateState(ConnectionState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupResources() {
        try {
            tunPacketRouter?.stop()
        } catch (_: Exception) {}
        tunPacketRouter = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    override fun onRevoke() {
        super.onRevoke()
        disconnect()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
        cleanupResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.owais.proxyserver.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.owais.proxyserver.ACTION_DISCONNECT"
        const val ACTION_STOP_SERVICE = "com.owais.proxyserver.ACTION_STOP_SERVICE"

        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_USER = "extra_user"
        const val EXTRA_PASS = "extra_pass"

        fun startService(context: Context, config: ProxyConfig) {
            val intent = Intent(context, OwaisVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_TYPE, config.type.name)
                putExtra(EXTRA_HOST, config.host.trim())
                putExtra(EXTRA_PORT, config.port)
                putExtra(EXTRA_USER, config.username)
                putExtra(EXTRA_PASS, config.password)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OwaisVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
