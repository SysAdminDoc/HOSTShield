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

    @Test
    fun `hosts import validates allowlist convention hosts`() = runBlocking {
        val content = """
            #allow# Allowed.Example.COM.
            #allow# bad domain.example
            # allow *.trusted.example
            #allow# localhost
        """.trimIndent()

        val result = ImportExportUtil().importHostsFormat(content)

        assertEquals(listOf("allowed.example.com", "*.trusted.example"), result.allowlist.map { it.hostname })
        assertEquals(listOf(false, true), result.allowlist.map { it.isWildcard })
    }

    @Test
    fun `AdAway import rejects redirects with invalid IP addresses`() = runBlocking {
        val content = """
            {
              "redirect_hosts": [
                {"hostname": "safe.example.com", "ip": "10.0.0.5"},
                {"hostname": "unsafe.example.com", "ip": "999.1.1.1"}
              ]
            }
        """.trimIndent()

        val result = ImportExportUtil().importAdAwayBackup(content)

        assertEquals(listOf("safe.example.com"), result.redirects.map { it.hostname })
        assertEquals(listOf("10.0.0.5"), result.redirects.map { it.redirectIp })
    }

    @Test
    fun `Pi-hole import normalizes exact domains and rejects malformed entries`() = runBlocking {
        val content = """
            id,type,domain,enabled,comment
            1,1,Ads.Example.COM.,1,
            2,1,bad domain.example,1,
            3,0,Allowed.Example.ORG.,1,
            4,0,localhost,1,
            5,3,(a+)+$,1,
            6,3,^ads[0-9]+\\.example$,1,
        """.trimIndent()

        val result = ImportExportUtil().importPiholeFormat(content)

        assertEquals(listOf("ads.example.com", "^ads[0-9]+\\\\.example$"), result.blocklist.map { it.hostname })
        assertEquals(listOf("allowed.example.org"), result.allowlist.map { it.hostname })
        assertEquals(listOf(false, true), result.blocklist.map { it.isRegex })
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
