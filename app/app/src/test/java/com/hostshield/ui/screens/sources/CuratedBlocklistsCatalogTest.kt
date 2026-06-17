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

    @Test
    fun `no duplicate URLs within same category`() {
        readCatalogByCategory().forEach { (category, items) ->
            val seen = mutableMapOf<String, String>()
            items.forEach { item ->
                val existing = seen.put(item.url, item.label)
                if (existing != null) {
                    assertTrue("Duplicate URL in $category: ${item.url} in '$existing' and '${item.label}'", false)
                }
            }
        }
    }

    @Test
    fun `all URLs are HTTPS`() {
        readCatalog().forEach { item ->
            assertTrue("${item.label} URL must be HTTPS: ${item.url}", item.url.startsWith("https://"))
        }
    }

    @Test
    fun `no duplicate labels within same category`() {
        readCatalogByCategory().forEach { (category, items) ->
            val labels = items.map { it.label }
            val duplicates = labels.groupBy { it }.filter { it.value.size > 1 }.keys
            assertTrue("Duplicate labels in $category: $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun `every list has non-blank label and entries`() {
        readCatalog().forEach { item ->
            assertFalse("Label must not be blank", item.label.isBlank())
            assertFalse("${item.label}: entries must not be blank", item.entries.isBlank())
            assertFalse("${item.label}: URL must not be blank", item.url.isBlank())
        }
    }

    @Test
    fun `valid categories only`() {
        val validCategories = setOf("ADS", "TRACKERS", "MALWARE", "ADULT", "SOCIAL", "CRYPTO", "ALLOWLIST")
        val file = listOf(
            File("src/main/assets/curated_blocklists.json"),
            File("app/src/main/assets/curated_blocklists.json"),
            File("app/app/src/main/assets/curated_blocklists.json")
        ).first { it.isFile }
        val categories = JSONArray(file.readText())
        for (i in 0 until categories.length()) {
            val cat = categories.getJSONObject(i).getString("category")
            assertTrue("Unknown category: $cat", cat in validCategories)
        }
    }

    private fun catalogFile(): File = listOf(
        File("src/main/assets/curated_blocklists.json"),
        File("app/src/main/assets/curated_blocklists.json"),
        File("app/app/src/main/assets/curated_blocklists.json")
    ).first { it.isFile }

    private fun readCatalog(): List<CatalogItem> =
        readCatalogByCategory().values.flatten()

    private fun readCatalogByCategory(): Map<String, List<CatalogItem>> {
        val categories = JSONArray(catalogFile().readText())
        val result = mutableMapOf<String, List<CatalogItem>>()
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            val catName = category.getString("category")
            val lists = category.getJSONArray("lists")
            val items = mutableListOf<CatalogItem>()
            for (j in 0 until lists.length()) {
                val item = lists.getJSONObject(j)
                items += CatalogItem(
                    label = item.getString("label"),
                    url = item.getString("url"),
                    entries = item.getString("entries"),
                    warning = item.optString("warning")
                )
            }
            result[catName] = items
        }
        return result
    }

    private data class CatalogItem(
        val label: String,
        val url: String,
        val entries: String,
        val warning: String
    )
}
