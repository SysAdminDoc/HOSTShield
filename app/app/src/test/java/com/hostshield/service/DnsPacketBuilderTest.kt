package com.hostshield.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DnsPacketBuilder].
 *
 * Covers: parseQueryType, queryTypeLabel, buildNxdomain, buildRefused,
 * buildZeroIp, buildBlockResponse, parseDomain edge cases.
 */
class DnsPacketBuilderTest {

    // ── Helpers ──────────────────────────────────────────────

    /** Build a minimal DNS query packet for [hostname] with the given [qtype]. */
    private fun buildQuery(hostname: String, qtype: Int = 1, qclass: Int = 1): ByteArray {
        val header = ByteArray(12).apply {
            this[0] = 0xDE.toByte()   // ID high
            this[1] = 0xAD.toByte()   // ID low
            this[2] = 0x01            // RD=1
            this[5] = 0x01            // QDCOUNT=1
        }
        val question = mutableListOf<Byte>()
        for (label in hostname.split('.')) {
            question.add(label.length.toByte())
            for (c in label) question.add(c.code.toByte())
        }
        question.add(0) // root terminator
        question.add((qtype shr 8).toByte())
        question.add((qtype and 0xFF).toByte())
        question.add((qclass shr 8).toByte())
        question.add((qclass and 0xFF).toByte())
        return header + question.toByteArray()
    }

    /** Read 16-bit unsigned value at [offset]. */
    private fun u16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    /** Read flags word (bytes 2-3). */
    private fun flags(packet: ByteArray): Int = u16(packet, 2)

    /** Extract RCODE from flags. */
    private fun rcode(packet: ByteArray): Int = flags(packet) and 0x000F

    /** True when QR bit (bit 15) is set. */
    private fun isResponse(packet: ByteArray): Boolean = (flags(packet) and 0x8000) != 0

    // ── parseQueryType ──────────────────────────────────────

    @Test
    fun `parseQueryType returns A for type 1 query`() {
        val query = buildQuery("example.com", qtype = 1)
        assertEquals(1, DnsPacketBuilder.parseQueryType(query))
    }

    @Test
    fun `parseQueryType returns AAAA for type 28 query`() {
        val query = buildQuery("example.com", qtype = 28)
        assertEquals(28, DnsPacketBuilder.parseQueryType(query))
    }

    @Test
    fun `parseQueryType returns CNAME for type 5`() {
        assertEquals(5, DnsPacketBuilder.parseQueryType(buildQuery("x.co", qtype = 5)))
    }

    @Test
    fun `parseQueryType returns MX for type 15`() {
        assertEquals(15, DnsPacketBuilder.parseQueryType(buildQuery("mail.co", qtype = 15)))
    }

    @Test
    fun `parseQueryType returns HTTPS for type 65`() {
        assertEquals(65, DnsPacketBuilder.parseQueryType(buildQuery("cdn.co", qtype = 65)))
    }

    @Test
    fun `parseQueryType returns SVCB for type 64`() {
        assertEquals(64, DnsPacketBuilder.parseQueryType(buildQuery("svc.co", qtype = 64)))
    }

    @Test
    fun `parseQueryType returns -1 for packet shorter than 14 bytes`() {
        assertEquals(-1, DnsPacketBuilder.parseQueryType(ByteArray(13)))
    }

    @Test
    fun `parseQueryType returns -1 for empty byte array`() {
        assertEquals(-1, DnsPacketBuilder.parseQueryType(ByteArray(0)))
    }

    @Test
    fun `parseQueryType handles single-label hostname`() {
        assertEquals(1, DnsPacketBuilder.parseQueryType(buildQuery("localhost", qtype = 1)))
    }

    // ── queryTypeLabel ──────────────────────────────────────

    @Test
    fun `queryTypeLabel maps all known types`() {
        val expected = mapOf(
            1 to "A", 2 to "NS", 5 to "CNAME", 6 to "SOA", 12 to "PTR",
            15 to "MX", 16 to "TXT", 28 to "AAAA", 33 to "SRV", 41 to "OPT",
            43 to "DS", 48 to "DNSKEY", 64 to "SVCB", 65 to "HTTPS", 255 to "ANY"
        )
        for ((code, label) in expected) {
            assertEquals("qtype $code", label, DnsPacketBuilder.queryTypeLabel(code))
        }
    }

    @Test
    fun `queryTypeLabel falls back for unknown type`() {
        assertEquals("TYPE999", DnsPacketBuilder.queryTypeLabel(999))
    }

    @Test
    fun `queryTypeLabel falls back for zero`() {
        assertEquals("TYPE0", DnsPacketBuilder.queryTypeLabel(0))
    }

