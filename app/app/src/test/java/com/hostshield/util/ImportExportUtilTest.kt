package com.hostshield.util

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImportExportUtilTest {

    // These three previously asserted against a copy-pasted in-test parser
    // (parseHostsContent) and inline ABP string handling, so they passed with the
    // production importer deleted. They now drive the real ImportExportUtil.
    @Test
    fun `hosts import extracts blocked domains from standard format`() = runBlocking {
        val content = """
            0.0.0.0 ads.example.com
            127.0.0.1 tracker.evil.com
            # comment line
            malware.bad.org
        """.trimIndent()

        val result = ImportExportUtil().importHostsFormat(content)
        val hosts = result.blocklist.map { it.hostname }

        assertTrue(hosts.contains("ads.example.com"))
        assertTrue(hosts.contains("tracker.evil.com"))
        assertTrue(hosts.contains("malware.bad.org"))
    }

    @Test
    fun `hosts import skips localhost aliases`() = runBlocking {
        val content = """
            127.0.0.1 localhost
            127.0.0.1 localhost.localdomain
            ::1 ip6-localhost
            0.0.0.0 real-blocked.com
        """.trimIndent()

        val result = ImportExportUtil().importHostsFormat(content)
        val hosts = result.blocklist.map { it.hostname }

        assertFalse(hosts.contains("localhost"))
        assertFalse(hosts.contains("localhost.localdomain"))
        assertFalse(hosts.contains("ip6-localhost"))
        assertTrue(hosts.contains("real-blocked.com"))
    }

    @Test
    fun `hosts import reads ABP block and exception rules`() = runBlocking {
        val content = """
            ||ads.example.com^
            ||tracker.evil.com^
            @@||allowed.com^
            ! comment
        """.trimIndent()

        val result = ImportExportUtil().importHostsFormat(content)

        assertTrue(result.blocklist.map { it.hostname }.contains("ads.example.com"))
        assertTrue(result.blocklist.map { it.hostname }.contains("tracker.evil.com"))
        assertTrue(result.allowlist.map { it.hostname }.contains("allowed.com"))
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

    // Regression: exportJson omitted is_regex and importJson ran every hostname
    // through hostname validation, so regex rules round-tripped to nothing. IPv6
    // redirect targets were dropped the same way by an IPv4-only isIpLike check.
    @Test
    fun `rules JSON round trip preserves exact wildcard regex and IPv6 redirect rules`() = runBlocking {
        val original = listOf(
            UserRule(hostname = "ads.example.com", type = RuleType.BLOCK),
            UserRule(hostname = "*.wild.example", type = RuleType.BLOCK, isWildcard = true),
            UserRule(hostname = """^ad[0-9]+\.example\.com$""", type = RuleType.BLOCK, isRegex = true),
            UserRule(hostname = "v4.example.com", type = RuleType.REDIRECT, redirectIp = "10.1.2.3"),
            UserRule(hostname = "v6.example.com", type = RuleType.REDIRECT, redirectIp = "fd00::5"),
        )

        val json = ImportExportUtil().exportJson(original, emptyList())
        val restored = ImportExportUtil().importJson(json)
        val all = restored.blocklist + restored.allowlist + restored.redirects

        assertEquals("every rule survives the round trip", original.size, all.size)
        val regex = all.single { it.isRegex }
        assertEquals("""^ad[0-9]+\.example\.com$""", regex.hostname)
        assertFalse("a regex rule is not also a wildcard", regex.isWildcard)
        assertTrue("wildcard rule survives", all.any { it.isWildcard && it.hostname == "*.wild.example" })
        assertTrue("IPv6 redirect survives", all.any { it.redirectIp == "fd00::5" })
        assertTrue("IPv4 redirect survives", all.any { it.redirectIp == "10.1.2.3" })
    }

    @Test
    fun `rules JSON import still rejects an invalid regex pattern`() = runBlocking {
        val content = """
            {"rules": [{"hostname": "^ad[0-9+.example$", "type": "BLOCK", "is_regex": true}]}
        """.trimIndent()

        val result = ImportExportUtil().importJson(content)
        assertEquals(0, (result.blocklist + result.allowlist + result.redirects).size)
    }
}
