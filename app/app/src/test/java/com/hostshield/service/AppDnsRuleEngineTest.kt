package com.hostshield.service

import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.domain.ScopedAppDnsRule
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDnsRuleEngineTest {

    private val engine = AppDnsRuleEngine(mockk<AppDnsRuleDao>())

    @Test
    fun `positive app scope applies only to the named package`() {
        engine.replaceSourceRules(
            listOf(
                ScopedAppDnsRule(
                    domain = "ads.example",
                    packageName = "com.example.app",
                )
            )
        )

        assertEquals(
            AppDnsRuleEngine.RuleAction.BLOCK,
            engine.checkDomain("com.example.app", "sub.ads.example")
        )
        assertNull(engine.checkDomain("com.other.app", "sub.ads.example"))
    }

    @Test
    fun `negated app scope applies to every package except the named package`() {
        engine.replaceSourceRules(
            listOf(
                ScopedAppDnsRule(
                    domain = "ads.example",
                    packageName = "com.example.app",
                    packageNegated = true,
                )
            )
        )

        assertNull(engine.checkDomain("com.example.app", "ads.example"))
        assertEquals(
            AppDnsRuleEngine.RuleAction.BLOCK,
            engine.checkDomain("com.other.app", "ads.example")
        )
    }

    @Test
    fun `scoped allow overrides a scoped block for the same package`() {
        engine.replaceSourceRules(
            listOf(
                ScopedAppDnsRule(
                    domain = "ads.example",
                    packageName = "com.example.app",
                ),
                ScopedAppDnsRule(
                    domain = "ads.example",
                    packageName = "com.example.app",
                    isException = true,
                )
            )
        )

        assertEquals(
            AppDnsRuleEngine.RuleAction.ALLOW,
            engine.checkDomain("com.example.app", "ads.example")
        )
    }
}
