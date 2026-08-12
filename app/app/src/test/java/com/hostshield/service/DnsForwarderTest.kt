package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsForwarderTest {
    @Test
    fun `route key preserves encrypted transport priority`() {
        val config = DnsForwardingConfig(
            useDoH = true,
            dohProvider = DohResolver.Provider.QUAD9,
            useDoT = true,
            dotProvider = DotResolver.Provider.GOOGLE,
            useDoQ = true,
            doqProvider = DoqResolver.Provider.NEXTDNS,
            useWireGuard = true,
            upstreamDnsServers = listOf("9.9.9.9"),
        )

        assertEquals("wireguard|doq=true:NEXTDNS|doh=true:QUAD9", config.routeKey())
    }

    @Test
    fun `route key includes selected fallback provider`() {
        val config = DnsForwardingConfig(
            useDoH = true,
            dohProvider = DohResolver.Provider.ADGUARD,
            useDoT = false,
            dotProvider = DotResolver.Provider.CLOUDFLARE,
            useDoQ = true,
            doqProvider = DoqResolver.Provider.ADGUARD,
            useWireGuard = false,
            upstreamDnsServers = listOf("8.8.8.8"),
        )

        assertEquals("doq:ADGUARD|doh=true:ADGUARD", config.routeKey())
    }

    @Test
    fun `plaintext route key changes when ordered upstreams change`() {
        val base = DnsForwardingConfig(
            useDoH = false,
            dohProvider = DohResolver.Provider.CLOUDFLARE,
            useDoT = false,
            dotProvider = DotResolver.Provider.CLOUDFLARE,
            useDoQ = false,
            doqProvider = DoqResolver.Provider.ADGUARD,
            useWireGuard = false,
            upstreamDnsServers = listOf("8.8.8.8", "1.1.1.1"),
        )

        assertEquals("udp:8.8.8.8,1.1.1.1", base.routeKey())
        assertEquals(
            "udp:1.1.1.1,8.8.8.8",
            base.copy(upstreamDnsServers = base.upstreamDnsServers.reversed()).routeKey(),
        )
    }
}
