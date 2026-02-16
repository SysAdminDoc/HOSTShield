package com.hostshield.domain.parser

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule

// HostShield v0.3.0 - Hosts File Parser with Wildcard Support

data class ParsedHost(
    val hostname: String,
    val ip: String = "0.0.0.0"
)

object HostsParser {

    private val HOSTS_LINE_REGEX = Regex("""^\s*(\S+)\s+(\S+)""")
    private val DOMAIN_REGEX = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")
    private val LOCALHOST_ENTRIES = setOf(
        "localhost", "localhost.localdomain", "local",
        "broadcasthost", "ip6-localhost", "ip6-loopback",
        "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes",
        "ip6-allrouters", "ip6-allhosts"
    )

    fun parse(content: String): Set<ParsedHost> {
        val results = mutableSetOf<ParsedHost>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            val match = HOSTS_LINE_REGEX.find(line)
            if (match != null) {
                val ip = match.groupValues[1]
                val host = match.groupValues[2].lowercase()
                if (isBlockingIp(ip) && isValidDomain(host) && host !in LOCALHOST_ENTRIES) {
                    results.add(ParsedHost(host, ip))
                } else if (!isIpAddress(ip) && isValidDomain(ip)) {
                    results.add(ParsedHost(ip.lowercase()))
                    if (isValidDomain(host) && host !in LOCALHOST_ENTRIES) {
                        results.add(ParsedHost(host.lowercase()))
                    }
                }
            } else {
                val domain = line.trim().lowercase()
                if (isValidDomain(domain) && domain !in LOCALHOST_ENTRIES) {
                    results.add(ParsedHost(domain))
                }
            }
        }
        return results
    }

    fun buildHostsFile(
        parsedSources: List<Set<ParsedHost>>,
        userRules: List<UserRule>,
        redirectIp4: String = "0.0.0.0",
        redirectIp6: String = "::",
        includeIpv6: Boolean = true
    ): String {
        val allBlocked = mutableSetOf<String>()
        parsedSources.forEach { set -> set.forEach { allBlocked.add(it.hostname) } }

        // Apply exact block rules
        userRules.filter { it.type == RuleType.BLOCK && it.enabled && !it.isWildcard }
            .forEach { allBlocked.add(it.hostname.lowercase()) }

        // Apply exact allow rules
        val allowSet = userRules
            .filter { it.type == RuleType.ALLOW && it.enabled && !it.isWildcard }
            .map { it.hostname.lowercase() }
            .toSet()
        allBlocked.removeAll(allowSet)

        // Apply wildcard allow rules (remove matching domains)
        val wildcardAllows = userRules.filter { it.type == RuleType.ALLOW && it.enabled && it.isWildcard }
        if (wildcardAllows.isNotEmpty()) {
            allBlocked.removeAll { domain ->
                wildcardAllows.any { rule -> matchesWildcard(domain, rule.hostname) }
            }
        }

        // Build redirect map
        val redirectMap = userRules
            .filter { it.type == RuleType.REDIRECT && it.enabled }
            .associate { it.hostname.lowercase() to it.redirectIp }

        val sb = StringBuilder()
        sb.appendLine("# HostShield - Generated hosts file")
        sb.appendLine("# Entries: ${allBlocked.size + redirectMap.size}")
        sb.appendLine("# Generated: ${java.time.Instant.now()}")
        sb.appendLine()
        sb.appendLine("# Localhost")
        sb.appendLine("127.0.0.1 localhost")
        sb.appendLine("::1 localhost")
        sb.appendLine()

        if (redirectMap.isNotEmpty()) {
            sb.appendLine("# User redirects")
            redirectMap.toSortedMap().forEach { (host, ip) -> sb.appendLine("$ip $host") }
            sb.appendLine()
        }

        sb.appendLine("# Blocked domains")
        val sorted = allBlocked.filter { it !in redirectMap }.sorted()
        sorted.forEach { host ->
            sb.appendLine("$redirectIp4 $host")
            if (includeIpv6) sb.appendLine("$redirectIp6 $host")
        }

        return sb.toString()
    }

    fun countUniqueDomains(sources: List<Set<ParsedHost>>): Int {
        val all = mutableSetOf<String>()
        sources.forEach { set -> set.forEach { all.add(it.hostname) } }
        return all.size
    }

    /**
     * Check if a domain matches a wildcard pattern.
     * Patterns:
     *   *.example.com  -> matches sub.example.com, deep.sub.example.com
     *   example.com    -> exact match only
     *   *ads*          -> contains match
     */
    fun matchesWildcard(domain: String, pattern: String): Boolean {
        val p = pattern.lowercase()
        val d = domain.lowercase()

        return when {
            // *.example.com pattern
            p.startsWith("*.") -> {
                val suffix = p.removePrefix("*")
                d.endsWith(suffix) || d == p.removePrefix("*.")
            }
            // *keyword* contains pattern
            p.startsWith("*") && p.endsWith("*") -> {
                d.contains(p.trim('*'))
            }
            // *suffix pattern
            p.startsWith("*") -> {
                d.endsWith(p.removePrefix("*"))
            }
            // prefix* pattern
            p.endsWith("*") -> {
                d.startsWith(p.removeSuffix("*"))
            }
            // exact match
            else -> d == p
        }
    }

    /**
     * Check if a domain should be blocked considering wildcard rules.
     */
    fun isBlockedByWildcard(domain: String, wildcardRules: List<UserRule>): Boolean {
        return wildcardRules.any { rule ->
            rule.enabled && rule.type == RuleType.BLOCK && matchesWildcard(domain, rule.hostname)
        }
    }

    private fun isBlockingIp(ip: String): Boolean =
        ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "::" || ip == "::1"

    private fun isIpAddress(s: String): Boolean =
        s.contains('.') && s.all { it.isDigit() || it == '.' } ||
        s.contains(':') && s.all { it.isLetterOrDigit() || it == ':' }

    private fun isValidDomain(s: String): Boolean =
        s.length in 3..253 && s.contains('.') && DOMAIN_REGEX.matches(s)
}
