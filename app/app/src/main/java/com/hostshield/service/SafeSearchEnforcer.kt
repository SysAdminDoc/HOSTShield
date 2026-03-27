package com.hostshield.service

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield — DNS-level Safe Search Enforcement (roadmap #41)
// ══════════════════════════════════════════════════════════════
//
// Rewrites DNS responses for major search engines so they
// resolve to their "safe search" / "restricted" variants.
// The caller (DnsVpnService) checks isSafeSearchDomain() on
// every query and, when true, calls buildSafeResponse() to
// return a spoofed A record pointing at the safe IP.

@Singleton
class SafeSearchEnforcer @Inject constructor() {

    // ── Safe-search IP table ─────────────────────────────────
    // Maps each search-engine domain to the IPv4 address of
    // its forced-safe-search endpoint.

    private val safeIpTable: ConcurrentHashMap<String, String> = ConcurrentHashMap<String, String>().apply {
        // Google  — forcesafesearch.google.com
        put("google.com",           "216.239.38.120")
        put("www.google.com",       "216.239.38.120")

        // Bing    — strict.bing.com
        put("bing.com",             "204.79.197.220")
        put("www.bing.com",         "204.79.197.220")

        // DuckDuckGo — safe.duckduckgo.com
        put("duckduckgo.com",       "52.250.42.157")
        put("www.duckduckgo.com",   "52.250.42.157")

        // YouTube — restrict.youtube.com (moderate restriction)
        put("youtube.com",          "216.239.38.120")
        put("www.youtube.com",      "216.239.38.120")
        put("m.youtube.com",        "216.239.38.120")
    }

    // ── Public API ───────────────────────────────────────────

    /**
     * Returns `true` if the given domain should be rewritten to
     * a safe-search IP address.
     */
    fun isSafeSearchDomain(domain: String): Boolean {
        return safeIpTable.containsKey(domain.lowercase())
    }

    /**
     * Returns the safe-search IPv4 address for [domain], or
     * `null` if the domain is not in the safe-search table.
     */
    fun getSafeSearchIp(domain: String): String? {
        return safeIpTable[domain.lowercase()]
    }

    /**
     * Builds a DNS A-record response that resolves [dns] query
     * to [safeIp] instead of the real address.
     *
     * Uses the same packet structure as
     * [DnsPacketBuilder.buildZeroIpA] but writes the real safe
     * IP octets instead of 0.0.0.0.
     *
     * @param dns   The original DNS query bytes (>= 12 bytes).
     * @param safeIp IPv4 address string (e.g. "216.239.38.120").
     * @return A valid DNS response byte array, or `null` if
     *         the query is too short or the IP is malformed.
     */
    fun buildSafeResponse(dns: ByteArray, safeIp: String): ByteArray? {
        if (dns.size < 12) return null

        val ipParts = safeIp.split('.')
        if (ipParts.size != 4) return null
        val ipBytes = try {
            ipParts.map { it.toInt().and(0xFF).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            return null
        }

        // ── Locate end of the question section ──────────────
        var qEnd = 12
        while (qEnd < dns.size) {
            val len = dns[qEnd].toInt() and 0xFF
            if (len == 0) { qEnd++; break }
            if (len and 0xC0 == 0xC0) { qEnd += 2; break }
            qEnd += 1 + len
        }
        qEnd += 4 // QTYPE + QCLASS
        if (qEnd > dns.size) return null

        val qSection = dns.sliceArray(12 until qEnd)

        // Answer record: name-pointer(2) + TYPE_A(2) + CLASS_IN(2) + TTL(4) + RDLENGTH(2) + RDATA(4)
        val answerLen = 2 + 2 + 2 + 4 + 2 + 4 // 16 bytes
        val resp = ByteArray(12 + qSection.size + answerLen)

        // ── Header ──────────────────────────────────────────
        // Transaction ID (copied from query)
        resp[0] = dns[0]; resp[1] = dns[1]
        // Flags: QR=1, RD=1, RA=1, RCODE=0 (NOERROR)
        resp[2] = 0x81.toByte()
        resp[3] = 0x80.toByte()
        // QDCOUNT = 1
        resp[4] = 0; resp[5] = 1
        // ANCOUNT = 1
        resp[6] = 0; resp[7] = 1
        // NSCOUNT = 0
        resp[8] = 0; resp[9] = 0
        // ARCOUNT = 0
        resp[10] = 0; resp[11] = 0

        // ── Question section (verbatim copy) ────────────────
        System.arraycopy(qSection, 0, resp, 12, qSection.size)

        // ── Answer record ───────────────────────────────────
        val aOff = 12 + qSection.size
        // Name pointer back to question QNAME (0xC00C)
        resp[aOff]     = 0xC0.toByte()
        resp[aOff + 1] = 0x0C.toByte()
        // TYPE = A (1)
        resp[aOff + 2] = 0; resp[aOff + 3] = 1
        // CLASS = IN (1)
        resp[aOff + 4] = 0; resp[aOff + 5] = 1
        // TTL = 300 seconds (0x0000012C)
        resp[aOff + 6] = 0; resp[aOff + 7] = 0
        resp[aOff + 8] = 1; resp[aOff + 9] = 0x2C.toByte()
        // RDLENGTH = 4
        resp[aOff + 10] = 0; resp[aOff + 11] = 4
        // RDATA = safe IP octets
        System.arraycopy(ipBytes, 0, resp, aOff + 12, 4)

        return resp
    }
}
