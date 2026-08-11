package com.hostshield.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsServerInputPolicyTest {
    @Test
    fun `normalizes valid server lists`() {
        assertEquals(
            "1.1.1.1,9.9.9.9",
            DnsServerInputPolicy.normalizeServerList(" 1.1.1.1; 9.9.9.9 1.1.1.1 ")
        )
    }

    @Test
    fun `accepts IPv6 literals and bracketed values`() {
        assertEquals(
            listOf("2001:4860:4860:0:0:0:0:8888"),
            DnsServerInputPolicy.parseServerList("[2001:4860:4860::8888]")
        )
    }

    @Test
    fun `rejects malformed and non literal DNS server values`() {
        assertNull(DnsServerInputPolicy.normalizeServerIp("999.1.1.1"))
        assertNull(DnsServerInputPolicy.normalizeServerIp("dns.google"))
        assertNull(DnsServerInputPolicy.normalizeServerIp("8.8.8.8; reboot"))
        assertEquals("", DnsServerInputPolicy.normalizeServerList("dns.google, 999.1.1.1"))
    }

    @Test
    fun `family-specific normalizers keep redirect targets in their address family`() {
        assertEquals("0.0.0.0", DnsServerInputPolicy.normalizeIpv4(" 0.0.0.0 "))
        assertNull(DnsServerInputPolicy.normalizeIpv4("::"))
        assertEquals(
            "0:0:0:0:0:0:0:1",
            DnsServerInputPolicy.normalizeIpv6("::1")
        )
        assertNull(DnsServerInputPolicy.normalizeIpv6("127.0.0.1"))
        assertNull(DnsServerInputPolicy.normalizeIpv6("2001:db8::bad value"))
    }

    @Test
    fun `redirect normalizer keeps the blackhole and sinkhole defaults`() {
        assertEquals("0.0.0.0", DnsServerInputPolicy.normalizeRedirectIpv4("0.0.0.0"))
        // 127.0.0.1 is the classic hosts-file sinkhole — must stay allowed.
        assertEquals("127.0.0.1", DnsServerInputPolicy.normalizeRedirectIpv4("127.0.0.1"))
        assertEquals("10.1.2.3", DnsServerInputPolicy.normalizeRedirectIpv4("10.1.2.3"))
        assertEquals("0:0:0:0:0:0:0:0", DnsServerInputPolicy.normalizeRedirectIpv6("::"))
    }

    @Test
    fun `redirect normalizer rejects targets that would break resolution`() {
        // HostShield's own virtual DNS servers — redirecting there loops back in.
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("192.0.2.1"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("198.51.100.7"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("203.0.113.9"))
        // Tunnel address, multicast, limited broadcast.
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("10.120.0.1"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("224.0.0.1"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("239.1.2.3"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("255.255.255.255"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv6("ff02::1"))
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv6("fd00::1"))
        // Still rejects plain garbage.
        assertNull(DnsServerInputPolicy.normalizeRedirectIpv4("not-an-ip"))
    }
}
