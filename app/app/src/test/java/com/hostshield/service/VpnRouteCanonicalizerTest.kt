package com.hostshield.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress

class VpnRouteCanonicalizerTest {

    @Test
    fun `canonicalizes IPv4 host bits for network routes`() {
        val route = VpnRouteCanonicalizer.canonicalize("192.168.13.42", 24)

        assertEquals("192.168.13.0", route.address)
        assertEquals(24, route.prefixLength)
    }

    @Test
    fun `keeps IPv4 host routes unchanged`() {
        val route = VpnRouteCanonicalizer.canonicalize("203.0.113.7", 32)

        assertEquals("203.0.113.7", route.address)
        assertEquals(32, route.prefixLength)
    }

    @Test
    fun `canonicalizes IPv4 non byte aligned prefixes`() {
        val route = VpnRouteCanonicalizer.canonicalize("10.10.10.200", 25)

        assertEquals("10.10.10.128", route.address)
        assertEquals(25, route.prefixLength)
    }

    @Test
    fun `canonicalizes IPv6 host bits for network routes`() {
        val route = VpnRouteCanonicalizer.canonicalize(
            "2001:db8:abcd:1234:ffff:ffff:ffff:beef",
            64
        )

        assertArrayEquals(
            InetAddress.getByName("2001:db8:abcd:1234::").address,
            InetAddress.getByName(route.address).address
        )
        assertEquals(64, route.prefixLength)
    }

    @Test
    fun `keeps IPv6 host routes unchanged`() {
        val route = VpnRouteCanonicalizer.canonicalize("fd00::2", 128)

        assertArrayEquals(
            InetAddress.getByName("fd00::2").address,
            InetAddress.getByName(route.address).address
        )
        assertEquals(128, route.prefixLength)
    }

    @Test
    fun `rejects non numeric route input`() {
        assertRejected { VpnRouteCanonicalizer.canonicalize("dns.google", 32) }
        assertRejected { VpnRouteCanonicalizer.canonicalize("192.0.2.1/32", 32) }
        assertRejected { VpnRouteCanonicalizer.canonicalize("192.0.2.1", 33) }
        assertRejected { VpnRouteCanonicalizer.canonicalize("fd00::1", 129) }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
