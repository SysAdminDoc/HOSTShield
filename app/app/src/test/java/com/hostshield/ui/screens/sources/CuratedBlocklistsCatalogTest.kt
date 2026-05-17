package com.hostshield.ui.screens.sources

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CuratedBlocklistsCatalogTest {

    @Test
    fun `hagezi pack chooser has expected URLs sizes and warnings`() {
        val lists = readCatalog()
        val byLabel = lists.associateBy { it.label }

        val expected = mapOf(
            "HaGeZi Light (Multi-Light)" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/light.txt",
            "HaGeZi Multi Normal" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/multi.txt",
            "HaGeZi Multi Pro" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt",
            "HaGeZi Multi Pro++" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.plus.txt",
            "HaGeZi Multi Ultimate" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/ultimate.txt",
            "HaGeZi Threat Intelligence" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/tif.txt",
            "HaGeZi TIF Mini" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/tif.mini.txt",
            "HaGeZi DynDNS" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/dyndns.txt",
            "HaGeZi Newly Registered Domains (7d)" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/nrd7.txt",
            "HaGeZi Most Abused TLDs" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/spam-tlds.txt"
        )

        expected.forEach { (label, url) ->
            val item = byLabel.getValue(label)
            assertEquals(url, item.url)
            assertFalse("$label should show expected size", item.entries.isBlank())
            assertFalse("$label should explain breakage risk", item.warning.isBlank())
        }

        assertTrue(byLabel.getValue("HaGeZi Light (Multi-Light)").warning.contains("Lowest breakage"))
        assertTrue(byLabel.getValue("HaGeZi Multi Ultimate").warning.contains("High breakage"))
        assertTrue(byLabel.getValue("HaGeZi Most Abused TLDs").warning.contains("whole TLD"))
    }

    @Test
    fun `subscribed allowlist gallery uses current unbreak URLs`() {
        val byLabel = readCatalog().associateBy { it.label }

        val expected = mapOf(
            "Anudeep's Whitelist" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/whitelist.txt",
            "Anudeep's Optional Whitelist" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/optional-list.txt",
            "Anudeep's Referral Sites" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/referral-sites.txt",
            "HaGeZi Referral Allowlist" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/whitelist-referral-native.txt",
            "HaGeZi Most Abused TLD Allowlist" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/spam-tlds-adblock-allow.txt"
        )

        expected.forEach { (label, url) ->
            val item = byLabel.getValue(label)
            assertEquals(url, item.url)
            assertFalse("$label should explain override behavior", item.warning.isBlank())
        }
    }

    private fun readCatalog(): List<CatalogItem> {
        val file = listOf(
            File("src/main/assets/curated_blocklists.json"),
            File("app/src/main/assets/curated_blocklists.json"),
            File("app/app/src/main/assets/curated_blocklists.json")
        ).first { it.isFile }

        val categories = JSONArray(file.readText())
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            val lists = category.getJSONArray("lists")
            for (j in 0 until lists.length()) {
                val item = lists.getJSONObject(j)
                items += CatalogItem(
                    label = item.getString("label"),
                    url = item.getString("url"),
                    entries = item.getString("entries"),
                    warning = item.optString("warning")
                )
            }
        }
        return items
    }

    private data class CatalogItem(
        val label: String,
        val url: String,
        val entries: String,
        val warning: String
    )
}
