package com.hostshield.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockRuleParserTest {

    @Test
    fun `scoped app rule is skipped`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$app=com.example")
        assertNull("Rule with \$app= must be skipped", rule)
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
    fun `scoped app rule with important is still skipped`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$important,app=com.example")
        assertNull("Rule with \$app= must be skipped even with \$important", rule)
    }

    @Test
    fun `negated app scope is skipped`() {
        val rule = AdblockRuleParser.parseLine("||tracker.com^\$app=~com.example")
        assertNull("Rule with negated \$app= must be skipped", rule)
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
        """.trimIndent()
        val result = AdblockRuleParser.parse(content)
        assertEquals(2, result.blockRules.size)
        assertEquals(3, result.scopedModifierSkipped)
        assertTrue(result.blockRules.any { it.domain == "tracker.com" })
        assertTrue(result.blockRules.any { it.domain == "normal.com" })
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
}