    @Test
    fun `queryTypeLabel falls back for negative`() {
        assertEquals("TYPE-1", DnsPacketBuilder.queryTypeLabel(-1))
    }

    // ── buildNxdomain ───────────────────────────────────────

    @Test
    fun `buildNxdomain sets QR bit and RCODE 3`() {
        val query = buildQuery("blocked.com")
        val resp = DnsPacketBuilder.buildNxdomain(query)

        assertTrue("QR bit must be set", isResponse(resp))
        assertEquals("RCODE must be NXDOMAIN (3)", 3, rcode(resp))
    }

    @Test
    fun `buildNxdomain preserves transaction ID`() {
        val query = buildQuery("blocked.com")
        val resp = DnsPacketBuilder.buildNxdomain(query)
        assertEquals(query[0], resp[0])
        assertEquals(query[1], resp[1])
    }

    @Test
    fun `buildNxdomain sets QDCOUNT to 1`() {
        val resp = DnsPacketBuilder.buildNxdomain(buildQuery("a.com"))
        assertEquals(1, u16(resp, 4))
    }

    @Test
    fun `buildNxdomain sets ANCOUNT to 0`() {
        val resp = DnsPacketBuilder.buildNxdomain(buildQuery("a.com"))
        assertEquals(0, u16(resp, 6))
    }

    @Test
    fun `buildNxdomain with SOA has NSCOUNT 1`() {
        val resp = DnsPacketBuilder.buildNxdomain(buildQuery("a.com"), includeSoa = true)
        assertEquals(1, u16(resp, 8))
    }

    @Test
    fun `buildNxdomain without SOA has NSCOUNT 0`() {
        val resp = DnsPacketBuilder.buildNxdomain(buildQuery("a.com"), includeSoa = false)
        assertEquals(0, u16(resp, 8))
    }

    @Test
    fun `buildNxdomain without SOA is shorter than with SOA`() {
        val q = buildQuery("a.com")
        val withSoa = DnsPacketBuilder.buildNxdomain(q, includeSoa = true)
        val noSoa = DnsPacketBuilder.buildNxdomain(q, includeSoa = false)
        assertTrue("SOA variant must be larger", withSoa.size > noSoa.size)
    }

    @Test
    fun `buildNxdomain returns query unchanged when shorter than 12 bytes`() {
        val tiny = ByteArray(8) { 0x42 }
        val resp = DnsPacketBuilder.buildNxdomain(tiny)
        assertArrayEquals(tiny, resp)
    }

    @Test
    fun `buildNxdomain sets RD and RA flags`() {
        val resp = DnsPacketBuilder.buildNxdomain(buildQuery("a.com"))
        // byte 2 bit 0 = RD, byte 3 bit 7 = RA
        val rd = (resp[2].toInt() and 0x01) != 0
        val ra = (resp[3].toInt() and 0x80) != 0
        assertTrue("RD should be set", rd)
        assertTrue("RA should be set", ra)
    }

    // ── buildRefused ────────────────────────────────────────

    @Test
    fun `buildRefused sets RCODE 5`() {
        val resp = DnsPacketBuilder.buildRefused(buildQuery("spam.com"))
        assertTrue(isResponse(resp))
        assertEquals(5, rcode(resp))
    }

    @Test
    fun `buildRefused has NSCOUNT 0 and ANCOUNT 0`() {
        val resp = DnsPacketBuilder.buildRefused(buildQuery("x.com"))
        assertEquals(0, u16(resp, 6)) // ANCOUNT
        assertEquals(0, u16(resp, 8)) // NSCOUNT
    }

    // ── buildZeroIp ─────────────────────────────────────────

    @Test
    fun `buildZeroIp for A query returns NOERROR with ANCOUNT 1`() {
        val resp = DnsPacketBuilder.buildZeroIp(buildQuery("ad.co", qtype = 1))
        assertTrue(isResponse(resp))
        assertEquals(DnsPacketBuilder.RCODE_NOERROR, rcode(resp))
        assertEquals(1, u16(resp, 6)) // ANCOUNT
    }

    @Test
    fun `buildZeroIp for A query has 4-byte RDATA of zeros`() {
        val resp = DnsPacketBuilder.buildZeroIp(buildQuery("ad.co", qtype = 1))
        // Last 4 bytes should be 0.0.0.0
        val tail = resp.sliceArray(resp.size - 4 until resp.size)
        assertArrayEquals(ByteArray(4), tail)
    }

