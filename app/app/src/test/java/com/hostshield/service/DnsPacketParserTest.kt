package com.hostshield.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DnsPacketParser].
 *
 * Covers: parseDnsQueryDomain, parseDnsQueryType, extractDnsPayload,
 * extractDnsPayloadV6, skipDnsName.
 */
class DnsPacketParserTest {

    // ── Helpers ──────────────────────────────────────────────

    /** Encode a hostname into DNS label format (e.g. "www.example.com" -> [3,w,w,w,7,e,...,0]). */
    private fun encodeDnsName(hostname: String): ByteArray {
        val buf = mutableListOf<Byte>()
        for (label in hostname.split('.')) {
            buf.add(label.length.toByte())
            for (c in label) buf.add(c.code.toByte())
        }
        buf.add(0) // root terminator
        return buf.toByteArray()
    }

    /**
     * Build a minimal DNS query payload (just the DNS layer, no IP/UDP headers).
     * 12-byte header + question section (name + QTYPE + QCLASS).
     */
    private fun buildDnsPayload(hostname: String, qtype: Int = 1): ByteArray {
        val header = ByteArray(12).apply {
            this[0] = 0xAB.toByte()
            this[1] = 0xCD.toByte()
            this[2] = 0x01            // RD=1
            this[5] = 0x01            // QDCOUNT=1
        }
        val name = encodeDnsName(hostname)
        val typeClass = byteArrayOf(
            (qtype shr 8).toByte(), (qtype and 0xFF).toByte(),
            0x00, 0x01  // QCLASS=IN
        )
        return header + name + typeClass
    }

