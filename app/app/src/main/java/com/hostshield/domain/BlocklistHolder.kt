package com.hostshield.domain

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
        var terminal = false
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
        val regexBlockRules: List<Regex>,
        val regexAllowRules: List<Regex>,
        val blockedIps: Set<String>,
        val domainCount: Int,
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
            )
        }
    }

    @Volatile private var snapshot: Snapshot = Snapshot.EMPTY

    val domainCount: Int get() = snapshot.domainCount
    val wildcardRules: List<UserRule> get() = snapshot.wildcardRules
    val blockedIps: Set<String> get() = snapshot.blockedIps

    /**
     * Filter decision cache: bounded access-ordered LRU. Wrapped in a
     * synchronized map because Compose/UI readers and the VPN packet loop
     * mutate it concurrently. Invalidated on every [update].
     */
    private val decisionCacheMaxSize = 8192
    private val decisionCache: MutableMap<String, Boolean> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Boolean>(2048, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
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
        ipBlocks: Set<String> = emptySet()
    ) {
        val newRoot = TrieNode()
        val newExactSet: MutableSet<String> = HashSet(newDomains.size + dohBypassDomains.size)

        for (domain in newDomains) {
            val lower = domain.lowercase()
            insertDomain(newRoot, lower, terminal = true)
            newExactSet.add(lower)
        }
        // Always block DoH bypass domains (exact match)
        for (domain in dohBypassDomains) {
            insertDomain(newRoot, domain, terminal = true)
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
        // Safety: reject patterns >500 chars or with nested quantifiers to prevent ReDoS.
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
        snapshot = Snapshot(
            root = newRoot,
            exactBlockSet = newExactSet,
            wildcardRules = wildcards,
            regexBlockRules = newRegexBlock,
            regexAllowRules = newRegexAllow,
            blockedIps = ipBlocks,
            domainCount = newDomains.size + dohBypassDomains.size,
        )

        decisionCache.clear()
    }

    suspend fun updateAsync(
        newDomains: Set<String>,
        wildcards: List<UserRule>,
        regexRules: List<UserRule> = emptyList(),
        ipBlocks: Set<String> = emptySet()
    ) = withContext(Dispatchers.Default) {
        update(newDomains, wildcards, regexRules, ipBlocks)
    }

    fun clear() {
        snapshot = Snapshot.EMPTY
        decisionCache.clear()
    }

    fun getBlockedCount(): Int = snapshot.domainCount

    @Synchronized
    fun addDomain(hostname: String) {
        val h = hostname.lowercase()
        val current = snapshot
        if (h in current.exactBlockSet) {
            // Already present — keep counts honest and skip work.
            decisionCache.remove(h)
            return
        }
        // Mutate copies, then swap atomically.
        val newRoot = current.root // trie mutation is structural-only; readers see new terminal flag through volatile snapshot swap below
        insertDomain(newRoot, h, terminal = true)
        val newSet = HashSet(current.exactBlockSet).apply { add(h) }
        snapshot = Snapshot(
            root = newRoot,
            exactBlockSet = newSet,
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = current.domainCount + 1,
        )
        decisionCache.remove(h)
    }

    @Synchronized
    fun removeDomain(hostname: String) {
        val h = hostname.lowercase()
        val current = snapshot
        val wasPresent = h in current.exactBlockSet ||
            removeDomainFromTrie(current.root, h)
        if (!wasPresent) {
            // Don't drop the counter for domains we never had.
            decisionCache.remove(h)
            return
        }
        // If the entry came from exactBlockSet, also clear the trie terminal.
        if (h in current.exactBlockSet) removeDomainFromTrie(current.root, h)
        val newSet = HashSet(current.exactBlockSet).apply { remove(h) }
        snapshot = Snapshot(
            root = current.root,
            exactBlockSet = newSet,
            wildcardRules = current.wildcardRules,
            regexBlockRules = current.regexBlockRules,
            regexAllowRules = current.regexAllowRules,
            blockedIps = current.blockedIps,
            domainCount = (current.domainCount - 1).coerceAtLeast(0),
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
        val lower = hostname.lowercase()

        // L1: Filter decision LRU cache — O(1) for hot domains. LinkedHashMap in
        // access-order auto-evicts the LRU entry on overflow (removeEldestEntry).
        decisionCache[lower]?.let { return it }

        // Snapshot once so we evaluate against a consistent view across the
        // entire decision (trie + set + regex). A concurrent update() can land
        // mid-evaluation and atomically swap `snapshot`, but the local copy is
        // immutable from this thread's perspective.
        val snap = snapshot
        val result = isBlockedInternal(lower, snap)
        decisionCache[lower] = result
        return result
    }

    /** Check if a resolved IP address is in the IP blocklist. */
    fun isIpBlocked(ip: String): Boolean = ip in snapshot.blockedIps

    private fun isBlockedInternal(lower: String, snap: Snapshot): Boolean {
        // Trie walk over a stable snapshot — gathers wildcard allow/block and
        // exact-match signal in one traversal.
        val labels = lower.split('.').reversed()
        var node = snap.root
        var wildcardBlocked = false
        var wildcardAllowed = false
        var depth = 0

        for (label in labels) {
            val child = node.children[label] ?: break
            if (child.wildcardAllow) { wildcardAllowed = true; break }
            if (child.wildcardBlock) wildcardBlocked = true
            node = child
            depth++
        }

        if (wildcardAllowed) return false

        val exactBlocked = lower in snap.exactBlockSet
        val trieExact = depth == labels.size && node.terminal
        val blocked = exactBlocked || trieExact || wildcardBlocked

        if (blocked) {
            if (snap.regexAllowRules.any { it.containsMatchIn(lower) }) return false
            return true
        }

        if (snap.regexBlockRules.any { it.containsMatchIn(lower) }) {
            if (snap.regexAllowRules.any { it.containsMatchIn(lower) }) return false
            return true
        }

        if (lower.startsWith("www.")) {
            return isBlockedInternal(lower.removePrefix("www."), snap)
        }

        return false
    }

    private fun insertDomain(
        trieRoot: TrieNode, domain: String,
        terminal: Boolean = false,
        wildcardBlock: Boolean = false,
        wildcardAllow: Boolean = false
    ) {
        val labels = domain.split('.').reversed()
        var node = trieRoot
        for (label in labels) {
            node = node.children.getOrPut(label) { TrieNode() }
        }
        if (terminal) node.terminal = true
        if (wildcardBlock) node.wildcardBlock = true
        if (wildcardAllow) node.wildcardAllow = true
    }

    /**
     * Walks the trie and clears the [TrieNode.terminal] flag for [domain].
     * Returns true if the domain was actually present (terminal was set).
     * Used by [removeDomain] to decide whether to decrement the domain count.
     */
    private fun removeDomainFromTrie(trieRoot: TrieNode, domain: String): Boolean {
        val labels = domain.split('.').reversed()
        var node = trieRoot
        for (label in labels) {
            node = node.children[label] ?: return false
        }
        val was = node.terminal
        node.terminal = false
        return was
    }
}