    @Test
    fun `buildZeroIp for AAAA query returns NOERROR with ANCOUNT 1`() {
        val resp = DnsPacketBuilder.buildZeroIp(buildQuery("ad.co", qtype = 28))
        assertEquals(DnsPacketBuilder.RCODE_NOERROR, rcode(resp))
        assertEquals(1, u16(resp, 6))
    }

    @Test
    fun `buildZeroIp for AAAA query has 16-byte RDATA of zeros`() {
        val resp = DnsPacketBuilder.buildZeroIp(buildQuery("ad.co", qtype = 28))
        val tail = resp.sliceArray(resp.size - 16 until resp.size)
        assertArrayEquals(ByteArray(16), tail)
    }

    @Test
    fun `buildZeroIp for non-A non-AAAA falls back to NXDOMAIN`() {
        val resp = DnsPacketBuilder.buildZeroIp(buildQuery("mx.co", qtype = 15))
        assertEquals(3, rcode(resp))
    }

    // ── buildBlockResponse ──────────────────────────────────

    @Test
    fun `buildBlockResponse routes nxdomain`() {
        val resp = DnsPacketBuilder.buildBlockResponse(buildQuery("b.com"), "nxdomain")
        assertEquals(3, rcode(resp))
    }

    @Test
    fun `buildBlockResponse routes zero_ip`() {
        val resp = DnsPacketBuilder.buildBlockResponse(buildQuery("b.com", qtype = 1), "zero_ip")
        assertEquals(DnsPacketBuilder.RCODE_NOERROR, rcode(resp))
        assertEquals(1, u16(resp, 6))
    }

    @Test
    fun `buildBlockResponse routes refused`() {
        val resp = DnsPacketBuilder.buildBlockResponse(buildQuery("b.com"), "refused")
        assertEquals(5, rcode(resp))
    }

    @Test
    fun `buildBlockResponse defaults to nxdomain for unknown type`() {
        val resp = DnsPacketBuilder.buildBlockResponse(buildQuery("b.com"), "drop")
        assertEquals(3, rcode(resp))
    }

    // ── parseDomain (edge cases not in DnsPacketParserTest) ─

    @Test
    fun `parseDomain lowercases result`() {
        // Build query with uppercase labels
        val header = ByteArray(12).apply { this[5] = 1 }
        val question = byteArrayOf(
            3, 'W'.code.toByte(), 'W'.code.toByte(), 'W'.code.toByte(),
            4, 'T'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(),
            0, 0, 1, 0, 1
        )
        assertEquals("www.test", DnsPacketBuilder.parseDomain(header + question))
    }

    @Test
    fun `parseDomain returns null for packet under 13 bytes`() {
        assertNull(DnsPacketBuilder.parseDomain(ByteArray(12)))
    }

    @Test
    fun `parseDomain returns null for empty question`() {
        val header = ByteArray(12).apply { this[5] = 1 }
        val question = byteArrayOf(0, 0, 1, 0, 1) // zero-length name
        assertNull(DnsPacketBuilder.parseDomain(header + question))
    }

