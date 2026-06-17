package com.hostshield.domain.parser

/**
 * v5.0: Adblock-syntax DNS rule parser.
 *
 * Parses rules in AdGuard/uBlock Origin DNS filtering syntax, the industry
 * standard format used by OISD, hagezi, AdGuard DNS Filter, and others.
 * OISD deprecated plain hosts format in Jan 2024 in favor of this syntax.
 *
 * Supported syntax:
 *   ||example.com^             — block domain + all subdomains
 *   @@||example.com^           — allow exception (unblock)
 *   ||example.com^$important   — force block even if excepted
 *   @@||example.com^$important — force allow even if force-blocked
 *   ||example.com^$dnstype=AAAA — block only for specific DNS type
 *   ||example.com^$dnstype=~A  — block for all types except A
 *   ||example.com^$badfilter   — disable any matching rule
 *   ||example.com^$denyallow=good.com — block all except good.com
 *   ||example.com^$dnsrewrite=NXDOMAIN — rewrite as NXDOMAIN (→ block)
 *   ||example.com^$dnsrewrite=1.2.3.4 — rewrite to IP (→ redirect rule)
 *   /regex/                    — regex block rule
 *   @@/regex/                  — regex allow rule
 *   0.0.0.0 example.com       — hosts-style rule (delegated to HostsParser)
 *   example.com                — domains-only rule
 *
 * Rule priority (highest to lowest, per AdGuard urlfilter spec):
 *   1. @@||...^$important  (force allow)
 *   2.   ||...^$important  (force block)
 *   3. @@||...^            (standard allow)
 *   4.   ||...^            (standard block)
 *   5. hosts-style rules   (lowest priority)
 *
 * This parser outputs [DnsRule] objects that BlocklistHolder consumes.
 * It does NOT handle cosmetic/CSS rules, URL-path rules, or browser-specific
 * modifiers — only DNS-compatible subset.
 */
object AdblockRuleParser {

    /**
     * Parsed DNS filtering rule.
     *
     * @param domain The domain pattern (lowercase, no leading ||, no trailing ^)
     * @param isException True if this is an @@-prefixed allow rule
     * @param isImportant True if $important modifier is present
     * @param isBadfilter True if $badfilter modifier — disables matching rules
     * @param isRegex True if this is a /regex/ pattern
     * @param dnsTypes Allowed/denied DNS types (null = all types). Negated types prefixed with ~
     * @param denyAllowDomains Domains excepted from this blocking rule ($denyallow)
     */
    data class DnsRule(
        val domain: String,
        val isException: Boolean = false,
        val isImportant: Boolean = false,
        val isBadfilter: Boolean = false,
        val isRegex: Boolean = false,
        val isWildcard: Boolean = false,         // true only for explicit *.domain patterns
        val matchesSubdomains: Boolean = true,   // true for ||domain^ (blocks domain + subdomains)
        val dnsTypes: Set<Int>? = null,          // null = all types
        val dnsTypesNegated: Boolean = false,    // true = block all EXCEPT these types
        val denyAllowDomains: Set<String>? = null, // $denyallow exception domains
        val redirectIp: String? = null           // non-null when parsed from $dnsrewrite=<IP>
    ) {
        /**
         * Priority level for rule ordering (higher = takes precedence).
         * Matches AdGuard urlfilter 6-level priority system.
         */
        val priority: Int get() = when {
            isException && isImportant -> 4  // @@||...^$important
            !isException && isImportant -> 3 // ||...^$important
            isException -> 2                 // @@||...^
            else -> 1                        // ||...^ or hosts-style
        }
    }

