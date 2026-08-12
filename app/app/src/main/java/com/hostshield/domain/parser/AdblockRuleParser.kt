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
 *   ||example.com^$app=com.example.app — block only for that Android package
 *   ||example.com^$app=~com.example.app — block for every other package
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

    /** A validated Android package scope from an `$app=` modifier. */
    data class AppScope(
        val packageName: String,
        val negated: Boolean = false,
    )

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
     * @param appScope Optional Android package scope from `$app=`.
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
        val redirectIp: String? = null,          // non-null when parsed from $dnsrewrite=<IP>
        val appScope: AppScope? = null           // non-null when parsed from $app=<package>
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
        val scopedModifierSkipped: Int = 0,
        val unsupportedModifierSkipped: Int = 0,
        val diagnostics: List<ParseDiagnostic> = emptyList()
    ) {
        /**
         * All block domains for the exact hash set + trie insertion.
         * Includes ||domain^ rules (matchesSubdomains=true, isWildcard=false).
         * Excludes explicit *.domain wildcards, regex, $dnstype-filtered, and redirect rules.
         */
        val exactBlockDomains: Set<String> get() =
            blockRules.filter { it.appScope == null && !it.isWildcard && !it.isRegex && it.dnsTypes == null && it.redirectIp == null }
                .map { it.domain }.toSet()

        /** All allow domains for subtraction from blocklist. */
        val exactAllowDomains: Set<String> get() =
            allowRules.filter { it.appScope == null && !it.isWildcard && !it.isRegex }
                .map { it.domain }.toSet()

        /** Explicit wildcard block rules (*.domain patterns only). */
        val wildcardBlockRules: List<DnsRule> get() =
            blockRules.filter { it.appScope == null && it.isWildcard }

        /** Explicit wildcard allow rules. */
        val wildcardAllowRules: List<DnsRule> get() =
            allowRules.filter { it.appScope == null && it.isWildcard }

        /** Redirect rules parsed from $dnsrewrite=<IP> modifiers. */
        val redirectRules: List<DnsRule> get() =
            blockRules.filter { it.redirectIp != null }
    }

    data class ParseDiagnostic(
        val lineNumber: Int,
        val reason: String,
        val modifier: String,
        val message: String
    )

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
    fun parse(rawContent: String): ParseResult {
        val content = HostsParser.stripBom(rawContent)
        val blockRules = mutableListOf<DnsRule>()
        val allowRules = mutableListOf<DnsRule>()
        val badfilterRules = mutableListOf<DnsRule>()
        var totalLines = 0
        var parsedRules = 0
        var skippedLines = 0
        var dnsRewriteSkipped = 0
        var scopedModifierSkipped = 0
        var unsupportedModifierSkipped = 0
        val diagnostics = mutableListOf<ParseDiagnostic>()

        content.lineSequence().forEachIndexed { lineIndex, rawLine ->
            totalLines++
            val line = rawLine.trim()
            val lineNumber = lineIndex + 1

            // Skip empty lines, comments, metadata headers
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('#') ||
                line.startsWith('[')) {
                skippedLines++
                return@forEachIndexed
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
                val scopedModifier = findScopedModifier(line)
                if (scopedModifier != null) {
                    scopedModifierSkipped++
                    diagnostics.add(
                        ParseDiagnostic(
                            lineNumber = lineNumber,
                            reason = "unsupported_scoped_modifier",
                            modifier = scopedModifier,
                            message = "Skipped scoped AdGuard DNS rule instead of applying it globally; HostShield does not yet enforce per-app or per-client source modifiers."
                        )
                    )
                    skippedLines++
                    return@forEachIndexed
                }
                val unsupportedModifier = findUnsupportedModifier(line)
                if (unsupportedModifier != null) {
                    unsupportedModifierSkipped++
                    diagnostics.add(
                        ParseDiagnostic(
                            lineNumber = lineNumber,
                            reason = "unsupported_modifier",
                            modifier = unsupportedModifier,
                            message = "Skipped rule with browser-only modifier '\$$unsupportedModifier' instead of applying it as an unconditional DNS block."
                        )
                    )
                    skippedLines++
                    return@forEachIndexed
                }
                if (line.contains("dnsrewrite=", ignoreCase = true)) {
                    dnsRewriteSkipped++
                    skippedLines++
                    return@forEachIndexed
                }
                // Try as hosts-style or domains-only
                val hostRules = parseAsHostsOrDomain(line)
                if (hostRules.isNotEmpty()) {
                    // One parsed LINE may emit several rules (multi-host hosts
                    // lines); parsedRules stays line-based so the invariant
                    // totalLines == parsedRules + skippedLines holds.
                    parsedRules++
                    blockRules.addAll(hostRules)
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
            scopedModifierSkipped = scopedModifierSkipped,
            unsupportedModifierSkipped = unsupportedModifierSkipped,
            diagnostics = diagnostics
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
        // Reject non-hostname shapes (paths, ports, inline wildcards, whitespace).
        // These would otherwise become inert junk rules that inflate entry counts
        // and the trie/bloom while matching nothing (a hostname query never
        // contains '/', ':', '*', or spaces). A leading "*." was already stripped
        // above, so any remaining '*' is an unsupported inline wildcard. A dot is
        // still required so bare tokens aren't treated as domains.
        if (cleanDomain.any { it == '/' || it == ':' || it == '*' || it == '?' || it.isWhitespace() }) return null
        // DNS queries arrive punycode-encoded, so an IDN rule stored verbatim
        // (||exämple.com^, or a Cyrillic homograph from a regional list) can never
        // match — it just inflates entry_count and the trie/bloom. Convert like
        // AdGuard does, and reject input IDN cannot encode.
        val asciiDomain = toPunycodeOrNull(cleanDomain) ?: return null

        // Parse modifiers
        var isImportant = false
        var isBadfilter = false
        var dnsTypes: MutableSet<Int>? = null
        var dnsTypesNegated = false
        var denyAllowDomains: MutableSet<String>? = null
        var redirectIpValue: String? = null
        var appScope: AppScope? = null

        if (modifiers.isNotEmpty()) {
            for (mod in modifiers.split(',')) {
                val m = mod.trim()
                when {
                    m == "important" -> isImportant = true
                    m == "badfilter" -> isBadfilter = true
                    m.startsWith("dnstype=", ignoreCase = true) -> {
                        val typeStr = m.substringAfter('=')
                        val positiveTypes = mutableSetOf<Int>()
                        val negatedTypes = mutableSetOf<Int>()
                        for (rawType in typeStr.split('|')) {
                            val t = rawType.trim()
                            if (t.isEmpty()) return null
                            val negated = t.startsWith('~')
                            val typeName = (if (negated) t.removePrefix("~") else t).uppercase()
                            val typeVal = DNS_TYPES[typeName] ?: return null
                            if (negated) negatedTypes.add(typeVal) else positiveTypes.add(typeVal)
                        }
                        dnsTypesNegated = positiveTypes.isEmpty() && negatedTypes.isNotEmpty()
                        val selectedTypes = if (positiveTypes.isNotEmpty()) positiveTypes else negatedTypes
                        dnsTypes = selectedTypes.takeIf { it.isNotEmpty() }
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
                    m.startsWith("app=", ignoreCase = true) -> {
                        val rawPackage = m.substringAfter('=').trim()
                        val negated = rawPackage.startsWith('~')
                        val packageName = if (negated) rawPackage.removePrefix("~").trim() else rawPackage
                        if (rawPackage.contains('|') || !ANDROID_PACKAGE_PATTERN.matches(packageName)) {
                            return null
                        }
                        appScope = AppScope(packageName = packageName, negated = negated)
                    }
                    m.startsWith("client=", ignoreCase = true) || m.startsWith("ctag=", ignoreCase = true) -> {
                        return null
                    }
                    // Any other modifier is NOT DNS-supported (e.g. $removeparam,
                    // $redirect, $csp, $media). Ignoring it would turn a scoped
                    // browser rule into an unconditional whole-domain DNS block —
                    // the same over-globalization class fixed for $app/$client/$ctag.
                    // Skip the whole rule; parse() emits an "unsupported_modifier"
                    // diagnostic for it.
                    else -> return null
                }
            }
        }

        // The app engine currently enforces domain and DNS-type decisions, but
        // cannot safely reproduce denyallow ownership or IP rewrites. Reject
        // those combinations instead of partially applying them.
        if (appScope != null && (denyAllowDomains != null || redirectIpValue != null)) return null

        return DnsRule(
            domain = asciiDomain,
            isException = isException,
            isImportant = isImportant,
            isBadfilter = isBadfilter,
            isWildcard = isWildcard,                   // true only for explicit *.domain
            matchesSubdomains = true,                  // ||domain^ always matches subdomains
            dnsTypes = dnsTypes,
            dnsTypesNegated = dnsTypesNegated,
            denyAllowDomains = denyAllowDomains,
            redirectIp = redirectIpValue,
            appScope = appScope
        )
    }

    /**
     * Convert a hostname to its ASCII (punycode) form, or null if it cannot be
     * encoded. Already-ASCII names pass through unchanged.
     */
    private fun toPunycodeOrNull(domain: String): String? {
        if (domain.all { it.code < 128 }) return domain
        return runCatching { java.net.IDN.toASCII(domain).lowercase() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && it.length <= 253 }
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
                when (val name = mod.trim()) {
                    "important" -> isImportant = true
                    "badfilter" -> isBadfilter = true
                    // Anything else scopes the rule ($client=, $app=, $denyallow=...).
                    // Ignoring it would turn a scoped rule into an unscoped global
                    // regex — the over-globalization class removed from the domain
                    // path in v6.9.59/63. Reject instead.
                    else -> if (name.isNotEmpty()) return null
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

    /**
     * Try to parse a line as hosts-style (0.0.0.0 domain [domain ...]) or
     * domains-only. Hosts lines may carry multiple hostnames after the sinkhole
     * IP; every valid hostname token becomes its own rule (capped at
     * [MAX_HOSTS_PER_LINE] tokens per line for safety). Invalid tokens are
     * skipped individually. Returns an empty list when nothing parses.
     */
    private fun parseAsHostsOrDomain(line: String): List<DnsRule> {
        val text = line.substringBefore('#').trim()
        if (text.isEmpty()) return emptyList()

        // Hosts-style: "0.0.0.0 example.com other.example.com ..."
        val parts = text.split(WHITESPACE_PATTERN)
        if (parts.size >= 2) {
            val ip = parts[0]
            if (ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "::" || ip == "::1") {
                return parts.asSequence()
                    .drop(1)
                    .take(MAX_HOSTS_PER_LINE)
                    .map { it.lowercase() }
                    .filter { isValidDomain(it) }
                    .map { DnsRule(domain = it, matchesSubdomains = false) } // hosts = exact match only
                    .toList()
            }
            return emptyList()
        }

        // Domains-only: just "example.com"
        val domain = text.lowercase()
        if (isValidDomain(domain)) {
            return listOf(DnsRule(domain = domain, matchesSubdomains = false)) // domains-only = exact match
        }

        return emptyList()
    }

    /**
     * Apply $badfilter rules — remove rules whose full signature matches.
     * Per AdGuard spec, $badfilter disables the rule whose text equals the
     * badfilter rule minus the badfilter modifier — not every rule for the
     * domain. So `||x.com^$dnstype=AAAA,badfilter` cancels only the
     * `||x.com^$dnstype=AAAA` rule, and a plain `||x.com^$badfilter` cancels
     * only the plain `||x.com^` rule. The match key is the badfilter rule with
     * isBadfilter cleared, which covers domain, exception status, important,
     * wildcard/regex/subdomain shape, dnstype (incl. negation), denyallow,
     * and dnsrewrite redirect targets.
     */
    private fun applyBadfilters(rules: List<DnsRule>, badfilters: List<DnsRule>): List<DnsRule> {
        if (badfilters.isEmpty()) return rules
        val badKeys = badfilters.map { it.copy(isBadfilter = false) }.toSet()
        return rules.filter { it !in badKeys }
    }

    private val SCOPED_MODIFIER_PATTERN = Regex("""(?:\$|,)(app|client|ctag)=""", RegexOption.IGNORE_CASE)

    /**
     * Modifier names this DNS parser understands. Everything here is either
     * enforced (important, badfilter, dnstype, denyallow, dnsrewrite) or
     * recognized-and-rejected with dedicated handling (scoped app/client/ctag
     * via [findScopedModifier], plus the browser modifiers explicitly rejected
     * in [parseLine]). Any modifier NOT in this set marks the rule as a
     * browser-list rule that must be skipped, never globalized.
     */
    private val DNS_SUPPORTED_MODIFIER_NAMES = setOf(
        "important", "badfilter", "dnstype", "denyallow", "dnsrewrite",
        "app", "client", "ctag"
    )
    private val DOMAIN_PATTERN = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")
    private val LOCALHOST = setOf("localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback")
    private val IPV4_PATTERN = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
    private val IPV6_PATTERN = Regex("""^[0-9a-fA-F:]+$""")
    private val ANDROID_PACKAGE_PATTERN = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""")
    private val NULL_IPS = setOf("0.0.0.0", "::", "::0", "0:0:0:0:0:0:0:0")
    private val WHITESPACE_PATTERN = Regex("""\s+""")

    /** Safety cap on hostname tokens consumed from one multi-host hosts line. */
    private const val MAX_HOSTS_PER_LINE = 16

    private fun isValidDomain(s: String): Boolean =
        s.length in 3..253 && s.contains('.') && s !in LOCALHOST && DOMAIN_PATTERN.matches(s)

    private fun findScopedModifier(line: String): String? =
        SCOPED_MODIFIER_PATTERN.find(line)?.groupValues?.getOrNull(1)?.lowercase()

    /**
     * Find the first DNS-unsupported modifier on an adblock domain rule line,
     * or null if all modifiers are known. Used by [parse] to diagnose skipped
     * browser-list rules ($removeparam, $redirect, $csp, ...) so they are
     * reported instead of silently dropped — and never globalized.
     */
    private fun findUnsupportedModifier(line: String): String? {
        var text = line.trim()
        if (text.startsWith("@@")) text = text.removePrefix("@@")
        if (!text.startsWith("||")) return null
        val dollarIdx = text.indexOf('$')
        if (dollarIdx < 0) return null
        for (mod in text.substring(dollarIdx + 1).split(',')) {
            val name = mod.trim().removePrefix("~").substringBefore('=').lowercase()
            if (name.isNotEmpty() && name !in DNS_SUPPORTED_MODIFIER_NAMES) return name
        }
        return null
    }

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
