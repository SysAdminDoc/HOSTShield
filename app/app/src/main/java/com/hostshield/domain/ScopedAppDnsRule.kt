package com.hostshield.domain

/**
 * A source rule whose AdGuard `$app=` scope is enforced against the querying
 * package instead of being folded into the global DNS blocklist.
 */
data class ScopedAppDnsRule(
    val domain: String,
    val packageName: String,
    val packageNegated: Boolean = false,
    val isException: Boolean = false,
    val isWildcard: Boolean = false,
    val matchesSubdomains: Boolean = true,
    val dnsTypes: Set<Int>? = null,
    val dnsTypesNegated: Boolean = false,
    val source: String = "",
) {
    fun normalized(sourceName: String = source): ScopedAppDnsRule = copy(
        domain = domain.trim().lowercase().removePrefix("*.").removeSuffix("."),
        packageName = packageName.trim(),
        dnsTypes = dnsTypes?.filter { it > 0 }?.toSet(),
        source = sourceName,
    )
}