    @Test
    fun `parseDomain returns null when label length overshoots packet`() {
        val header = ByteArray(12).apply { this[5] = 1 }
        // Label says length 50 but only 3 bytes follow
        val question = byteArrayOf(50, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertNull(DnsPacketBuilder.parseDomain(header + question))
    }

    @Test
    fun `parseDomain stops at compression pointer`() {
        val header = ByteArray(12).apply { this[5] = 1 }
        // label "abc" then compression pointer 0xC0 0x20
        val question = byteArrayOf(
            3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(),
            0xC0.toByte(), 0x20, 0, 1, 0, 1
        )
        assertEquals("abc", DnsPacketBuilder.parseDomain(header + question))
    }

    @Test
    fun `parseDomain handles deeply nested subdomains`() {
        val hostname = (1..20).joinToString(".") { "s$it" }
        val query = buildQuery(hostname)
        assertEquals(hostname, DnsPacketBuilder.parseDomain(query))
    }

    // ── EDE (Extended DNS Errors, RFC 8914) ───────────────

    @Test
    fun `buildBlockResponse with EDE appends OPT record and bumps ARCOUNT`() {
        val resp = DnsPacketBuilder.buildBlockResponse(
            buildQuery("ad.com"), "nxdomain", DnsPacketBuilder.EDE_BLOCKED
        )
        assertTrue(isResponse(resp))
        assertEquals(3, rcode(resp))
        assertEquals(1, u16(resp, 10)) // ARCOUNT = 1
    }

    @Test
    fun `buildBlockResponse without EDE has ARCOUNT 0`() {
        val resp = DnsPacketBuilder.buildBlockResponse(buildQuery("ad.com"), "nxdomain")
        assertEquals(0, u16(resp, 10))
    }

    @Test
    fun `buildBlockResponse with negative EDE code has ARCOUNT 0`() {
        val resp = DnsPacketBuilder.buildBlockResponse(buildQuery("ad.com"), "nxdomain", -1)
        assertEquals(0, u16(resp, 10))
    }

    @Test
    fun `appendEdeOpt encodes correct OPT wire format`() {
        val base = DnsPacketBuilder.buildNxdomain(buildQuery("x.com"))
        val result = DnsPacketBuilder.appendEdeOpt(base, DnsPacketBuilder.EDE_BLOCKED)

        assertEquals(base.size + 17, result.size)
        assertEquals(1, u16(result, 10)) // ARCOUNT bumped

        val optStart = base.size
        assertEquals(0x00.toByte(), result[optStart])          // root name
        assertEquals(0x00.toByte(), result[optStart + 1])      // TYPE high
        assertEquals(0x29.toByte(), result[optStart + 2])      // TYPE low = 41 (OPT)
        assertEquals(0x04.toByte(), result[optStart + 3])      // CLASS high = 1024 UDP
        assertEquals(0x00.toByte(), result[optStart + 4])      // CLASS low
        assertEquals(6, u16(result, optStart + 9))             // RDLENGTH = 6
        assertEquals(15, u16(result, optStart + 11))           // OPTION-CODE = 15 (EDE)
        assertEquals(2, u16(result, optStart + 13))            // OPTION-LENGTH = 2
        assertEquals(15, u16(result, optStart + 15))           // INFO-CODE = 15 (Blocked)
    }

    @Test
    fun `appendEdeOpt with FILTERED info code`() {
        val base = DnsPacketBuilder.buildRefused(buildQuery("y.com"))
        val result = DnsPacketBuilder.appendEdeOpt(base, DnsPacketBuilder.EDE_FILTERED)
        val optStart = base.size
        assertEquals(17, u16(result, optStart + 15)) // INFO-CODE = 17
    }

    @Test
    fun `EDE works with zero_ip response type`() {
        val resp = DnsPacketBuilder.buildBlockResponse(
            buildQuery("ad.co", qtype = 1), "zero_ip", DnsPacketBuilder.EDE_BLOCKED
        )
        assertEquals(DnsPacketBuilder.RCODE_NOERROR, rcode(resp))
        assertEquals(1, u16(resp, 6))  // ANCOUNT = 1
        assertEquals(1, u16(resp, 10)) // ARCOUNT = 1
    }

    @Test
    fun `EDE works with refused response type`() {
        val resp = DnsPacketBuilder.buildBlockResponse(
            buildQuery("ad.co"), "refused", DnsPacketBuilder.EDE_BLOCKED
        )
        assertEquals(5, rcode(resp))
        assertEquals(1, u16(resp, 10))
    }

    @Test
    fun `appendEdeOpt on tiny packet returns unchanged`() {
        val tiny = ByteArray(8)
        val result = DnsPacketBuilder.appendEdeOpt(tiny, DnsPacketBuilder.EDE_BLOCKED)
        assertArrayEquals(tiny, result)
    }

    // ── Constants ───────────────────────────────────────────

    @Test
    fun `RCODE constants have expected values`() {
        assertEquals(0, DnsPacketBuilder.RCODE_NOERROR)
        assertEquals(3, DnsPacketBuilder.RCODE_NXDOMAIN)
        assertEquals(5, DnsPacketBuilder.RCODE_REFUSED)
    }

    @Test
    fun `EDE constants have expected RFC 8914 values`() {
        assertEquals(15, DnsPacketBuilder.EDE_BLOCKED)
        assertEquals(16, DnsPacketBuilder.EDE_CENSORED)
        assertEquals(17, DnsPacketBuilder.EDE_FILTERED)
        assertEquals(18, DnsPacketBuilder.EDE_PROHIBITED)
    }

    @Test
    fun `TYPE constants have expected values`() {
        assertEquals(2, DnsPacketBuilder.TYPE_NS)
        assertEquals(5, DnsPacketBuilder.TYPE_CNAME)
        assertEquals(6, DnsPacketBuilder.TYPE_SOA)
        assertEquals(15, DnsPacketBuilder.TYPE_MX)
        assertEquals(16, DnsPacketBuilder.TYPE_TXT)
        assertEquals(33, DnsPacketBuilder.TYPE_SRV)
        assertEquals(41, DnsPacketBuilder.TYPE_OPT)
        assertEquals(64, DnsPacketBuilder.TYPE_SVCB)
        assertEquals(65, DnsPacketBuilder.TYPE_HTTPS)
    }
}
