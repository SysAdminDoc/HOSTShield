package com.hostshield.service

import com.hostshield.util.DnsStampParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCryptRoutePlannerTest {

    @Test
    fun `direct DNSCrypt route sends packets to resolver and exposes client IP`() {
        val resolver = resolverStamp("9.9.9.9:8443")
        val route = DnsCryptRoutePlanner.plan(resolver).getOrThrow()
        val query = byteArrayOf(1, 2, 3)

        assertFalse(route.anonymized)
        assertTrue(route.resolverSeesClientIp)
        assertEquals("9.9.9.9", route.networkDestination.host)
        assertEquals(8443, route.networkDestination.port)
        assertSame(query, DnsCryptRoutePlanner.wrapForRelay(route, query))
    }

    @Test
    fun `anonymized route sends packets to relay and prefixes resolver target`() {
        val resolver = resolverStamp("192.0.2.1:443")
        val relay = relayStamp("198.51.100.7:8443")

        val route = DnsCryptRoutePlanner.plan(resolver, relay).getOrThrow()
        val prefix = DnsCryptRoutePlanner.relayPrefix(route)
        val wrapped = DnsCryptRoutePlanner.wrapForRelay(route, byteArrayOf(0x12, 0x34))

        assertTrue(route.anonymized)
        assertFalse(route.resolverSeesClientIp)
        assertEquals("198.51.100.7", route.networkDestination.host)
        assertEquals(8443, route.networkDestination.port)
        assertEquals(28, prefix.size)
        assertArrayEquals(
            byteArrayOf(
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0x00, 0x00
            ),
            prefix.copyOfRange(0, 10)
        )
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0xff.toByte(), 0xff.toByte(), 192.toByte(), 0, 2, 1
            ),
            prefix.copyOfRange(10, 26)
        )
        assertEquals(0x01, prefix[26].toInt() and 0xFF)
        assertEquals(0xbb, prefix[27].toInt() and 0xFF)
        assertArrayEquals(prefix + byteArrayOf(0x12, 0x34), wrapped)
    }

    @Test
    fun `rejects relay route when resolver is not DNSCrypt`() {
        val resolver = resolverStamp("192.0.2.1").copy(
            protocol = DnsStampParser.DnsStamp.Protocol.DOH
        )

        assertTrue(DnsCryptRoutePlanner.plan(resolver, relayStamp("198.51.100.7")).isFailure)
    }

    @Test
    fun `rejects relay route when resolver and relay are the same endpoint`() {
        val resolver = resolverStamp("192.0.2.1:443")
        val relay = relayStamp("192.0.2.1:443")

        assertTrue(DnsCryptRoutePlanner.plan(resolver, relay).isFailure)
    }

    @Test
    fun `rejects hostname endpoints because relay prefix requires literal IP`() {
        val resolver = resolverStamp("dns.example.com:443")

        assertTrue(DnsCryptRoutePlanner.plan(resolver, relayStamp("198.51.100.7")).isFailure)
    }

    @Test
    fun `builds IPv6 relay target bytes`() {
        val route = DnsCryptRoutePlanner.plan(
            resolverStamp("[2001:db8::53]:443"),
            relayStamp("198.51.100.7")
        ).getOrThrow()

        val prefix = DnsCryptRoutePlanner.relayPrefix(route)

        assertArrayEquals(
            byteArrayOf(
                0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0x53
            ),
            prefix.copyOfRange(10, 26)
        )
    }

    private fun resolverStamp(address: String): DnsStampParser.DnsStamp =
        DnsStampParser.DnsStamp(
            protocol = DnsStampParser.DnsStamp.Protocol.DNSCRYPT,
            address = address,
            hostname = "",
            path = "",
            dnssec = true,
            noLog = true,
            noFilter = false,
            providerName = "2.dnscrypt-cert.example",
            providerPublicKey = ByteArray(32) { it.toByte() }
        )

    private fun relayStamp(address: String): DnsStampParser.DnsStamp =
        DnsStampParser.DnsStamp(
            protocol = DnsStampParser.DnsStamp.Protocol.DNSCRYPT_RELAY,
            address = address,
            hostname = "",
            path = "",
            dnssec = false,
            noLog = false,
            noFilter = false
        )
}
