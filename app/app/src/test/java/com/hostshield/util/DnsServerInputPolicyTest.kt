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
}
