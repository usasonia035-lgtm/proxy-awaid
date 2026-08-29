package com.example.proxy

import android.util.Base64
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class ProbeResult(
    val isSuccess: Boolean,
    val latencyMs: Long = 0L,
    val resolvedIp: String? = null,
    val errorMessage: String? = null
)

class ProxyProber {

    /**
     * Performs a direct socket-level handshake probe against the proxy server,
     * validating credentials and reachability without faking.
     */
    suspend fun probeProxy(
        config: ProxyConfig,
        timeoutMs: Int = 8000,
        socketProtector: ((Socket) -> Boolean)? = null
    ): ProbeResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        try {
            val (isValid, validationErr) = config.isValid()
            if (!isValid) {
                return@withContext ProbeResult(false, errorMessage = validationErr ?: "Invalid proxy settings")
            }

            // 1. Resolve DNS
            val address = try {
                InetAddress.getByName(config.host.trim())
            } catch (e: UnknownHostException) {
                return@withContext ProbeResult(false, errorMessage = "DNS failure: Cannot resolve proxy host '${config.host}'")
            }

            // 2. Connect raw socket
            socket = Socket()
            socketProtector?.invoke(socket)
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(address, config.port), timeoutMs)

            // 3. Protocol handshake
            when (config.type) {
                ProxyType.SOCKS5 -> {
                    performSocks5Handshake(socket, config)
                }
                ProxyType.HTTP -> {
                    performHttpConnectHandshake(socket, config, isTls = false)
                }
                ProxyType.HTTPS -> {
                    val sslSocket = wrapTlsSocket(socket, config.host.trim(), config.port)
                    socketProtector?.invoke(sslSocket)
                    performHttpConnectHandshake(sslSocket, config, isTls = true)
                }
            }

