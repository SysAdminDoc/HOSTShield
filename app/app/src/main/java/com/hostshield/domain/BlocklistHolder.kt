package com.hostshield.domain

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.6.0 -- Trie-optimized blocklist holder
//
// Uses a reversed-label domain trie for O(m) lookups where m = label count.
// "ads.example.com" is stored as com -> example -> ads (TERMINAL).
// Wildcard "*.example.com" is stored as com -> example (WILDCARD).
// This eliminates linear scans over 100K+ domain sets.

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
    private val dohBypassDomains = setOf(
        "use-application-dns.net",       // Firefox DoH canary
        "dns.google",                    // Google DoH
        "dns.google.com",
        "cloudflare-dns.com",            // Cloudflare DoH
        "mozilla.cloudflare-dns.com",    // Firefox default DoH
        "one.one.one.one",
        "dns.quad9.net",                 // Quad9 DoH
        "dns9.quad9.net",
        "dns.adguard-dns.com",           // AdGuard DoH
        "dns-unfiltered.adguard.com",
        "doh.opendns.com",               // OpenDNS DoH
        "doh.cleanbrowsing.org",         // CleanBrowsing DoH
        "dns.nextdns.io",               // NextDNS DoH
        "dns.alidns.com",               // Alibaba DoH
        "doh.pub",                       // DNSPod/Tencent DoH
    )

    fun update(newDomains: Set<String>, wildcards: List<UserRule>) {
        val newRoot = TrieNode()
        for (domain in newDomains) {
            insertDomain(newRoot, domain.lowercase(), terminal = true)
        }
        // Always block DoH bypass domains
        for (domain in dohBypassDomains) {
            insertDomain(newRoot, domain, terminal = true)
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
