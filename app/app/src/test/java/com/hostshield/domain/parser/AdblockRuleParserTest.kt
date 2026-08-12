package com.hostshield.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockRuleParserTest {

    @Test
    fun `scoped app rule is parsed without globalizing it`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$app=com.example")
        assertNotNull(rule)
        assertEquals(AdblockRuleParser.AppScope("com.example"), rule!!.appScope)
    }

    @Test
    fun `scoped client rule is skipped`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$client=192.168.1.0/24")
        assertNull("Rule with \$client= must be skipped", rule)
    }

    @Test
    fun `scoped ctag rule is skipped`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$ctag=device_phone")
        assertNull("Rule with \$ctag= must be skipped", rule)
    }

    @Test
    fun `scoped app rule with important keeps its scope`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$important,app=com.example")
        assertNotNull(rule)
        assertTrue(rule!!.isImportant)
        assertEquals("com.example", rule.appScope?.packageName)
    }

    @Test
    fun `negated app scope is parsed`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$app=~com.example")
        assertNotNull(rule)
        assertEquals(AdblockRuleParser.AppScope("com.example", negated = true), rule!!.appScope)
    }

    @Test
    fun `invalid app package is rejected`() {
        assertNull(AdblockRuleParser.parseLine("||tracker.com^\$app=not-a-package"))
        assertNull(AdblockRuleParser.parseLine("||tracker.com^\$app=com.example|com.other"))
        assertNull(AdblockRuleParser.parseLine("||tracker.com^\$app=com.example,denyallow=good.example"))
    }

    @Test
    fun `unscoped rule is parsed normally`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^")
        assertNotNull(rule)
        assertEquals("tracker.com", rule!!.domain)
    }

    @Test
    fun `unscoped rule with important is parsed`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$important")
        assertNotNull(rule)
        assertTrue(rule!!.isImportant)
    }

    @Test
    fun `bulk parse counts scoped modifiers`() {
        val content = """
            ||tracker.com^
            ||scoped.com^${'$'}app=com.example
            ||other.com^${'$'}client=lan
            ||normal.com^
            ||tagged.com^${'$'}ctag=device_phone
            ||important-scoped.com^${'$'}important,app=com.example
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)
        assertEquals(4, result.blockRules.size)
        assertEquals(2, result.scopedModifierSkipped)
        assertEquals(2, result.diagnostics.size)
        assertEquals("unsupported_scoped_modifier", result.diagnostics.first().reason)
        assertEquals("client", result.diagnostics.first().modifier)
        assertTrue(result.diagnostics.first().message.contains("instead of applying it globally"))
        assertTrue(result.blockRules.any { it.domain == "tracker.com" })
        assertTrue(result.blockRules.any { it.domain == "normal.com" })
        assertEquals(
            setOf("com.example"),
            result.blockRules.filter { it.appScope != null }.map { it.appScope!!.packageName }.toSet()
        )
    }

    @Test
    fun `allow exception is parsed`() {
        val rule = AdblockRuleParser.parseLine("@@||example.com^")
        assertNotNull(rule)
        assertTrue(rule!!.isException)
        assertEquals("example.com", rule.domain)
    }

    @Test
    fun `dnstype modifier is parsed`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$dnstype=AAAA")
        assertNotNull(rule)
        assertEquals(setOf(28), rule!!.dnsTypes)
    }

    @Test
    fun `negated dnstype is parsed`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$dnstype=~A")
        assertNotNull(rule)
        assertEquals(setOf(1), rule!!.dnsTypes)
        assertTrue(rule.dnsTypesNegated)
    }

    @Test
    fun `mixed dnstype keeps positive types only`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$dnstype=~A|AAAA")
        assertNotNull(rule)
        assertEquals(setOf(28), rule!!.dnsTypes)
        assertFalse(rule.dnsTypesNegated)
    }

    @Test
    fun `invalid dnstype is skipped instead of globalized`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$dnstype=NOT_A_TYPE")
        assertNull(rule)
    }

    @Test
    fun `denyallow modifier is parsed`() {
        val rule = AdblockRuleParser.parseLine("||ads.com^\$denyallow=safe.com|good.com")
        assertNotNull(rule)
        assertEquals(setOf("safe.com", "good.com"), rule!!.denyAllowDomains)
    }

    @Test
    fun `badfilter modifier is parsed`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$badfilter")
        assertNotNull(rule)
        assertTrue(rule!!.isBadfilter)
    }

    @Test
    fun `wildcard rule is parsed`() {
        val rule = AdblockRuleParser.parseLine("||*.tracker.com^")
        assertNotNull(rule)
        assertTrue(rule!!.isWildcard)
        assertEquals("tracker.com", rule.domain)
    }

    @Test
    fun `regex rule is parsed`() {
        val rule = AdblockRuleParser.parseLine("/tracker\\d+\\.com/")
        assertNotNull(rule)
        assertTrue(rule!!.isRegex)
    }

    @Test
    fun `hosts-style rule is parsed in bulk`() {
        val result = AdblockRuleParser.parse("0.0.0.0 ads.example.com")
        assertEquals(1, result.blockRules.size)
        assertEquals("ads.example.com", result.blockRules[0].domain)
    }

    @Test
    fun `dnsrewrite NXDOMAIN is block-equivalent`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$dnsrewrite=NXDOMAIN")
        assertNotNull(rule)
        assertNull("NXDOMAIN rewrite is a block, not a redirect", rule!!.redirectIp)
    }

    @Test
    fun `dnsrewrite IP redirect is parsed`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$dnsrewrite=1.2.3.4")
        assertNotNull(rule)
        assertEquals("1.2.3.4", rule!!.redirectIp)
    }

    @Test
    fun `priority ordering is correct`() {
        val forceAllow = AdblockRuleParser.parseLine("@@||x.com^\$important")!!
        val forceBlock = AdblockRuleParser.parseLine("||x.com^\$important")!!
        val allow = AdblockRuleParser.parseLine("@@||x.com^")!!
        val block = AdblockRuleParser.parseLine("||x.com^")!!

        assertTrue(forceAllow.priority > forceBlock.priority)
        assertTrue(forceBlock.priority > allow.priority)
        assertTrue(allow.priority > block.priority)
    }

    @Test
    fun `badfilter removes matching rules`() {
        val content = """
            ||block-me.com^
            ||keep-me.com^
            ||block-me.com^${'$'}badfilter
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)
        assertEquals(1, result.blockRules.size)
        assertEquals("keep-me.com", result.blockRules[0].domain)
    }

    @Test
    fun `removeparam rule is skipped not globalized`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$removeparam=utm")
        assertNull("Rule with \$removeparam must be skipped, not become a whole-domain block", rule)
    }

    @Test
    fun `redirect rule is skipped not globalized`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$media,redirect=noopmp3-0.1s")
        assertNull("Rule with \$redirect must be skipped, not become a whole-domain block", rule)
    }

    @Test
    fun `csp rule is skipped not globalized`() {
        val rule = AdblockRuleParser.parseLine("||example.com^\$csp=script-src 'none'")
        assertNull("Rule with \$csp must be skipped, not become a whole-domain block", rule)
    }

    @Test
    fun `supported modifiers still parse after unknown-modifier whitelist`() {
        assertNotNull(AdblockRuleParser.parseLine("||example.com^\$important"))
        assertNotNull(AdblockRuleParser.parseLine("||example.com^\$badfilter"))
        assertNotNull(AdblockRuleParser.parseLine("||example.com^\$dnstype=AAAA"))
        assertNotNull(AdblockRuleParser.parseLine("||example.com^\$denyallow=safe.com"))
        assertNotNull(AdblockRuleParser.parseLine("||example.com^\$dnsrewrite=NXDOMAIN"))
        assertNotNull(AdblockRuleParser.parseLine("||example.com^\$important,dnstype=A"))
    }

    @Test
    fun `bulk parse diagnoses browser-only modifier rules`() {
        val content = """
            ||tracker.com^
            ||params.com^${'$'}removeparam=utm
            ||media.com^${'$'}media,redirect=noopmp3-0.1s
            ||secure.com^${'$'}csp=script-src 'none'
            ||normal.com^
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)

        assertEquals(2, result.blockRules.size)
        assertTrue(result.blockRules.any { it.domain == "tracker.com" })
        assertTrue(result.blockRules.any { it.domain == "normal.com" })
        assertFalse(result.blockRules.any { it.domain == "params.com" })
        assertFalse(result.blockRules.any { it.domain == "media.com" })
        assertFalse(result.blockRules.any { it.domain == "secure.com" })

        assertEquals(3, result.unsupportedModifierSkipped)
        val unsupported = result.diagnostics.filter { it.reason == "unsupported_modifier" }
        assertEquals(3, unsupported.size)
        assertEquals(setOf("removeparam", "media", "csp"), unsupported.map { it.modifier }.toSet())
        assertTrue(unsupported.first().message.contains("instead of applying it"))
    }

    @Test
    fun `badfilter with dnstype only cancels the matching typed rule`() {
        val content = """
            ||x.com^
            ||x.com^${'$'}dnstype=AAAA
            ||x.com^${'$'}dnstype=AAAA,badfilter
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)

        assertEquals(1, result.blockRules.size)
        assertEquals("x.com", result.blockRules[0].domain)
        assertNull("Plain block rule must survive a typed badfilter", result.blockRules[0].dnsTypes)
    }

    @Test
    fun `plain badfilter does not cancel typed or wildcard rules`() {
        val content = """
            ||y.com^${'$'}dnstype=AAAA
            ||*.y.com^
            ||y.com^${'$'}badfilter
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)

        assertEquals(2, result.blockRules.size)
        assertTrue(result.blockRules.any { it.dnsTypes == setOf(28) })
        assertTrue(result.blockRules.any { it.isWildcard })
    }

    @Test
    fun `badfilter still cancels identical plain rule`() {
        val content = """
            ||z.com^
            ||z.com^${'$'}badfilter
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)
        assertTrue(result.blockRules.isEmpty())
    }

    @Test
    fun `multi-host hosts line emits every valid hostname`() {
        val result = AdblockRuleParser.parse("0.0.0.0 a.example.com b.example.com c.example.com")
        assertEquals(3, result.blockRules.size)
        assertEquals(
            setOf("a.example.com", "b.example.com", "c.example.com"),
            result.blockRules.map { it.domain }.toSet()
        )
    }

    @Test
    fun `multi-host hosts line skips invalid tokens individually`() {
        val result = AdblockRuleParser.parse("0.0.0.0 good.example.com localhost bad_token -bad.com also.example.com")
        assertEquals(
            setOf("good.example.com", "also.example.com"),
            result.blockRules.map { it.domain }.toSet()
        )
    }

    @Test
    fun `multi-host hosts line is capped at 16 hostnames`() {
        val hosts = (1..20).joinToString(" ") { "host$it.example.com" }
        val result = AdblockRuleParser.parse("0.0.0.0 $hosts")
        assertEquals(16, result.blockRules.size)
    }

    // Regression: parseRegexRule parsed only important/badfilter and silently
    // ignored every other modifier, so a scoped rule became an unscoped global
    // regex - the over-globalization class removed from the domain path earlier.
    @Test
    fun `a regex rule carrying a scoping modifier is rejected`() {
        assertNull(AdblockRuleParser.parseLine("/tracker/\$client=192.168.1.5"))
        assertNull(AdblockRuleParser.parseLine("/ads/\$app=com.example"))
        assertNull(AdblockRuleParser.parseLine("/x/\$denyallow=good.com"))
    }

    @Test
    fun `a regex rule keeps the modifiers it does support`() {
        val important = AdblockRuleParser.parseLine("/ads/\$important")
        assertNotNull(important)
        assertTrue(important!!.isRegex)
        assertTrue(important.isImportant)

        val badfilter = AdblockRuleParser.parseLine("/ads/\$badfilter")
        assertNotNull(badfilter)
        assertTrue(badfilter!!.isBadfilter)

        val plain = AdblockRuleParser.parseLine("/ads[0-9]+/")
        assertNotNull(plain)
        assertTrue(plain!!.isRegex)
    }

    // DNS queries arrive punycode-encoded, so an IDN rule stored verbatim could
    // never match - it only inflated entry counts and the trie/bloom.
    @Test
    fun `IDN domain rules are converted to punycode`() {
        val rule = AdblockRuleParser.parseLine("||ex\u00E4mple.com^")
        assertNotNull(rule)
        assertEquals("xn--exmple-cua.com", rule!!.domain)
    }

    @Test
    fun `ASCII domain rules are unchanged`() {
        val rule = AdblockRuleParser.parseLine("||ads.example.com^")
        assertNotNull(rule)
        assertEquals("ads.example.com", rule!!.domain)
    }
}
