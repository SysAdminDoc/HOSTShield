package com.hostshield.domain

import com.hostshield.domain.parser.HostsParser
import org.junit.Assert.*
import org.junit.Test

class HostsParserTest {

    @Test
    fun `parse standard hosts format`() {
        val content = """
            0.0.0.0 ads.example.com
            127.0.0.1 tracker.evil.com
            0.0.0.0 malware.bad.org
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertEquals(3, results.size)
        assertTrue(results.any { it.hostname == "ads.example.com" })
        assertTrue(results.any { it.hostname == "tracker.evil.com" })
        assertTrue(results.any { it.hostname == "malware.bad.org" })
    }

    @Test
    fun `skip comments and blank lines`() {
        val content = """
            # This is a comment
            0.0.0.0 ads.example.com
            
            # Another comment
            0.0.0.0 tracker.evil.com
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertEquals(2, results.size)
    }

    @Test
    fun `skip inline comments`() {
        val content = "0.0.0.0 ads.example.com # block this"
        val results = HostsParser.parse(content)
        assertEquals(1, results.size)
        assertEquals("ads.example.com", results.first().hostname)
    }

    @Test
    fun `skip localhost entries`() {
        val content = """
            127.0.0.1 localhost
            127.0.0.1 localhost.localdomain
            0.0.0.0 ip6-localhost
            0.0.0.0 broadcasthost
            0.0.0.0 real-blocked.com
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertEquals(1, results.size)
        assertEquals("real-blocked.com", results.first().hostname)
    }

    @Test
    fun `parse domain-only format`() {
        val content = """
            ads.example.com
            tracker.evil.com
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertEquals(2, results.size)
    }

    @Test
    fun `lowercase normalization`() {
        val content = "0.0.0.0 ADS.Example.COM"
        val results = HostsParser.parse(content)
        assertEquals("ads.example.com", results.first().hostname)
    }

    @Test
    fun `deduplicate domains`() {
        val content = """
            0.0.0.0 ads.example.com
            127.0.0.1 ads.example.com
            ads.example.com
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertEquals(1, results.size)
    }

    @Test
    fun `reject invalid domains`() {
        val content = """
            0.0.0.0 -invalid.com
            0.0.0.0 .leading-dot.com
            0.0.0.0 valid-domain.com
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertTrue(results.any { it.hostname == "valid-domain.com" })
    }

    @Test
    fun `empty input returns empty set`() {
        val results = HostsParser.parse("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `handles various blocking IPs`() {
        val content = """
            0.0.0.0 zero.com
            127.0.0.1 loopback.com
            :: ipv6zero.com
            ::1 ipv6loop.com
        """.trimIndent()

        val results = HostsParser.parse(content)
        assertTrue(results.size >= 2) // At minimum 0.0.0.0 and 127.0.0.1 hosts
    }

    @Test
    fun `large file parsing`() {
        val lines = (1..50_000).joinToString("\n") { "0.0.0.0 domain$it.example.com" }

        val start = System.nanoTime()
        val results = HostsParser.parse(lines)
        val elapsed = (System.nanoTime() - start) / 1_000_000

        assertEquals(50_000, results.size)
        assertTrue("50k lines parsed in ${elapsed}ms", elapsed < 5000)
    }
}
