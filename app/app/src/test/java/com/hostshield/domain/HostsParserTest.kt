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
    fun `multi-host line emits every hostname`() {
        val content = "0.0.0.0 a.example.com b.example.com c.example.com"
        val results = HostsParser.parse(content)
        assertEquals(3, results.size)
        assertEquals(
            setOf("a.example.com", "b.example.com", "c.example.com"),
            results.map { it.hostname }.toSet()
        )
    }

    @Test
    fun `multi-host line skips invalid tokens individually`() {
        val content = "127.0.0.1 good.example.com localhost bad_token -bad.com also.example.com"
        val results = HostsParser.parse(content)
        assertEquals(
            setOf("good.example.com", "also.example.com"),
            results.map { it.hostname }.toSet()
        )
    }

    @Test
    fun `multi-host line with inline comment keeps hosts before the comment`() {
        val content = "0.0.0.0 a.example.com b.example.com # c.example.com"
        val results = HostsParser.parse(content)
        assertEquals(
            setOf("a.example.com", "b.example.com"),
            results.map { it.hostname }.toSet()
        )
    }

    @Test
    fun `multi-host line is capped at 16 hostnames`() {
        val hosts = (1..20).joinToString(" ") { "host$it.example.com" }
        val results = HostsParser.parse("0.0.0.0 $hosts")
        assertEquals(16, results.size)
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
    fun `parseForBlocking preserves adblock wildcard and denyallow rules`() {
        val content = """
            [Adblock Plus]
            ||*.actor^
            ||*.africa^${'$'}denyallow=nation.africa|trusted.africa
            @@||allowed.actor^
            ||exact.example^${'$'}dnstype=A
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)

        assertTrue(result.wildcardBlockDomains.contains("actor"))
        assertTrue(result.wildcardBlockDomains.contains("africa"))
        assertTrue(result.wildcardAllowDomains.contains("nation.africa"))
        assertTrue(result.wildcardAllowDomains.contains("trusted.africa"))
        assertTrue(result.wildcardAllowDomains.contains("allowed.actor"))
        assertTrue(result.allowDomains.contains("allowed.actor"))
        // $denyallow only weakens its own rule: its domains stay out of the
        // global exact allowDomains set (they'd override exact blocks and
        // threat intel from every other source).
        assertFalse(result.allowDomains.contains("nation.africa"))
        assertFalse(result.allowDomains.contains("trusted.africa"))
        assertFalse(result.blockDomains.contains("exact.example"))
        assertTrue(result.dnsTypeRules.any {
            it.domain == "exact.example" && it.dnsTypes == setOf(1) && !it.dnsTypesNegated && !it.allow
        })
    }

    @Test
    fun `important block outranks non-important allow in the same source`() {
        val content = """
            [Adblock Plus]
            ||forced.example^${'$'}important
            @@||forced.example^
            ||plain.example^
            @@||plain.example^
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)

        // The $important block is not overridden by the plain exception. `||x^`
        // matches subdomains, so it lands in the wildcard block set.
        assertTrue(result.wildcardBlockDomains.contains("forced.example"))
        assertFalse(result.allowDomains.contains("forced.example"))
        assertFalse(result.wildcardAllowDomains.contains("forced.example"))
        // A normal block/allow pair still lets the allow win (unchanged behavior).
        assertTrue(result.allowDomains.contains("plain.example"))
    }

    @Test
    fun `important allow still overrides an important block`() {
        val content = """
            [Adblock Plus]
            ||override.example^${'$'}important
            @@||override.example^${'$'}important
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)

        assertTrue(result.allowDomains.contains("override.example"))
    }

    @Test
    fun `parseForBlocking preserves dnstype allow and negated block rules`() {
        val content = """
            [Adblock Plus]
            @@||safe.example^${'$'}dnstype=A
            ||no-ipv6.example^${'$'}dnstype=~A
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)

        assertTrue(result.dnsTypeRules.any {
            it.domain == "safe.example" && it.dnsTypes == setOf(1) && it.allow
        })
        assertTrue(result.dnsTypeRules.any {
            it.domain == "no-ipv6.example" && it.dnsTypes == setOf(1) && it.dnsTypesNegated && !it.allow
        })
        assertFalse(result.blockDomains.contains("no-ipv6.example"))
    }

    @Test
    fun `parseForAllowing treats plain and adblock allowlists as allow domains`() {
        val plain = HostsParser.parseForAllowing(
            """
                cdn.example.com
                0.0.0.0 login.example.com
            """.trimIndent()
        )
        assertTrue(plain.allowDomains.contains("cdn.example.com"))
        assertTrue(plain.allowDomains.contains("login.example.com"))

        val adblock = HostsParser.parseForAllowing(
            """
                [Adblock Plus]
                @@||cashback.example^
                @@||*.trusted.example^
                ||blocked.example^
            """.trimIndent()
        )
        assertTrue(adblock.allowDomains.contains("cashback.example"))
        assertTrue(adblock.wildcardAllowDomains.contains("cashback.example"))
        assertTrue(adblock.wildcardAllowDomains.contains("trusted.example"))
        assertFalse(adblock.allowDomains.contains("blocked.example"))
    }

    @Test
    fun `parseForAllowing preserves dnstype allow rules only`() {
        val adblock = HostsParser.parseForAllowing(
            """
                [Adblock Plus]
                @@||safe.example^${'$'}dnstype=AAAA
                ||blocked.example^${'$'}dnstype=AAAA
            """.trimIndent()
        )

        assertTrue(adblock.dnsTypeAllowRules.any {
            it.domain == "safe.example" && it.dnsTypes == setOf(28) && it.allow
        })
        assertFalse(adblock.dnsTypeAllowRules.any { it.domain == "blocked.example" })
    }

    @Test
    fun `adblock typed rule in a hosts-majority file is not globalized to an exact block`() {
        // Hosts-majority content (adblock lines under the classification
        // threshold) must not turn an embedded typed rule into an unconditional
        // all-qtype exact block for the apex.
        val content = """
            0.0.0.0 ads1.example.com
            0.0.0.0 ads2.example.com
            0.0.0.0 ads3.example.com
            0.0.0.0 ads4.example.com
            ||typed.example^${'$'}dnstype=AAAA
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)
        assertFalse("typed rule must not become an exact block", result.blockDomains.contains("typed.example"))
        assertTrue("plain hosts entries still block", result.blockDomains.contains("ads1.example.com"))
    }

    @Test
    fun `adblock subdomain rule in a hosts-majority file does not become an apex-only exact block`() {
        val content = """
            0.0.0.0 ads1.example.com
            0.0.0.0 ads2.example.com
            0.0.0.0 ads3.example.com
            0.0.0.0 ads4.example.com
            ||wild.example^
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)
        assertFalse("subdomain rule must not become an apex exact block", result.blockDomains.contains("wild.example"))
    }

    @Test
    fun `important block outranks a subdomain-scoped allow in the same source`() {
        val content = """
            [Adblock Plus]
            ||x.example^${'$'}important
            @@||sub.x.example^
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)

        // The subdomain allow must not override the important block.
        assertFalse(result.allowDomains.contains("sub.x.example"))
        assertFalse(result.wildcardAllowDomains.contains("sub.x.example"))
        assertTrue(result.wildcardBlockDomains.contains("x.example"))
    }

    @Test
    fun `adblock parser rejects path port and inline-wildcard rules`() {
        val content = """
            [Adblock Plus]
            ||example.com/path^
            ||example.com:8080^
            ||ad*.example.com^
            ||valid.example^
        """.trimIndent()

        val result = HostsParser.parseForBlocking(content)

        assertFalse(result.blockDomains.contains("example.com/path"))
        assertFalse(result.blockDomains.contains("example.com:8080"))
        assertTrue(result.wildcardBlockDomains.contains("valid.example") || result.blockDomains.contains("valid.example"))
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
