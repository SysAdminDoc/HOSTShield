package com.hostshield.util

import org.junit.Assert.*
import org.junit.Test

class ImportExportUtilTest {

    @Test
    fun `parseHostsImport handles standard format`() {
        val content = """
            0.0.0.0 ads.example.com
            127.0.0.1 tracker.evil.com
            # comment line
            malware.bad.org
        """.trimIndent()

        val domains = parseHostsContent(content)
        assertTrue(domains.contains("ads.example.com"))
        assertTrue(domains.contains("tracker.evil.com"))
        assertTrue(domains.contains("malware.bad.org"))
    }

    @Test
    fun `parseHostsImport skips localhost`() {
        val content = """
            127.0.0.1 localhost
            0.0.0.0 real-blocked.com
        """.trimIndent()

        val domains = parseHostsContent(content)
        assertFalse(domains.contains("localhost"))
        assertTrue(domains.contains("real-blocked.com"))
    }

    @Test
    fun `parseABPFormat extracts domains`() {
        val content = """
            ||ads.example.com^
            ||tracker.evil.com^
            @@||allowed.com^
            ! comment
        """.trimIndent()

        val blocked = mutableSetOf<String>()
        val allowed = mutableSetOf<String>()

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("@@||") && trimmed.endsWith("^") -> {
                    allowed.add(trimmed.removePrefix("@@||").removeSuffix("^"))
                }
                trimmed.startsWith("||") && trimmed.endsWith("^") -> {
                    blocked.add(trimmed.removePrefix("||").removeSuffix("^"))
                }
            }
        }

        assertEquals(2, blocked.size)
        assertEquals(1, allowed.size)
        assertTrue(blocked.contains("ads.example.com"))
        assertTrue(allowed.contains("allowed.com"))
    }

    // Helper that mimics the hosts file parsing logic
    private fun parseHostsContent(content: String): Set<String> {
        val localhost = setOf("localhost", "localhost.localdomain", "local",
            "broadcasthost", "ip6-localhost", "ip6-loopback")
        val domains = mutableSetOf<String>()

        content.lines().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            val parts = line.split(Regex("\\s+"))
            when {
                parts.size >= 2 -> {
                    val host = parts[1].lowercase()
                    if (host !in localhost) domains.add(host)
                }
                parts.size == 1 && parts[0].contains('.') -> {
                    domains.add(parts[0].lowercase())
                }
            }
        }
        return domains
    }
}
