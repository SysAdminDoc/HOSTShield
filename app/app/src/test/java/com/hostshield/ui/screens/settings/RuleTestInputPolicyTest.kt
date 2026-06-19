package com.hostshield.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleTestInputPolicyTest {

    @Test
    fun `normalizes valid domains`() {
        assertThat(normalizeRuleTestDomain(" Ads.Example.COM. ")).isEqualTo("ads.example.com")
        assertThat(normalizeRuleTestDomain("a-b.example.co.uk")).isEqualTo("a-b.example.co.uk")
    }

    @Test
    fun `rejects malformed and unsafe domains`() {
        assertThat(normalizeRuleTestDomain("localhost")).isNull()
        assertThat(normalizeRuleTestDomain("-bad.example.com")).isNull()
        assertThat(normalizeRuleTestDomain("bad-.example.com")).isNull()
        assertThat(normalizeRuleTestDomain("ads.example.com; reboot")).isNull()
        assertThat(normalizeRuleTestDomain("ads example com")).isNull()
    }

    @Test
    fun `batch parsing deduplicates and caps valid domains`() {
        val input = """
            ads.example.com
            Ads.Example.COM
            bad domain
            metrics.example.net
        """.trimIndent()

        assertThat(normalizeRuleTestDomains(input)).containsExactly(
            "ads.example.com",
            "metrics.example.net"
        ).inOrder()

        val many = (1..105).joinToString("\n") { "host$it.example.com" }
        assertThat(normalizeRuleTestDomains(many)).hasSize(100)
    }
}
