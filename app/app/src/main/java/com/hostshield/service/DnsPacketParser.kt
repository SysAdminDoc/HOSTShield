package com.hostshield.service

/**
 * Pure DNS protocol parsing utilities — domain extraction, query type parsing,
 * DNS payload extraction, and name skipping.
 * Zero state dependencies; all methods are stateless.
 */
object DnsPacketParser {

    /** Extract DNS payload from IPv4 UDP packet. */
    fun extractDnsPayload(p: ByteArray, len: Int, ihl: Int): ByteArray? {
        if (len < ihl + 8) return null
        val udpLen = ((p[ihl + 4].toInt() and 0xFF) shl 8) or (p[ihl + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8; val dnsStart = ihl + 8
        if (dnsLen < 12 || dnsStart + dnsLen > len) return null
        return p.copyOfRange(dnsStart, dnsStart + dnsLen)
    }

    /** Extract DNS payload from IPv6 UDP packet. */
    fun extractDnsPayloadV6(p: ByteArray, len: Int, hdr: Int): ByteArray? {
        if (len < hdr + 8) return null
        val udpLen = ((p[hdr + 4].toInt() and 0xFF) shl 8) or (p[hdr + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8; val dnsStart = hdr + 8
        if (dnsLen < 12 || dnsStart + dnsLen > len) return null
        return p.copyOfRange(dnsStart, dnsStart + dnsLen)
    }

    /** Extract domain name from DNS query (RFC 1035 label encoding). */
    fun parseDnsQueryDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        var off = 12; val parts = mutableListOf<String>()
        while (off < dns.size) {
            val l = dns[off].toInt() and 0xFF
            if (l == 0) break
            if (l > 63 || off + 1 + l > dns.size) return null
            parts.add(String(dns, off + 1, l, Charsets.US_ASCII)); off += 1 + l
        }
        return if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else null
    }

    /** Extract query type from DNS query (A, AAAA, CNAME, etc.). */
    fun parseDnsQueryType(dns: ByteArray): String {
        if (dns.size < 14) return "?"
        var off = 12
        while (off < dns.size) {
            val l = dns[off].toInt() and 0xFF; if (l == 0) { off++; break }; off += 1 + l
        }
        if (off + 2 > dns.size) return "?"
        val qt = ((dns[off].toInt() and 0xFF) shl 8) or (dns[off + 1].toInt() and 0xFF)
        return when (qt) {
            1 -> "A"; 2 -> "NS"; 5 -> "CNAME"; 6 -> "SOA"; 12 -> "PTR"
            15 -> "MX"; 16 -> "TXT"; 28 -> "AAAA"; 33 -> "SRV"; 41 -> "OPT"
            43 -> "DS"; 48 -> "DNSKEY"; 64 -> "SVCB"; 65 -> "HTTPS"; 255 -> "ANY"; 257 -> "CAA"
            else -> "TYPE$qt"
        }
    }

    /** Skip a DNS name (labels or compressed pointer) and return offset after it. */
    fun skipDnsName(buf: ByteArray, start: Int): Int {
        var off = start
        while (off < buf.size) {
            val len = buf[off].toInt() and 0xFF
            if (len == 0) return off + 1
            if (len and 0xC0 == 0xC0) {
                return if (off + 1 < buf.size) off + 2 else -1
            }
            off += 1 + len
        }
        return -1
    }

    /** Wrap DNS response bytes into an IPv4 UDP response packet. */
    fun wrapResponseV4(orig: ByteArray, ihl: Int, dns: ByteArray): ByteArray? {
        try {
            val total = ihl + 8 + dns.size
            val r = ByteArray(total)
            System.arraycopy(orig, 0, r, 0, ihl)
            System.arraycopy(orig, 12, r, 16, 4)
            System.arraycopy(orig, 16, r, 12, 4)
            r[2] = ((total shr 8) and 0xFF).toByte(); r[3] = (total and 0xFF).toByte()
            r[6] = 0; r[7] = 0; r[8] = 64
            r[ihl] = orig[ihl + 2]; r[ihl + 1] = orig[ihl + 3]
            r[ihl + 2] = orig[ihl]; r[ihl + 3] = orig[ihl + 1]
            val udpLen = 8 + dns.size
            r[ihl + 4] = ((udpLen shr 8) and 0xFF).toByte(); r[ihl + 5] = (udpLen and 0xFF).toByte()
            r[ihl + 6] = 0; r[ihl + 7] = 0
            System.arraycopy(dns, 0, r, ihl + 8, dns.size)
            // IP checksum
            r[10] = 0; r[11] = 0
            var sum = 0L
            for (i in 0 until ihl step 2) sum += ((r[i].toInt() and 0xFF) shl 8) or (r[i + 1].toInt() and 0xFF)
            while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
            val ck = sum.inv().toInt() and 0xFFFF
            r[10] = ((ck shr 8) and 0xFF).toByte(); r[11] = (ck and 0xFF).toByte()
            return r
        } catch (_: Exception) { return null }
    }

    /** Wrap DNS response bytes into an IPv6 UDP response packet. */
    fun wrapResponseV6(orig: ByteArray, hdr: Int, dns: ByteArray): ByteArray? {
        try {
            val udpLen = 8 + dns.size; val total = hdr + udpLen
            val r = ByteArray(total)
            System.arraycopy(orig, 0, r, 0, hdr)
            System.arraycopy(orig, 8, r, 24, 16)
            System.arraycopy(orig, 24, r, 8, 16)
            r[4] = ((udpLen shr 8) and 0xFF).toByte(); r[5] = (udpLen and 0xFF).toByte()
            r[7] = 64
            r[hdr] = orig[hdr + 2]; r[hdr + 1] = orig[hdr + 3]
            r[hdr + 2] = orig[hdr]; r[hdr + 3] = orig[hdr + 1]
            r[hdr + 4] = ((udpLen shr 8) and 0xFF).toByte(); r[hdr + 5] = (udpLen and 0xFF).toByte()
            r[hdr + 6] = 0; r[hdr + 7] = 0
            System.arraycopy(dns, 0, r, hdr + 8, dns.size)
            // UDP checksum is mandatory over IPv6 (RFC 8200 §8.1) — a zero
            // checksum makes the local stack discard the datagram, so the IPv6
            // DNS path only worked via IPv4 fallback until this was computed.
            writeUdpChecksumV6(r, hdr, udpLen)
            return r
        } catch (_: Exception) { return null }
    }

    /** Compute and write the IPv6 UDP checksum (pseudo-header + UDP header + payload). */
    private fun writeUdpChecksumV6(pkt: ByteArray, hdr: Int, udpLen: Int) {
        var sum = 0L
        // Pseudo-header: source (16) + destination (16) addresses.
        for (i in 8 until 40 step 2) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        // Upper-layer packet length (32-bit; udpLen < 65536 so high word is 0).
        sum += udpLen.toLong()
        // Next header = 17 (UDP).
        sum += 17L
        // UDP header + payload, checksum field already zeroed.
        var i = hdr
        val end = hdr + udpLen
        while (i + 1 < end) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (pkt[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        var ck = sum.inv().toInt() and 0xFFFF
        // A computed checksum of zero is transmitted as all ones over IPv6.
        if (ck == 0) ck = 0xFFFF
        pkt[hdr + 6] = ((ck shr 8) and 0xFF).toByte()
        pkt[hdr + 7] = (ck and 0xFF).toByte()
    }
}
