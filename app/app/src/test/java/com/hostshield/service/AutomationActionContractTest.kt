package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationActionContractTest {

    @Test
    fun canonicalActionsNormalizeToThemselves() {
        AutomationActionContract.canonicalActions.forEach { action ->
            assertSame(action, AutomationActionContract.normalizeAction(action))
        }
    }

    @Test
    fun legacyActionAliasesNormalizeToCanonicalActions() {
        assertEquals(
            AutomationActionContract.ACTION_ENABLE,
            AutomationActionContract.normalizeAction("com.hostshield.action.ENABLE")
        )
        assertEquals(
            AutomationActionContract.ACTION_REFRESH_BLOCKLIST,
            AutomationActionContract.normalizeAction("com.hostshield.action.REFRESH_BLOCKLIST")
        )
        assertEquals(
            AutomationActionContract.ACTION_PAUSE,
            AutomationActionContract.normalizeAction("com.hostshield.action.PAUSE")
        )
    }

    @Test
    fun unknownActionsAreRejected() {
        assertNull(AutomationActionContract.normalizeAction(null))
        assertNull(AutomationActionContract.normalizeAction("com.hostshield.action.UNKNOWN"))
        assertNull(AutomationActionContract.normalizeAction("com.example.ACTION_ENABLE"))
    }

    @Test
    fun manifestActionSetCoversCanonicalAndLegacyContracts() {
        AutomationActionContract.canonicalActions.forEach { action ->
            assertTrue(AutomationActionContract.intentFilterActions.contains(action))
        }
        AutomationActionContract.legacyActionAliases.keys.forEach { action ->
            assertTrue(AutomationActionContract.intentFilterActions.contains(action))
        }
    }

    @Test
    fun pauseDurationPrefersCanonicalExtraAndSupportsLegacyFallback() {
        assertEquals(15, AutomationActionContract.pauseDurationMinutes(15, 30))
        assertEquals(30, AutomationActionContract.pauseDurationMinutes(null, 30))
        assertEquals(
            AutomationActionContract.DEFAULT_PAUSE_MINUTES,
            AutomationActionContract.pauseDurationMinutes(null, null)
        )
    }

    @Test
    fun pauseDurationZeroMeansResumeAndNonZeroValuesAreClamped() {
        assertEquals(0, AutomationActionContract.pauseDurationMinutes(0, null))
        assertEquals(1, AutomationActionContract.pauseDurationMinutes(-3, null))
        assertEquals(
            AutomationActionContract.MAX_PAUSE_MINUTES,
            AutomationActionContract.pauseDurationMinutes(5_000, null)
        )
    }
}
