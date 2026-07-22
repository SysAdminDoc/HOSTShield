package com.hostshield.service

import com.hostshield.domain.BlockDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatIntelBypassDecisionTest {
    @Test
    fun `user allow and pause decisions bypass threat intel checks`() {
        assertTrue(
            "protection_paused",
            BlockDecision(blocked = false, reason = "protection_paused").skipsThreatIntelChecks()
        )
        listOf(
            "User allow rule",
            "User wildcard allow rule",
            "User regex allow rule",
        ).forEach { source ->
            assertTrue(
                source,
                BlockDecision(blocked = false, reason = "allowlist", source = source).skipsThreatIntelChecks()
            )
        }
    }

    @Test
    fun `source allowlists do NOT bypass threat intel checks`() {
        // A downloaded allowlist must not be able to whitelist a malware domain
        // past URLhaus/Spamhaus — only user-originated allows may.
        listOf(
            BlockDecision(blocked = false, reason = "allowlist", source = "Source allowlist"),
            BlockDecision(blocked = false, reason = "allowlist_wildcard", source = "Source wildcard allowlist"),
            BlockDecision(blocked = false, reason = "dns_type_allow", source = "Source DNS type allow rule"),
        ).forEach { decision ->
            assertFalse(decision.source, decision.skipsThreatIntelChecks())
        }
    }

    @Test
    fun `default allowed and blocked decisions still run threat intel checks`() {
        assertFalse(BlockDecision.ALLOWED_DEFAULT.skipsThreatIntelChecks())
        assertFalse(BlockDecision(blocked = true, reason = "source_list").skipsThreatIntelChecks())
    }
}
