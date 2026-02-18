package com.hostshield.domain

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.11.0 -- Trie-optimized blocklist holder
//
// Uses a reversed-label domain trie for O(m) lookups where m = label count.
// "ads.example.com" is stored as com -> example -> ads (TERMINAL).
// Wildcard "*.example.com" is stored as com -> example (WILDCARD).
// This eliminates linear scans over 100K+ domain sets.
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

    @Volatile private var root = TrieNode()
    @Volatile var domainCount: Int = 0; private set
    @Volatile var wildcardRules: List<UserRule> = emptyList(); private set

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

    fun update(newDomains: Set<String>, wildcards: List<UserRule>) {
        val newRoot = TrieNode()
        for (domain in newDomains) {
            insertDomain(newRoot, domain.lowercase(), terminal = true)
        }
        // Always block DoH bypass domains (exact match)
        for (domain in dohBypassDomains) {
            insertDomain(newRoot, domain, terminal = true)
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
        // Atomic swap — volatile write ensures visibility to reader threads
        domainCount = newDomains.size + dohBypassDomains.size
        wildcardRules = wildcards
        root = newRoot
    }

    fun clear() {
        root = TrieNode()
        domainCount = 0
        wildcardRules = emptyList()
    }

    fun getBlockedCount(): Int = domainCount

    fun addDomain(hostname: String) {
        val h = hostname.lowercase()
        insertDomain(root, h, terminal = true)
        domainCount++
    }

    fun removeDomain(hostname: String) {
        val h = hostname.lowercase()
        removeDomainFromTrie(root, h)
        domainCount--
    }

    /**
     * O(m) trie lookup. Walks reversed labels from TLD to leaf.
     * Wildcard allow > wildcard block > exact match.
     */
    fun isBlocked(hostname: String): Boolean {
        return isBlockedInternal(hostname.lowercase())
    }

    private fun isBlockedInternal(lower: String): Boolean {
        val labels = lower.split('.').reversed()
        var node = root
        var wildcardBlocked = false
        var depth = 0

        for (label in labels) {
            val child = node.children[label] ?: break
            if (child.wildcardAllow) return false
            if (child.wildcardBlock) wildcardBlocked = true
            node = child
            depth++
        }

        // Exact match: all labels consumed and node is terminal
        if (depth == labels.size && node.terminal) return true

        // Wildcard block matched at some ancestor
        if (wildcardBlocked) return true

        // www. prefix fallback
        if (lower.startsWith("www.")) {
            return isBlockedInternal(lower.removePrefix("www."))
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

    private fun removeDomainFromTrie(trieRoot: TrieNode, domain: String) {
        val labels = domain.split('.').reversed()
        var node = trieRoot
        for (label in labels) {
            node = node.children[label] ?: return
        }
        node.terminal = false
    }
}
