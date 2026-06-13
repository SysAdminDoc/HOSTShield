package com.hostshield.service

import android.util.Log
import com.hostshield.domain.BlocklistHolder
import com.hostshield.util.PrivacyLog

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
 * v5.0: Also checks CNAME targets against dedicated cloak databases from
 * AdGuard (github.com/AdguardTeam/cname-trackers) and NextDNS
 * (github.com/nextdns/cname-cloaking-blocklist). These are auto-updated
 * lists of known first-party→tracker CNAME mappings that supplement
 * the main blocklist.
 *
 * Used by:
 * - DnsVpnService: after forwarding an allowed query, inspect the response
 *   before sending it back to the app
 * - RootDnsLogger: if we ever add response inspection to root mode
 */
object CnameCloakDetector {

    private const val TAG = "CnameCloak"
    private const val TYPE_CNAME = 5
    private const val TYPE_HTTPS = 65  // HTTPS/SVCB record
    private const val TYPE_SVCB = 64
    private const val MAX_CHAIN_LENGTH = 10 // prevent infinite loops

    /**
     * v5.0: Known CNAME cloak tracker domains from community-maintained databases.
     * These are domains that are known CNAME cloaking targets — when a CNAME
     * in a response chain points to one of these, it's a tracker.
     *
     * Updated via CnameCloakUpdater (fetches from AdGuard + NextDNS lists).
     * This set is the fallback — remote updates supplement it.
     */
    @Volatile
    var cnameCloakDomains: Set<String> = setOf(
        // AdGuard cname-trackers: top known CNAME cloak targets
        // Source: https://github.com/AdguardTeam/cname-trackers/blob/master/combined_disguised_trackers.txt
        "2cnt.net",
        "a8723.com",
        "abtasty.com",
        "at-o.net",
        "eulerian.net",
        "keyade.com",
        "omtrdc.net",                    // Adobe Analytics CNAME cloak
        "storetail.io",
        "wizaly.com",
        "dnsdelegation.io",
        "pardot.com",                    // Salesforce tracker
        "criteo.net",                    // Criteo retargeting CNAME cloak
        "wt-eu02.net",                  // Webtrekk
        // NextDNS cname-cloaking-blocklist: additional known targets
        // Source: https://github.com/nextdns/cname-cloaking-blocklist
        "affex.org",
        "intentmedia.net",
        "ptr6237.net",
        "lead.center",
    )
        private set

    /**
     * Update the CNAME cloak database with remotely-fetched domains.
     * Called by CnameCloakUpdater after downloading fresh lists.
     * Merges with (never replaces) the hardcoded fallback set.
     */
    private val cloakDbLock = Any()

    fun updateCloakDatabase(remoteDomains: Set<String>) {
        synchronized(cloakDbLock) {
            val merged = HashSet<String>(cnameCloakDomains.size + remoteDomains.size)
            merged.addAll(cnameCloakDomains)
            merged.addAll(remoteDomains)
            cnameCloakDomains = merged
        }
        Log.i(TAG, "CNAME cloak database updated: ${cnameCloakDomains.size} domains")
    }

    data class CnameResult(
        /** Whether any CNAME target in the chain is blocked */
        val blocked: Boolean,
        /** The specific CNAME target that was blocked (for logging) */
        val blockedCname: String?,
        /** All CNAME targets found in the chain */
        val cnameChain: List<String>,
        /** Whether blocked via the dedicated cloak database (vs main blocklist) */
        val blockedViaCloakDb: Boolean = false
    )

    /**
     * Inspect a DNS response for CNAME cloaking.
     *
     * v5.0: Checks CNAME targets against both the main blocklist AND the
     * dedicated CNAME cloak database. Also extracts HTTPS/SVCB TargetName
     * for SVCB-based cloaking detection.
     *
     * @param response Raw DNS response bytes from upstream
     * @param blocklist The active blocklist to check CNAME targets against
     * @return CnameResult with blocked=true if any CNAME target is in the blocklist or cloak DB
     */
    fun inspect(response: ByteArray, blocklist: BlocklistHolder): CnameResult {
        val cnameChain = extractCnameChain(response)
        val svcbTargets = extractSvcbTargets(response)
        val allTargets = cnameChain + svcbTargets

        if (allTargets.isEmpty()) {
            return CnameResult(blocked = false, blockedCname = null, cnameChain = emptyList())
        }

        // Check each target in the chain against main blocklist
        for (target in allTargets) {
            if (blocklist.isBlocked(target)) {
                PrivacyLog.i(TAG, "CNAME cloak detected: $target blocked in chain $allTargets")
                return CnameResult(blocked = true, blockedCname = target, cnameChain = allTargets)
            }
        }

        // v5.0: Check against dedicated CNAME cloak database
        val cloakDb = cnameCloakDomains
        for (target in allTargets) {
            // Check exact match and parent domain match
            if (target in cloakDb || getParentDomain(target) in cloakDb) {
                PrivacyLog.i(TAG, "CNAME cloak detected (cloak DB): $target in chain $allTargets")
                return CnameResult(
                    blocked = true, blockedCname = target,
                    cnameChain = allTargets, blockedViaCloakDb = true
                )
            }
        }

        return CnameResult(blocked = false, blockedCname = null, cnameChain = allTargets)
    }

    /** Extract parent domain: "sub.tracker.example.com" → "tracker.example.com" */
    private fun getParentDomain(domain: String): String {
        val dot = domain.indexOf('.')
        return if (dot >= 0 && dot < domain.length - 1) domain.substring(dot + 1) else domain
    }

    /**
     * Extract all CNAME targets from a DNS response's answer section.
     *
     * @param response Raw DNS response bytes
     * @return List of CNAME target domain names (lowercased)
     */
    fun extractCnameChain(response: ByteArray): List<String> {
        if (response.size < 12) return emptyList()

        val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        // Skip question section
        var off = 12
        val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
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

            val rtype = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
            val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
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

        val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        var off = 12
        val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
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

            val rtype = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
            val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
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

    /**
     * v5.0: Extract SVCB/HTTPS TargetName from answer section.
     * SVCB (TYPE 64) and HTTPS (TYPE 65) records can redirect to a different
     * domain via TargetName, which can be used for SVCB-based cloaking.
     *
     * @param response Raw DNS response bytes
     * @return List of SVCB/HTTPS TargetName domains (lowercased)
     */
    fun extractSvcbTargets(response: ByteArray): List<String> {
        if (response.size < 12) return emptyList()

        val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        var off = 12
        val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
        for (i in 0 until qdCount) {
            off = skipName(response, off)
            if (off < 0 || off >= response.size) return emptyList()
            off += 4
        }

        val targets = mutableListOf<String>()
        for (i in 0 until anCount.coerceAtMost(MAX_CHAIN_LENGTH)) {
            if (off >= response.size) break
            off = skipName(response, off)
            if (off < 0 || off + 10 > response.size) break

            val rtype = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
            val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
            off += 10

            if (off + rdLen > response.size) break

            // SVCB (64) and HTTPS (65) records: RDATA starts with
            // SvcPriority (2 bytes) + TargetName (compressed name)
            if ((rtype == TYPE_SVCB || rtype == TYPE_HTTPS) && rdLen >= 3) {
                val priority = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
                // AliasMode (priority=0) has a TargetName that redirects
                // ServiceMode (priority>0) also has TargetName
                val targetName = readName(response, off + 2)
                if (targetName != null && targetName.isNotEmpty() && targetName != ".") {
                    targets.add(targetName.lowercase())
                }
            }
            off += rdLen
        }
        return targets
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
