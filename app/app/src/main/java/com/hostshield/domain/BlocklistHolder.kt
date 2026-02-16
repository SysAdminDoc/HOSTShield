package com.hostshield.domain

import com.hostshield.data.model.UserRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe in-memory holder for the active blocklist.
 * Shared between HomeViewModel (writes) and DnsVpnService (reads).
 */
@Singleton
class BlocklistHolder @Inject constructor() {
    @Volatile var domains: Set<String> = emptySet()
        private set
    @Volatile var wildcardRules: List<UserRule> = emptyList()
        private set

    fun update(domains: Set<String>, wildcards: List<UserRule>) {
        this.domains = domains.toMutableSet()
        this.wildcardRules = wildcards
    }

    fun clear() {
        domains = emptySet()
        wildcardRules = emptyList()
    }

    /** Hot-add a single domain to the active blocklist. */
    fun addDomain(hostname: String) {
        val current = domains.toMutableSet()
        current.add(hostname.lowercase())
        domains = current
    }

    /** Remove a single domain from the active blocklist. */
    fun removeDomain(hostname: String) {
        val current = domains.toMutableSet()
        current.remove(hostname.lowercase())
        domains = current
    }

    /**
     * Check if a hostname should be blocked.
     * Checks exact domain match, then wildcard rules.
     */
    fun isBlocked(hostname: String): Boolean {
        val lower = hostname.lowercase()
        if (lower in domains) return true

        // Check wildcard rules
        for (rule in wildcardRules) {
            val pattern = rule.hostname.lowercase()
            if (pattern.startsWith("*.")) {
                val suffix = pattern.substring(1) // ".example.com"
                if (lower.endsWith(suffix) || lower == pattern.substring(2)) {
                    return when (rule.type) {
                        com.hostshield.data.model.RuleType.BLOCK -> true
                        com.hostshield.data.model.RuleType.ALLOW -> false
                        else -> false
                    }
                }
            }
        }
        return false
    }
}
