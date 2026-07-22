package com.hostshield.util

import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class QrConfigSharingTest {

    @Test
    fun `encoded config decodes and applies through validated import plan`() = runTest {
        val sharing = QrConfigSharing()
        val config = ShareableConfig(
            userRules = listOf(
                RuleEntry("Ads.Example.COM", "block"),
                RuleEntry("Trusted.Example", "allow"),
                RuleEntry("bad host", "block"),
            ),
            customDns = "1.1.1.1,9.9.9.9",
            dohEnabled = true,
            dohProvider = "cloudflare",
            sourceUrls = listOf(
                "https://lists.example.com/hosts.txt",
                "http://insecure.example/hosts.txt",
                "https://lists.example.com/hosts.txt",
            ),
        )

        val decoded = sharing.decodeConfig(sharing.encodeConfig(config))
        assertNotNull(decoded)

        val plan = QrConfigImportPlanner.buildPlan(decoded!!)
        assertEquals(2, plan.rulesToAdd.size)
        assertEquals(1, plan.sourcesToAdd.size)
        assertEquals(1, plan.skippedRules)
        assertEquals(2, plan.skippedSources)
        assertEquals("https://lists.example.com/hosts.txt", plan.sourcesToAdd.single().url)

        val sink = RecordingSink()
        val result = QrConfigImportPlanner.applyPlan(plan, sink)

        assertEquals(2, result.rulesAdded)
        assertEquals(1, result.sourcesAdded)
        assertEquals(3, result.settingsUpdated)
        assertEquals(listOf("ads.example.com", "trusted.example"), sink.rules.map { it.hostname })
        assertEquals(listOf(RuleType.BLOCK, RuleType.ALLOW), sink.rules.map { it.type })
        assertEquals(listOf("https://lists.example.com/hosts.txt"), sink.sources.map { it.url })
        assertEquals("1.1.1.1,9.9.9.9", sink.customDns)
        assertEquals(true, sink.dohEnabled)
        assertEquals("cloudflare", sink.dohProvider)
    }

    @Test
    fun `import plan skips existing rules and sources`() {
        val plan = QrConfigImportPlanner.buildPlan(
            config = ShareableConfig(
                userRules = listOf(RuleEntry("Ads.Example.COM", "block")),
                sourceUrls = listOf("https://lists.example.com/hosts.txt"),
            ),
            existingRuleHostnames = setOf("ads.example.com"),
            existingSourceUrls = setOf("https://lists.example.com/hosts.txt"),
        )

        assertFalse(plan.hasChanges)
        assertEquals(1, plan.skippedRules)
        assertEquals(1, plan.skippedSources)
    }

    @Test
    fun `import plan skips invalid DNS settings`() {
        val plan = QrConfigImportPlanner.buildPlan(
            config = ShareableConfig(
                customDns = "dns.google, 999.1.1.1",
                dohEnabled = true,
                dohProvider = "unsupported"
            )
        )

        assertNull(plan.customDns)
        assertNull(plan.dohProvider)
        assertEquals(true, plan.dohEnabled)
        assertEquals(1, plan.changeCount)
    }

    @Test
    fun `import plan keeps bracketed IPv6 source hosts intact`() {
        // URI.getHost() returns IPv6 literals already bracketed — no double wrap
        val plan = QrConfigImportPlanner.buildPlan(
            config = ShareableConfig(sourceUrls = listOf("https://[2001:db8::1]:8443/hosts.txt")),
        )

        assertEquals(listOf("https://[2001:db8::1]:8443/hosts.txt"), plan.sourcesToAdd.map { it.url })
        assertEquals(0, plan.skippedSources)
    }

    @Test
    fun `import plan rejects non-ASCII rule hostnames`() {
        val plan = QrConfigImportPlanner.buildPlan(
            config = ShareableConfig(userRules = listOf(RuleEntry("münchen.example", "block"))),
        )

        assertTrue(plan.rulesToAdd.isEmpty())
        assertEquals(1, plan.skippedRules)
    }

    @Test
    fun `decode rejects encoded input over four kilobytes`() {
        val sharing = QrConfigSharing()
        val oversized = QrConfigSharing.SCHEME_PREFIX + "A".repeat(QrConfigSharing.MAX_DECODED_QR_CHARS + 1)

        assertNull(sharing.decodeConfig(oversized))
    }

    @Test
    fun `decode rejects payloads over decompressed limit`() {
        val sharing = QrConfigSharing()
        val json = """{"v":1,"pn":"${"A".repeat(QrConfigSharing.MAX_DECOMPRESSED_BYTES + 1)}"}"""
        val encoded = gzipUrlSafe(json)

        assertTrue(encoded.length < QrConfigSharing.MAX_DECODED_QR_CHARS)
        assertNull(sharing.decodeConfig(QrConfigSharing.SCHEME_PREFIX + encoded))
    }

    private fun gzipUrlSafe(value: String): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private class RecordingSink : QrImportSink {
        val rules = mutableListOf<UserRule>()
        val sources = mutableListOf<HostSource>()
        var customDns: String? = null
        var dohEnabled: Boolean? = null
        var dohProvider: String? = null

        override suspend fun addRule(rule: UserRule) {
            rules.add(rule)
        }

        override suspend fun addSource(source: HostSource) {
            sources.add(source)
        }

        override suspend fun setCustomDns(value: String) {
            customDns = value
        }

        override suspend fun setDohEnabled(value: Boolean) {
            dohEnabled = value
        }

        override suspend fun setDohProvider(value: String) {
            dohProvider = value
        }
    }
}
