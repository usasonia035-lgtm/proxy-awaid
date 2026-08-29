package com.example.proxy

import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

interface TrafficStatsListener {
    fun onTrafficUpdated(bytesTx: Long, bytesRx: Long)
}

class TunPacketRouter(
    private val vpnInterface: ParcelFileDescriptor,
    private val proxyConfig: ProxyConfig,
    private val socketProtector: (Socket) -> Boolean,
    private val datagramProtector: (DatagramSocket) -> Boolean,
    private val statsListener: TrafficStatsListener? = null
) {
    private val isRunning = AtomicBoolean(true)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    val totalBytesTx = AtomicLong(0L)
    val totalBytesRx = AtomicLong(0L)

    private var packetReaderJob: Job? = null
    private var statsJob: Job? = null

    // Active TCP proxy sessions (key: "srcIP:srcPort-dstIP:dstPort")
    private val activeTcpSessions = ConcurrentHashMap<String, TcpSession>()

    fun start() {
        isRunning.set(true)

        // Periodic stats reporter
        statsJob = scope.launch {
            while (isActive && isRunning.get()) {
                delay(1000L)
                statsListener?.onTrafficUpdated(totalBytesTx.get(), totalBytesRx.get())
            }
        }

        // TUN Packet read loop
        packetReaderJob = scope.launch {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
            val packetBuffer = ByteArray(32767)

            try {
                while (isActive && isRunning.get()) {
                    val length = inputStream.read(packetBuffer)
                    if (length <= 0) {
                        if (length < 0) break
                        continue
                    }

                    handlePacket(packetBuffer, length, outputStream)
                }
            } catch (e: IOException) {
                if (isRunning.get()) {
                    Log.d("TunPacketRouter", "TUN read loop terminated: ${e.message}")
                }
            } catch (_: Exception) {}
        }
    }

    private fun handlePacket(data: ByteArray, length: Int, outStream: FileOutputStream) {
        if (length < 20) return

        val versionAndIHL = data[0].toInt()
        val version = (versionAndIHL shr 4) and 0x0F
        if (version != 4) return // IPv4

        val ihl = (versionAndIHL and 0x0F) * 4
        if (length < ihl) return

        val protocol = data[9].toInt() and 0xFF
        val srcIp = getIpAddress(data, 12)
        val dstIp = getIpAddress(data, 16)

        when (protocol) {
            17 -> { // UDP (e.g. DNS)
                handleUdpPacket(data, ihl, length, srcIp, dstIp, outStream)
            }
            6 -> { // TCP
                handleTcpPacket(data, ihl, length, srcIp, dstIp, outStream)
            }
        }
    }

    private fun handleUdpPacket(
        data: ByteArray,
        ihl: Int,
        totalLength: Int,
        srcIp: String,
        dstIp: String,
        outStream: FileOutputStream
    ) {
        if (totalLength < ihl + 8) return

        val srcPort = getPort(data, ihl)
        val dstPort = getPort(data, ihl + 2)
        val udpPayloadLength = ((data[ihl + 4].toInt() and 0xFF) shl 8) or (data[ihl + 5].toInt() and 0xFF)
        val payloadOffset = ihl + 8
        val payloadSize = udpPayloadLength - 8

        if (payloadSize <= 0 || payloadOffset + payloadSize > totalLength) return

        // Forward DNS UDP queries to upstream DNS (1.1.1.1 / 8.8.8.8) using protected datagram socket
        if (dstPort == 53) {
            totalBytesTx.addAndGet(payloadSize.toLong())
            scope.launch {
                try {
                    val socket = DatagramSocket()
                    datagramProtector(socket)
                    socket.soTimeout = 4000

                    val queryData = ByteArray(payloadSize)
                    System.arraycopy(data, payloadOffset, queryData, 0, payloadSize)

                    val dnsServer = InetAddress.getByName(if (dstIp != "10.0.0.2") dstIp else "1.1.1.1")
                    val queryPacket = DatagramPacket(queryData, queryData.size, dnsServer, 53)
                    socket.send(queryPacket)

                    val respBuffer = ByteArray(4096)
                    val respPacket = DatagramPacket(respBuffer, respBuffer.size)
                    socket.receive(respPacket)

                    val respSize = respPacket.length
                    totalBytesRx.addAndGet(respSize.toLong())

                    // Synthesize reply IPv4/UDP packet and write back into TUN
                    val replyPacket = buildUdpIpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        payload = respBuffer,
                        payloadLen = respSize
                    )

                    synchronized(outStream) {
                        if (isRunning.get()) {
                            outStream.write(replyPacket)
                            outStream.flush()
                        }
                    }
                    socket.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleTcpPacket(
        data: ByteArray,
        ihl: Int,
        totalLength: Int,
        srcIp: String,
        dstIp: String,
        outStream: FileOutputStream
    ) {
        if (totalLength < ihl + 20) return

        val srcPort = getPort(data, ihl)
        val dstPort = getPort(data, ihl + 2)
        val seqNum = getUint32(data, ihl + 4)
        val ackNum = getUint32(data, ihl + 8)
        val dataOffset = ((data[ihl + 12].toInt() shr 4) and 0x0F) * 4
        if (ihl + dataOffset > totalLength) return

        val flags = data[ihl + 13].toInt() and 0xFF

        val isSyn = (flags and 0x02) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0
        val isAck = (flags and 0x10) != 0

        val payloadOffset = ihl + dataOffset
        val payloadLen = totalLength - payloadOffset

        val sessionKey = "$srcIp:$srcPort-$dstIp:$dstPort"

        if (isSyn && !isAck) {
            // New TCP connection attempt from local client
            val session = TcpSession(sessionKey, srcIp, srcPort, dstIp, dstPort, seqNum)
            activeTcpSessions[sessionKey] = session
            session.startProxyTunnel(proxyConfig, socketProtector, outStream, totalBytesRx, totalBytesTx)
        } else if (isRst) {
            val session = activeTcpSessions.remove(sessionKey)
            session?.close()
        } else if (isFin) {
            val session = activeTcpSessions.remove(sessionKey)
            session?.handleClientFin(seqNum, ackNum, outStream)
        } else if (isAck) {
            val session = activeTcpSessions[sessionKey]
            if (session != null) {
                if (payloadLen > 0) {
                    val clientPayload = ByteArray(payloadLen)
                    System.arraycopy(data, payloadOffset, clientPayload, 0, payloadLen)
                    session.handleClientData(clientPayload, seqNum, ackNum, outStream)
                } else {
                    session.handleClientAck(ackNum)
                }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        packetReaderJob?.cancel()
        statsJob?.cancel()

        for ((_, session) in activeTcpSessions) {
            session.close()
        }
        activeTcpSessions.clear()

        try {
            vpnInterface.close()
        } catch (_: Exception) {}
    }

    private inner class TcpSession(
        val key: String,
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int,
        val initialClientSeq: Long
    ) {
        private var proxySocket: Socket? = null
        private var isClosed = false
        private var isEstablished = false

        private var serverSeq: Long = 1000L
        private var clientSeq: Long = initialClientSeq + 1
        private val writeLock = Any()

        fun startProxyTunnel(
            config: ProxyConfig,
            protector: (Socket) -> Boolean,
            outStream: FileOutputStream,
            rxCounter: AtomicLong,
            txCounter: AtomicLong
        ) {
            scope.launch {
                try {
                    val socket = Socket()
                    protector(socket)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 15000
                    socket.connect(InetSocketAddress(config.host.trim(), config.port), 8000)
                    proxySocket = socket

                    // Perform Handshake
                    when (config.type) {
                        ProxyType.SOCKS5 -> {
                            handshakeSocks5(socket, config, dstIp, dstPort)
                        }
                        ProxyType.HTTP -> {
                            handshakeHttpConnect(socket, config, dstIp, dstPort)
                        }
                        ProxyType.HTTPS -> {
                            val ssl = wrapSsl(socket, config.host.trim(), config.port)
                            protector(ssl)
                            proxySocket = ssl
                            handshakeHttpConnect(ssl, config, dstIp, dstPort)
                        }
                    }

                    isEstablished = true

                    // Send SYN+ACK packet to local Android stack
                    val synAckPacket = buildTcpIpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = serverSeq,
                        ack = clientSeq,
                        flags = 0x12 // SYN + ACK
                    )
                    serverSeq += 1L // SYN consumes 1 sequence number

                    synchronized(outStream) {
                        if (isRunning.get() && !isClosed) {
                            outStream.write(synAckPacket)
                            outStream.flush()
                        }
                    }

                    // Loop to read incoming data from upstream proxy server
                    val inStream = proxySocket?.getInputStream() ?: return@launch
                    val buffer = ByteArray(16384)

                    while (isRunning.get() && !isClosed) {
                        val read = inStream.read(buffer)
                        if (read <= 0) break
                        rxCounter.addAndGet(read.toLong())

                        // Construct TCP data packet and inject into TUN
                        val dataPacket = buildTcpDataIpPacket(
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = dstPort,
                            dstPort = srcPort,
                            seq = serverSeq,
                            ack = clientSeq,
                            payload = buffer,
                            payloadLen = read
                        )
                        serverSeq += read.toLong()

                        synchronized(outStream) {
                            if (isRunning.get() && !isClosed) {
                                outStream.write(dataPacket)
                                outStream.flush()
                            }
                        }
                    }

                    // Upstream server closed connection - send FIN+ACK
                    if (isRunning.get() && !isClosed) {
                        val finPacket = buildTcpIpPacket(
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = dstPort,
                            dstPort = srcPort,
                            seq = serverSeq,
                            ack = clientSeq,
                            flags = 0x11 // FIN + ACK
                        )
                        synchronized(outStream) {
                            outStream.write(finPacket)
                            outStream.flush()
                        }
                    }
                } catch (e: Exception) {
                    // Send RST to local client if proxy failed
                    if (!isClosed && isRunning.get()) {
                        val rstPacket = buildTcpIpPacket(
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = dstPort,
                            dstPort = srcPort,
                            seq = 0L,
                            ack = initialClientSeq + 1,
                            flags = 0x14 // RST + ACK
                        )
                        try {
                            synchronized(outStream) {
                                outStream.write(rstPacket)
                                outStream.flush()
                            }
                        } catch (_: Exception) {}
                    }
                } finally {
                    close()
                }
            }
        }

        fun handleClientData(
            payload: ByteArray,
            seq: Long,
            ack: Long,
            outStream: FileOutputStream
        ) {
            if (isClosed) return
            clientSeq = seq + payload.size.toLong()
            totalBytesTx.addAndGet(payload.size.toLong())

            scope.launch {
                try {
                    // Send immediate TCP ACK back to local client to acknowledge data reception
                    val ackPacket = buildTcpIpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = serverSeq,
                        ack = clientSeq,
                        flags = 0x10 // ACK
                    )
                    synchronized(outStream) {
                        if (isRunning.get() && !isClosed) {
                            outStream.write(ackPacket)
                            outStream.flush()
                        }
                    }

                    // Write client payload to upstream proxy socket
                    val sock = proxySocket ?: return@launch
                    synchronized(writeLock) {
                        val out = sock.getOutputStream()
                        out.write(payload)
                        out.flush()
                    }
                } catch (e: Exception) {
                    close()
                }
            }
        }

        fun handleClientAck(ack: Long) {
            // Client acknowledged server data
        }

        fun handleClientFin(seq: Long, ack: Long, outStream: FileOutputStream) {
            clientSeq = seq + 1L
            scope.launch {
                try {
                    val finAckPacket = buildTcpIpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = serverSeq,
                        ack = clientSeq,
                        flags = 0x11 // FIN + ACK
                    )
                    synchronized(outStream) {
                        if (isRunning.get()) {
                            outStream.write(finAckPacket)
                            outStream.flush()
                        }
                    }
                } catch (_: Exception) {}
                close()
            }
        }

        private fun handshakeSocks5(socket: Socket, config: ProxyConfig, targetHost: String, targetPort: Int) {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            if (config.requiresAuth) {
                output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                output.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            output.flush()

            val ver = input.read()
            val method = input.read()
            if (method == 0x02) {
                val userBytes = config.username.toByteArray(StandardCharsets.UTF_8)
                val passBytes = config.password.toByteArray(StandardCharsets.UTF_8)
                output.write(0x01)
                output.write(userBytes.size)
                output.write(userBytes)
                output.write(passBytes.size)
                output.write(passBytes)
                output.flush()
                input.read() // auth ver
                val authStatus = input.read()
                if (authStatus != 0x00) throw IOException("SOCKS5 auth failed")
            } else if (method != 0x00) {
                throw IOException("Unsupported SOCKS5 method: $method")
            }

            // SOCKS5 CONNECT
            val isIp = targetHost.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
            if (isIp) {
                val ipParts = targetHost.split(".").map { it.toInt().toByte() }
                output.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, ipParts[0], ipParts[1], ipParts[2], ipParts[3]))
            } else {
                val domainBytes = targetHost.toByteArray(StandardCharsets.UTF_8)
                output.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, domainBytes.size.toByte()))
                output.write(domainBytes)
            }
            output.write(byteArrayOf(((targetPort shr 8) and 0xFF).toByte(), (targetPort and 0xFF).toByte()))
            output.flush()

            val repVer = input.read()
            val repStatus = input.read()
            if (repStatus != 0x00) throw IOException("SOCKS5 connect error: $repStatus")

            input.read() // RSV
            val atyp = input.read()
            when (atyp) {
                0x01 -> input.skip(4 + 2) // IPv4 + Port
                0x03 -> {
                    val len = input.read()
                    input.skip((len + 2).toLong())
                }
                0x04 -> input.skip(16 + 2) // IPv6 + Port
            }
        }

        private fun handshakeHttpConnect(socket: Socket, config: ProxyConfig, targetHost: String, targetPort: Int) {
            val req = StringBuilder()
            req.append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
            req.append("Host: $targetHost:$targetPort\r\n")
            req.append("User-Agent: Mozilla/5.0 (Android; Mobile)\r\n")
            req.append("Proxy-Connection: Keep-Alive\r\n")
            if (config.requiresAuth) {
                val cred = Base64.encodeToString("${config.username}:${config.password}".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                req.append("Proxy-Authorization: Basic $cred\r\n")
            }
            req.append("\r\n")

            val out = socket.getOutputStream()
            out.write(req.toString().toByteArray(StandardCharsets.UTF_8))
            out.flush()

            val inStream = socket.getInputStream()
            val line = readLine(inStream) ?: throw IOException("Empty proxy response")
            if (!line.contains("200")) throw IOException("HTTP Connect error: $line")

            // Read remaining headers until empty line
            while (true) {
                val h = readLine(inStream)
                if (h.isNullOrBlank()) break
            }
        }

        private fun wrapSsl(plain: Socket, host: String, port: Int): SSLSocket {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(plain, host, port, true) as SSLSocket
            val params = SSLParameters()
            params.serverNames = listOf(SNIHostName(host))
            ssl.sslParameters = params
            ssl.startHandshake()
            return ssl
        }

        private fun readLine(input: InputStream): String? {
            val sb = StringBuilder()
            var c: Int
            var previousWasCr = false
            while (true) {
                c = input.read()
                if (c == -1) return if (sb.isEmpty()) null else sb.toString()
                if (c == '\n'.code) break
                if (c != '\r'.code) {
                    sb.append(c.toChar())
                }
            }
            return sb.toString()
        }

        fun close() {
            if (isClosed) return
            isClosed = true
            activeTcpSessions.remove(key)
            try {
                proxySocket?.close()
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun getIpAddress(data: ByteArray, offset: Int): String {
            return "${data[offset].toInt() and 0xFF}.${data[offset + 1].toInt() and 0xFF}.${data[offset + 2].toInt() and 0xFF}.${data[offset + 3].toInt() and 0xFF}"
        }

        fun getPort(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        }

        fun getUint32(data: ByteArray, offset: Int): Long {
            return ((data[offset].toLong() and 0xFF) shl 24) or
                    ((data[offset + 1].toLong() and 0xFF) shl 16) or
                    ((data[offset + 2].toLong() and 0xFF) shl 8) or
                    (data[offset + 3].toLong() and 0xFF)
        }

        fun buildUdpIpPacket(
            srcIp: String,
            dstIp: String,
            srcPort: Int,
            dstPort: Int,
            payload: ByteArray,
            payloadLen: Int
        ): ByteArray {
            val totalLen = 20 + 8 + payloadLen
            val packet = ByteArray(totalLen)
            val buffer = ByteBuffer.wrap(packet)

            // IPv4 Header (20 bytes)
            buffer.put(0x45.toByte()) // Ver 4, IHL 5
            buffer.put(0.toByte())
            buffer.putShort(totalLen.toShort())
            buffer.putShort(0.toShort())
            buffer.putShort(0.toShort())
            buffer.put(64.toByte()) // TTL
            buffer.put(17.toByte()) // UDP
            buffer.putShort(0.toShort()) // Checksum placeholder

            val srcParts = srcIp.split(".").map { it.toInt().toByte() }
            val dstParts = dstIp.split(".").map { it.toInt().toByte() }
            buffer.put(byteArrayOf(srcParts[0], srcParts[1], srcParts[2], srcParts[3]))
            buffer.put(byteArrayOf(dstParts[0], dstParts[1], dstParts[2], dstParts[3]))

            // Calculate IP Checksum
            val ipChecksum = calculateChecksum(packet, 0, 20)
            buffer.putShort(10, ipChecksum.toShort())

            // UDP Header (8 bytes)
            buffer.putShort(srcPort.toShort())
            buffer.putShort(dstPort.toShort())
            buffer.putShort((8 + payloadLen).toShort())
            buffer.putShort(0.toShort())

            // Payload
            buffer.put(payload, 0, payloadLen)
            return packet
        }

        fun buildTcpIpPacket(
            srcIp: String,
            dstIp: String,
            srcPort: Int,
            dstPort: Int,
            seq: Long,
            ack: Long,
            flags: Int
        ): ByteArray {
            val totalLen = 20 + 20
            val packet = ByteArray(totalLen)
            val buffer = ByteBuffer.wrap(packet)

            // IP Header (20 bytes)
            buffer.put(0x45.toByte())
            buffer.put(0.toByte())
            buffer.putShort(totalLen.toShort())
            buffer.putShort(1234.toShort())
            buffer.putShort(0x4000.toShort()) // Don't fragment
            buffer.put(64.toByte())
            buffer.put(6.toByte()) // TCP
            buffer.putShort(0.toShort())

            val srcParts = srcIp.split(".").map { it.toInt().toByte() }
            val dstParts = dstIp.split(".").map { it.toInt().toByte() }
            buffer.put(byteArrayOf(srcParts[0], srcParts[1], srcParts[2], srcParts[3]))
            buffer.put(byteArrayOf(dstParts[0], dstParts[1], dstParts[2], dstParts[3]))

            val ipChecksum = calculateChecksum(packet, 0, 20)
            buffer.putShort(10, ipChecksum.toShort())

            // TCP Header (20 bytes)
            buffer.putShort(srcPort.toShort())
            buffer.putShort(dstPort.toShort())
            buffer.putInt(seq.toInt())
            buffer.putInt(ack.toInt())
            buffer.put(0x50.toByte()) // 5 words = 20 bytes
            buffer.put(flags.toByte())
            buffer.putShort(65535.toShort()) // Window
            buffer.putShort(0.toShort()) // Checksum
            buffer.putShort(0.toShort()) // Urgent pointer

            val tcpChecksum = calculateTcpChecksum(packet, 20, 20, srcParts, dstParts)
            buffer.putShort(20 + 16, tcpChecksum.toShort())

            return packet
        }

        fun buildTcpDataIpPacket(
            srcIp: String,
            dstIp: String,
            srcPort: Int,
            dstPort: Int,
            seq: Long,
            ack: Long,
            payload: ByteArray,
            payloadLen: Int
        ): ByteArray {
            val totalLen = 20 + 20 + payloadLen
            val packet = ByteArray(totalLen)
            val buffer = ByteBuffer.wrap(packet)

            buffer.put(0x45.toByte())
            buffer.put(0.toByte())
            buffer.putShort(totalLen.toShort())
            buffer.putShort(1235.toShort())
            buffer.putShort(0x4000.toShort())
            buffer.put(64.toByte())
            buffer.put(6.toByte())
            buffer.putShort(0.toShort())

            val srcParts = srcIp.split(".").map { it.toInt().toByte() }
            val dstParts = dstIp.split(".").map { it.toInt().toByte() }
            buffer.put(byteArrayOf(srcParts[0], srcParts[1], srcParts[2], srcParts[3]))
            buffer.put(byteArrayOf(dstParts[0], dstParts[1], dstParts[2], dstParts[3]))

            val ipChecksum = calculateChecksum(packet, 0, 20)
            buffer.putShort(10, ipChecksum.toShort())

            buffer.putShort(srcPort.toShort())
            buffer.putShort(dstPort.toShort())
            buffer.putInt(seq.toInt())
            buffer.putInt(ack.toInt())
            buffer.put(0x50.toByte())
            buffer.put(0x18.toByte()) // PSH + ACK
            buffer.putShort(65535.toShort())
            buffer.putShort(0.toShort())
            buffer.putShort(0.toShort())

            buffer.put(payload, 0, payloadLen)

            val tcpChecksum = calculateTcpChecksum(packet, 20, 20 + payloadLen, srcParts, dstParts)
            buffer.putShort(20 + 16, tcpChecksum.toShort())

            return packet
        }

        fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var i = offset
            while (i < offset + length - 1) {
                val b1 = data[i].toLong() and 0xFF
                val b2 = data[i + 1].toLong() and 0xFF
                sum += (b1 shl 8) or b2
                i += 2
            }
            if (i < offset + length) {
                sum += (data[i].toLong() and 0xFF) shl 8
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return (sum.inv() and 0xFFFF).toInt()
        }

        fun calculateTcpChecksum(
            data: ByteArray,
            tcpOffset: Int,
            tcpLength: Int,
            srcIp: List<Byte>,
            dstIp: List<Byte>
        ): Int {
            var sum = 0L
            // Pseudo-header
            sum += ((srcIp[0].toLong() and 0xFF) shl 8) or (srcIp[1].toLong() and 0xFF)
            sum += ((srcIp[2].toLong() and 0xFF) shl 8) or (srcIp[3].toLong() and 0xFF)
            sum += ((dstIp[0].toLong() and 0xFF) shl 8) or (dstIp[1].toLong() and 0xFF)
            sum += ((dstIp[2].toLong() and 0xFF) shl 8) or (dstIp[3].toLong() and 0xFF)
            sum += 6L // Protocol TCP
            sum += tcpLength.toLong()

            var i = tcpOffset
            while (i < tcpOffset + tcpLength - 1) {
                val b1 = data[i].toLong() and 0xFF
                val b2 = data[i + 1].toLong() and 0xFF
                sum += (b1 shl 8) or b2
                i += 2
            }
            if (i < tcpOffset + tcpLength) {
                sum += (data[i].toLong() and 0xFF) shl 8
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return (sum.inv() and 0xFFFF).toInt()
        }
    }
}
