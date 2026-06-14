package com.hostshield.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Doh3ResolverTest {

    @Test
    fun `maps existing DoH providers to DoH3 providers`() {
        assertEquals(
            Doh3Resolver.Provider.CLOUDFLARE,
            Doh3Resolver.Provider.fromDohProvider(DohResolver.Provider.CLOUDFLARE)
        )
        assertEquals(
            Doh3Resolver.Provider.GOOGLE,
            Doh3Resolver.Provider.fromDohProvider(DohResolver.Provider.GOOGLE)
        )
        assertEquals(
            Doh3Resolver.Provider.ADGUARD,
            Doh3Resolver.Provider.fromDohProvider(DohResolver.Provider.ADGUARD)
        )
    }

    @Test
    fun `accepts only HTTP3 negotiated protocols`() {
        assertTrue(Doh3Resolver.isHttp3Protocol("h3"))
        assertTrue(Doh3Resolver.isHttp3Protocol("h3-29"))
        assertTrue(Doh3Resolver.isHttp3Protocol("quic/1"))
        assertTrue(Doh3Resolver.isHttp3Protocol("HTTP/3"))

        assertFalse(Doh3Resolver.isHttp3Protocol(null))
        assertFalse(Doh3Resolver.isHttp3Protocol(""))
        assertFalse(Doh3Resolver.isHttp3Protocol("h2"))
        assertFalse(Doh3Resolver.isHttp3Protocol("http/1.1"))
    }

    @Test
    fun `embedded Cronet transport remains disabled when artifact is unavailable`() = runTest {
        val resolver = Doh3Resolver()

        assertFalse(resolver.isAvailable)
        assertNull(resolver.getFastestProvider())
        assertTrue(resolver.getLatencyStats().isEmpty())
        assertNull(resolver.resolve(ByteArray(12)))
    }
}
