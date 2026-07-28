package com.hostshield.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
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
// every query and, when true, calls buildSafeResponse() to return
// a query-type-aware answer for the service's safe endpoint.

@Singleton
class SafeSearchEnforcer @Inject constructor() {

    private data class SafeTarget(
        val canonicalHost: String,
        val fallbackIpv4: String?,
        val fallbackIpv6: String?,
        val domains: Set<String>,
        val googleDomainPattern: Boolean = false
    )

    private data class ResolvedTarget(
        val ipv4: String?,
        val ipv6: String?,
        val expiresAtMillis: Long
    )

    private val safeTargets = listOf(
        SafeTarget(
            canonicalHost = "forcesafesearch.google.com",
            fallbackIpv4 = "216.239.38.120",
            fallbackIpv6 = "::ffff:216.239.38.120",
            domains = setOf("google.com", "www.google.com"),
            googleDomainPattern = true
        ),
        SafeTarget(
            canonicalHost = "strict.bing.com",
            fallbackIpv4 = "204.79.197.220",
            fallbackIpv6 = null,
            domains = setOf("bing.com", "www.bing.com")
        ),
        SafeTarget(
            canonicalHost = "safe.duckduckgo.com",
            fallbackIpv4 = "52.250.42.157",
            fallbackIpv6 = null,
            domains = setOf("duckduckgo.com", "www.duckduckgo.com")
        ),
        SafeTarget(
            canonicalHost = "restrict.youtube.com",
            fallbackIpv4 = "216.239.38.120",
            fallbackIpv6 = "::ffff:216.239.38.120",
            domains = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
        )
    )

    private val resolvedTargets = ConcurrentHashMap<String, ResolvedTarget>()

    internal var addressResolver: (String) -> List<InetAddress> =
        { host -> InetAddress.getAllByName(host).toList() }
    internal var nowMillis: () -> Long = System::currentTimeMillis

    // ── Public API ───────────────────────────────────────────

    /**
     * Returns `true` if the given domain should be rewritten to
     * a safe-search IP address.
     */
    fun isSafeSearchDomain(domain: String): Boolean {
        return targetFor(domain) != null
    }

    /**
     * Returns the fallback safe-search IPv4 address for [domain], or
     * `null` if the domain is not in the safe-search table.
     */
    fun getSafeSearchIp(domain: String): String? {
        return targetFor(domain)?.fallbackIpv4
    }

    /**
     * Builds a DNS response that resolves [dns] query for [domain] to the
     * relevant safe-search endpoint.
     *
     * A queries return safe IPv4, AAAA queries return safe IPv6 when available
     * or NODATA, and HTTPS/SVCB/other metadata queries return NODATA to avoid
     * advertising an alternate clear endpoint.
     *
     * @param dns   The original DNS query bytes (>= 12 bytes).
     * @param domain Domain name from the DNS query.
     * @return A valid DNS response byte array, or `null` if
     *         the query is too short or the domain is not supported.
     */
    fun buildSafeResponse(dns: ByteArray, domain: String): ByteArray? {
        if (dns.size < 12) return null
        val target = targetFor(domain) ?: return null
        val resolved = resolveTarget(target)
        return when (DnsPacketBuilder.parseQueryType(dns)) {
            TYPE_A -> resolved.ipv4?.let { buildAddressResponse(dns, TYPE_A, it) }
            TYPE_AAAA -> resolved.ipv6?.let { buildAddressResponse(dns, TYPE_AAAA, it) } ?: buildNoDataResponse(dns)
            else -> buildNoDataResponse(dns)
        }
    }

    private fun targetFor(domain: String): SafeTarget? {
        val lower = domain.lowercase().trimEnd('.')
        return safeTargets.firstOrNull { target ->
            lower in target.domains || (target.googleDomainPattern && isGoogleSearchDomain(lower))
        }
    }

    private fun isGoogleSearchDomain(domain: String): Boolean {
        return domain == "google.com" ||
            domain == "www.google.com" ||
            GOOGLE_COUNTRY_DOMAIN.matches(domain)
    }

    private fun resolveTarget(target: SafeTarget): ResolvedTarget {
        val now = nowMillis()
        resolvedTargets[target.canonicalHost]?.let { cached ->
            if (cached.expiresAtMillis > now) return cached
        }
        return forceResolve(target)
    }

