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
            "HaGeZi Light (Multi-Light)" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-Light.txt",
            "HaGeZi Multi Normal" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-Multi.txt",
            "HaGeZi Multi Pro" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-Pro.txt",
            "HaGeZi Multi Pro++" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-ProPlus.txt",
            "HaGeZi Multi Ultimate" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-Ultimate.txt",
            "HaGeZi Threat Intelligence" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-TIF.txt",
            "HaGeZi TIF Mini" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-TIF-Mini.txt",
            "HaGeZi DynDNS" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-DynDNS.txt",
            "HaGeZi Most Abused TLDs" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-SpamTLDs.txt"
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
    fun `hagezi catalog URLs point to non-empty HostShield mirrors`() {
        val mirrorRoot = listOf(
            File("blocklists"),
            File("../blocklists"),
            File("../../blocklists")
        ).first { it.isDirectory }

        readCatalog()
            .filter { it.url.contains("/blocklists/HaGeZi-") }
            .forEach { item ->
                val file = File(mirrorRoot, item.url.substringAfterLast('/'))
                assertTrue("${item.label} mirror is missing: ${file.path}", file.isFile)
                assertTrue("${item.label} mirror is empty", file.length() > 0L)
            }
    }

    @Test
    fun `repaired gallery sources use verified primary or HostShield URLs`() {
        val byLabel = readCatalog().associateBy { it.label }
        val expected = mapOf(
            "1Hosts Lite" to "https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/hosts.txt",
            "NextDNS CNAME Cloaking" to "https://raw.githubusercontent.com/nextdns/cname-cloaking-blocklist/master/domains",
            "Perflyst Smart TV Tracking" to "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/SmartTV.txt",
            "Windows Spy Blocker" to "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt",
            "Stamparm Malware" to "https://raw.githubusercontent.com/stamparm/blackbook/master/blackbook.txt",
            "NoCoin Cryptojacking" to "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt",
            "HaGeZi Gambling" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-Gambling.txt",
            "HostShield Facebook Domains" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/Facebook.txt",
            "HostShield TikTok Domains" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/Tiktok.txt"
        )

        expected.forEach { (label, url) ->
            assertEquals(url, byLabel.getValue(label).url)
        }
    }

    @Test
    fun `retired or invalid gallery sources are absent`() {
        val labels = readCatalog().map { it.label }.toSet()
        setOf(
            "1Hosts Pro",
            "DuckDuckGo Tracker Radar",
            "HaGeZi Newly Registered Domains (7d)",
            "Sinfool Pornhosts",
            "ZeroDot1 CoinBlocker"
        ).forEach { removed ->
            assertFalse("$removed should not be offered", removed in labels)
        }
    }

    @Test
    fun `subscribed allowlist gallery uses current unbreak URLs`() {
        val byLabel = readCatalog().associateBy { it.label }

        val expected = mapOf(
            "Anudeep's Whitelist" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/whitelist.txt",
            "Anudeep's Optional Whitelist" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/optional-list.txt",
            "Anudeep's Referral Sites" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/referral-sites.txt",
            "HaGeZi Referral Allowlist" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-ReferralAllowlist.txt",
            "HaGeZi Most Abused TLD Allowlist" to "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-SpamTLDsAllow.txt"
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
    fun `every list has license homepage and last_reviewed`() {
        readCatalog().forEach { item ->
            assertFalse("${item.label}: license must not be blank", item.license.isBlank())
            assertFalse("${item.label}: homepage must not be blank", item.homepage.isBlank())
            assertTrue("${item.label}: homepage must be HTTPS", item.homepage.startsWith("https://"))
            assertFalse("${item.label}: last_reviewed must not be blank", item.lastReviewed.isBlank())
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
                    warning = item.optString("warning"),
                    license = item.optString("license"),
                    homepage = item.optString("homepage"),
                    lastReviewed = item.optString("last_reviewed")
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
        val warning: String,
        val license: String,
        val homepage: String,
        val lastReviewed: String
    )
}
