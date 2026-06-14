package com.hostshield.ui.screens.home

import com.google.common.truth.Truth.assertThat
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import org.junit.Test

class HomeDnsLogUiTest {

    @Test
    fun manualExactBlockRuleMarksPreviouslyAllowedLogRowBlocked() {
        val entry = dnsLog("Ads.Example.Com.")
        val blockRules = listOf(UserRule(hostname = "ads.example.com", type = RuleType.BLOCK))

        assertThat(dnsLogDisplayBlocked(entry, blockRules)).isTrue()
    }

    @Test
    fun wildcardBlockRuleMarksSubdomainLogRowBlocked() {
        val entry = dnsLog("cdn.ads.example.com")
        val blockRules = listOf(
            UserRule(hostname = "*.ads.example.com", type = RuleType.BLOCK, isWildcard = true)
        )

        assertThat(dnsLogDisplayBlocked(entry, blockRules)).isTrue()
    }

    @Test
    fun disabledOrNonBlockRulesDoNotRestyleAllowedLogRows() {
        val entry = dnsLog("ads.example.com")
        val rules = listOf(
            UserRule(hostname = "ads.example.com", type = RuleType.BLOCK, enabled = false),
            UserRule(hostname = "ads.example.com", type = RuleType.ALLOW)
        )

        assertThat(dnsLogDisplayBlocked(entry, rules)).isFalse()
    }

    @Test
    fun originallyBlockedLogRowsStayBlockedWithoutCurrentRule() {
        val entry = dnsLog("tracker.example.com", blocked = true)

        assertThat(dnsLogDisplayBlocked(entry, emptyList())).isTrue()
    }

    private fun dnsLog(
        hostname: String,
        blocked: Boolean = false
    ) = DnsLogEntry(
        hostname = hostname,
        blocked = blocked,
        timestamp = 1_700_000_000_000L,
    )
}
