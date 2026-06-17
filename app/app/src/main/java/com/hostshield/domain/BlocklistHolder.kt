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
        val wildcardRules: List<UserRule>,
        val regexBlockRules: List<TimedRegex>,
        val regexAllowRules: List<TimedRegex>,
        val blockedIps: Set<String>,
        val domainCount: Int,
        val sourceWildcardBlockDomains: Set<String>,
        val exactBlockOrigins: Map<String, String>,
        val sourceWildcardBlockOrigins: Map<String, String>,
        val sourceExactAllowDomains: Set<String>,
        val sourceWildcardAllowDomains: Set<String>,
    ) {
        companion object {
            val EMPTY = Snapshot(
                root = TrieNode(),
                exactBlockSet = emptySet(),
                wildcardRules = emptyList(),
                regexBlockRules = emptyList(),
                regexAllowRules = emptyList(),
                blockedIps = emptySet(),
                domainCount = 0,
                sourceWildcardBlockDomains = emptySet(),
                exactBlockOrigins = emptyMap(),
                sourceWildcardBlockOrigins = emptyMap(),
                sourceExactAllowDomains = emptySet(),
                sourceWildcardAllowDomains = emptySet(),
            )
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
            wildcardRules = wildcards,
            regexBlockRules = newRegexBlock.map(::TimedRegex),
            regexAllowRules = newRegexAllow.map(::TimedRegex),
            blockedIps = ipBlocks,
            domainCount = newDomains.size + normalizedSourceWildcardBlocks.size + dohBypassDomains.size,
            sourceWildcardBlockDomains = normalizedSourceWildcardBlocks,
            exactBlockOrigins = normalizedExactBlockOrigins,
            sourceWildcardBlockOrigins = normalizedSourceWildcardBlockOrigins,
            sourceExactAllowDomains = normalizedSourceExactAllows,
            sourceWildcardAllowDomains = normalizedSourceWildcardAllows,
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
            sourceExactAllows
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
        snapshot = Snapshot(
            root = current.root,
            exactBlockSet = newSet,
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = current.domainCount + 1,
            sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
            exactBlockOrigins = current.exactBlockOrigins + (h to "User block rule"),
            sourceWildcardBlockOrigins = current.sourceWildcardBlockOrigins,
            sourceExactAllowDomains = current.sourceExactAllowDomains,
            sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
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
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = (current.domainCount - 1).coerceAtLeast(0),
            sourceWildcardBlockDomains = current.sourceWildcardBlockDomains,
            exactBlockOrigins = current.exactBlockOrigins - h,
            sourceWildcardBlockOrigins = current.sourceWildcardBlockOrigins,
            sourceExactAllowDomains = current.sourceExactAllowDomains,
            sourceWildcardAllowDomains = current.sourceWildcardAllowDomains,
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
    fun isBlocked(hostname: String): Boolean {
        return decide(hostname).blocked
    }

    fun decide(hostname: String): BlockDecision {
        val lower = hostname.lowercase()

        // L1: Filter decision LRU cache — O(1) for hot domains. LinkedHashMap in
        // access-order auto-evicts the LRU entry on overflow (removeEldestEntry).
        // Snapshot once so we evaluate against a consistent view across the
        // entire decision (trie + set + regex). A concurrent update() can land
        // mid-evaluation and atomically swap `snapshot`, but the local copy is
        // immutable from this thread's perspective.
        val snap = snapshot
        decisionCache[lower]?.let { cached ->
            if (cached.snapshot === snap) {
                return cached.decision
            }
            decisionCache.remove(lower)
        }

        val result = decideInternal(lower, snap)
        if (snapshot === snap) {
            decisionCache[lower] = CachedDecision(snap, result)
        }
        return result
    }

    /** Check if a resolved IP address is in the IP blocklist. */
    fun isIpBlocked(ip: String): Boolean = ip in snapshot.blockedIps

    private fun decideInternal(lower: String, snap: Snapshot): BlockDecision {
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
        if (lower in snap.sourceExactAllowDomains) {
            return BlockDecision(
                blocked = false,
                reason = "allowlist",
                source = "Source allowlist",
                matchedValue = lower,
                precedence = "source allowlist overrides source and user block entries"
            )
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
            val wwwDecision = decideInternal(lower.removePrefix("www."), snap)
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
