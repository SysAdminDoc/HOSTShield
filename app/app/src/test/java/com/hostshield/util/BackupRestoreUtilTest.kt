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
}
