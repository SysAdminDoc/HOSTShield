package com.hostshield.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainAgeCheckerTest {

    private val checker = DomainAgeChecker()

    @Test
    fun `subdomains collapse to the registered domain`() {
        assertEquals("example.com", checker.extractRegisteredDomain("a.b.example.com"))
        assertEquals("example.com", checker.extractRegisteredDomain("EXAMPLE.COM"))
    }

    @Test
    fun `multi part public suffixes keep three labels`() {
        // x.example.co.uk must query RDAP for example.co.uk, not co.uk
        assertEquals("example.co.uk", checker.extractRegisteredDomain("x.example.co.uk"))
        assertEquals("example.co.uk", checker.extractRegisteredDomain("example.co.uk"))
        assertEquals("example.com.au", checker.extractRegisteredDomain("shop.example.com.au"))
        assertEquals("example.co.kr", checker.extractRegisteredDomain("a.b.example.co.kr"))
    }

    @Test
    fun `bare suffix and single labels pass through`() {
        assertEquals("co.uk", checker.extractRegisteredDomain("co.uk"))
        assertEquals("localhost", checker.extractRegisteredDomain("localhost"))
    }
}
