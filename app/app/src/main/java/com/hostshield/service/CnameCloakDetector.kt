package com.hostshield.service

import android.util.Log
import com.hostshield.domain.BlocklistHolder

/**
 * CNAME Cloaking Detector
 *
 * First-party CNAME cloaking is the #1 technique ad/tracking networks use to
 * bypass DNS-based blockers. Example:
 *
 *   tracker.example.com → CNAME → analytics.tracker-corp.net → A 1.2.3.4
 *
 * The user queries "tracker.example.com" (first-party, not in blocklist).
 * The response contains a CNAME pointing to "analytics.tracker-corp.net"
 * which IS in our blocklist. Without CNAME inspection, the tracker gets through.
 *
 * This class inspects DNS response CNAME chains and returns whether any
 * intermediate CNAME target is blocked. If so, the entire response should
 * be replaced with a block response.
 *
 * Used by:
 * - DnsVpnService: after forwarding an allowed query, inspect the response
 *   before sending it back to the app
 * - RootDnsLogger: if we ever add response inspection to root mode
 */
object CnameCloakDetector {

    private const val TAG = "CnameCloak"
    private const val TYPE_CNAME = 5
    private const val MAX_CHAIN_LENGTH = 10 // prevent infinite loops

    data class CnameResult(
        /** Whether any CNAME target in the chain is blocked */
        val blocked: Boolean,
        /** The specific CNAME target that was blocked (for logging) */
        val blockedCname: String?,
        /** All CNAME targets found in the chain */
        val cnameChain: List<String>
    )

    /**
     * Inspect a DNS response for CNAME cloaking.
     *
     * @param response Raw DNS response bytes from upstream
     * @param blocklist The active blocklist to check CNAME targets against
     * @return CnameResult with blocked=true if any CNAME target is in the blocklist
     */
    fun inspect(response: ByteArray, blocklist: BlocklistHolder): CnameResult {
        val cnameChain = extractCnameChain(response)
        if (cnameChain.isEmpty()) {
            return CnameResult(blocked = false, blockedCname = null, cnameChain = emptyList())
        }

        for (cname in cnameChain) {
            if (blocklist.isBlocked(cname)) {
                Log.i(TAG, "CNAME cloak detected: $cname blocked in chain $cnameChain")
                return CnameResult(blocked = true, blockedCname = cname, cnameChain = cnameChain)
            }
        }

        return CnameResult(blocked = false, blockedCname = null, cnameChain = cnameChain)
    }

    /**
     * Extract all CNAME targets from a DNS response's answer section.
     *
     * @param response Raw DNS response bytes
     * @return List of CNAME target domain names (lowercased)
     */
    fun extractCnameChain(response: ByteArray): List<String> {
        if (response.size < 12) return emptyList()

        val anCount = (response[6].toInt() and 0xFF shl 8) or (response[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        // Skip question section
        var off = 12
        val qdCount = (response[4].toInt() and 0xFF shl 8) or (response[5].toInt() and 0xFF)
        for (i in 0 until qdCount) {
            off = skipName(response, off)
            if (off < 0 || off >= response.size) return emptyList()
            off += 4 // QTYPE + QCLASS
        }

        val cnames = mutableListOf<String>()
        for (i in 0 until anCount.coerceAtMost(MAX_CHAIN_LENGTH)) {
            if (off >= response.size) break
            off = skipName(response, off)
            if (off < 0 || off + 10 > response.size) break

            val rtype = (response[off].toInt() and 0xFF shl 8) or (response[off + 1].toInt() and 0xFF)
            val rdLen = (response[off + 8].toInt() and 0xFF shl 8) or (response[off + 9].toInt() and 0xFF)
            off += 10 // TYPE + CLASS + TTL + RDLENGTH

            if (rtype == TYPE_CNAME && off + rdLen <= response.size) {
                val cname = readName(response, off)
                if (cname != null) {
                    cnames.add(cname.lowercase())
                }
            }
            off += rdLen
        }

        return cnames
    }

    /**
     * Extract resolved IPs from a DNS response (for logging/detail view).
     *
     * @param response Raw DNS response bytes
     * @return List of IP address strings (IPv4 and IPv6)
     */
    fun extractAnswerIps(response: ByteArray): List<String> {
        if (response.size < 12) return emptyList()

        val anCount = (response[6].toInt() and 0xFF shl 8) or (response[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        var off = 12
        val qdCount = (response[4].toInt() and 0xFF shl 8) or (response[5].toInt() and 0xFF)
        for (i in 0 until qdCount) {
            off = skipName(response, off)
            if (off < 0 || off >= response.size) return emptyList()
            off += 4
        }

        val ips = mutableListOf<String>()
        for (i in 0 until anCount.coerceAtMost(10)) {
            if (off >= response.size) break
            off = skipName(response, off)
            if (off < 0 || off + 10 > response.size) break

            val rtype = (response[off].toInt() and 0xFF shl 8) or (response[off + 1].toInt() and 0xFF)
            val rdLen = (response[off + 8].toInt() and 0xFF shl 8) or (response[off + 9].toInt() and 0xFF)
            off += 10

            if (off + rdLen > response.size) break

            when {
                rtype == 1 && rdLen == 4 -> { // A record
                    ips.add("${response[off].toInt() and 0xFF}.${response[off+1].toInt() and 0xFF}." +
                        "${response[off+2].toInt() and 0xFF}.${response[off+3].toInt() and 0xFF}")
                }
                rtype == 28 && rdLen == 16 -> { // AAAA record
                    try {
                        ips.add(java.net.InetAddress.getByAddress(
                            response.copyOfRange(off, off + 16)).hostAddress ?: "")
                    } catch (_: Exception) { }
                }
            }
            off += rdLen
        }
        return ips
    }

    // ── Name parsing ─────────────────────────────────────────

    private fun skipName(data: ByteArray, start: Int): Int {
        var pos = start
        var iterations = 0
        while (pos < data.size && iterations++ < 64) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if (len and 0xC0 == 0xC0) return pos + 2
            pos += 1 + len
        }
        return -1
    }

    /**
     * Read a DNS name at the given offset, following compression pointers.
     */
    private fun readName(data: ByteArray, start: Int): String? {
        val sb = StringBuilder(64)
        var pos = start
        var iterations = 0
        var jumped = false

        while (pos < data.size && iterations++ < 64) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) {
                // Compression pointer
                if (pos + 1 >= data.size) return null
                val ptr = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                if (ptr >= data.size) return null
                pos = ptr
                jumped = true
                continue
            }
            if (pos + 1 + len > data.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) {
                sb.append(data[pos + i].toInt().toChar())
            }
            pos += 1 + len
        }

        return if (sb.isNotEmpty()) sb.toString() else null
    }
}
