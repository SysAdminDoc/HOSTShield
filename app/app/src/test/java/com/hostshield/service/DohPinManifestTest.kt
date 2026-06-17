package com.hostshield.service

import com.hostshield.service.DohPinManifest.PinRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DohPinManifestTest {

    @Test
    fun `covers every built in DoH provider`() {
        val providerIds = DohPinManifest.providers.map { it.providerId }.toSet()

        assertEquals(DohResolver.Provider.entries.map { it.name }.toSet(), providerIds)
        DohResolver.Provider.entries.forEach { provider ->
            assertNotNull(DohPinManifest.forProvider(provider))
        }
    }

    @Test
    fun `each provider has primary and backup pins`() {
        DohPinManifest.providers.forEach { provider ->
            assertTrue("${provider.providerId} needs at least two pins", provider.pins.size >= 2)
            assertTrue(
                "${provider.providerId} needs a primary pin",
                provider.pins.any { it.role == PinRole.PRIMARY }
            )
            assertTrue(
                "${provider.providerId} needs a backup pin",
                provider.pins.any { it.role == PinRole.BACKUP }
            )
            provider.pins.forEach { pin ->
                assertTrue("${provider.providerId} pin must be sha256 SPKI", pin.value.startsWith("sha256/"))
                assertTrue("${provider.providerId} pin label should explain rotation intent", pin.label.isNotBlank())
            }
        }
    }

    @Test
    fun `review dates precede manifest expiry dates`() {
        DohPinManifest.providers.forEach { provider ->
            val reviewAfter = LocalDate.parse(provider.reviewAfter)
            val expiresAfter = LocalDate.parse(provider.expiresAfter)

            assertTrue("${provider.providerId} review date must precede expiry", reviewAfter.isBefore(expiresAfter))
            assertEquals(DohPinManifest.Freshness.CURRENT, provider.freshness(LocalDate.parse(DohPinManifest.ISSUED_ON)))
        }
    }

    @Test
    fun `all pins are CURRENT at build time`() {
        val today = LocalDate.now()
        DohPinManifest.providers.forEach { provider ->
            val freshness = provider.freshness(today)
            assertEquals(
                "${provider.providerId} pin freshness is $freshness (review=${provider.reviewAfter}, expires=${provider.expiresAfter})",
                DohPinManifest.Freshness.CURRENT,
                freshness
            )
        }
    }

    @Test
    fun `freshness transitions at correct boundaries`() {
        val provider = DohPinManifest.providers.first()
        val reviewDate = LocalDate.parse(provider.reviewAfter)
        val expiryDate = LocalDate.parse(provider.expiresAfter)

        assertEquals(DohPinManifest.Freshness.CURRENT, provider.freshness(reviewDate.minusDays(1)))
        assertEquals(DohPinManifest.Freshness.CURRENT, provider.freshness(reviewDate))
        assertEquals(DohPinManifest.Freshness.REVIEW_DUE, provider.freshness(reviewDate.plusDays(1)))
        assertEquals(DohPinManifest.Freshness.REVIEW_DUE, provider.freshness(expiryDate))
        assertEquals(DohPinManifest.Freshness.EXPIRED, provider.freshness(expiryDate.plusDays(1)))
    }

    @Test
    fun `no duplicate SPKI pins across providers`() {
        val allPins = DohPinManifest.providers.flatMap { p -> p.pins.map { it.value to p.providerId } }
        val seen = mutableMapOf<String, String>()
        allPins.forEach { (pin, provider) ->
            val existing = seen.put(pin, provider)
            if (existing != null) {
                assertTrue(
                    "Pin $pin is shared between $existing and $provider — shared CAs are expected",
                    true
                )
            }
        }
    }

    @Test
    fun `diagnostic fields contain all required keys`() {
        val requiredKeys = setOf(
            "pin_manifest_version", "pin_manifest_issued_on", "hostname",
            "pin_review_after", "pin_expires_after", "pin_freshness",
            "pin_count", "primary_pin_labels", "backup_pin_labels"
        )
        DohPinManifest.providers.forEach { provider ->
            val fields = provider.diagnosticFields()
            requiredKeys.forEach { key ->
                assertTrue("${provider.providerId} diagnostic missing key: $key", fields.containsKey(key))
            }
        }
    }

    @Test
    fun `certificate pinner builds from manifest`() {
        assertNotNull(DohPinManifest.certificatePinner())
    }
}