    /**
     * Result of parsing a blocklist file in adblock syntax.
     */
    data class ParseResult(
        val blockRules: List<DnsRule>,
        val allowRules: List<DnsRule>,
        val badfilterRules: List<DnsRule>,
        val totalLines: Int,
        val parsedRules: Int,
        val skippedLines: Int,
        val dnsRewriteSkipped: Int = 0,
        val scopedModifierSkipped: Int = 0
    ) {
        /**
         * All block domains for the exact hash set + trie insertion.
         * Includes ||domain^ rules (matchesSubdomains=true, isWildcard=false).
         * Excludes explicit *.domain wildcards, regex, $dnstype-filtered, and redirect rules.
         */
        val exactBlockDomains: Set<String> get() =
            blockRules.filter { !it.isWildcard && !it.isRegex && it.dnsTypes == null && it.redirectIp == null }
                .map { it.domain }.toSet()

        /** All allow domains for subtraction from blocklist. */
        val exactAllowDomains: Set<String> get() =
            allowRules.filter { !it.isWildcard && !it.isRegex }
                .map { it.domain }.toSet()

        /** Explicit wildcard block rules (*.domain patterns only). */
        val wildcardBlockRules: List<DnsRule> get() =
            blockRules.filter { it.isWildcard }

        /** Explicit wildcard allow rules. */
        val wildcardAllowRules: List<DnsRule> get() =
            allowRules.filter { it.isWildcard }

        /** Redirect rules parsed from $dnsrewrite=<IP> modifiers. */
        val redirectRules: List<DnsRule> get() =
            blockRules.filter { it.redirectIp != null }
    }

    // DNS type name → value mapping
    private val DNS_TYPES = mapOf(
        "A" to 1, "NS" to 2, "CNAME" to 5, "SOA" to 6, "PTR" to 12,
        "MX" to 15, "TXT" to 16, "AAAA" to 28, "SRV" to 33,
        "SVCB" to 64, "HTTPS" to 65, "ANY" to 255
    )

    /**
     * Parse a blocklist file containing adblock-syntax rules.
     *
     * Handles mixed-format files: adblock-syntax, hosts-style, and domains-only
     * lines are all parsed. Comments (! and #) and metadata (Adblock headers) are skipped.
     *
     * @param content Raw file content
     * @return ParseResult with categorized rules
     */
    fun parse(content: String): ParseResult {
        val blockRules = mutableListOf<DnsRule>()
        val allowRules = mutableListOf<DnsRule>()
        val badfilterRules = mutableListOf<DnsRule>()
        var totalLines = 0
        var parsedRules = 0
        var skippedLines = 0
        var dnsRewriteSkipped = 0
        var scopedModifierSkipped = 0

        content.lineSequence().forEach { rawLine ->
            totalLines++
            val line = rawLine.trim()

            // Skip empty lines, comments, metadata headers
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('#') ||
                line.startsWith('[')) {
                skippedLines++
                return@forEach
            }

            val rule = parseLine(line)
            if (rule != null) {
                parsedRules++
                when {
                    rule.isBadfilter -> badfilterRules.add(rule)
                    rule.isException -> allowRules.add(rule)
                    else -> blockRules.add(rule)
                }
            } else {
                if (SCOPED_MODIFIER_PATTERN.containsMatchIn(line)) {
                    scopedModifierSkipped++
                    skippedLines++
                    return@forEach
                }
                if (line.contains("dnsrewrite=", ignoreCase = true)) {
                    dnsRewriteSkipped++
                    skippedLines++
                    return@forEach
                }
                // Try as hosts-style or domains-only
                val hostRule = parseAsHostsOrDomain(line)
                if (hostRule != null) {
                    parsedRules++
                    blockRules.add(hostRule)
                } else {
                    skippedLines++
                }
            }
        }

        // Apply $badfilter — remove rules that match badfilter patterns
        val effectiveBlock = applyBadfilters(blockRules, badfilterRules)
        val effectiveAllow = applyBadfilters(allowRules, badfilterRules)