    /** Always performs the (blocking) system-resolver lookup and caches it. */
    private fun forceResolve(target: SafeTarget): ResolvedTarget {
        val now = nowMillis()
        val resolved = try {
            addressResolver(target.canonicalHost)
        } catch (_: Exception) {
            emptyList()
        }
        val fresh = ResolvedTarget(
            ipv4 = resolved.firstOrNull { it is Inet4Address }?.hostAddress ?: target.fallbackIpv4,
            ipv6 = resolved.firstOrNull { it is Inet6Address }?.hostAddress ?: target.fallbackIpv6,
            expiresAtMillis = now + RESOLUTION_CACHE_TTL_MS
        )
        resolvedTargets[target.canonicalHost] = fresh
        return fresh
    }

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    /**
     * Keep every safe-search endpoint resolved OFF the packet path.
     * `buildSafeResponse` runs inline in the single VPN packet-loop thread, and
     * a cold or expired cache there means a blocking system-resolver lookup
     * that stalls ALL device DNS for up to the resolver timeout. Refreshing
     * shortly before expiry keeps the inline path a pure cache hit.
     */
    fun startBackgroundRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = refreshScope.launch {
            while (isActive) {
                safeTargets.forEach { target ->
                    try { forceResolve(target) } catch (_: Exception) { }
                }
                delay(RESOLUTION_CACHE_TTL_MS - REFRESH_MARGIN_MS)
            }
        }
    }

    fun stopBackgroundRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun buildAddressResponse(dns: ByteArray, qtype: Int, ip: String): ByteArray? {
        val ipBytes = addressBytes(qtype, ip) ?: return null
        val expectedLength = if (qtype == TYPE_AAAA) 16 else 4
        if (ipBytes.size != expectedLength) return null

        val qEnd = questionEnd(dns) ?: return null

        val qSection = dns.sliceArray(12 until qEnd)

        val answerLen = 2 + 2 + 2 + 4 + 2 + ipBytes.size
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
        // TYPE
        resp[aOff + 2] = ((qtype shr 8) and 0xFF).toByte()
        resp[aOff + 3] = (qtype and 0xFF).toByte()
        // CLASS = IN (1)
        resp[aOff + 4] = 0; resp[aOff + 5] = 1
        // TTL = 300 seconds (0x0000012C)
        resp[aOff + 6] = 0; resp[aOff + 7] = 0
        resp[aOff + 8] = 1; resp[aOff + 9] = 0x2C.toByte()
        // RDLENGTH
        resp[aOff + 10] = ((ipBytes.size shr 8) and 0xFF).toByte()
        resp[aOff + 11] = (ipBytes.size and 0xFF).toByte()
        // RDATA = safe IP bytes
        System.arraycopy(ipBytes, 0, resp, aOff + 12, ipBytes.size)

        return resp
    }

    private fun addressBytes(qtype: Int, ip: String): ByteArray? {
        if (qtype == TYPE_AAAA && ip.startsWith("::ffff:", ignoreCase = true)) {
            val ipv4 = ipv4Bytes(ip.substringAfterLast(':')) ?: return null
            return ByteArray(16).apply {
                this[10] = 0xFF.toByte()
                this[11] = 0xFF.toByte()
                System.arraycopy(ipv4, 0, this, 12, 4)
            }
        }
        return try {
            InetAddress.getByName(ip).address
        } catch (_: Exception) {
            null
        }
    }

    private fun ipv4Bytes(ip: String): ByteArray? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return parts.map {
            val octet = it.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            octet.toByte()
        }.toByteArray()
    }

    private fun buildNoDataResponse(dns: ByteArray): ByteArray? {
        val qEnd = questionEnd(dns) ?: return null
        val qSection = dns.sliceArray(12 until qEnd)
        val resp = ByteArray(12 + qSection.size)
        resp[0] = dns[0]; resp[1] = dns[1]
        resp[2] = 0x81.toByte()
        resp[3] = 0x80.toByte()
        resp[4] = 0; resp[5] = 1
        resp[6] = 0; resp[7] = 0
        resp[8] = 0; resp[9] = 0
        resp[10] = 0; resp[11] = 0
        System.arraycopy(qSection, 0, resp, 12, qSection.size)
        return resp
    }

    private fun questionEnd(dns: ByteArray): Int? {
        val nameEnd = DnsPacketParser.skipDnsName(dns, 12)
        if (nameEnd < 0 || nameEnd + 4 > dns.size) return null
        return nameEnd + 4
    }

    companion object {
        private const val TYPE_A = 1
        private const val TYPE_AAAA = 28
        private const val RESOLUTION_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
        private val GOOGLE_COUNTRY_DOMAIN = Regex("""^(?:www\.)?google\.(?:[a-z]{2}|com)(?:\.[a-z]{2})?$""")
    }
}
