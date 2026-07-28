package com.hostshield.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSourceHealthTest {

    private val source = HostSource(
        url = "https://example.test/hosts",
        label = "Example"
    )

    @Test
    fun `disabled failures are not actionable health issues`() {
        val disabledDead = source.copy(
            enabled = false,
            health = SourceHealth.DEAD,
            consecutiveFailures = 11
        )

        assertFalse(disabledDead.hasActiveHealthWarning())
        assertFalse(disabledDead.hasActiveHealthFailure())
    }

    @Test
    fun `enabled stale source is a warning but not a failure`() {
        val stale = source.copy(enabled = true, health = SourceHealth.STALE)

        assertTrue(stale.hasActiveHealthWarning())
        assertFalse(stale.hasActiveHealthFailure())
    }

    @Test
    fun `enabled error and dead source states are failures`() {
        assertTrue(source.copy(health = SourceHealth.ERROR).hasActiveHealthFailure())
        assertTrue(source.copy(health = SourceHealth.DEAD).hasActiveHealthFailure())
    }
}
