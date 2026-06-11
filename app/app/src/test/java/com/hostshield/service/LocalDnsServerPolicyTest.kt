package com.hostshield.service

import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDnsServerPolicyTest {

    @Test
    fun `local client policy allows private link local loopback and ula clients`() {
        val allowed = listOf(
            "127.0.0.1",
            "10.0.0.8",
            "172.16.1.8",
            "192.168.1.8",
            "169.254.1.8",
            "::1",
            "fe80::1",
            "fc00::1",
            "fd12:3456:789a::1"
        )

        for (address in allowed) {
            assertTrue(address, isAllowedLocalDnsClient(InetAddress.getByName(address)))
        }
    }

    @Test
    fun `local client policy rejects public clients unless explicitly allowed`() {
        val publicIpv4 = InetAddress.getByName("8.8.8.8")
        val publicIpv6 = InetAddress.getByName("2001:4860:4860::8888")

        assertFalse(isAllowedLocalDnsClient(publicIpv4))
        assertFalse(isAllowedLocalDnsClient(publicIpv6))
        assertTrue(isAllowedLocalDnsClient(publicIpv4, allowExternalClients = true))
        assertTrue(isAllowedLocalDnsClient(publicIpv6, allowExternalClients = true))
    }

    @Test
    fun `rate limiter allows configured budget and resets after window`() {
        var now = 1_000L
        val limiter = LocalDnsClientRateLimiter(
            maxQueriesPerWindow = 2,
            windowMillis = 1_000L,
            nowMillis = { now }
        )
        val client = InetAddress.getByName("192.168.1.20")

        assertTrue(limiter.tryAcquire(client))
        assertTrue(limiter.tryAcquire(client))
        assertFalse(limiter.tryAcquire(client))

        now += 1_000L
        assertTrue(limiter.tryAcquire(client))
    }

    @Test
    fun `rate limiter tracks clients independently`() {
        val limiter = LocalDnsClientRateLimiter(maxQueriesPerWindow = 1, nowMillis = { 5_000L })

        assertTrue(limiter.tryAcquire(InetAddress.getByName("192.168.1.20")))
        assertFalse(limiter.tryAcquire(InetAddress.getByName("192.168.1.20")))
        assertTrue(limiter.tryAcquire(InetAddress.getByName("192.168.1.21")))
    }

    @Test
    fun `udp response keeps responses within safe datagram size`() {
        val query = buildQuery("example.com")
        val response = byteArrayOf(1, 2, 3)

        assertSame(response, localDnsUdpResponse(query, response))
    }

    @Test
    fun `udp response truncates oversized upstream responses`() {
        val query = buildQuery("example.com")
        val oversized = ByteArray(LOCAL_DNS_MAX_UDP_RESPONSE_BYTES + 1) { 0x7F }

        val truncated = localDnsUdpResponse(query, oversized)

        assertTrue(truncated.size < oversized.size)
        assertArrayEquals(query.copyOfRange(0, 2), truncated.copyOfRange(0, 2))
        assertEquals(0x83, truncated[2].toInt() and 0xFF)
        assertEquals(0x80, truncated[3].toInt() and 0xFF)
        assertEquals(1, u16(truncated, 4))
        assertEquals(0, u16(truncated, 6))
        assertEquals(0, u16(truncated, 8))
        assertEquals(0, u16(truncated, 10))
        assertEquals(DnsPacketBuilder.parseDomain(query), DnsPacketBuilder.parseDomain(truncated))
    }

    private fun buildQuery(hostname: String, qtype: Int = 1): ByteArray {
        val header = ByteArray(12).apply {
            this[0] = 0xBE.toByte()
            this[1] = 0xEF.toByte()
            this[2] = 0x01
            this[5] = 0x01
        }
        val question = mutableListOf<Byte>()
        for (label in hostname.split('.')) {
            question += label.length.toByte()
            for (char in label) question += char.code.toByte()
        }
        question += 0
        question += (qtype shr 8).toByte()
        question += (qtype and 0xFF).toByte()
        question += 0
        question += 1
        return header + question.toByteArray()
    }

    private fun u16(packet: ByteArray, offset: Int): Int {
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }
}
