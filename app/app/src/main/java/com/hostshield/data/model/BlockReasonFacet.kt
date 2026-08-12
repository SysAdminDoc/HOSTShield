package com.hostshield.data.model

/** User-facing groupings for the raw decision reasons stored in DNS logs. */
enum class BlockReasonFacet(
    val key: String,
    val label: String,
) {
    SOURCE("source", "Source lists"),
    THREAT_INTEL("threat_intel", "Threat feeds"),
    CONTENT_CATEGORY("content_category", "Content categories"),
    USER_RULE("user_rule", "User rules"),
    REGEX("regex", "Regex rules"),
    APP_POLICY("app_policy", "App policy"),
    OTHER("other", "Other"),
    ;

    companion object {
        fun fromKey(key: String?): BlockReasonFacet? =
            entries.firstOrNull { it.key == key }
    }
}

/**
 * Collapse detailed decision provenance into stable filter/chart facets. The
 * source text disambiguates wildcard and DNS-type matches created by a user's
 * rule from the equivalent source-list rules.
 */
fun blockReasonFacet(reason: String, source: String = ""): BlockReasonFacet {
    val normalizedReason = reason.trim().lowercase()
    val normalizedSource = source.trim()
    return when {
        normalizedReason.startsWith("threat_intel_") -> BlockReasonFacet.THREAT_INTEL
        normalizedReason.startsWith("regex_") -> BlockReasonFacet.REGEX
        normalizedReason in setOf("content_filter", "parental_control") -> BlockReasonFacet.CONTENT_CATEGORY
        normalizedReason in setOf("app_firewall", "context_firewall", "app_rule_block") -> BlockReasonFacet.APP_POLICY
        normalizedReason == "user_rule" || normalizedSource.startsWith("User ", ignoreCase = true) ->
            BlockReasonFacet.USER_RULE
        normalizedReason in setOf("source_list", "wildcard_block", "dns_type_rule", "doh_bypass") ->
            BlockReasonFacet.SOURCE
        else -> BlockReasonFacet.OTHER
    }
}
