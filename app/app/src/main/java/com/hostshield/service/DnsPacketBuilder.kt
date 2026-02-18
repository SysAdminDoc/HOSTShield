package com.hostshield.service

import android.util.Log

/**
 * Shared DNS wire-format packet builder and parser.
 *
 * Used by both DnsVpnService (VPN mode) and RootDnsLogger (root mode) to
 * construct block responses (NXDOMAIN, zero-IP, REFUSED) and parse query
 * domains. Eliminates ~200 lines of duplication between the two services.
 *
 * All methods are pure functions (no state) — safe to call from any thread.
 */
object DnsPacketBuilder {

    private const val TAG = "DnsPacketBuilder"

    // DNS constants
    const val RCODE_NOERROR = 0
    const val RCODE_NXDOMAIN = 3
    const val RCODE_REFUSED = 5

    // Record types
    private const val TYPE_A: Short = 1
    private const val TYPE_AAAA: Short = 28
    private const val CLASS_IN: Short = 1

    // SOA RDATA for NXDOMAIN negative caching
    // Synthesized authority: ns.hostshield.local / admin.hostshield.local
    // Serial=1, Refresh=3600, Retry=600, Expire=86400, Minimum(NX TTL)=60
    private val SOA_RDATA = byteArrayOf(
        // MNAME: ns.hostshield.local (compressed would be complex, use simple label)
        2, 'n'.code.toByte(), 's'.code.toByte(),
        10, 'h'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
        's'.code.toByte(), 'h'.code.toByte(), 'i'.code.toByte(), 'e'.code.toByte(),
        'l'.code.toByte(), 'd'.code.toByte(),
        5, 'l'.code.toByte(), 'o'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(),
        0,
        // RNAME: admin.hostshield.local
        5, 'a'.code.toByte(), 'd'.code.toByte(), 'm'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(),
        10, 'h'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
        's'.code.toByte(), 'h'.code.toByte(), 'i'.code.toByte(), 'e'.code.toByte(),
        'l'.code.toByte(), 'd'.code.toByte(),
        5, 'l'.code.toByte(), 'o'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(),
        0,
        // SERIAL (1)
        0, 0, 0, 1,
        // REFRESH (3600)
        0, 0, 0x0E, 0x10,
        // RETRY (600)
        0, 0, 0x02, 0x58.toByte(),
        // EXPIRE (86400)
        0, 0x01, 0x51.toByte(), 0x80.toByte(),
        // MINIMUM / NX TTL (60)
        0, 0, 0, 0x3C
    )

