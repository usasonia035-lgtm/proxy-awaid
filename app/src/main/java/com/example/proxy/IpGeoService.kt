package com.example.proxy

import com.example.model.ProxyConfig
import com.example.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

data class IpGeoResult(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val countryFlag: String,
    val isp: String = "",
    val city: String = "",
    val success: Boolean = true,
    val errorMessage: String? = null
)

class IpGeoService {

    /**
     * Converts a 2-letter ISO country code into a regional indicator emoji flag.
     * e.g. "SA" -> "🇸🇦", "AE" -> "🇦🇪", "US" -> "🇺🇸"
     */
    fun countryCodeToFlagEmoji(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return "🌐"
        val upper = countryCode.uppercase(Locale.ROOT)
        val firstChar = upper[0]
        val secondChar = upper[1]
        if (firstChar !in 'A'..'Z' || secondChar !in 'A'..'Z') return "🌐"

        val firstCodePoint = 0x1F1E6 + (firstChar - 'A')
        val secondCodePoint = 0x1F1E6 + (secondChar - 'A')
        return String(Character.toChars(firstCodePoint)) + String(Character.toChars(secondCodePoint))
    }

    /**
     * Creates an OkHttpClient optionally routed explicitly through the given proxy config.
     */
    fun createProxiedClient(
        config: ProxyConfig?,
        socketTimeoutSeconds: Long = 10,
        socketProtector: ((java.net.Socket) -> Boolean)? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(socketTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(socketTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(socketTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)

        if (socketProtector != null) {
            builder.socketFactory(object : javax.net.SocketFactory() {
                private val defaultFactory = javax.net.SocketFactory.getDefault()
                override fun createSocket(): java.net.Socket {
                    val s = defaultFactory.createSocket()
                    socketProtector(s)
                    return s
                }
                override fun createSocket(host: String?, port: Int): java.net.Socket {
                    val s = defaultFactory.createSocket()
                    socketProtector(s)
                    s.connect(InetSocketAddress(host, port))
                    return s
                }
                override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): java.net.Socket {
                    val s = defaultFactory.createSocket()
                    socketProtector(s)
                    s.bind(InetSocketAddress(localHost, localPort))
                    s.connect(InetSocketAddress(host, port))
                    return s
                }
                override fun createSocket(host: java.net.InetAddress?, port: Int): java.net.Socket {
                    val s = defaultFactory.createSocket()
                    socketProtector(s)
                    s.connect(InetSocketAddress(host, port))
                    return s
                }
                override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): java.net.Socket {
                    val s = defaultFactory.createSocket()
                    socketProtector(s)
                    s.bind(InetSocketAddress(localAddress, localPort))
                    s.connect(InetSocketAddress(address, port))
                    return s
                }
            })
        }

        if (config != null && config.host.isNotBlank()) {
            when (config.type) {
                ProxyType.SOCKS5 -> {
                    val socketAddress = InetSocketAddress(config.host.trim(), config.port)
                    builder.proxy(Proxy(Proxy.Type.SOCKS, socketAddress))
                    if (config.requiresAuth) {
                        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                            override fun getPasswordAuthentication(): java.net.PasswordAuthentication {
                                return java.net.PasswordAuthentication(
                                    config.username,
                                    config.password.toCharArray()
                                )
                            }
                        })
                    }
                }
                ProxyType.HTTP, ProxyType.HTTPS -> {
                    val socketAddress = InetSocketAddress(config.host.trim(), config.port)
                    builder.proxy(Proxy(Proxy.Type.HTTP, socketAddress))
                    if (config.requiresAuth) {
                        builder.proxyAuthenticator(object : Authenticator {
                            override fun authenticate(route: Route?, response: Response): Request? {
                                if (response.request.header("Proxy-Authorization") != null) {
                                    return null // Give up if already attempted
                                }
                                val credential = Credentials.basic(config.username, config.password)
                                return response.request.newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build()
                            }
                        })
                    }
                }
            }
        }

        return builder.build()
    }

    /**
     * Fetches public IP and country info visible through the proxy connection.
     */
    suspend fun fetchIpGeolocation(
        config: ProxyConfig? = null,
        socketProtector: ((java.net.Socket) -> Boolean)? = null,
        clientOverride: OkHttpClient? = null
    ): IpGeoResult = withContext(Dispatchers.IO) {
        val client = clientOverride ?: createProxiedClient(config, socketTimeoutSeconds = 12, socketProtector = socketProtector)

        // Try Provider 1: ipwho.is (fast, returns country, code, flag emoji, city)
        tryProviderIpWhoIs(client)?.let { return@withContext it }

        // Try Provider 2: ipapi.co (reliable HTTPS JSON)
        tryProviderIpApiCo(client)?.let { return@withContext it }

        // Try Provider 3: ifconfig.co (clean minimal JSON)
        tryProviderIfconfigCo(client)?.let { return@withContext it }

        // Try Provider 4: ipify.org (minimal IP-only provider)
        tryProviderIpify(client)?.let { return@withContext it }

        IpGeoResult(
            ip = "Unknown",
            countryCode = "",
            countryName = "Location unavailable",
            countryFlag = "🌐",
            success = false,
            errorMessage = "IP location temporarily unavailable"
        )
    }

    private fun tryProviderIpWhoIs(client: OkHttpClient): IpGeoResult? {
        return try {
            val request = Request.Builder()
                .url("https://ipwho.is/")
                .header("User-Agent", "OwaisProxy/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (!json.optBoolean("success", true)) return null

                val ip = json.optString("ip", "")
                if (ip.isBlank()) return null
                val countryCode = json.optString("country_code", "")
                val countryName = json.optString("country", "Unknown")
                val flagEmoji = json.optJSONObject("flag")?.optString("emoji", "") ?: countryCodeToFlagEmoji(countryCode)
                val city = json.optString("city", "")
                val isp = json.optJSONObject("connection")?.optString("isp", "") ?: ""

                IpGeoResult(
                    ip = ip,
                    countryCode = countryCode,
                    countryName = countryName,
                    countryFlag = if (flagEmoji.isNotBlank()) flagEmoji else countryCodeToFlagEmoji(countryCode),
                    isp = isp,
                    city = city,
                    success = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryProviderIpApiCo(client: OkHttpClient): IpGeoResult? {
        return try {
            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "OwaisProxy/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val ip = json.optString("ip", "")
                if (ip.isBlank()) return null

                val countryCode = json.optString("country_code", "")
                val countryName = json.optString("country_name", "Unknown")
                val city = json.optString("city", "")
                val org = json.optString("org", "")

                IpGeoResult(
                    ip = ip,
                    countryCode = countryCode,
                    countryName = countryName,
                    countryFlag = countryCodeToFlagEmoji(countryCode),
                    isp = org,
                    city = city,
                    success = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryProviderIfconfigCo(client: OkHttpClient): IpGeoResult? {
        return try {
            val request = Request.Builder()
                .url("https://ifconfig.co/json")
                .header("User-Agent", "OwaisProxy/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val ip = json.optString("ip", "")
                if (ip.isBlank()) return null

                val countryCode = json.optString("country_iso", "")
                val countryName = json.optString("country", "Unknown")
                val city = json.optString("city", "")

                IpGeoResult(
                    ip = ip,
                    countryCode = countryCode,
                    countryName = countryName,
                    countryFlag = countryCodeToFlagEmoji(countryCode),
                    city = city,
                    success = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryProviderIpify(client: OkHttpClient): IpGeoResult? {
        return try {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("User-Agent", "OwaisProxy/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val ip = json.optString("ip", "")
                if (ip.isBlank()) return null

                IpGeoResult(
                    ip = ip,
                    countryCode = "",
                    countryName = "Location unavailable",
                    countryFlag = "🌐",
                    success = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
