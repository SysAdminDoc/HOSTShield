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

    // Regression: this test was named for port 853 but asserted only hostname/IP
    // shape — changing the connect port to 53 (plaintext) passed the whole suite.
    @Test
    fun `DoT connects on the RFC 7858 port`() {
        assertEquals(853, DotResolver.DOT_PORT)
    }

    @Test
    fun `DoT providers declare a usable hostname and literal IPv4 address`() {
        DotResolver.Provider.entries.forEach { provider ->
            assertTrue(
                "Provider ${provider.name} must have a non-blank hostname",
                provider.hostname.isNotBlank()
            )
            val octets = provider.ip.split(".")
            assertEquals("Provider ${provider.name} must have a dotted-quad IP", 4, octets.size)
            octets.forEach { octet ->
                val value = octet.toIntOrNull()
                assertNotNull("Provider ${provider.name} has a non-numeric octet", value)
                assertTrue("Provider ${provider.name} octet out of range: $octet", value!! in 0..255)
            }
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
