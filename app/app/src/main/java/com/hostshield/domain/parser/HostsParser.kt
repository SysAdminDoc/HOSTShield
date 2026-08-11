package com.hostshield.domain.parser

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.domain.DnsTypeRule
import com.hostshield.domain.ScopedDenyAllowRule

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
        val wildcardAllowDomains: Set<String> = emptySet(),
        val dnsTypeRules: List<DnsTypeRule> = emptyList(),
        val scopedDenyAllowRules: List<ScopedDenyAllowRule> = emptyList(),
        val parseDiagnostics: List<AdblockRuleParser.ParseDiagnostic> = emptyList()
    ) {
        val entryCount: Int get() = blockDomains.size + wildcardBlockDomains.size +
            dnsTypeRules.count { !it.allow }
        val parseWarning: String get() = parseDiagnostics.toParseWarning()
    }

    data class AllowlistParseResult(
        val allowDomains: Set<String>,
        val wildcardAllowDomains: Set<String> = emptySet(),
        val dnsTypeAllowRules: List<DnsTypeRule> = emptyList(),
        val parseDiagnostics: List<AdblockRuleParser.ParseDiagnostic> = emptyList()
    ) {
        val entryCount: Int get() = allowDomains.size + wildcardAllowDomains.size +
            dnsTypeAllowRules.size
        val parseWarning: String get() = parseDiagnostics.toParseWarning()
    }

    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val DOMAIN_REGEX = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")

    /** Safety cap on hostname tokens consumed from one multi-host hosts line. */
    private const val MAX_HOSTS_PER_LINE = 16
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
    /**
     * Strip a UTF-8 BOM. U+FEFF is not whitespace, so `trim()` keeps it and the
     * first line of a BOM-prefixed list fails every shape check — silently losing
     * one entry per such source and skewing the [isAdblockFormat] ratio.
     */
    internal fun stripBom(content: String): String = content.removePrefix("\uFEFF")

    fun isAdblockFormat(rawContent: String): Boolean {
        val content = stripBom(rawContent)
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
    fun parse(rawContent: String): Set<ParsedHost> {
        val content = stripBom(rawContent)
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
    fun parseForBlocking(rawContent: String): BlockingParseResult {
        val content = stripBom(rawContent)
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
        val dnsTypeRules = mutableListOf<DnsTypeRule>()
        val scopedDenyAllowRules = mutableListOf<ScopedDenyAllowRule>()

        // Domains an $important block rule protects: a non-important allow in the
        // same source must not override them (AdGuard priority: ||x^$important
        // outranks @@||x^). An @@...$important allow still wins.
        val importantBlockDomains = parsed.blockRules
            .filter { it.isImportant && it.dnsTypes == null && it.redirectIp == null && it.domain.isNotBlank() }
            .map { it.domain }
            .toSet()

        parsed.blockRules.forEach { rule ->
            if (rule.isRegex || rule.redirectIp != null || rule.domain.isBlank()) return@forEach

            if (rule.dnsTypes != null) {
                dnsTypeRules.add(rule.toDnsTypeRule(allow = false))
                rule.denyAllowDomains.orEmpty()
                    .filter { isValidDomain(it) }
                    .forEach { allowedDomain ->
                        scopedDenyAllowRules.add(
                            rule.toScopedDenyAllowRule(allowedDomain)
                        )
                    }
                return@forEach
            }

            if (rule.matchesSubdomains || rule.isWildcard) {
                wildcardBlockDomains.add(rule.domain)
            } else {
                blockDomains.add(rule.domain)
            }

            rule.denyAllowDomains.orEmpty()
                .filter { isValidDomain(it) }
                .forEach { allowedDomain ->
                    scopedDenyAllowRules.add(
                        rule.toScopedDenyAllowRule(allowedDomain)
                    )
                }
        }

        parsed.allowRules.forEach { rule ->
            if (rule.isRegex || rule.domain.isBlank()) return@forEach
            if (rule.dnsTypes != null) {
                dnsTypeRules.add(rule.toDnsTypeRule(allow = true))
                return@forEach
            }
            // A non-important allow cannot override an $important block in the
            // same source — including a subdomain of an $important-blocked domain
            // (e.g. `||x^$important` must keep blocking `sub.x` despite `@@||sub.x^`).
            if (!rule.isImportant && isCoveredByImportantBlock(rule.domain, importantBlockDomains)) return@forEach
            allowDomains.add(rule.domain)
            if (rule.matchesSubdomains || rule.isWildcard) {
                wildcardAllowDomains.add(rule.domain)
            }
        }

        return BlockingParseResult(
            blockDomains = blockDomains,
            allowDomains = allowDomains,
            wildcardBlockDomains = wildcardBlockDomains,
            wildcardAllowDomains = wildcardAllowDomains,
            dnsTypeRules = dnsTypeRules,
            scopedDenyAllowRules = scopedDenyAllowRules,
            parseDiagnostics = parsed.diagnostics
        )
    }

    /**
     * True when [domain] is exactly an `$important`-blocked domain or a subdomain
     * of one. `||x^$important` matches subdomains, so an `@@||sub.x^` exception in
     * the same source must not override it (AdGuard priority).
     */
    private fun isCoveredByImportantBlock(domain: String, importantBlocks: Set<String>): Boolean {
        if (domain in importantBlocks) return true
        var idx = domain.indexOf('.')
        while (idx >= 0) {
            if (domain.substring(idx + 1) in importantBlocks) return true
            idx = domain.indexOf('.', idx + 1)
        }
        return false
    }

    /**
     * Parse a subscribed allowlist source.
     *
     * Plain hosts/domains files are interpreted as allow domains. Adblock
     * allowlists use `@@||domain^` exception rules, which are preserved as
     * wildcard allows so they override matching source wildcard blocks.
     */
    fun parseForAllowing(rawContent: String): AllowlistParseResult {
        val content = stripBom(rawContent)
        if (!isAdblockFormat(content)) {
            return AllowlistParseResult(
                allowDomains = parseHostsFormat(content).mapTo(mutableSetOf()) { it.hostname }
            )
        }

        val parsed = parseForBlocking(content)
        return AllowlistParseResult(
            allowDomains = parsed.allowDomains,
            wildcardAllowDomains = parsed.wildcardAllowDomains,
            dnsTypeAllowRules = parsed.dnsTypeRules.filter { it.allow },
            parseDiagnostics = parsed.parseDiagnostics
        )
    }

    private fun AdblockRuleParser.DnsRule.toDnsTypeRule(allow: Boolean): DnsTypeRule =
        DnsTypeRule(
            domain = domain,
            dnsTypes = dnsTypes.orEmpty(),
            dnsTypesNegated = dnsTypesNegated,
            allow = allow,
            matchesSubdomains = matchesSubdomains || isWildcard
        ).normalized()

    private fun AdblockRuleParser.DnsRule.toScopedDenyAllowRule(
        allowedDomain: String
    ): ScopedDenyAllowRule =
        ScopedDenyAllowRule(
            ownerDomain = domain,
            allowedDomain = allowedDomain,
            ownerMatchesSubdomains = matchesSubdomains || isWildcard,
            dnsTypes = dnsTypes,
            dnsTypesNegated = dnsTypesNegated,
        ).normalized()

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

            val tokens = line.split(WHITESPACE_REGEX)
            if (tokens.size >= 2) {
                val ip = tokens[0]
                // Hosts lines may carry multiple hostnames after the sinkhole IP
                // ("0.0.0.0 a.com b.com c.com") — emit every valid token instead
                // of keeping only the first. Invalid tokens are skipped
                // individually; token count is capped per line for safety.
                val hostTokens = tokens.asSequence()
                    .drop(1)
                    .take(MAX_HOSTS_PER_LINE)
                    .map { it.lowercase() }
                    .filter { isValidDomain(it) && it !in LOCALHOST_ENTRIES }
                if (isBlockingIp(ip)) {
                    hostTokens.forEach { results.add(ParsedHost(it, ip)) }
                } else if (!isIpAddress(ip) && isValidDomain(ip)) {
                    results.add(ParsedHost(ip.lowercase()))
                    hostTokens.forEach { results.add(ParsedHost(it)) }
                }
            } else {
                // v5.0: Also try parsing as adblock-syntax single line. Only a
                // plain exact block maps to a hosts entry — mirror
                // parseAdblockAsHosts and skip exception/regex/wildcard/typed/
                // redirect rules so an embedded `||x^$dnstype=AAAA` or `||x^`
                // (subdomain) line is NOT globalized into an unconditional
                // all-qtype exact block for the apex only.
                val adblockRule = AdblockRuleParser.parseLine(line)
                if (adblockRule != null &&
                    !adblockRule.isException &&
                    !adblockRule.isRegex &&
                    !adblockRule.isWildcard &&
                    !adblockRule.matchesSubdomains &&
                    adblockRule.dnsTypes == null &&
                    adblockRule.redirectIp == null &&
                    adblockRule.domain.isNotEmpty()
                ) {
                    results.add(ParsedHost(adblockRule.domain))
                } else if (adblockRule == null) {
                    val domain = line.trim().lowercase()
                    if (isValidDomain(domain) && domain !in LOCALHOST_ENTRIES) {
                        results.add(ParsedHost(domain))
                    }
                }
            }
        }
        return results
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
        (s.contains('.') && s.all { it.isDigit() || it == '.' }) ||
        (s.contains(':') && s.all { it.isLetterOrDigit() || it == ':' })

    private fun isValidDomain(s: String): Boolean =
        s.length in 3..253 && s.contains('.') && DOMAIN_REGEX.matches(s)

    private fun List<AdblockRuleParser.ParseDiagnostic>.toParseWarning(): String {
        val scoped = count { it.reason == "unsupported_scoped_modifier" }
        val unsupported = count { it.reason == "unsupported_modifier" }
        val parts = mutableListOf<String>()
        if (scoped > 0) {
            parts.add("Skipped $scoped scoped AdGuard rule(s) with app/client/ctag modifiers; HostShield rejected them instead of applying them globally.")
        }
        if (unsupported > 0) {
            parts.add("Skipped $unsupported rule(s) with browser-only modifiers (e.g. \$removeparam/\$redirect/\$csp) instead of applying them as whole-domain DNS blocks.")
        }
        return parts.joinToString(" ")
    }
}
