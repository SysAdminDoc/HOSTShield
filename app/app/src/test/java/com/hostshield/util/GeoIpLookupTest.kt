package com.hostshield.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeoIpLookupTest {

    private val lookup = GeoIpLookup()

    @Test
    fun `IPv4 RFC 1918 - 10 dot x`() = runBlocking {
        val result = lookup.lookup("10.0.0.1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv4 RFC 1918 - 192 dot 168`() = runBlocking {
        val result = lookup.lookup("192.168.1.1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv4 RFC 1918 - 172 dot 16 through 31`() = runBlocking {
        for (octet in 16..31) {
            val result = lookup.lookup("172.$octet.0.1")
            assertNotNull("172.$octet.0.1 should be private", result)
            assertEquals("172.$octet.0.1 should be Local", "Local", result!!.country)
        }
    }

    @Test
    fun `IPv4 172 dot 15 is not private`() = runBlocking {
        val result = lookup.lookup("172.15.0.1")
        // Should NOT return "Local" — either null (rate limited) or real GeoIP
        if (result != null) {
            assert(result.country != "Local") { "172.15.0.1 is not private" }
        }
    }

    @Test
    fun `IPv4 loopback`() = runBlocking {
        val result = lookup.lookup("127.0.0.1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv4 link-local`() = runBlocking {
        val result = lookup.lookup("169.254.1.1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv4 zero address`() = runBlocking {
        val result = lookup.lookup("0.0.0.0")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv6 loopback`() = runBlocking {
        val result = lookup.lookup("::1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv6 ULA fc prefix`() = runBlocking {
        val result = lookup.lookup("fc00::1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv6 ULA fd prefix`() = runBlocking {
        val result = lookup.lookup("fd12:3456:789a::1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `IPv6 link-local`() = runBlocking {
        val result = lookup.lookup("fe80::1")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }

    @Test
    fun `blank IP returns local`() = runBlocking {
        val result = lookup.lookup("")
        assertNotNull(result)
        assertEquals("Local", result!!.country)
    }
}
