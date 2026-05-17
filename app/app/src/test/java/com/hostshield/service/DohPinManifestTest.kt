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
    fun `certificate pinner builds from manifest`() {
        assertNotNull(DohPinManifest.certificatePinner())
    }
}
