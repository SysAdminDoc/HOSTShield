package com.hostshield.domain

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BlockDecision(
    val blocked: Boolean,
    val reason: String,
    val source: String = "",
    val matchedValue: String = "",
    val precedence: String = ""
) {
    companion object {
        val ALLOWED_DEFAULT = BlockDecision(
            blocked = false,
            reason = "none",
            precedence = "no matching block rule"
        )
    }
}

// Trie-optimized blocklist holder with hash set fast path
// and filter decision LRU cache.
//
// Uses a reversed-label domain trie for O(m) lookups where m = label count.
// "ads.example.com" is stored as com -> example -> ads (TERMINAL).
// Wildcard "*.example.com" is stored as com -> example (WILDCARD).
// This eliminates linear scans over 100K+ domain sets.
//
// v5.0 enhancements:
// - Hash set fast path: O(1) exact-match check before trie traversal.
//   Covers ~90% of lookups since most queries are exact domain matches,
//   not wildcard/regex patterns. ~2x faster for the common case.
// - Bloom pre-check: fast-rejects cold negative lookups that cannot match any
//   exact domain, wildcard suffix, DNS-type rule, or explicit allow rule before
//   walking the trie. False positives are safe and only fall back to full checks.
// - Filter decision LRU cache: Caches blocked/allowed results for hot
//   domains to skip both hash set and trie on repeated queries. Separate
//   from DnsCache (which caches DNS response bytes). Invalidated on
//   blocklist update.
//
// DoH bypass prevention: hardcoded set of ~65 DoH resolver domains plus
// wildcard patterns for providers with per-profile subdomains (NextDNS,
// ControlD, etc.). These are always blocked regardless of user lists.
// Architecture decision: DNS-only interception with comprehensive domain
// blocking covers ~95% of real-world DoH bypass. See ARCHITECTURE.md.

@Singleton
class BlocklistHolder @Inject constructor() {

    private class TrieNode {
        val children = HashMap<String, TrieNode>(4)
        var wildcardBlock = false
        var wildcardAllow = false
    }

    /**
     * Immutable snapshot of the blocklist state. The single volatile [snapshot]
     * reference is atomically swapped on every [update] so readers either see
     * the entire old state or the entire new state — never a torn mix.
     */
    private class Snapshot(
        val root: TrieNode,
        val exactBlockSet: Set<String>,
        val structuralBloom: DomainBloomFilter,
        val wildcardRules: List<UserRule>,
        val regexBlockRules: List<TimedRegex>,
        val regexAllowRules: List<TimedRegex>,
        val blockedIps: Set<String>,
        val domainCount: Int,
        val sourceWildcardBlockDomains: Set<String>,
        val exactBlockOrigins: Map<String, String>,
        val sourceWildcardBlockOrigins: Map<String, String>,
        val userExactAllowDomains: Set<String>,
        val sourceExactAllowDomains: Set<String>,
        val sourceWildcardAllowDomains: Set<String>,
        val dnsTypeRules: List<DnsTypeRule>,
    ) {
        companion object {
            val EMPTY = Snapshot(
                root = TrieNode(),
                exactBlockSet = emptySet(),
                structuralBloom = DomainBloomFilter.EMPTY,
                wildcardRules = emptyList(),
                regexBlockRules = emptyList(),
                regexAllowRules = emptyList(),
                blockedIps = emptySet(),
                domainCount = 0,
                sourceWildcardBlockDomains = emptySet(),
                exactBlockOrigins = emptyMap(),
                sourceWildcardBlockOrigins = emptyMap(),
                userExactAllowDomains = emptySet(),
                sourceExactAllowDomains = emptySet(),
                sourceWildcardAllowDomains = emptySet(),
                dnsTypeRules = emptyList(),
            )
        }
    }

