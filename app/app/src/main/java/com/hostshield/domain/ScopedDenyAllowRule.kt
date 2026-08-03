package com.hostshield.domain

/**
 * A `$denyallow` exception attached to the block rule that declared it.
 *
 * Keeping the owner and source on the exception is important: a denyallow
 * clause may weaken only its declaring source rule. It must not become a
 * global allow that neutralizes an exact block, a more-specific wildcard, or
 * a block from another source.
 */
data class ScopedDenyAllowRule(
    val ownerDomain: String,
    val allowedDomain: String,
    val ownerMatchesSubdomains: Boolean = true,
    val allowedMatchesSubdomains: Boolean = true,
    val dnsTypes: Set<Int>? = null,
    val dnsTypesNegated: Boolean = false,
    val source: String = "",
) {
    fun normalized(sourceName: String = source): ScopedDenyAllowRule = copy(
        ownerDomain = ownerDomain.trim().lowercase().removePrefix("*.").removeSuffix("."),
        allowedDomain = allowedDomain.trim().lowercase().removePrefix("*.").removeSuffix("."),
        dnsTypes = dnsTypes?.filter { it > 0 }?.toSet(),
        source = sourceName,
    )

    fun matchesOwner(hostname: String): Boolean =
        hostname == ownerDomain ||
            (ownerMatchesSubdomains && hostname.endsWith(".$ownerDomain"))

    fun matchesAllowed(hostname: String): Boolean =
        hostname == allowedDomain ||
            (allowedMatchesSubdomains && hostname.endsWith(".$allowedDomain"))

    fun matchesQueryType(queryType: Int?): Boolean {
        val types = dnsTypes ?: return true
        if (queryType == null || queryType <= 0 || types.isEmpty()) return false
        return if (dnsTypesNegated) queryType !in types else queryType in types
    }
}
