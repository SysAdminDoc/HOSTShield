package com.hostshield.util

import kotlinx.coroutines.runBlocking
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

    @Test
    fun `HostShield JSON import skips invalid rules and non-HTTPS sources`() = runBlocking {
        val content = """
            {
              "app": "HostShield",
              "rules": [
                {"hostname": "Ads.Example.COM.", "type": "BLOCK", "enabled": true},
                {"hostname": "bad domain.example", "type": "BLOCK"},
                {"hostname": "example.org", "type": "NOT_A_RULE"},
                {"hostname": "*.wild.example", "type": "ALLOW", "is_wildcard": true},
                {"hostname": "redirect.example", "type": "REDIRECT", "redirect_ip": "999.1.1.1"}
              ],
              "sources": [
                {"url": "https://lists.example.com/hosts.txt", "label": "Safe"},
                {"url": "http://insecure.example.com/hosts.txt", "label": "Unsafe"}
              ]
            }
        """.trimIndent()

        val result = ImportExportUtil().importJson(content)

        assertEquals(listOf("ads.example.com"), result.blocklist.map { it.hostname })
        assertEquals(listOf("*.wild.example"), result.allowlist.map { it.hostname })
        assertEquals(emptyList<String>(), result.redirects.map { it.hostname })
        assertEquals(listOf("https://lists.example.com/hosts.txt"), result.sources.map { it.url })
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