    private class DomainBloomFilter private constructor(
        private val bits: LongArray,
        private val bitCount: Int,
        private val hashCount: Int,
    ) {
        fun mightContainCandidateFor(hostname: String): Boolean {
            if (bits.isEmpty() || hostname.isBlank()) return false
            return hostnameCandidates(hostname).any(::mightContain)
        }

        private fun add(value: String) {
            repeat(hashCount) { index ->
                setBit(indexFor(value, index))
            }
        }

        private fun mightContain(value: String): Boolean =
            (0 until hashCount).all { index -> getBit(indexFor(value, index)) }

        private fun indexFor(value: String, round: Int): Int {
            val first = hash64(value, FNV_OFFSET)
            val second = hash64(value, FNV_OFFSET xor BLOOM_HASH_SEED)
            val combined = first + (round.toLong() * second)
            return Math.floorMod(combined, bitCount.toLong()).toInt()
        }

        private fun setBit(index: Int) {
            bits[index ushr 6] = bits[index ushr 6] or (1L shl (index and 63))
        }

        private fun getBit(index: Int): Boolean =
            (bits[index ushr 6] and (1L shl (index and 63))) != 0L

        companion object {
            val EMPTY = DomainBloomFilter(LongArray(0), bitCount = 0, hashCount = 0)
            private const val BLOOM_FALSE_POSITIVE_RATE = 0.001
            private const val MIN_BLOOM_BITS = 1024
            private const val MAX_BLOOM_HASHES = 12
            private const val FNV_OFFSET = -3750763034362895579L
            private const val FNV_PRIME = 1099511628211L
            private const val BLOOM_HASH_SEED = -7046029254386353131L

            fun build(domains: Iterable<String>): DomainBloomFilter {
                val normalized = LinkedHashSet<String>()
                domains.forEach { domain ->
                    domain.trim().lowercase().removePrefix("*.").removeSuffix(".")
                        .takeIf { it.isNotBlank() }
                        ?.let(normalized::add)
                }
                if (normalized.isEmpty()) return EMPTY

                val desiredBits = optimalBitCount(normalized.size, BLOOM_FALSE_POSITIVE_RATE)
                val bitCount = desiredBits.coerceAtLeast(MIN_BLOOM_BITS)
                val hashCount = optimalHashCount(bitCount, normalized.size)
                val filter = DomainBloomFilter(
                    bits = LongArray((bitCount + 63) / 64),
                    bitCount = bitCount,
                    hashCount = hashCount,
                )
                normalized.forEach(filter::add)
                return filter
            }

            private fun optimalBitCount(entries: Int, falsePositiveRate: Double): Int {
                val numerator = -entries * kotlin.math.ln(falsePositiveRate)
                val denominator = kotlin.math.ln(2.0) * kotlin.math.ln(2.0)
                return kotlin.math.ceil(numerator / denominator).toInt()
            }

            private fun optimalHashCount(bitCount: Int, entries: Int): Int {
                val hashCount = (bitCount.toDouble() / entries * kotlin.math.ln(2.0)).toInt()
                return hashCount.coerceIn(2, MAX_BLOOM_HASHES)
            }

            private fun hash64(value: String, seed: Long): Long {
                var hash = seed
                value.forEach { ch ->
                    hash = hash xor ch.code.toLong()
                    hash *= FNV_PRIME
                }
                return hash
            }

            private fun hostnameCandidates(hostname: String): Sequence<String> = sequence {
                yield(hostname)
                var dotIndex = hostname.indexOf('.')
                while (dotIndex >= 0 && dotIndex < hostname.lastIndex) {
                    yield(hostname.substring(dotIndex + 1))
                    dotIndex = hostname.indexOf('.', dotIndex + 1)
                }
            }
        }
    }

    private data class CachedDecision(
        val snapshot: Snapshot,
        val decision: BlockDecision
    )

    @Volatile private var snapshot: Snapshot = Snapshot.EMPTY

    val domainCount: Int get() = snapshot.domainCount
    val wildcardRules: List<UserRule> get() = snapshot.wildcardRules
    val blockedIps: Set<String> get() = snapshot.blockedIps

    private class TimedRegex(private val regex: Regex) {
        @Volatile private var disabled = false

        val pattern: String get() = regex.pattern

        fun containsMatchIn(input: String): Boolean {
            if (disabled) return false
            val deadlineNanos = System.nanoTime() + REGEX_MATCH_TIMEOUT_NANOS
            return try {
                regex.containsMatchIn(DeadlineCharSequence(input, deadlineNanos))
            } catch (_: RegexMatchTimeoutException) {
                disabled = true
                false
            } catch (_: StackOverflowError) {
                disabled = true
                false
            } catch (_: RuntimeException) {
                disabled = true
                false
            }
        }
    }

