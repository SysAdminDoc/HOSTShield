package com.hostshield.service

import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeSearchEnforcerTest {

    @Test
    fun `google country domains are included without matching adjacent google hosts`() {
        val enforcer = SafeSearchEnforcer()

        assertTrue(enforcer.isSafeSearchDomain("google.co.uk"))
        assertTrue(enforcer.isSafeSearchDomain("www.google.com.br"))
        assertTrue(enforcer.isSafeSearchDomain("www.google.de"))
        assertFalse(enforcer.isSafeSearchDomain("googleapis.com"))
        assertFalse(enforcer.isSafeSearchDomain("www.google.evil.example"))
    }

    @Test
    fun `a query returns safe ipv4 answer from resolved endpoint`() {
        val enforcer = SafeSearchEnforcer().apply {
            addressResolver = { host ->
                assertEquals("strict.bing.com", host)
                listOf(InetAddress.getByName("203.0.113.25"))
            }
        }
        val query = buildQuery("www.bing.com", qtype = 1)

        val response = requireNotNull(enforcer.buildSafeResponse(query, "www.bing.com"))

        assertEquals(1, answerCount(response))
        val answerOffset = answerOffset(response)
        assertEquals(1, u16(response, answerOffset + 2))
        assertArrayEquals(byteArrayOf(203.toByte(), 0, 113, 25), rdata(response, answerOffset))
    }

    @Test
    fun `a query falls back to bundled safe ipv4 when endpoint resolution fails`() {
        val enforcer = SafeSearchEnforcer().apply {
            addressResolver = { throw IllegalStateException("resolver unavailable") }
        }
        val query = buildQuery("google.com", qtype = 1)

        val response = requireNotNull(enforcer.buildSafeResponse(query, "google.com"))

        assertEquals(1, answerCount(response))
        assertArrayEquals(byteArrayOf(216.toByte(), 239.toByte(), 38, 120), rdata(response, answerOffset(response)))
    }

    @Test
    fun `aaaa query returns ipv4 mapped safe ipv6 when available`() {
        val enforcer = SafeSearchEnforcer().apply {
            addressResolver = { emptyList() }
        }
        val query = buildQuery("www.google.co.uk", qtype = 28)

        val response = requireNotNull(enforcer.buildSafeResponse(query, "www.google.co.uk"))

        assertEquals(1, answerCount(response))
        val answerOffset = answerOffset(response)
        assertEquals(28, u16(response, answerOffset + 2))
        assertArrayEquals(ipv4Mapped("216.239.38.120"), rdata(response, answerOffset))
    }

    @Test
    fun `aaaa query returns nodata when safe endpoint has no ipv6`() {
        val enforcer = SafeSearchEnforcer().apply {
            addressResolver = { emptyList() }
        }
        val query = buildQuery("duckduckgo.com", qtype = 28)

        val response = requireNotNull(enforcer.buildSafeResponse(query, "duckduckgo.com"))

        assertEquals(0, rcode(response))
        assertEquals(0, answerCount(response))
        assertEquals("duckduckgo.com", DnsPacketBuilder.parseDomain(response))
    }

    @Test
    fun `https query returns nodata to suppress alternate endpoint metadata`() {
        val enforcer = SafeSearchEnforcer()
        val query = buildQuery("youtube.com", qtype = 65)

        val response = requireNotNull(enforcer.buildSafeResponse(query, "youtube.com"))

        assertEquals(0, rcode(response))
        assertEquals(0, answerCount(response))
        assertEquals(65, DnsPacketBuilder.parseQueryType(response))
    }

    @Test
    fun `resolved endpoint is cached within ttl`() {
        var now = 10_000L
        var resolveCalls = 0
        val enforcer = SafeSearchEnforcer().apply {
            nowMillis = { now }
            addressResolver = {
                resolveCalls += 1
                listOf(InetAddress.getByName("203.0.113.44"))
            }
        }
        val query = buildQuery("bing.com", qtype = 1)

        enforcer.buildSafeResponse(query, "bing.com")
        enforcer.buildSafeResponse(query, "bing.com")
        now += 6 * 60 * 60 * 1000L + 1
        enforcer.buildSafeResponse(query, "bing.com")

        assertEquals(2, resolveCalls)
    }

    private fun buildQuery(hostname: String, qtype: Int): ByteArray {
        val header = ByteArray(12).apply {
            this[0] = 0xCA.toByte()
            this[1] = 0xFE.toByte()
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

    private fun answerOffset(packet: ByteArray): Int {
        val nameEnd = DnsPacketParser.skipDnsName(packet, 12)
        return nameEnd + 4
    }

    private fun answerCount(packet: ByteArray): Int = u16(packet, 6)

    private fun rcode(packet: ByteArray): Int = u16(packet, 2) and 0x000F

    private fun rdata(packet: ByteArray, answerOffset: Int): ByteArray {
        val rdLength = u16(packet, answerOffset + 10)
        return packet.copyOfRange(answerOffset + 12, answerOffset + 12 + rdLength)
    }

    private fun ipv4Mapped(ipv4: String): ByteArray {
        val octets = ipv4.split(".").map { it.toInt().toByte() }
        return ByteArray(16).apply {
            this[10] = 0xFF.toByte()
            this[11] = 0xFF.toByte()
            for (index in 0..3) {
                this[12 + index] = octets[index]
            }
        }
    }

    private fun u16(packet: ByteArray, offset: Int): Int {
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }
}
