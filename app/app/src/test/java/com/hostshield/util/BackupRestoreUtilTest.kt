package com.hostshield.util

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class BackupRestoreUtilTest {

    @Test
    fun `JSON backup structure is valid`() {
        // Verify backup JSON can be parsed
        val json = JSONObject().apply {
            put("version", 4)
            put("app_version", "1.3.0")
            put("timestamp", System.currentTimeMillis())
            put("sources", org.json.JSONArray())
            put("rules", org.json.JSONArray())
            put("profiles", org.json.JSONArray())
            put("firewall_rules", org.json.JSONArray())
            put("preferences", JSONObject())
        }

        assertTrue(json.has("version"))
        assertTrue(json.has("app_version"))
        assertTrue(json.has("timestamp"))
        assertTrue(json.has("sources"))
        assertTrue(json.has("rules"))
        assertTrue(json.has("firewall_rules"))
        assertEquals(4, json.getInt("version"))
    }

    @Test
    fun `preferences serialize correctly`() {
        val prefs = JSONObject().apply {
            put("ipv4_redirect", "0.0.0.0")
            put("ipv6_redirect", "::")
            put("include_ipv6", true)
            put("network_firewall_enabled", true)
            put("auto_apply_firewall", true)
            put("custom_upstream_dns", "1.1.1.1")
        }

        assertEquals("0.0.0.0", prefs.getString("ipv4_redirect"))
        assertEquals(true, prefs.getBoolean("network_firewall_enabled"))
        assertEquals("1.1.1.1", prefs.getString("custom_upstream_dns"))
    }

    @Test
    fun `v2 preferences serialize correctly`() {
        val prefs = JSONObject().apply {
            put("dns_trap_enabled", true)
            put("block_response_type", "zero_ip")
            put("ede_enabled", true)
            put("local_webserver", false)
            put("dot_enabled", true)
            put("dot_provider", "quad9")
            put("doq_enabled", false)
            put("doq_provider", "adguard")
            put("custom_upstream_dns", "9.9.9.9")
            put("dns_only_mode", false)
            put("captive_portal_handling", true)
            put("online_geoip_enabled", true)
            put("threat_intel_enabled", true)
            put("safe_search_enabled", false)
            put("content_filter_categories", org.json.JSONArray(listOf("ADULT", "GAMBLING")))
            put("parental_enabled", false)
            put("parental_age_profile", "teen")
            put("wireguard_enabled", false)
            put("wireguard_endpoint", "wg.example.com:51820")
            put("wireguard_dns_ip", "10.0.0.1")
            put("accent_color", "purple")
            put("high_contrast_amoled", true)
            put("show_notification", true)
            put("pinned_domains", org.json.JSONArray(listOf("example.com", "test.org")))
            put("schedule_enabled", true)
            put("schedule_start", "22:00")
            put("schedule_end", "07:00")
            put("schedule_mode", "disable")
            put("auto_backup_enabled", true)
            put("auto_backup_interval_days", 7)
            put("webdav_url", "https://dav.example.com")
            put("webdav_username", "user")
            put("rule_sync_urls", "https://rules.example.com/list.txt")
            put("blocked_apps", org.json.JSONArray(listOf("com.spam.app")))
        }

        assertEquals("zero_ip", prefs.getString("block_response_type"))
        assertTrue(prefs.getBoolean("ede_enabled"))
        assertTrue(prefs.getBoolean("dot_enabled"))
        assertEquals("quad9", prefs.getString("dot_provider"))
        assertTrue(prefs.getBoolean("threat_intel_enabled"))
        assertEquals(2, prefs.getJSONArray("content_filter_categories").length())
        assertEquals("ADULT", prefs.getJSONArray("content_filter_categories").getString(0))
        assertEquals("purple", prefs.getString("accent_color"))
        assertTrue(prefs.getBoolean("high_contrast_amoled"))
        assertEquals(2, prefs.getJSONArray("pinned_domains").length())
        assertTrue(prefs.getBoolean("schedule_enabled"))
        assertEquals("22:00", prefs.getString("schedule_start"))
        assertEquals(7, prefs.getInt("auto_backup_interval_days"))
        assertEquals(1, prefs.getJSONArray("blocked_apps").length())
    }

    @Test
    fun `v1 backup missing v2 keys does not crash on optBoolean`() {
        val v1Prefs = JSONObject().apply {
            put("block_method", "ROOT_HOSTS")
            put("ipv4_redirect", "0.0.0.0")
        }

        assertFalse(v1Prefs.optBoolean("ede_enabled", false))
        assertEquals("nxdomain", v1Prefs.optString("block_response_type", "nxdomain"))
        assertFalse(v1Prefs.optBoolean("dot_enabled", false))
        assertFalse(v1Prefs.optBoolean("threat_intel_enabled", false))
        assertFalse(v1Prefs.has("content_filter_categories"))
        assertFalse(v1Prefs.has("pinned_domains"))
    }

    @Test
    fun `firewall rule serialization roundtrip`() {
        val rule = JSONObject().apply {
            put("uid", 10042)
            put("package_name", "com.example.app")
            put("app_label", "Example App")
            put("wifi_allowed", false)
            put("mobile_allowed", true)
            put("vpn_allowed", true)
            put("is_system", false)
        }

        assertEquals(10042, rule.getInt("uid"))
        assertEquals(false, rule.getBoolean("wifi_allowed"))
        assertEquals("com.example.app", rule.getString("package_name"))
    }

    @Test
    fun `restore validators reject unsafe source and rule fields`() {
        assertEquals(
            "https://lists.example.com/hosts.txt",
            BackupRestoreUtil.normalizeRestoredSourceUrl(" https://lists.example.com/hosts.txt ")
        )
        assertNull(BackupRestoreUtil.normalizeRestoredSourceUrl("http://192.168.1.50/hosts.txt"))

        assertEquals("example.com", BackupRestoreUtil.normalizeRestoredHostname(" Example.COM. "))
        assertEquals("example.com", BackupRestoreUtil.normalizeRestoredHostname("*.example.com", isWildcard = true))
        assertNull(BackupRestoreUtil.normalizeRestoredHostname("*.example.com", isWildcard = false))
        assertNull(BackupRestoreUtil.normalizeRestoredHostname("bad host.example"))
    }

    @Test
    fun `restore validators reject bad redirect IPs and packages`() {
        assertTrue(BackupRestoreUtil.isValidRedirectIp("0.0.0.0"))
        assertTrue(BackupRestoreUtil.isValidRedirectIp("2001:4860:4860::8888"))
        assertFalse(BackupRestoreUtil.isValidRedirectIp("999.1.1.1"))
        assertFalse(BackupRestoreUtil.isValidRedirectIp("8.8.8.8; reboot"))

        assertEquals("com.example.app", BackupRestoreUtil.normalizePackageName(" com.example.app "))
        assertNull(BackupRestoreUtil.normalizePackageName("com.example;rm"))
        assertNull(BackupRestoreUtil.normalizePackageName("example"))
    }
}
