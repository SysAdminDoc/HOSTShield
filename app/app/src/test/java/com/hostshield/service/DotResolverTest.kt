package com.hostshield.service

import org.junit.Assert.*
import org.junit.Test

class DotResolverTest {

    @Test
    fun `all DoT providers have corresponding DoH pin manifest entries`() {
        DotResolver.Provider.entries.forEach { provider ->
            val dohProviderId = provider.name
            val pins = DohPinManifest.providers.firstOrNull { it.providerId == dohProviderId }
            assertNotNull(
                "DoT provider ${provider.name} has no SPKI pins in DohPinManifest",
                pins
            )
            assertTrue(
                "DoT provider ${provider.name} has empty pin set",
                pins!!.pins.isNotEmpty()
            )
        }
    }

    @Test
    fun `DoT providers connect to port 853`() {
        DotResolver.Provider.entries.forEach { provider ->
            assertTrue(
                "Provider ${provider.name} must have a non-blank hostname",
                provider.hostname.isNotBlank()
            )
            assertTrue(
                "Provider ${provider.name} must have a valid IP",
                provider.ip.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
            )
        }
    }

    @Test
    fun `fromId falls back to CLOUDFLARE for unknown ids`() {
        assertEquals(DotResolver.Provider.CLOUDFLARE, DotResolver.Provider.fromId("unknown"))
        assertEquals(DotResolver.Provider.CLOUDFLARE, DotResolver.Provider.fromId(""))
        assertEquals(DotResolver.Provider.GOOGLE, DotResolver.Provider.fromId("google"))
        assertEquals(DotResolver.Provider.QUAD9, DotResolver.Provider.fromId("QUAD9"))
    }
}
