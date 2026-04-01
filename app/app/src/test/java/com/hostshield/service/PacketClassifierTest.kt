package com.hostshield.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PacketClassifier].
 *
 * Covers: isIpv4UdpDns, isIpv6UdpDns, isIpv4TcpDns, isIpv6TcpDns.
 */
class PacketClassifierTest {

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Build a minimal IPv4 UDP packet with the given dest port.
     * IHL = 5 (20 bytes header), protocol = 17 (UDP), version = 4.
     * Total minimum: 20 (IP) + 8 (UDP) = 28 bytes.
     */
    private fun ipv4UdpPacket(destPort: Int): ByteArray {
        val packet = ByteArray(28)
        packet[0] = 0x45.toByte()           // version=4, IHL=5
        packet[9] = 17.toByte()             // protocol = UDP
        // UDP dest port at offset IHL*4 + 2 = 22
        packet[22] = (destPort shr 8).toByte()
        packet[23] = (destPort and 0xFF).toByte()
        return packet
    }

    /**
     * Build a minimal IPv6 UDP packet with the given dest port.
     * 40-byte IPv6 header + 8-byte UDP header = 48 bytes minimum.
     * Next header (byte 6) = 17 (UDP). Version nibble = 6.
     */
    private fun ipv6UdpPacket(destPort: Int): ByteArray {
        val packet = ByteArray(48)
        packet[0] = 0x60.toByte()           // version=6
        packet[6] = 17.toByte()             // next header = UDP
        // UDP dest port at offset 42
        packet[42] = (destPort shr 8).toByte()
        packet[43] = (destPort and 0xFF).toByte()
        return packet
    }

    /**
     * Build a minimal IPv4 TCP packet with the given dest port.
     * IHL = 5 (20 bytes), protocol = 6 (TCP), version = 4.
     * Total minimum: 20 (IP) + 20 (TCP) = 40 bytes.
     */
    private fun ipv4TcpPacket(destPort: Int): ByteArray {
        val packet = ByteArray(40)
        packet[0] = 0x45.toByte()           // version=4, IHL=5
        packet[9] = 6.toByte()              // protocol = TCP
        // TCP dest port at offset IHL*4 + 2 = 22
        packet[22] = (destPort shr 8).toByte()
        packet[23] = (destPort and 0xFF).toByte()
        return packet
    }

    /**
     * Build a minimal IPv6 TCP packet with the given dest port.
     * 40-byte IPv6 header + 20-byte TCP header = 60 bytes minimum.
     * Next header (byte 6) = 6 (TCP). Version nibble = 6.
     */
    private fun ipv6TcpPacket(destPort: Int): ByteArray {
        val packet = ByteArray(60)
        packet[0] = 0x60.toByte()           // version=6
        packet[6] = 6.toByte()              // next header = TCP
        // TCP dest port at offset 42
        packet[42] = (destPort shr 8).toByte()
        packet[43] = (destPort and 0xFF).toByte()
        return packet
    }

    // ── isIpv4UdpDns ─────────────────────────────────────────

    @Test
    fun `isIpv4UdpDns returns true for valid DNS packet`() {
        val p = ipv4UdpPacket(53)
        assertTrue(PacketClassifier.isIpv4UdpDns(p, p.size))
    }

    @Test
    fun `isIpv4UdpDns returns false for non-DNS port`() {
        val p = ipv4UdpPacket(80)
        assertFalse(PacketClassifier.isIpv4UdpDns(p, p.size))
    }

    @Test
    fun `isIpv4UdpDns returns false for too-short packet`() {
        val p = ByteArray(27)
        p[0] = 0x45.toByte()
        p[9] = 17.toByte()
        assertFalse(PacketClassifier.isIpv4UdpDns(p, p.size))
    }

    @Test
    fun `isIpv4UdpDns returns false for wrong IP version`() {
        val p = ipv4UdpPacket(53)
        p[0] = 0x65.toByte() // version=6 instead of 4
        assertFalse(PacketClassifier.isIpv4UdpDns(p, p.size))
    }

    @Test
    fun `isIpv4UdpDns returns false for wrong protocol`() {
        val p = ipv4UdpPacket(53)
        p[9] = 6.toByte() // TCP instead of UDP
        assertFalse(PacketClassifier.isIpv4UdpDns(p, p.size))
    }

    // ── isIpv6UdpDns ─────────────────────────────────────────

    @Test
    fun `isIpv6UdpDns returns true for valid IPv6 UDP DNS packet`() {
        val p = ipv6UdpPacket(53)
        assertTrue(PacketClassifier.isIpv6UdpDns(p, p.size))
    }

    @Test
    fun `isIpv6UdpDns returns false for wrong port`() {
        val p = ipv6UdpPacket(443)
        assertFalse(PacketClassifier.isIpv6UdpDns(p, p.size))
    }

    @Test
    fun `isIpv6UdpDns returns false for too-short packet`() {
        val p = ByteArray(47)
        p[6] = 17.toByte()
        assertFalse(PacketClassifier.isIpv6UdpDns(p, p.size))
    }

    // ── isIpv4TcpDns ─────────────────────────────────────────

    @Test
    fun `isIpv4TcpDns returns true for valid IPv4 TCP DNS packet`() {
        val p = ipv4TcpPacket(53)
        assertTrue(PacketClassifier.isIpv4TcpDns(p, p.size))
    }

    @Test
    fun `isIpv4TcpDns returns false for wrong port`() {
        val p = ipv4TcpPacket(8080)
        assertFalse(PacketClassifier.isIpv4TcpDns(p, p.size))
    }

    @Test
    fun `isIpv4TcpDns returns false for too-short packet`() {
        val p = ByteArray(39)
        p[0] = 0x45.toByte()
        p[9] = 6.toByte()
        assertFalse(PacketClassifier.isIpv4TcpDns(p, p.size))
    }

    // ── isIpv6TcpDns ─────────────────────────────────────────

    @Test
    fun `isIpv6TcpDns returns true for valid IPv6 TCP DNS packet`() {
        val p = ipv6TcpPacket(53)
        assertTrue(PacketClassifier.isIpv6TcpDns(p, p.size))
    }

    @Test
    fun `isIpv6TcpDns returns false for wrong port`() {
        val p = ipv6TcpPacket(5353)
        assertFalse(PacketClassifier.isIpv6TcpDns(p, p.size))
    }

    // ── DNS_PORT constant ────────────────────────────────────

    @Test
    fun `DNS_PORT constant is 53`() {
        assertEquals(53, PacketClassifier.DNS_PORT)
    }
}
