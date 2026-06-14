package com.hostshield.ui.screens.home

import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule

internal fun dnsLogDisplayBlocked(
    entry: DnsLogEntry,
    enabledBlockRules: List<UserRule>
): Boolean {
    if (entry.blocked) return true

    val host = entry.hostname.normalizedDnsHostname()
    if (host.isEmpty()) return false

    return enabledBlockRules.any { rule ->
        rule.enabled &&
            rule.type == RuleType.BLOCK &&
            !rule.isRegex &&
            rule.matchesDnsHost(host)
    }
}

private fun UserRule.matchesDnsHost(host: String): Boolean {
    val ruleHost = hostname.normalizedDnsHostname()
    if (ruleHost.isEmpty()) return false
    if (!isWildcard) return host == ruleHost

    val baseDomain = ruleHost
        .removePrefix("*.")
        .removePrefix(".")
        .normalizedDnsHostname()

    return baseDomain.isNotEmpty() && (host == baseDomain || host.endsWith(".$baseDomain"))
}

private fun String.normalizedDnsHostname(): String =
    trim().trimEnd('.').lowercase()