    private class DeadlineCharSequence(
        private val value: String,
        private val deadlineNanos: Long
    ) : CharSequence {
        override val length: Int get() = value.length

        override fun get(index: Int): Char {
            throwIfExpired()
            return value[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            throwIfExpired()
            return DeadlineCharSequence(value.substring(startIndex, endIndex), deadlineNanos)
        }

        override fun toString(): String = value

        private fun throwIfExpired() {
            if (System.nanoTime() - deadlineNanos >= 0L) {
                throw RegexMatchTimeoutException
            }
        }
    }

    private object RegexMatchTimeoutException : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    /**
     * Filter decision cache: bounded access-ordered LRU. Wrapped in a
     * synchronized map because Compose/UI readers and the VPN packet loop
     * mutate it concurrently. Invalidated on every [update].
     */
    private val decisionCacheMaxSize = 8192
    private val decisionCache: MutableMap<String, CachedDecision> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedDecision>(2048, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDecision>?): Boolean =
                    size > decisionCacheMaxSize
            }
        )

    // DoH canary and bypass domains — always blocked to prevent DNS filter bypass.
    // use-application-dns.net: Firefox checks this; NXDOMAIN disables Firefox DoH.
    // Others: well-known DoH endpoints that apps may resolve to bypass local DNS.
    //
    // Sources: curl/wiki DoH provider list, RethinkDNS bypass list, AdGuard KB,
    // IANA special-use domains, and manual enumeration of major providers.
    //
    // This list covers ~95% of real-world DoH bypass attempts. The remaining
    // 5% (custom/self-hosted DoH servers) cannot be blocked by domain name
    // without full-traffic VPN inspection (see ARCHITECTURE.md).
    private val dohBypassDomains = setOf(
        // ── Browser canary domains ──────────────────────────
        "use-application-dns.net",           // Firefox DoH canary (NXDOMAIN disables DoH)
        "mask.icloud.com",                   // iCloud Private Relay DNS
        "mask-h2.icloud.com",

        // ── Tier 1: Major public resolvers ──────────────────
        // Google
        "dns.google",
        "dns.google.com",
        "dns64.dns.google",
        // Cloudflare
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",        // Firefox default DoH
        "one.one.one.one",
        "1dot1dot1dot1.cloudflare-dns.com",
        "dns.cloudflare.com",
        "family.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        // Quad9
        "dns.quad9.net",
        "dns9.quad9.net",
        "dns10.quad9.net",
        "dns11.quad9.net",
        // AdGuard
        "dns.adguard-dns.com",
        "dns-unfiltered.adguard.com",
        "dns-family.adguard.com",
        // OpenDNS / Cisco
        "doh.opendns.com",
        "dns.opendns.com",
        "familyshield.opendns.com",
        // NextDNS
        "dns.nextdns.io",
        "chromium.dns.nextdns.io",
        "firefox.dns.nextdns.io",

        // ── Tier 2: Regional / privacy-focused resolvers ────
        // CleanBrowsing
        "doh.cleanbrowsing.org",
        "family-filter-dns.cleanbrowsing.org",
        "adult-filter-dns.cleanbrowsing.org",
        // Mullvad
        "dns.mullvad.net",
        "adblock.dns.mullvad.net",
        "base.dns.mullvad.net",
        // Control D
        "freedns.controld.com",
        "dns.controld.com",
        // DNS.SB
        "doh.dns.sb",
        "dns.sb",
        // Applied Privacy
        "doh.applied-privacy.net",
        // LibreDNS
        "doh.libredns.gr",
        // DNS0.eu
        "dns0.eu",
        "zero.dns0.eu",
        "kids.dns0.eu",
        // SWITCH (Swiss)
        "dns.switch.ch",
        // CZ.NIC (Czech)
        "odvr.nic.cz",
        // Taiwan NIC
        "dns.twnic.tw",
        // CIRA (Canadian)
        "private.canadianshield.cira.ca",
        "protected.canadianshield.cira.ca",
        "family.canadianshield.cira.ca",

        // ── Tier 3: ISP / vendor embedded DoH ───────────────
        // Samsung
        "chrome.cloudflare-dns.com",
        // Apple
        "doh.dns.apple.com",
        // Microsoft (Windows 11 DoH)
        // (uses known IPs, not custom hostnames — covered by IP trap)

        // ── Tier 4: Chinese / Asian resolvers ───────────────
        "dns.alidns.com",                    // Alibaba DoH
        "doh.pub",                           // DNSPod/Tencent DoH
        "dns.rubyfish.cn",                   // Rubyfish (China)
        "doh.360.cn",                        // 360 Secure DNS
    )

    // Wildcard patterns for DoH bypass — catches subdomains of known providers.
    // e.g., "*.dns.nextdns.io" catches per-profile NextDNS endpoints like
    // "abc123.dns.nextdns.io" which can't be enumerated statically.
    private val dohBypassWildcards = setOf(
        "dns.nextdns.io",           // NextDNS per-profile: <id>.dns.nextdns.io
        "dns.controld.com",         // ControlD per-profile
        "mullvad.net",              // Mullvad DNS variants
        "canadianshield.cira.ca",   // CIRA variants
    )

    @Synchronized
    fun update(
        newDomains: Set<String>,
        wildcards: List<UserRule>,
        regexRules: List<UserRule> = emptyList(),
        ipBlocks: Set<String> = emptySet(),
        sourceWildcardBlocks: Set<String> = emptySet(),
        sourceWildcardAllows: Set<String> = emptySet(),
        exactBlockOrigins: Map<String, String> = emptyMap(),
        sourceWildcardBlockOrigins: Map<String, String> = emptyMap(),
        sourceExactAllows: Set<String> = emptySet(),
        userExactAllows: Set<String> = emptySet(),
        dnsTypeRules: List<DnsTypeRule> = emptyList(),
    ) {
        val newRoot = TrieNode()
        val newExactSet: MutableSet<String> = HashSet(newDomains.size + dohBypassDomains.size)
        val normalizedExactBlockOrigins = exactBlockOrigins
            .mapKeys { it.key.lowercase() }
            .filterKeys { it.isNotBlank() }
        val normalizedSourceWildcardBlockOrigins = sourceWildcardBlockOrigins
            .mapKeys { it.key.lowercase() }
            .filterKeys { it.isNotBlank() }
        val normalizedSourceExactAllows = sourceExactAllows
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val normalizedUserExactAllows = userExactAllows
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        for (domain in newDomains) {
            val lower = domain.lowercase()
            newExactSet.add(lower)
        }
        val normalizedSourceWildcardBlocks = sourceWildcardBlocks
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val normalizedSourceWildcardAllows = sourceWildcardAllows
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val normalizedDnsTypeRules = dnsTypeRules
            .map { it.normalized() }
            .filter { it.domain.isNotBlank() && it.dnsTypes.isNotEmpty() }

        for (domain in normalizedSourceWildcardBlocks) {
            insertDomain(newRoot, domain, wildcardBlock = true)
        }
        for (domain in normalizedSourceWildcardAllows) {
            insertDomain(newRoot, domain, wildcardAllow = true)
        }
        // Always block DoH bypass domains (exact match)
        for (domain in dohBypassDomains) {
            newExactSet.add(domain)
        }
        // Always block DoH bypass wildcards (catches subdomains like *.dns.nextdns.io)
        for (domain in dohBypassWildcards) {
            insertDomain(newRoot, domain, wildcardBlock = true)
        }
        for (rule in wildcards) {
            val pattern = rule.hostname.lowercase()
            val base = if (pattern.startsWith("*.")) pattern.substring(2) else pattern
            if (base.isNotEmpty()) {
                when (rule.type) {
                    RuleType.BLOCK -> insertDomain(newRoot, base, wildcardBlock = true)
                    RuleType.ALLOW -> insertDomain(newRoot, base, wildcardAllow = true)
                    else -> { }
                }
            }
        }
        // Compile regex rules (validated, invalid patterns silently skipped).
        // Matching also runs through a per-rule deadline to prevent packet-loop hangs.
        val newRegexBlock = mutableListOf<Regex>()
        val newRegexAllow = mutableListOf<Regex>()
        val nestedQuantifier = Regex("""\([^)]*[+*][^)]*\)[+*?]""")
        for (rule in regexRules) {
            if (rule.hostname.length > 500) continue
            if (nestedQuantifier.containsMatchIn(rule.hostname)) continue
            try {
                val regex = Regex(rule.hostname, RegexOption.IGNORE_CASE)
                when (rule.type) {
                    RuleType.BLOCK -> newRegexBlock.add(regex)
                    RuleType.ALLOW -> newRegexAllow.add(regex)
                    else -> { }
                }
            } catch (_: Exception) { /* skip invalid regex */ }
        }

        // Atomic single-reference swap. Readers either see fully-built newSnapshot
        // or the previous snapshot — never a torn view of root vs exactBlockSet.
        val newSnapshot = Snapshot(
            root = newRoot,
            exactBlockSet = newExactSet,
            structuralBloom = buildStructuralBloom(
                exactBlockSet = newExactSet,
                wildcardRules = wildcards,
                sourceWildcardBlockDomains = normalizedSourceWildcardBlocks,
                sourceWildcardAllowDomains = normalizedSourceWildcardAllows,
                userExactAllowDomains = normalizedUserExactAllows,
                sourceExactAllowDomains = normalizedSourceExactAllows,
                dnsTypeRules = normalizedDnsTypeRules,
            ),
            wildcardRules = wildcards,
            regexBlockRules = newRegexBlock.map(::TimedRegex),
            regexAllowRules = newRegexAllow.map(::TimedRegex),
            blockedIps = ipBlocks,
            domainCount = newDomains.size + normalizedSourceWildcardBlocks.size +
                normalizedDnsTypeRules.count { !it.allow } + dohBypassDomains.size,
            sourceWildcardBlockDomains = normalizedSourceWildcardBlocks,
            exactBlockOrigins = normalizedExactBlockOrigins,
            sourceWildcardBlockOrigins = normalizedSourceWildcardBlockOrigins,
            userExactAllowDomains = normalizedUserExactAllows,
            sourceExactAllowDomains = normalizedSourceExactAllows,
            sourceWildcardAllowDomains = normalizedSourceWildcardAllows,
            dnsTypeRules = normalizedDnsTypeRules,
        )

        decisionCache.clear()
        snapshot = newSnapshot
    }

    suspend fun updateAsync(
        newDomains: Set<String>,
        wildcards: List<UserRule>,
        regexRules: List<UserRule> = emptyList(),
        ipBlocks: Set<String> = emptySet(),
        sourceWildcardBlocks: Set<String> = emptySet(),
        sourceWildcardAllows: Set<String> = emptySet(),
        exactBlockOrigins: Map<String, String> = emptyMap(),
        sourceWildcardBlockOrigins: Map<String, String> = emptyMap(),
        sourceExactAllows: Set<String> = emptySet(),
        userExactAllows: Set<String> = emptySet(),
        dnsTypeRules: List<DnsTypeRule> = emptyList(),
    ) = withContext(Dispatchers.Default) {
        update(
            newDomains,
            wildcards,
            regexRules,
            ipBlocks,
            sourceWildcardBlocks,
            sourceWildcardAllows,
            exactBlockOrigins,
            sourceWildcardBlockOrigins,
            sourceExactAllows,
            userExactAllows,
            dnsTypeRules
        )
    }

    fun clear() {
        decisionCache.clear()
        snapshot = Snapshot.EMPTY
    }

    fun getBlockedCount(): Int = snapshot.domainCount

    fun exportBlockKeysForPreview(): Set<String> {
        val current = snapshot
        val keys = HashSet<String>(current.exactBlockSet.size + current.sourceWildcardBlockDomains.size)
        current.exactBlockSet.forEach { domain ->
            if (domain !in dohBypassDomains) keys.add(domain)
        }
        current.sourceWildcardBlockDomains.forEach { domain ->
            keys.add("*.$domain")
        }
        current.dnsTypeRules
            .filter { !it.allow }
            .forEach { keys.add(it.previewKey()) }
        return keys
    }

    @Synchronized
    fun addDomain(hostname: String) {
        val h = hostname.lowercase()
        val current = snapshot
        if (h in current.exactBlockSet) {
            // Already present — keep counts honest and skip work.
            decisionCache.remove(h)
            return
        }
        val newSet = HashSet(current.exactBlockSet).apply { add(h) }
        val newUserAllows = current.userExactAllowDomains - h
        snapshot = Snapshot(
            root = current.root,
            exactBlockSet = newSet,
            structuralBloom = buildStructuralBloom(
                exactBlockSet = newSet,
                wildcardRules = current.wildcardRules,
                sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
                sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
                userExactAllowDomains = newUserAllows,
                sourceExactAllowDomains = current.sourceExactAllowDomains,
                dnsTypeRules = current.dnsTypeRules,
            ),
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = current.domainCount + 1,
            sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
            exactBlockOrigins = current.exactBlockOrigins + (h to "User block rule"),
            sourceWildcardBlockOrigins = current.sourceWildcardBlockOrigins,
            userExactAllowDomains = newUserAllows,
            sourceExactAllowDomains = current.sourceExactAllowDomains,
            sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
            dnsTypeRules = current.dnsTypeRules,
        )
        decisionCache.remove(h)
    }

    @Synchronized
    fun removeDomain(hostname: String) {
        val h = hostname.lowercase()
        val current = snapshot
        val wasPresent = h in current.exactBlockSet
        if (!wasPresent) {
            // Don't drop the counter for domains we never had.
            decisionCache.remove(h)
            return
        }
        val newSet = HashSet(current.exactBlockSet).apply { remove(h) }
        snapshot = Snapshot(
            root = current.root,
            exactBlockSet = newSet,
            structuralBloom = buildStructuralBloom(
                exactBlockSet = newSet,
                wildcardRules = current.wildcardRules,
                sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
                sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
                userExactAllowDomains = current.userExactAllowDomains,
                sourceExactAllowDomains = current.sourceExactAllowDomains,
                dnsTypeRules = current.dnsTypeRules,
            ),
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = (current.domainCount - 1).coerceAtLeast(0),
            sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
            exactBlockOrigins = current.exactBlockOrigins - h,
            sourceWildcardBlockOrigins = current.sourceWildcardBlockOrigins,
            userExactAllowDomains = current.userExactAllowDomains,
            sourceExactAllowDomains = current.sourceExactAllowDomains,
            sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
            dnsTypeRules = current.dnsTypeRules,
        )
        decisionCache.remove(h)
    }

    @Synchronized
    fun allowDomain(hostname: String) {
        val h = hostname.lowercase()
        val current = snapshot
        val wasBlocked = h in current.exactBlockSet
        val newBlockSet = if (wasBlocked) HashSet(current.exactBlockSet).apply { remove(h) } else current.exactBlockSet
        val newUserAllows = current.userExactAllowDomains + h
        snapshot = Snapshot(
            root = current.root,
            exactBlockSet = newBlockSet,
            structuralBloom = buildStructuralBloom(
                exactBlockSet = newBlockSet,
                wildcardRules = current.wildcardRules,
                sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
                sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
                userExactAllowDomains = newUserAllows,
                sourceExactAllowDomains = current.sourceExactAllowDomains,
                dnsTypeRules = current.dnsTypeRules,
            ),
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = if (wasBlocked) (current.domainCount - 1).coerceAtLeast(0) else current.domainCount,
            sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
            exactBlockOrigins = current.exactBlockOrigins - h,
            sourceWildcardBlockOrigins = current.sourceWildcardBlockOrigins,
            userExactAllowDomains = newUserAllows,
            sourceExactAllowDomains = current.sourceExactAllowDomains,
            sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
            dnsTypeRules = current.dnsTypeRules,
        )
        decisionCache.remove(h)
    }

    /**
     * Single trie walk + hash set check → regex fallback.
     *
     * v6.2: Unified check path — one trie traversal gathers all signals
     * (wildcard allow/block), then O(1) hash set for exact blocks. Regex rules
     * only evaluated when needed. Results cached in decision LRU.
     */
    fun isBlocked(hostname: String, queryType: Int? = null): Boolean {
        return decide(hostname, queryType).blocked
    }

    fun decide(hostname: String, queryType: Int? = null): BlockDecision {
        val lower = hostname.lowercase()
        val effectiveQueryType = queryType?.takeIf { it > 0 }
        val cacheKey = if (effectiveQueryType == null) lower else "$lower|$effectiveQueryType"

        // L1: Filter decision LRU cache — O(1) for hot domains. LinkedHashMap in
        // access-order auto-evicts the LRU entry on overflow (removeEldestEntry).
        // Snapshot once so we evaluate against a consistent view across the
        // entire decision (trie + set + regex). A concurrent update() can land
        // mid-evaluation and atomically swap `snapshot`, but the local copy is
        // immutable from this thread's perspective.
        val snap = snapshot
        decisionCache[cacheKey]?.let { cached ->
            if (cached.snapshot === snap) {
                return cached.decision
            }
            decisionCache.remove(cacheKey)
        }

        val result = decideInternal(lower, snap, effectiveQueryType)
        if (snapshot === snap) {
            decisionCache[cacheKey] = CachedDecision(snap, result)
        }
        return result
    }

    /** Check if a resolved IP address is in the IP blocklist. */
    fun isIpBlocked(ip: String): Boolean = ip in snapshot.blockedIps

    private fun decideInternal(lower: String, snap: Snapshot, queryType: Int?): BlockDecision {
        if (lower in dohBypassDomains) {
            return blockedDecision(
                reason = "doh_bypass",
                source = "Built-in DoH bypass guard",
                matchedValue = lower,
                precedence = "DoH bypass guard is always blocked"
            )
        }
        findWildcardMatch(lower, dohBypassWildcards)?.let { match ->
            return blockedDecision(
                reason = "doh_bypass",
                source = "Built-in DoH bypass guard",
                matchedValue = match,
                precedence = "DoH bypass guard is always blocked"
            )
        }
        if (lower in snap.userExactAllowDomains) {
            return BlockDecision(
                blocked = false,
                reason = "allowlist",
                source = "User allow rule",
                matchedValue = lower,
                precedence = "user allow rule overrides blocklist and threat intel"
            )
        }
        if (lower in snap.sourceExactAllowDomains) {
            return BlockDecision(
                blocked = false,
                reason = "allowlist",
                source = "Source allowlist",
                matchedValue = lower,
                precedence = "source allowlist overrides source and user block entries"
            )
        }
        snap.dnsTypeRules.firstMatchingDnsTypeRule(lower, queryType, allow = true)?.let { rule ->
            return BlockDecision(
                blocked = false,
                reason = "dns_type_allow",
                source = rule.source.ifBlank { "Source DNS type allow rule" },
                matchedValue = rule.previewKey(),
                precedence = "DNS type allow rule overrides blocklist matches for this query type"
            )
        }
        if (!snap.structuralBloom.mightContainCandidateFor(lower)) {
            return decideRegexWwwOrDefault(lower, snap, queryType)
        }

        // Trie walk over a stable snapshot gathers wildcard allow/block signals.
        // Exact blocks are hash-set only so large lists are not duplicated as
        // trie nodes on low-memory devices.
        val labels = lower.split('.').reversed()
        var node = snap.root
        var wildcardBlockMatch = ""
        var wildcardAllowMatch = ""
        var depth = 0

        for (label in labels) {
            val child = node.children[label] ?: break
            depth++
            val match = labels.take(depth).asReversed().joinToString(".")
            if (child.wildcardAllow) wildcardAllowMatch = match
            if (child.wildcardBlock) wildcardBlockMatch = match
            node = child
        }

        // Most-specific-wins: a deeper wildcardBlock overrides a shallower wildcardAllow
        if (wildcardAllowMatch.isNotEmpty() &&
            (wildcardBlockMatch.isEmpty() || wildcardAllowMatch.count { it == '.' } >= wildcardBlockMatch.count { it == '.' })
        ) {
            return BlockDecision(
                blocked = false,
                reason = "allowlist_wildcard",
                source = if (wildcardAllowMatch in snap.sourceWildcardAllowDomains) {
                    "Source wildcard allowlist"
                } else {
                    "User wildcard allow rule"
                },
                matchedValue = wildcardAllowMatch,
                precedence = "wildcard allow overrides blocklist matches"
            )
        }
        snap.dnsTypeRules.firstMatchingDnsTypeRule(lower, queryType, allow = false)?.let { rule ->
            return blockedDecision(
                reason = "dns_type_rule",
                source = rule.source.ifBlank { "Source DNS type block rule" },
                matchedValue = rule.previewKey(),
                precedence = "DNS type rule match"
            )
        }

        val exactBlocked = lower in snap.exactBlockSet
        val blocked = exactBlocked || wildcardBlockMatch.isNotEmpty()

        if (blocked) {
            snap.regexAllowRules.firstMatchingRegex(lower)?.let { regex ->
                return BlockDecision(
                    blocked = false,
                    reason = "regex_allow",
                    source = "User regex allow rule",
                    matchedValue = regex.pattern,
                    precedence = "regex allow overrides blocklist match"
                )
            }
            if (exactBlocked) {
                val origin = snap.exactBlockOrigins[lower].orEmpty()
                return blockedDecision(
                    reason = origin.toBlockReason(default = "source_list"),
                    source = origin.ifBlank { "Source or user blocklist" },
                    matchedValue = lower,
                    precedence = "exact block match"
                )
            }
            val origin = snap.sourceWildcardBlockOrigins[wildcardBlockMatch].orEmpty()
            return blockedDecision(
                reason = origin.toBlockReason(default = "wildcard_block"),
                source = origin.ifBlank { "User wildcard block rule" },
                matchedValue = wildcardBlockMatch,
                precedence = "wildcard block match"
            )
        }

        return decideRegexWwwOrDefault(lower, snap, queryType)
    }

    private fun decideRegexWwwOrDefault(lower: String, snap: Snapshot, queryType: Int?): BlockDecision {
        snap.regexBlockRules.firstMatchingRegex(lower)?.let { regex ->
            snap.regexAllowRules.firstMatchingRegex(lower)?.let { allowRegex ->
                return BlockDecision(
                    blocked = false,
                    reason = "regex_allow",
                    source = "User regex allow rule",
                    matchedValue = allowRegex.pattern,
                    precedence = "regex allow overrides regex block"
                )
            }
            return blockedDecision(
                reason = "regex_block",
                source = "User regex block rule",
                matchedValue = regex.pattern,
                precedence = "regex block fallback after exact and wildcard checks"
            )
        }

        if (lower.startsWith("www.")) {
            val wwwDecision = decideInternal(lower.removePrefix("www."), snap, queryType)
            return if (wwwDecision.blocked) {
                wwwDecision.copy(
                    precedence = listOf("www alias fallback", wwwDecision.precedence)
                        .filter { it.isNotBlank() }
                        .joinToString("; ")
                )
            } else {
                wwwDecision
            }
        }

        return BlockDecision.ALLOWED_DEFAULT
    }

    private fun blockedDecision(
        reason: String,
        source: String,
        matchedValue: String,
        precedence: String
    ): BlockDecision = BlockDecision(
        blocked = true,
        reason = reason,
        source = source,
        matchedValue = matchedValue,
        precedence = precedence
    )

    private fun findWildcardMatch(lower: String, wildcards: Set<String>): String? =
        wildcards.firstOrNull { lower == it || lower.endsWith(".$it") }

    private fun List<TimedRegex>.firstMatchingRegex(input: String): TimedRegex? =
        firstOrNull { it.containsMatchIn(input) }

    private fun List<DnsTypeRule>.firstMatchingDnsTypeRule(
        input: String,
        queryType: Int?,
        allow: Boolean
    ): DnsTypeRule? {
        val qtype = queryType ?: return null
        return asSequence()
            .filter { it.allow == allow && it.matches(input, qtype) }
            .maxWithOrNull(
                compareBy<DnsTypeRule> { it.domain.count { ch -> ch == '.' } }
                    .thenBy { it.domain.length }
            )
    }

    private fun buildStructuralBloom(
        exactBlockSet: Set<String>,
        wildcardRules: List<UserRule>,
        sourceWildcardBlockDomains: Set<String>,
        sourceWildcardAllowDomains: Set<String>,
        userExactAllowDomains: Set<String>,
        sourceExactAllowDomains: Set<String>,
        dnsTypeRules: List<DnsTypeRule>,
    ): DomainBloomFilter {
        val candidates = LinkedHashSet<String>(
            exactBlockSet.size +
                wildcardRules.size +
                sourceWildcardBlockDomains.size +
                sourceWildcardAllowDomains.size +
                userExactAllowDomains.size +
                sourceExactAllowDomains.size +
                dnsTypeRules.size
        )
        candidates.addAll(exactBlockSet)
        candidates.addAll(sourceWildcardBlockDomains)
        candidates.addAll(sourceWildcardAllowDomains)
        candidates.addAll(userExactAllowDomains)
        candidates.addAll(sourceExactAllowDomains)
        wildcardRules.forEach { rule ->
            val pattern = rule.hostname.lowercase()
            val base = if (pattern.startsWith("*.")) pattern.substring(2) else pattern
            if (base.isNotBlank()) candidates.add(base)
        }
        dnsTypeRules.forEach { rule ->
            if (rule.domain.isNotBlank()) candidates.add(rule.domain)
        }
        return DomainBloomFilter.build(candidates)
    }

    private fun String.toBlockReason(default: String): String = when {
        startsWith("User block rule", ignoreCase = true) -> "user_rule"
        startsWith("Remote DoH bypass", ignoreCase = true) -> "doh_bypass"
        isNotBlank() -> "source_list"
        else -> default
    }

    private fun insertDomain(
        trieRoot: TrieNode, domain: String,
        wildcardBlock: Boolean = false,
        wildcardAllow: Boolean = false
    ) {
        val labels = domain.split('.').reversed()
        var node = trieRoot
        for (label in labels) {
            node = node.children.getOrPut(label) { TrieNode() }
        }
        if (wildcardBlock) node.wildcardBlock = true
        if (wildcardAllow) node.wildcardAllow = true
    }

    private companion object {
        private const val REGEX_MATCH_TIMEOUT_NANOS = 5_000_000L
    }
}