    /**
     * Parse domain name from a DNS query payload.
     *
     * @param dns Raw DNS message bytes (header + question)
     * @return Fully qualified domain name, or null if unparseable
     */
    fun parseDomain(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val sb = StringBuilder(64)
        var pos = 12 // skip 12-byte DNS header
        var iterations = 0
        while (pos < dns.size && iterations++ < 128) {
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) break // compression pointer — not expected in query
            if (pos + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) sb.append(dns[pos + i].toInt().toChar())
            pos += 1 + len
        }
        return if (sb.isNotEmpty()) sb.toString().lowercase() else null
    }

    /**
     * Extract query type from DNS question section.
     *
     * @param dns Raw DNS message bytes
     * @return QTYPE value (1=A, 28=AAAA, etc.), or -1 if unparseable
     */
    fun parseQueryType(dns: ByteArray): Int {
        if (dns.size < 14) return -1
        var pos = 12
        // Skip QNAME labels
        while (pos < dns.size) {
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if (len and 0xC0 == 0xC0) { pos += 2; break }
            pos += 1 + len
        }
        if (pos + 2 > dns.size) return -1
        return (dns[pos].toInt() and 0xFF shl 8) or (dns[pos + 1].toInt() and 0xFF)
    }

    /**
     * Build an NXDOMAIN response with optional SOA authority record.
     *
     * @param queryDns Original DNS query bytes
     * @param includeSoa Whether to include SOA record for negative caching
     * @return DNS response bytes with RCODE=3
     */
    fun buildNxdomain(queryDns: ByteArray, includeSoa: Boolean = true): ByteArray {
        return buildResponseWithRcode(queryDns, RCODE_NXDOMAIN, includeSoa)
    }

    /**
     * Build a REFUSED response (RCODE=5).
     */
    fun buildRefused(queryDns: ByteArray): ByteArray {
        return buildResponseWithRcode(queryDns, RCODE_REFUSED, includeSoa = false)
    }

    /**
     * Build a zero-IP (NOERROR) response.
     *
     * Returns A=0.0.0.0 for A queries, AAAA=:: for AAAA queries.
     * Non-A/AAAA queries fall back to NXDOMAIN.
     *
     * @param queryDns Original DNS query bytes
     * @return DNS response bytes with appropriate answer
     */
    fun buildZeroIp(queryDns: ByteArray): ByteArray {
        val qtype = parseQueryType(queryDns)
        return when (qtype) {
            TYPE_A.toInt() -> buildZeroIpA(queryDns)
            TYPE_AAAA.toInt() -> buildZeroIpAaaa(queryDns)
            else -> buildNxdomain(queryDns, includeSoa = false)
        }
    }

    /**
     * Build a block response based on type string.
     *
     * @param queryDns Original DNS query bytes
     * @param responseType One of "nxdomain", "zero_ip", "refused"
     * @return DNS response bytes
     */
    fun buildBlockResponse(queryDns: ByteArray, responseType: String): ByteArray {
        return when (responseType) {
            "zero_ip" -> buildZeroIp(queryDns)
            "refused" -> buildRefused(queryDns)
            else -> buildNxdomain(queryDns)
        }
    }

    // ── Internal builders ─────────────────────────────────────

    private fun buildResponseWithRcode(queryDns: ByteArray, rcode: Int, includeSoa: Boolean): ByteArray {
        if (queryDns.size < 12) return queryDns

        // Find end of question section
        var qEnd = 12
        while (qEnd < queryDns.size) {
            val len = queryDns[qEnd].toInt() and 0xFF
            if (len == 0) { qEnd++; break }
            if (len and 0xC0 == 0xC0) { qEnd += 2; break }
            qEnd += 1 + len
        }
        qEnd += 4 // QTYPE + QCLASS

        val qSection = if (qEnd <= queryDns.size) queryDns.sliceArray(12 until qEnd) else ByteArray(0)

        val soaRecord = if (includeSoa && rcode == RCODE_NXDOMAIN) {
            buildSoaAuthorityRecord()
        } else {
            ByteArray(0)
        }
        val nsCount = if (soaRecord.isNotEmpty()) 1 else 0

        val resp = ByteArray(12 + qSection.size + soaRecord.size)
        // Transaction ID
        resp[0] = queryDns[0]; resp[1] = queryDns[1]
        // Flags: QR=1, RD=1, RA=1, RCODE
        resp[2] = 0x81.toByte()
        resp[3] = (0x80 or rcode).toByte()
        // QDCOUNT = 1
        resp[4] = 0; resp[5] = 1
        // ANCOUNT = 0
        resp[6] = 0; resp[7] = 0
        // NSCOUNT
        resp[8] = 0; resp[9] = nsCount.toByte()
        // ARCOUNT = 0
        resp[10] = 0; resp[11] = 0

        System.arraycopy(qSection, 0, resp, 12, qSection.size)
        if (soaRecord.isNotEmpty()) {
            System.arraycopy(soaRecord, 0, resp, 12 + qSection.size, soaRecord.size)
        }
        return resp
    }

    private fun buildSoaAuthorityRecord(): ByteArray {
        // Name pointer to question (0xC00C), TYPE=SOA(6), CLASS=IN(1), TTL=60, RDLENGTH, RDATA
        val record = ByteArray(2 + 2 + 2 + 4 + 2 + SOA_RDATA.size)
        record[0] = 0xC0.toByte(); record[1] = 0x0C // name pointer
        record[2] = 0; record[3] = 6 // TYPE = SOA
        record[4] = 0; record[5] = 1 // CLASS = IN
        // TTL = 60s
        record[6] = 0; record[7] = 0; record[8] = 0; record[9] = 60
        // RDLENGTH
        val rdLen = SOA_RDATA.size
        record[10] = (rdLen shr 8).toByte(); record[11] = (rdLen and 0xFF).toByte()
        System.arraycopy(SOA_RDATA, 0, record, 12, SOA_RDATA.size)
        return record
    }

    private fun buildZeroIpA(queryDns: ByteArray): ByteArray {
        // NOERROR with A=0.0.0.0
        val resp = buildResponseWithRcode(queryDns, RCODE_NOERROR, includeSoa = false)
        // Add answer record: name pointer + TYPE_A + CLASS_IN + TTL(300) + RDLENGTH(4) + 0.0.0.0
        val answer = ByteArray(2 + 2 + 2 + 4 + 2 + 4)
        answer[0] = 0xC0.toByte(); answer[1] = 0x0C // name pointer
        answer[2] = 0; answer[3] = 1 // TYPE = A
        answer[4] = 0; answer[5] = 1 // CLASS = IN
        // TTL = 300
        answer[6] = 0; answer[7] = 0; answer[8] = 1; answer[9] = 0x2C
        answer[10] = 0; answer[11] = 4 // RDLENGTH = 4
        // 0.0.0.0 (already zeroed)

        val full = ByteArray(resp.size + answer.size)
        System.arraycopy(resp, 0, full, 0, resp.size)
        System.arraycopy(answer, 0, full, resp.size, answer.size)
        // Set ANCOUNT = 1
        full[6] = 0; full[7] = 1
        return full
    }

    private fun buildZeroIpAaaa(queryDns: ByteArray): ByteArray {
        // NOERROR with AAAA=::
        val resp = buildResponseWithRcode(queryDns, RCODE_NOERROR, includeSoa = false)
        // Answer: name pointer + TYPE_AAAA + CLASS_IN + TTL(300) + RDLENGTH(16) + ::
        val answer = ByteArray(2 + 2 + 2 + 4 + 2 + 16)
        answer[0] = 0xC0.toByte(); answer[1] = 0x0C
        answer[2] = 0; answer[3] = 28 // TYPE = AAAA
        answer[4] = 0; answer[5] = 1 // CLASS = IN
        answer[6] = 0; answer[7] = 0; answer[8] = 1; answer[9] = 0x2C // TTL = 300
        answer[10] = 0; answer[11] = 16 // RDLENGTH = 16
        // :: (all zeroed)

        val full = ByteArray(resp.size + answer.size)
        System.arraycopy(resp, 0, full, 0, resp.size)
        System.arraycopy(answer, 0, full, resp.size, answer.size)
        full[6] = 0; full[7] = 1 // ANCOUNT = 1
        return full
    }
}
