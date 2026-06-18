package com.hostshield.data

import com.hostshield.data.database.Converters
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import org.junit.Assert.*
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `SourceCategory round trip`() {
        for (cat in SourceCategory.values()) {
            val str = converters.fromSourceCategory(cat)
            val back = converters.toSourceCategory(str)
            assertEquals(cat, back)
        }
    }

    @Test
    fun `RuleType round trip`() {
        for (type in RuleType.values()) {
            val str = converters.fromRuleType(type)
            val back = converters.toRuleType(str)
            assertEquals(type, back)
        }
    }

    @Test
    fun `SourceCategory unknown fallback`() {
        val result = converters.toSourceCategory("INVALID_VALUE")
        assertEquals(SourceCategory.CUSTOM, result)
    }

    @Test
    fun `SourceCategory blank fallback`() {
        val result = converters.toSourceCategory("")
        assertEquals(SourceCategory.CUSTOM, result)
    }

    @Test
    fun `RuleType unknown fallback`() {
        val result = converters.toRuleType("INVALID_VALUE")
        assertEquals(RuleType.BLOCK, result)
    }

    @Test
    fun `RuleType long corrupted fallback`() {
        val result = converters.toRuleType("INVALID_".repeat(40))
        assertEquals(RuleType.BLOCK, result)
    }

    @Test
    fun `SourceHealth round trip`() {
        for (health in SourceHealth.values()) {
            val str = converters.fromSourceHealth(health)
            val back = converters.toSourceHealth(str)
            assertEquals(health, back)
        }
    }

    @Test
    fun `SourceHealth unknown fallback`() {
        val result = converters.toSourceHealth("INVALID_VALUE")
        assertEquals(SourceHealth.UNKNOWN, result)
    }

    @Test
    fun `SourceHealth lowercase fallback`() {
        val result = converters.toSourceHealth("ok")
        assertEquals(SourceHealth.UNKNOWN, result)
    }
}
