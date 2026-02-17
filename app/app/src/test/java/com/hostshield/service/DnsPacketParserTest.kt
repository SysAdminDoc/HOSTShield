package com.hostshield.service

import org.junit.Assert.*
import org.junit.Test

class DnsPacketParserTest {

    /**
     * Test DNS hostname extraction from raw packets.
     * DNS question section format: sequence of labels (length byte + chars), terminated by 0x00.
     * Then 2 bytes QTYPE + 2 bytes QCLASS.
     */

    @Test
    fun `extract hostname from valid DNS query`() {
        // Build a minimal DNS query for "ads.example.com"
        val packet = buildDnsQuery("ads.example.com")
        val hostname = extractHostname(packet)
        assertEquals("ads.example.com", hostname)
    }

    @Test
    fun `extract hostname with subdomain`() {
        val packet = buildDnsQuery("deep.sub.domain.example.com")
        val hostname = extractHostname(packet)
        assertEquals("deep.sub.domain.example.com", hostname)
    }

    @Test
    fun `extract hostname single label TLD`() {
        val packet = buildDnsQuery("localhost")
        val hostname = extractHostname(packet)
        assertEquals("localhost", hostname)
    }

    @Test
    fun `malformed packet returns null`() {
        val packet = byteArrayOf(0, 0, 0, 0) // Too short
        val hostname = extractHostname(packet)
        assertNull(hostname)
    }

    @Test
    fun `empty hostname returns null`() {
        // DNS header (12 bytes) + 0x00 (empty name) + type/class
        val packet = ByteArray(12) + byteArrayOf(0, 0, 1, 0, 1)
        val hostname = extractHostname(packet)
        assertNull(hostname)
    }

    @Test
    fun `build NXDOMAIN response has correct flags`() {
        val query = buildDnsQuery("blocked.com")
        val response = buildNxdomainResponse(query)

        // Byte 2-3 should have QR=1 (response), RCODE=3 (NXDOMAIN)
        // Flags: 0x8403 = 1000 0100 0000 0011
        val flags = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        assertTrue("QR bit should be set", (flags and 0x8000) != 0)
        assertEquals("RCODE should be 3 (NXDOMAIN)", 3, flags and 0x000F)
    }

    // ---- Helper functions that mirror RootDnsLogger's DNS parsing ----

    private fun extractHostname(packet: ByteArray): String? {
        if (packet.size < 13) return null
        val sb = StringBuilder()
        var pos = 12 // Skip DNS header
        while (pos < packet.size) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) break
            if (pos + len >= packet.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) {
                sb.append(packet[pos + i].toInt().toChar())
            }
            pos += len + 1
        }
        return sb.toString().ifEmpty { null }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val header = ByteArray(12).apply {
            this[0] = 0xAB.toByte() // ID high
            this[1] = 0xCD.toByte() // ID low
            this[2] = 0x01          // RD=1
            this[5] = 0x01          // QDCOUNT=1
        }
        val question = buildQuestionSection(hostname)
        return header + question
    }

    private fun buildQuestionSection(hostname: String): ByteArray {
        val labels = hostname.split('.')
        val buf = mutableListOf<Byte>()
        for (label in labels) {
            buf.add(label.length.toByte())
            for (c in label) buf.add(c.code.toByte())
        }
        buf.add(0) // End of name
        buf.addAll(listOf(0, 1, 0, 1)) // QTYPE=A, QCLASS=IN
        return buf.toByteArray()
    }

    private fun buildNxdomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size >= 4) {
            response[2] = 0x84.toByte() // QR=1, AA=1
            response[3] = 0x03.toByte() // RCODE=3 (NXDOMAIN)
        }
        return response
    }
}