            val latency = System.currentTimeMillis() - startTime
            ProbeResult(
                isSuccess = true,
                latencyMs = latency,
                resolvedIp = address.hostAddress
            )
        } catch (e: SocketTimeoutException) {
            ProbeResult(false, errorMessage = "Connection timed out after ${timeoutMs / 1000}s. Proxy server did not respond.")
        } catch (e: ConnectException) {
            ProbeResult(false, errorMessage = "Proxy server unreachable: Connection refused (${config.host}:${config.port}).")
        } catch (e: NoRouteToHostException) {
            ProbeResult(false, errorMessage = "Network unavailable: No route to proxy host.")
        } catch (e: UnknownHostException) {
            ProbeResult(false, errorMessage = "DNS failure: Host not found.")
        } catch (e: ProxyAuthException) {
            ProbeResult(false, errorMessage = "Authentication failed: ${e.message}")
        } catch (e: ProxyProtocolException) {
            ProbeResult(false, errorMessage = "Unsupported proxy response: ${e.message}")
        } catch (e: Exception) {
            ProbeResult(false, errorMessage = e.message ?: "Proxy connection failed")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun performSocks5Handshake(socket: Socket, config: ProxyConfig) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        val hasAuth = config.requiresAuth
        // Version identifier/method selection message:
        // +----+----------+----------+
        // |VER | NMETHODS | METHODS  |
        // +----+----------+----------+
        // | 1  |    1     | 1 to 255 |
        // +----+----------+----------+
        if (hasAuth) {
            output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02)) // Supports NO_AUTH (0x00) and USER/PASS (0x02)
        } else {
            output.write(byteArrayOf(0x05, 0x01, 0x00)) // Supports NO_AUTH (0x00)
        }
        output.flush()

        // Read server selection:
        // +----+--------+
        // |VER | METHOD |
        // +----+--------+
        val ver = input.read()
        val method = input.read()

        if (ver != 0x05) {
            throw ProxyProtocolException("Invalid SOCKS5 version returned: $ver")
        }
        if (method == 0xFF) {
            throw ProxyAuthException("No acceptable authentication methods supported by proxy")
        }

        if (method == 0x02) {
            // RFC 1929 Username/Password Authentication:
            // +----+------+----------+------+----------+
            // |VER | ULEN |  UNAME   | PLEN |  PASSWD  |
            // +----+------+----------+------+----------+
            val userBytes = config.username.toByteArray(StandardCharsets.UTF_8)
            val passBytes = config.password.toByteArray(StandardCharsets.UTF_8)
            if (userBytes.size > 255 || passBytes.size > 255) {
                throw ProxyAuthException("Username or password exceeds maximum 255 bytes")
            }

            output.write(0x01) // Auth Sub-negotiation Version 1
            output.write(userBytes.size)
            output.write(userBytes)
            output.write(passBytes.size)
            output.write(passBytes)
            output.flush()

            // Server auth response:
            // +----+--------+
            // |VER | STATUS |
            // +----+--------+
            val authVer = input.read()
            val authStatus = input.read()
            if (authStatus != 0x00) {
                throw ProxyAuthException("Invalid username or password (status code $authStatus)")
            }
        } else if (method != 0x00) {
            throw ProxyProtocolException("Unsupported SOCKS5 auth method: $method")
        }
    }

    private fun performHttpConnectHandshake(socket: Socket, config: ProxyConfig, isTls: Boolean) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        // We test tunnel to a public test target (e.g. api.ipify.org:443 or 80)
        val targetHost = "api.ipify.org"
        val targetPort = 443

        val requestBuilder = StringBuilder()
        requestBuilder.append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
        requestBuilder.append("Host: $targetHost:$targetPort\r\n")
        requestBuilder.append("User-Agent: OwaisProxy/1.0\r\n")
        requestBuilder.append("Proxy-Connection: Keep-Alive\r\n")

        if (config.requiresAuth) {
            val userPass = "${config.username}:${config.password}"
            val encoded = Base64.encodeToString(userPass.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            requestBuilder.append("Proxy-Authorization: Basic $encoded\r\n")
        }
        requestBuilder.append("\r\n")

        output.write(requestBuilder.toString().toByteArray(StandardCharsets.UTF_8))
        output.flush()

        // Read HTTP status line (e.g., "HTTP/1.1 200 Connection established" or "HTTP/1.1 407 Proxy Authentication Required")
        val statusLine = readLine(input)
        if (statusLine.isNullOrBlank()) {
            throw ProxyProtocolException("Empty HTTP response from proxy")
        }

        val parts = statusLine.split(" ", limit = 3)
        if (parts.size < 2) {
            throw ProxyProtocolException("Malformed HTTP status line: $statusLine")
        }

        val statusCode = parts[1].toIntOrNull() ?: 0
        if (statusCode == 407) {
            throw ProxyAuthException("407 Proxy Authentication Required - Check username & password")
        }
        if (statusCode !in 200..299) {
            val reason = if (parts.size > 2) parts[2] else "Status $statusCode"
            throw ProxyProtocolException("Proxy returned HTTP $statusCode ($reason)")
        }
    }

    private fun wrapTlsSocket(plainSocket: Socket, host: String, port: Int): SSLSocket {
        val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sslSocket = sslSocketFactory.createSocket(
            plainSocket,
            host,
            port,
            true
        ) as SSLSocket

        val params = SSLParameters()
        params.serverNames = listOf(SNIHostName(host))
        sslSocket.sslParameters = params
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        var previousWasCr = false
        while (true) {
            c = input.read()
            if (c == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (c == '\n'.code) {
                break
            } else if (c == '\r'.code) {
                previousWasCr = true
            } else {
                if (previousWasCr) {
                    sb.append('\r')
                    previousWasCr = false
                }
                sb.append(c.toChar())
            }
        }
        return sb.toString()
    }
}

class ProxyAuthException(message: String) : Exception(message)
class ProxyProtocolException(message: String) : Exception(message)
