package com.hostshield.domain.parser

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule

// Multi-format parser for hosts, domains-only, and adblock syntax

/**
 * A single parsed host entry. Equality is defined on [hostname] only — the IP
 * is informational (which sinkhole the source pointed at) and not relevant for
 * dedupe. Same hostname listed twice with different sinkhole IPs is one logical
 * block entry, not two.
 */
class ParsedHost(
    val hostname: String,
    val ip: String = "0.0.0.0",
) {
    override fun equals(other: Any?): Boolean =
        other is ParsedHost && other.hostname == hostname
    override fun hashCode(): Int = hostname.hashCode()
    override fun toString(): String = "ParsedHost($hostname -> $ip)"
}

object HostsParser {
    data class BlockingParseResult(
        val blockDomains: Set<String>,
        val allowDomains: Set<String> = emptySet(),
        val wildcardBlockDomains: Set<String> = emptySet(),
        val wildcardAllowDomains: Set<String> = emptySet()
    ) {
        val entryCount: Int get() = blockDomains.size + wildcardBlockDomains.size
    }

    data class AllowlistParseResult(
        val allowDomains: Set<String>,
        val wildcardAllowDomains: Set<String> = emptySet()
    ) {
        val entryCount: Int get() = allowDomains.size + wildcardAllowDomains.size
    }

    private val HOSTS_LINE_REGEX = Regex("""^\s*(\S+)\s+(\S+)""")
    private val DOMAIN_REGEX = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")
    private val LOCALHOST_ENTRIES = setOf(
        "localhost", "localhost.localdomain", "local",
        "broadcasthost", "ip6-localhost", "ip6-loopback",
        "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes",
        "ip6-allrouters", "ip6-allhosts"
    )

    /**
     * Detect if content is adblock-syntax format.
     * Heuristic: if >20% of non-empty, non-comment lines start with || or @@||,
     * treat the entire file as adblock syntax.
     */
    fun isAdblockFormat(content: String): Boolean {
        var total = 0
        var adblock = 0
        content.lineSequence().take(100).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('#') || line.startsWith('[')) return@forEach
            total++
            if (line.startsWith("||") || line.startsWith("@@||") || line.startsWith("@@/")) adblock++
        }
        return total > 0 && adblock.toFloat() / total > 0.2f
    }

    /**
     * Parse a blocklist file, auto-detecting format.
     *
     * v5.0: If the content is adblock-syntax (||domain^), delegates to
     * AdblockRuleParser and converts results to ParsedHost set. For pure
     * hosts/domains-only files, uses the original parser.
     *
     * For adblock-syntax, only block rules are returned as ParsedHost.
     * Allow rules, $important, $dnstype, and other modifiers are available
     * via parseAdblock() for callers that need them.
     */
    fun parse(content: String): Set<ParsedHost> {
        if (isAdblockFormat(content)) {
            return parseAdblockAsHosts(content)
        }
        return parseHostsFormat(content)
    }

    /**
     * v5.0: Parse adblock-syntax content and return full rule details.
     * Use this instead of parse() when you need allow rules, $important, etc.
     */
    fun parseAdblock(content: String): AdblockRuleParser.ParseResult {
        return AdblockRuleParser.parse(content)
    }

    /**
     * Parse a downloaded source for in-memory DNS blocking.
     *
     * Plain hosts/domains files become exact block domains. Adblock DNS rules
     * preserve subdomain semantics by emitting wildcard trie entries for
     * `||domain^` and `||*.domain^` rules, plus allow wildcards for `@@||`
     * and `$denyallow=` exceptions.
     */
    fun parseForBlocking(content: String): BlockingParseResult {
        if (!isAdblockFormat(content)) {
            return BlockingParseResult(
                blockDomains = parseHostsFormat(content).mapTo(mutableSetOf()) { it.hostname }
            )
        }

        val parsed = AdblockRuleParser.parse(content)
        val blockDomains = mutableSetOf<String>()
        val allowDomains = mutableSetOf<String>()
        val wildcardBlockDomains = mutableSetOf<String>()
        val wildcardAllowDomains = mutableSetOf<String>()

        parsed.blockRules.forEach { rule ->
            if (rule.isRegex || rule.dnsTypes != null || rule.redirectIp != null || rule.domain.isBlank()) return@forEach

            if (rule.matchesSubdomains || rule.isWildcard) {
                wildcardBlockDomains.add(rule.domain)
            } else {
                blockDomains.add(rule.domain)
            }

            rule.denyAllowDomains.orEmpty()
                .filter { isValidDomain(it) }
                .forEach {
                    allowDomains.add(it)
                    wildcardAllowDomains.add(it)
                }
        }

        parsed.allowRules.forEach { rule ->
            if (rule.isRegex || rule.dnsTypes != null || rule.domain.isBlank()) return@forEach
            allowDomains.add(rule.domain)
            if (rule.matchesSubdomains || rule.isWildcard) {
                wildcardAllowDomains.add(rule.domain)
            }
        }

        return BlockingParseResult(
            blockDomains = blockDomains,
            allowDomains = allowDomains,
            wildcardBlockDomains = wildcardBlockDomains,
            wildcardAllowDomains = wildcardAllowDomains
        )
    }

    /**
     * Parse a subscribed allowlist source.
     *
     * Plain hosts/domains files are interpreted as allow domains. Adblock
     * allowlists use `@@||domain^` exception rules, which are preserved as
     * wildcard allows so they override matching source wildcard blocks.
     */
    fun parseForAllowing(content: String): AllowlistParseResult {
        if (!isAdblockFormat(content)) {
            return AllowlistParseResult(
                allowDomains = parseHostsFormat(content).mapTo(mutableSetOf()) { it.hostname }
            )
        }

        val parsed = parseForBlocking(content)
        return AllowlistParseResult(
            allowDomains = parsed.allowDomains,
            wildcardAllowDomains = parsed.wildcardAllowDomains
        )
    }

    /**
     * Parse adblock-syntax and flatten to simple block domain set (for backward compat).
     * Uses the same filtering as ParseResult.exactBlockDomains — excludes regex,
     * explicit wildcards, and $dnstype-filtered rules for consistency.
     * Allow rules are NOT subtracted here — that's done by the caller (HostsUpdateWorker).
     */
    private fun parseAdblockAsHosts(content: String): Set<ParsedHost> {
        val result = AdblockRuleParser.parse(content)
        val hosts = mutableSetOf<ParsedHost>()
        for (rule in result.blockRules) {
            if (!rule.isRegex && !rule.isWildcard && rule.dnsTypes == null && rule.redirectIp == null && rule.domain.isNotEmpty()) {
                hosts.add(ParsedHost(rule.domain))
            }
        }
        return hosts
    }

    /** Original hosts-file and domains-only parser. */
    private fun parseHostsFormat(content: String): Set<ParsedHost> {
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
                // v5.0: Also try parsing as adblock-syntax single line
                val adblockRule = AdblockRuleParser.parseLine(line)
                if (adblockRule != null && !adblockRule.isException && !adblockRule.isRegex) {
                    results.add(ParsedHost(adblockRule.domain))
                } else {
                    val domain = line.trim().lowercase()
                    if (isValidDomain(domain) && domain !in LOCALHOST_ENTRIES) {
                        results.add(ParsedHost(domain))
                    }
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