        return ParseResult(
            blockRules = effectiveBlock,
            allowRules = effectiveAllow,
            badfilterRules = badfilterRules,
            totalLines = totalLines,
            parsedRules = parsedRules,
            skippedLines = skippedLines,
            dnsRewriteSkipped = dnsRewriteSkipped,
            scopedModifierSkipped = scopedModifierSkipped
        )
    }

    /**
     * Parse a single adblock-syntax line.
     * Returns null if the line is not valid adblock syntax.
     */
    fun parseLine(line: String): DnsRule? {
        var text = line.trim()
        if (text.isEmpty()) return null

        // Check for regex rule: /pattern/ or @@/pattern/
        val isException = text.startsWith("@@")
        if (isException) text = text.removePrefix("@@")

        if (text.startsWith('/') && (text.contains("/$") || text.endsWith('/'))) {
            return parseRegexRule(text, isException)
        }

        // Must start with || for domain rules
        if (!text.startsWith("||")) return null
        text = text.removePrefix("||")

        // Split domain from modifiers at ^$ or ^ boundary
        val modifiers: String
        val domain: String

        val caretIdx = text.indexOf('^')
        if (caretIdx >= 0) {
            domain = text.substring(0, caretIdx).lowercase()
            val afterCaret = text.substring(caretIdx + 1)
            modifiers = if (afterCaret.startsWith('$')) afterCaret.removePrefix("$")
            else if (afterCaret.isEmpty()) ""
            else return null // unexpected content after ^
        } else if (text.contains('$')) {
            // ||domain$modifier (no caret — some lists omit it)
            val dollarIdx = text.indexOf('$')
            domain = text.substring(0, dollarIdx).lowercase()
            modifiers = text.substring(dollarIdx + 1)
        } else {
            // ||domain (no caret, no modifiers)
            domain = text.lowercase()
            modifiers = ""
        }

        // Validate domain
        if (domain.isEmpty() || domain.length > 253) return null
        // Allow wildcard prefix like *.example.com
        val isWildcard = domain.startsWith("*.")
        val cleanDomain = if (isWildcard) domain.removePrefix("*.") else domain
        if (cleanDomain.isEmpty()) return null

        // Parse modifiers
        var isImportant = false
        var isBadfilter = false
        var dnsTypes: MutableSet<Int>? = null
        var dnsTypesNegated = false
        var denyAllowDomains: MutableSet<String>? = null
        var redirectIpValue: String? = null

        if (modifiers.isNotEmpty()) {
            for (mod in modifiers.split(',')) {
                val m = mod.trim()
                when {
                    m == "important" -> isImportant = true
                    m == "badfilter" -> isBadfilter = true
                    m.startsWith("dnstype=") -> {
                        val typeStr = m.removePrefix("dnstype=")
                        val parsedDnsTypes = mutableSetOf<Int>()
                        var hasNegated = false
                        var hasPositive = false
                        for (t in typeStr.split('|')) {
                            val negated = t.startsWith('~')
                            val typeName = (if (negated) t.removePrefix("~") else t).uppercase()
                            val typeVal = DNS_TYPES[typeName]
                            if (typeVal != null) {
                                parsedDnsTypes.add(typeVal)
                                if (negated) hasNegated = true else hasPositive = true
                            }
                        }
                        // AdGuard doesn't support mixing negated and non-negated — pick one mode
                        dnsTypesNegated = hasNegated && !hasPositive
                        dnsTypes = parsedDnsTypes.ifEmpty { null }
                    }
                    m.startsWith("denyallow=") -> {
                        val domains = m.removePrefix("denyallow=")
                        denyAllowDomains = domains.split('|')
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }
                            .toMutableSet()
                        if (denyAllowDomains.isNullOrEmpty()) denyAllowDomains = null
                    }
                    m.startsWith("dnsrewrite=") -> {
                        val rewrite = parseDnsRewriteValue(m.removePrefix("dnsrewrite=").trim())
                            ?: return null // unsupported form (CNAME, etc.)
                        if (rewrite.isNotEmpty()) redirectIpValue = rewrite
                    }
                    m == "all" || m == "popup" || m == "third-party" || m == "first-party" ||
                        m.startsWith("domain=") -> {
                        return null
                    }
                    m.startsWith("app=") || m.startsWith("client=") || m.startsWith("ctag=") -> {
                        return null
                    }
                    // Unknown modifiers — ignore silently (forward-compatible)
                }
            }
        }

        return DnsRule(
            domain = cleanDomain,
            isException = isException,
            isImportant = isImportant,
            isBadfilter = isBadfilter,
            isWildcard = isWildcard,                   // true only for explicit *.domain
            matchesSubdomains = true,                  // ||domain^ always matches subdomains
            dnsTypes = dnsTypes,
            dnsTypesNegated = dnsTypesNegated,
            denyAllowDomains = denyAllowDomains,
            redirectIp = redirectIpValue
        )
    }

    /** Parse a /regex/ rule. */
    private fun parseRegexRule(text: String, isException: Boolean): DnsRule? {
        // Extract pattern between first / and last /
        val firstSlash = text.indexOf('/')
        val lastSlash = text.lastIndexOf('/')
        if (firstSlash < 0 || lastSlash <= firstSlash) return null

        val pattern = text.substring(firstSlash + 1, lastSlash)
        if (pattern.isEmpty() || pattern.length > 500) return null

        // Check for modifiers after the closing /
        val afterPattern = text.substring(lastSlash + 1)
        var isImportant = false
        var isBadfilter = false
        if (afterPattern.startsWith('$')) {
            for (mod in afterPattern.removePrefix("$").split(',')) {
                when (mod.trim()) {
                    "important" -> isImportant = true
                    "badfilter" -> isBadfilter = true
                }
            }
        }

        return DnsRule(
            domain = pattern,
            isException = isException,
            isImportant = isImportant,
            isBadfilter = isBadfilter,
            isRegex = true,
            matchesSubdomains = false // regex handles its own matching
        )
    }

    /** Try to parse a line as hosts-style (0.0.0.0 domain) or domains-only. */
    private fun parseAsHostsOrDomain(line: String): DnsRule? {
        val text = line.substringBefore('#').trim()
        if (text.isEmpty()) return null

        // Hosts-style: "0.0.0.0 example.com" or "127.0.0.1 example.com"
        val parts = text.split(Regex("\\s+"), limit = 2)
        if (parts.size == 2) {
            val ip = parts[0]
            val host = parts[1].lowercase()
            if ((ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "::" || ip == "::1") &&
                isValidDomain(host)) {
                return DnsRule(domain = host, matchesSubdomains = false) // hosts = exact match only
            }
        }

        // Domains-only: just "example.com"
        val domain = text.lowercase()
        if (isValidDomain(domain)) {
            return DnsRule(domain = domain, matchesSubdomains = false) // domains-only = exact match
        }

        return null
    }

    /**
     * Apply $badfilter rules — remove rules that match on domain + exception status.
     * Per AdGuard spec, $badfilter disables a rule with the same domain and same
     * type (block/allow). A badfilter for @@||domain^ only removes allow rules,
     * and a badfilter for ||domain^ only removes block rules.
     */
    private fun applyBadfilters(rules: List<DnsRule>, badfilters: List<DnsRule>): List<DnsRule> {
        if (badfilters.isEmpty()) return rules
        // Build set of (domain, isException) pairs for efficient lookup
        val badKeys = badfilters.map { Pair(it.domain, it.isException) }.toSet()
        return rules.filter { Pair(it.domain, it.isException) !in badKeys }
    }

    private val SCOPED_MODIFIER_PATTERN = Regex("""\$(app|client|ctag)=""", RegexOption.IGNORE_CASE)
    private val DOMAIN_PATTERN = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")
    private val LOCALHOST = setOf("localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback")
    private val IPV4_PATTERN = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
    private val IPV6_PATTERN = Regex("""^[0-9a-fA-F:]+$""")
    private val NULL_IPS = setOf("0.0.0.0", "::", "::0", "0:0:0:0:0:0:0:0")

    private fun isValidDomain(s: String): Boolean =
        s.length in 3..253 && s.contains('.') && s !in LOCALHOST && DOMAIN_PATTERN.matches(s)

    /**
     * Parse a $dnsrewrite= value.
     * Returns "" for block-equivalent forms (NXDOMAIN/REFUSED/null-IP),
     * an IP string for A/AAAA redirects, or null for unsupported forms (CNAME, etc.).
     */
    private fun parseDnsRewriteValue(value: String): String? {
        if (value.isEmpty()) return null

        val upper = value.uppercase()
        if (upper == "NXDOMAIN" || upper == "REFUSED" || upper == "SERVFAIL") return ""
        if (value in NULL_IPS) return ""

        // 3-part form: RCODE;TYPE;VALUE (e.g. NOERROR;A;1.2.3.4)
        if (value.contains(';')) {
            val parts = value.split(';', limit = 3)
            if (parts.size != 3) return null
            val rcode = parts[0].uppercase()
            val type = parts[1].uppercase()
            val v = parts[2].trim()

            if (rcode == "NXDOMAIN" || rcode == "REFUSED" || rcode == "SERVFAIL") return ""
            if ((type == "A" || type == "AAAA") && v in NULL_IPS) return ""
            if (type == "A" && IPV4_PATTERN.matches(v)) return v
            if (type == "AAAA" && v.contains(':') && IPV6_PATTERN.matches(v)) return v
            return null
        }

        if (IPV4_PATTERN.matches(value)) return value
        if (value.contains(':') && IPV6_PATTERN.matches(value)) return value

        // Bare domain = CNAME rewrite — not supported
        return null
    }
}
