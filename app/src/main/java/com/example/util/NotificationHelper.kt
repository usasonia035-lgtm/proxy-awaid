package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.ConnectionInfo
import com.example.model.ConnectionState

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Owais Proxy Tunnel Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time connection status and public IP for Owais Proxy Server"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        state: ConnectionState,
        info: ConnectionInfo? = null,
        stopIntentAction: String
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(stopIntentAction).setPackage(context.packageName)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Owais Proxy Server"
        val (contentText, subText) = when (state) {
            is ConnectionState.Connecting -> {
                "Connecting to proxy..." to "Establishing secure tunnel"
            }
            is ConnectionState.Connected -> {
                val country = if (info?.countryName?.isNotBlank() == true && !info.countryName.contains("Detecting")) {
                    "${info.countryFlag} ${info.countryName}"
                } else {
                    "Connected"
                }
                val ipText = if (info?.publicIp?.isNotBlank() == true && info.publicIp != "Detecting...") {
                    "IP: ${info.publicIp}"
                } else {
                    "Tunnel Active (${info?.proxyType?.name ?: "SOCKS5"})"
                }
                "Connected to $country" to ipText
            }
            is ConnectionState.Disconnecting -> {
                "Disconnecting..." to "Releasing proxy resources"
            }
            is ConnectionState.Error -> {
                "Connection failed" to state.message
            }
            is ConnectionState.Disconnected -> {
                "Proxy disconnected" to "Ready to connect"
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(state.isConnected || state.isConnecting)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (state.isConnected || state.isConnecting) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                stopPendingIntent
            )
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "owais_proxy_vpn_channel"
        const val NOTIFICATION_ID = 1001
    }
}
