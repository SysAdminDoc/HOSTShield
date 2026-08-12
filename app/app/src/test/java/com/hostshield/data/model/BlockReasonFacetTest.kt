package com.hostshield.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockReasonFacetTest {

    @Test
    fun `raw decision reasons map to user-facing facets`() {
        assertEquals(BlockReasonFacet.SOURCE, blockReasonFacet("source_list", "AdGuard"))
        assertEquals(BlockReasonFacet.THREAT_INTEL, blockReasonFacet("threat_intel_domain", "URLhaus"))
        assertEquals(BlockReasonFacet.CONTENT_CATEGORY, blockReasonFacet("content_filter", "Adult"))
        assertEquals(BlockReasonFacet.USER_RULE, blockReasonFacet("user_rule", "User block rule"))
        assertEquals(BlockReasonFacet.USER_RULE, blockReasonFacet("wildcard_block", "User wildcard block rule"))
        assertEquals(BlockReasonFacet.REGEX, blockReasonFacet("regex_block", "User regex block rule"))
        assertEquals(BlockReasonFacet.APP_POLICY, blockReasonFacet("app_rule_block", "Per-app DNS rule"))
    }

    @Test
    fun `unknown reason remains available as other facet`() {
        assertEquals(BlockReasonFacet.OTHER, blockReasonFacet("future_policy", "New policy"))
        assertEquals(BlockReasonFacet.THREAT_INTEL, BlockReasonFacet.fromKey("threat_intel"))
        assertEquals(null, BlockReasonFacet.fromKey("missing"))
    }
}
