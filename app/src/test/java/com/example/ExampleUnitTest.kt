package com.example

import com.example.model.ConnectionState
import com.example.model.ProxyConfig
import com.example.model.ProxyType
import com.example.proxy.IpGeoService
import com.example.proxy.TunPacketRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `test ProxyConfig validation for valid configs`() {
        val socks5 = ProxyConfig(
            type = ProxyType.SOCKS5,
            host = "proxy.example.com",
            port = 1080,
            username = "user",
            password = "password"
        )
        val (isValid1, err1) = socks5.isValid()
        assertTrue(isValid1)
        assertEquals(null, err1)

        val http = ProxyConfig(
            type = ProxyType.HTTP,
            host = "192.168.1.100",
            port = 8080,
            username = "",
            password = ""
        )
        val (isValid2, err2) = http.isValid()
        assertTrue(isValid2)
        assertEquals(null, err2)

        val https = ProxyConfig(
            type = ProxyType.HTTPS,
            host = "secure-proxy.net",
            port = 443,
            username = "admin",
            password = "secretpassword"
        )
        val (isValid3, err3) = https.isValid()
        assertTrue(isValid3)
        assertEquals(null, err3)
    }

    @Test
    fun `test ProxyConfig validation for invalid configs`() {
        // Empty host
        val emptyHost = ProxyConfig(
            type = ProxyType.SOCKS5,
            host = "",
            port = 1080
        )
        val (isValid1, err1) = emptyHost.isValid()
        assertFalse(isValid1)
        assertNotNull(err1)

        // Invalid port: 0
        val portZero = ProxyConfig(
            type = ProxyType.HTTP,
            host = "proxy.org",
            port = 0
        )
        val (isValid2, err2) = portZero.isValid()
        assertFalse(isValid2)
        assertNotNull(err2)

        // Invalid port: > 65535
        val portTooLarge = ProxyConfig(
            type = ProxyType.HTTP,
            host = "proxy.org",
            port = 70000
        )
        val (isValid3, err3) = portTooLarge.isValid()
        assertFalse(isValid3)
        assertNotNull(err3)
    }

    @Test
    fun `test Country Code to Flag emoji mapping`() {
        val geoService = IpGeoService()
        assertEquals("🇸🇦", geoService.countryCodeToFlagEmoji("SA"))
        assertEquals("🇦🇪", geoService.countryCodeToFlagEmoji("AE"))
        assertEquals("🇺🇸", geoService.countryCodeToFlagEmoji("US"))
        assertEquals("🇬🇧", geoService.countryCodeToFlagEmoji("GB"))
        assertEquals("🇩🇪", geoService.countryCodeToFlagEmoji("DE"))
        assertEquals("🇯🇵", geoService.countryCodeToFlagEmoji("JP"))
        assertEquals("🌐", geoService.countryCodeToFlagEmoji(null))
        assertEquals("🌐", geoService.countryCodeToFlagEmoji(""))
        assertEquals("🌐", geoService.countryCodeToFlagEmoji("123"))
    }

    @Test
    fun `test IP and Port parsing helpers`() {
        // Sample IPv4 header bytes
        val testData = byteArrayOf(
            0x45.toByte(), 0x00, 0x00, 0x3C,
            0x00, 0x01, 0x00, 0x00,
            0x40, 0x06, 0x00, 0x00,
            10.toByte(), 0.toByte(), 0.toByte(), 2.toByte(), // Src: 10.0.0.2
            1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), // Dst: 1.1.1.1
            0x1F.toByte(), 0x90.toByte(), // SrcPort: 8080
            0x00.toByte(), 0x50.toByte()  // DstPort: 80
        )

        assertEquals("10.0.0.2", TunPacketRouter.getIpAddress(testData, 12))
        assertEquals("1.1.1.1", TunPacketRouter.getIpAddress(testData, 16))
        assertEquals(8080, TunPacketRouter.getPort(testData, 20))
        assertEquals(80, TunPacketRouter.getPort(testData, 22))
    }

    @Test
    fun `test TCP IP Packet builder creates valid IPv4 and TCP headers`() {
        val synAck = TunPacketRouter.buildTcpIpPacket(
            srcIp = "1.1.1.1",
            dstIp = "10.0.0.2",
            srcPort = 443,
            dstPort = 50000,
            seq = 1000L,
            ack = 2001L,
            flags = 0x12 // SYN-ACK
        )

        // Total length should be 40 bytes (20 IP + 20 TCP)
        assertEquals(40, synAck.size)

        // Version and IHL: IPv4 (4) and 5 words (0x45)
        assertEquals(0x45.toByte(), synAck[0])

        // Protocol: TCP (6)
        assertEquals(6.toByte(), synAck[9])

        // Src and Dst IP
        assertEquals("1.1.1.1", TunPacketRouter.getIpAddress(synAck, 12))
        assertEquals("10.0.0.2", TunPacketRouter.getIpAddress(synAck, 16))

        // Src and Dst Port
        assertEquals(443, TunPacketRouter.getPort(synAck, 20))
        assertEquals(50000, TunPacketRouter.getPort(synAck, 22))

        // TCP Sequence and Ack
        assertEquals(1000L, TunPacketRouter.getUint32(synAck, 24))
        assertEquals(2001L, TunPacketRouter.getUint32(synAck, 28))

        // TCP Flags
        assertEquals(0x12.toByte(), synAck[33])
    }

    @Test
    fun `test UDP IP Packet builder creates valid DNS packet`() {
        val sampleDnsPayload = byteArrayOf(0x00, 0x01, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01, 0x00, 0x01)
        val udpPacket = TunPacketRouter.buildUdpIpPacket(
            srcIp = "1.1.1.1",
            dstIp = "10.0.0.2",
            srcPort = 53,
            dstPort = 54321,
            payload = sampleDnsPayload,
            payloadLen = sampleDnsPayload.size
        )

        // 20 IP + 8 UDP + 8 Payload = 36 bytes
        assertEquals(36, udpPacket.size)
        assertEquals(0x45.toByte(), udpPacket[0])
        assertEquals(17.toByte(), udpPacket[9]) // UDP protocol 17
        assertEquals("1.1.1.1", TunPacketRouter.getIpAddress(udpPacket, 12))
        assertEquals("10.0.0.2", TunPacketRouter.getIpAddress(udpPacket, 16))
        assertEquals(53, TunPacketRouter.getPort(udpPacket, 20))
        assertEquals(54321, TunPacketRouter.getPort(udpPacket, 22))
    }

    @Test
    fun `test ConnectionState states`() {
        val disconnected = ConnectionState.Disconnected
        assertTrue(disconnected.isDisconnected)
        assertFalse(disconnected.isConnected)
        assertFalse(disconnected.isConnecting)

        val connecting = ConnectionState.Connecting("Connecting...")
        assertFalse(connecting.isDisconnected)
        assertFalse(connecting.isConnected)
        assertTrue(connecting.isConnecting)

        val connected = ConnectionState.Connected
        assertFalse(connected.isDisconnected)
        assertTrue(connected.isConnected)
        assertFalse(connected.isConnecting)

        val error = ConnectionState.Error("Auth failed")
        assertFalse(error.isConnected)
        assertFalse(error.isConnecting)
    }
}