    /**
     * Wrap a DNS payload inside an IPv4 UDP packet.
     * IHL = 5 (20 bytes), protocol = 17 (UDP), dest port = 53.
     */
    private fun wrapInIpv4Udp(dnsPayload: ByteArray): ByteArray {
        val ihl = 20
        val udpLen = 8 + dnsPayload.size
        val total = ihl + udpLen
        val packet = ByteArray(total)
        packet[0] = 0x45.toByte()           // version=4, IHL=5
        packet[9] = 17.toByte()             // protocol = UDP
        // UDP header at offset 20
        packet[20] = 0x00                   // src port high
        packet[21] = 0x35                   // src port low (53)
        packet[22] = 0x00                   // dest port high
        packet[23] = 0x35                   // dest port low (53)
        // UDP length
        packet[24] = (udpLen shr 8).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        // Copy DNS payload after UDP header
        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)
        return packet
    }

    // ── parseDnsQueryDomain ──────────────────────────────────

    @Test
    fun `parseDnsQueryDomain returns correct domain for www_example_com`() {
        val dns = buildDnsPayload("www.example.com")
        assertEquals("www.example.com", DnsPacketParser.parseDnsQueryDomain(dns))
    }

    @Test
    fun `parseDnsQueryDomain lowercases result`() {
        val header = ByteArray(12).apply { this[5] = 1 }
        val name = byteArrayOf(
            3, 'W'.code.toByte(), 'W'.code.toByte(), 'W'.code.toByte(),
            4, 'T'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(),
            0
        )
        val typeClass = byteArrayOf(0, 1, 0, 1)
        val dns = header + name + typeClass
        assertEquals("www.test", DnsPacketParser.parseDnsQueryDomain(dns))
    }

    @Test
    fun `parseDnsQueryDomain returns null for empty packet`() {
        assertNull(DnsPacketParser.parseDnsQueryDomain(ByteArray(0)))
    }

    @Test
    fun `parseDnsQueryDomain returns null for too-short packet`() {
        assertNull(DnsPacketParser.parseDnsQueryDomain(ByteArray(11)))
    }

    @Test
    fun `parseDnsQueryDomain returns null for empty name`() {
        // 12-byte header then 0x00 root label immediately
        val dns = ByteArray(12).apply { this[5] = 1 } + byteArrayOf(0, 0, 1, 0, 1)
        assertNull(DnsPacketParser.parseDnsQueryDomain(dns))
    }

    @Test
    fun `parseDnsQueryDomain returns null when label overshoots packet`() {
        // Label claims length 50 but only 3 bytes follow
        val header = ByteArray(12).apply { this[5] = 1 }
        val badName = byteArrayOf(50, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertNull(DnsPacketParser.parseDnsQueryDomain(header + badName))
    }

    @Test
    fun `parseDnsQueryDomain handles single-label domain`() {
        val dns = buildDnsPayload("localhost")
        assertEquals("localhost", DnsPacketParser.parseDnsQueryDomain(dns))
    }

    // ── parseDnsQueryType ────────────────────────────────────

    @Test
    fun `parseDnsQueryType returns A for type 1`() {
        val dns = buildDnsPayload("example.com", qtype = 1)
        assertEquals("A", DnsPacketParser.parseDnsQueryType(dns))
    }

    @Test
    fun `parseDnsQueryType returns AAAA for type 28`() {
        val dns = buildDnsPayload("example.com", qtype = 28)
        assertEquals("AAAA", DnsPacketParser.parseDnsQueryType(dns))
    }

    @Test
    fun `parseDnsQueryType returns CNAME for type 5`() {
        assertEquals("CNAME", DnsPacketParser.parseDnsQueryType(buildDnsPayload("x.co", qtype = 5)))
    }

    @Test
    fun `parseDnsQueryType returns MX for type 15`() {
        assertEquals("MX", DnsPacketParser.parseDnsQueryType(buildDnsPayload("x.co", qtype = 15)))
    }

    @Test
    fun `parseDnsQueryType returns HTTPS for type 65`() {
        assertEquals("HTTPS", DnsPacketParser.parseDnsQueryType(buildDnsPayload("x.co", qtype = 65)))
    }

    @Test
    fun `parseDnsQueryType returns TYPE_N for unknown type`() {
        assertEquals("TYPE99", DnsPacketParser.parseDnsQueryType(buildDnsPayload("x.co", qtype = 99)))
    }

    @Test
    fun `parseDnsQueryType returns question mark for too-short packet`() {
        assertEquals("?", DnsPacketParser.parseDnsQueryType(ByteArray(13)))
    }

    @Test
    fun `parseDnsQueryType returns question mark for empty packet`() {
        assertEquals("?", DnsPacketParser.parseDnsQueryType(ByteArray(0)))
    }

    // ── extractDnsPayload ────────────────────────────────────

    @Test
    fun `extractDnsPayload extracts correct DNS bytes from IPv4 UDP packet`() {
        val dnsPayload = buildDnsPayload("www.example.com")
        val packet = wrapInIpv4Udp(dnsPayload)
        val extracted = DnsPacketParser.extractDnsPayload(packet, packet.size, 20)
        assertNotNull(extracted)
        assertArrayEquals(dnsPayload, extracted)
    }

    @Test
    fun `extractDnsPayload returns null for too-short packet`() {
        val packet = ByteArray(25) // less than ihl(20) + 8
        packet[0] = 0x45.toByte()
        assertNull(DnsPacketParser.extractDnsPayload(packet, packet.size, 20))
    }

    @Test
    fun `extractDnsPayload returns null when UDP length indicates sub-12 DNS payload`() {
        // Build a packet where UDP length = 8 + 11 = 19; DNS len = 11 < 12
        val ihl = 20
        val packet = ByteArray(ihl + 19)
        packet[0] = 0x45.toByte()
        packet[9] = 17.toByte()
        // UDP length = 19
        packet[ihl + 4] = 0x00
        packet[ihl + 5] = 19.toByte()
        assertNull(DnsPacketParser.extractDnsPayload(packet, packet.size, ihl))
    }

    // ── extractDnsPayloadV6 ──────────────────────────────────

    @Test
    fun `extractDnsPayloadV6 extracts correct DNS bytes`() {
        val dnsPayload = buildDnsPayload("example.org")
        val hdr = 40
        val udpLen = 8 + dnsPayload.size
        val packet = ByteArray(hdr + udpLen)
        packet[0] = 0x60.toByte()
        packet[6] = 17.toByte()
        packet[hdr + 4] = (udpLen shr 8).toByte()
        packet[hdr + 5] = (udpLen and 0xFF).toByte()
        System.arraycopy(dnsPayload, 0, packet, hdr + 8, dnsPayload.size)

        val extracted = DnsPacketParser.extractDnsPayloadV6(packet, packet.size, hdr)
        assertNotNull(extracted)
        assertArrayEquals(dnsPayload, extracted)
    }

    @Test
    fun `extractDnsPayloadV6 returns null for too-short packet`() {
        assertNull(DnsPacketParser.extractDnsPayloadV6(ByteArray(45), 45, 40))
    }

    // ── skipDnsName ──────────────────────────────────────────

    @Test
    fun `skipDnsName returns correct offset after label sequence`() {
        // "www.example.com" encoded: [3,w,w,w,7,e,x,a,m,p,l,e,3,c,o,m,0]
        val name = encodeDnsName("www.example.com")
        val result = DnsPacketParser.skipDnsName(name, 0)
        assertEquals(name.size, result) // should point just past the 0x00 terminator
    }

    @Test
    fun `skipDnsName returns correct offset for compressed pointer`() {
        // A name with a label then a compression pointer (0xC0 0x0C)
        val buf = byteArrayOf(
            3, 'f'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(),
            0xC0.toByte(), 0x0C.toByte()  // pointer to offset 12
        )
        val result = DnsPacketParser.skipDnsName(buf, 0)
        // Should skip "foo" label (4 bytes) then pointer (2 bytes) = 6
        assertEquals(6, result)
    }

    @Test
    fun `skipDnsName returns correct offset for pointer-only name`() {
        val buf = byteArrayOf(0xC0.toByte(), 0x0C.toByte())
        assertEquals(2, DnsPacketParser.skipDnsName(buf, 0))
    }

    @Test
    fun `skipDnsName returns -1 for malformed name that runs off end`() {
        // Label says length 10 but only 3 bytes total in buffer
        val buf = byteArrayOf(10, 'a'.code.toByte(), 'b'.code.toByte())
        assertEquals(-1, DnsPacketParser.skipDnsName(buf, 0))
    }

    @Test
    fun `skipDnsName returns correct offset for single label`() {
        val name = encodeDnsName("localhost")
        // [9,l,o,c,a,l,h,o,s,t,0] = 11 bytes
        assertEquals(11, DnsPacketParser.skipDnsName(name, 0))
    }

    @Test
    fun `skipDnsName handles start offset in middle of buffer`() {
        // Prefix junk then a valid name
        val prefix = ByteArray(5) { 0x00 }
        val name = encodeDnsName("a.b")
        val buf = prefix + name
        val result = DnsPacketParser.skipDnsName(buf, 5)
        assertEquals(5 + name.size, result)
    }
}
