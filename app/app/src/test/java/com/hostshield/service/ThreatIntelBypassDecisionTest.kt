package com.hostshield.service

import com.hostshield.domain.BlockDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatIntelBypassDecisionTest {
    @Test
    fun `explicit allow and pause decisions bypass threat intel checks`() {
        listOf(
            "allowlist",
            "allowlist_wildcard",
            "dns_type_allow",
            "regex_allow",
            "protection_paused"
        ).forEach { reason ->
            assertTrue(
                reason,
                BlockDecision(blocked = false, reason = reason).skipsThreatIntelChecks()
            )
        }
    }

    @Test
    fun `default allowed and blocked decisions still run threat intel checks`() {
        assertFalse(BlockDecision.ALLOWED_DEFAULT.skipsThreatIntelChecks())
        assertFalse(BlockDecision(blocked = true, reason = "source_list").skipsThreatIntelChecks())
    }
}
