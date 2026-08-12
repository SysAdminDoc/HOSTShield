package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class DohPinFreshnessMonitorTest {

    @Test
    fun `current pins do not produce a warning`() {
        val issuedOn = LocalDate.parse(DohPinManifest.ISSUED_ON)

        assertNull(monitorAt(issuedOn).currentWarning())
    }

    @Test
    fun `review due warning names affected providers and review date`() {
        val reviewDate = LocalDate.parse(DohPinManifest.providers.first().reviewAfter)
        val warning = requireNotNull(monitorAt(reviewDate.plusDays(1)).currentWarning())

        assertEquals(DohPinManifest.Freshness.REVIEW_DUE, warning.freshness)
        assertEquals(
            DohPinManifest.providers.map { it.hostname },
            warning.providerHostnames
        )
        assertEquals(reviewDate.toString(), warning.date)
    }

    @Test
    fun `expired warning has stronger state and expiry date`() {
        val expiryDate = LocalDate.parse(DohPinManifest.providers.first().expiresAfter)
        val warning = requireNotNull(monitorAt(expiryDate.plusDays(1)).currentWarning())

        assertEquals(DohPinManifest.Freshness.EXPIRED, warning.freshness)
        assertEquals(
            DohPinManifest.providers.map { it.hostname },
            warning.providerHostnames
        )
        assertEquals(expiryDate.toString(), warning.date)
    }

    private fun monitorAt(date: LocalDate): DohPinFreshnessMonitor {
        val zone = ZoneOffset.UTC
        return DohPinFreshnessMonitor(
            Clock.fixed(date.atStartOfDay(zone).toInstant(), zone)
        )
    }
}
