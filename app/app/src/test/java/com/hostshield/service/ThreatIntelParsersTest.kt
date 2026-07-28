package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreatIntelParsersTest {

    @Test
    fun `ip list parser handles whitespace separated Emerging Threats shape`() {
        val body = "192.0.2.10 198.51.100.24 203.0.113.0/24"

        val result = parseThreatIpCidrs(body, "Emerging Threats")

        assertEquals(
            listOf(
                "192.0.2.10/32" to "Emerging Threats",
                "198.51.100.24/32" to "Emerging Threats",
                "203.0.113.0/24" to "Emerging Threats"
            ),
            result
        )
    }

    @Test
    fun `ip list parser strips comments and preserves first occurrence order`() {
        val body = """
            # comment
            198.51.100.1 198.51.100.1 # duplicate
            203.0.113.8 ; inline comment
            192.0.2.0/28
        """.trimIndent()

        val result = parseThreatIpCidrs(body, "Feed")

        assertEquals(
            listOf(
                "198.51.100.1/32" to "Feed",
                "203.0.113.8/32" to "Feed",
                "192.0.2.0/28" to "Feed"
            ),
            result
        )
    }

    @Test
    fun `ip list parser rejects invalid and overly broad tokens`() {
        val body = "999.1.1.1 1.2.3.4/7 10.0.0.0/8 192.168.1.1/33 text.example"

        val result = parseThreatIpCidrs(body, "Feed")

        assertEquals(listOf("10.0.0.0/8" to "Feed"), result)
    }

    @Test
    fun `threat ip token normalizer rejects malformed Spamhaus-shaped tokens`() {
        // The Spamhaus DROP parser now validates every token through this helper,
        // so an out-of-range octet cannot be mapped onto the 0.0.0.0 bit path.
        assertEquals(null, normalizeThreatIpToken("999.1.2.3/24"))
        assertEquals(null, normalizeThreatIpToken("1.2.3/24"))
        assertEquals("1.10.16.0/20", normalizeThreatIpToken("1.10.16.0/20"))
    }

    @Test
    fun `domain normalizer rejects URLs wildcards whitespace and malformed labels`() {
        assertEquals("malware.example.com", normalizeThreatDomainToken(" Malware.Example.Com. "))
        assertEquals(null, normalizeThreatDomainToken("https://malware.example.com"))
        assertEquals(null, normalizeThreatDomainToken("*.malware.example.com"))
        assertEquals(null, normalizeThreatDomainToken("bad domain.example"))
        assertEquals(null, normalizeThreatDomainToken("bad..example.com"))
        assertEquals(null, normalizeThreatDomainToken("-bad.example.com"))
        assertEquals(null, normalizeThreatDomainToken("localhost"))
    }
}
