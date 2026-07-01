package com.hostshield.domain

data class DnsTypeRule(
    val domain: String,
    val dnsTypes: Set<Int>,
    val dnsTypesNegated: Boolean = false,
    val allow: Boolean = false,
    val matchesSubdomains: Boolean = true,
    val source: String = ""
) {
    fun normalized(sourceName: String = source): DnsTypeRule =
        copy(
            domain = domain.trim().lowercase().removePrefix("*.").removeSuffix("."),
            dnsTypes = dnsTypes.filter { it > 0 }.toSet(),
            source = sourceName
        )

    fun matches(hostname: String, queryType: Int): Boolean {
        if (queryType <= 0 || dnsTypes.isEmpty()) return false
        val lowerDomain = domain.lowercase()
        val domainMatches = hostname == lowerDomain ||
            (matchesSubdomains && hostname.endsWith(".$lowerDomain"))
        if (!domainMatches) return false
        return if (dnsTypesNegated) queryType !in dnsTypes else queryType in dnsTypes
    }

    fun previewKey(): String {
        val typeList = dnsTypes.sorted().joinToString("|")
        val mode = if (dnsTypesNegated) "~" else ""
        val prefix = if (matchesSubdomains) "||" else ""
        val suffix = if (allow) ",allow" else ""
        return "$prefix$domain^${'$'}dnstype=$mode$typeList$suffix"
    }
}
