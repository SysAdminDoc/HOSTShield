package com.hostshield.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.data.database.HostShieldDatabase
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.model.FirewallRule
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.BlockingPreferences
import com.hostshield.data.preferences.DnsPreferences
import com.hostshield.data.preferences.FirewallPreferences
import com.hostshield.data.preferences.SecureStore
import com.hostshield.data.preferences.SecurityPreferences
import com.hostshield.data.preferences.SyncPreferences
import com.hostshield.data.preferences.UiPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreUtilConnectedTest {
    private lateinit var context: Context
    private lateinit var prefs: AppPreferences
    private lateinit var sourceDb: HostShieldDatabase
    private lateinit var restoreDb: HostShieldDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("hostshield_secure_store_v2", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = appPreferences(context)
        sourceDb = newDatabase()
        restoreDb = newDatabase()
        runBlocking { resetPreferences() }
    }

    @After
    fun tearDown() {
        sourceDb.close()
        restoreDb.close()
        runBlocking { resetPreferences() }
    }

    @Test
    fun backupSchemaV2RoundTripsModernPreferencesAndData() = runBlocking {
        seedDatabase(sourceDb)
        seedModernPreferences()

        val json = backupUtil(sourceDb).createBackup()
        val root = JSONObject(json)
        val exportedPrefs = root.getJSONObject("preferences")

        assertEquals(2, root.getInt("backup_version"))
        assertEquals("zero_ip", exportedPrefs.getString("block_response_type"))
        assertEquals("quad9", exportedPrefs.getString("dot_provider"))
        assertEquals("9.9.9.9,1.1.1.1", exportedPrefs.getString("custom_upstream_dns"))
        assertEquals(
            setOf("adult", "gambling"),
            exportedPrefs.getJSONArray("content_filter_categories").toStringSet(),
        )
        assertEquals(
            setOf("reports.example", "blocked.example"),
            exportedPrefs.getJSONArray("pinned_domains").toStringSet(),
        )
        assertEquals(
            listOf("blocked.example", "reports.example"),
            exportedPrefs.getJSONArray("search_history").toStringList(),
        )

        overwritePreferences()
        val result = backupUtil(restoreDb).restoreBackup(json)

        assertEquals(1, result.sourcesCount)
        assertEquals(1, result.rulesCount)
        assertEquals(1, result.profilesCount)
        assertEquals(1, result.firewallRulesCount)

        val restoredSource = restoreDb.hostSourceDao().getAllSourcesList().single()
        assertEquals("https://lists.example.com/hosts.txt", restoredSource.url)
        assertEquals(SourceCategory.MALWARE, restoredSource.category)

        val restoredRule = restoreDb.userRuleDao().getAllRulesList().single()
        assertEquals("ads.example.com", restoredRule.hostname)
        assertEquals(RuleType.BLOCK, restoredRule.type)
        assertTrue(restoredRule.isWildcard)

        val restoredProfile = restoreDb.profileDao().getAllProfilesList().single()
        assertEquals("Travel", restoredProfile.name)
        assertEquals("08:00", restoredProfile.scheduleStart)

        val restoredFirewallRule = restoreDb.firewallRuleDao().getAllRulesList().single()
        assertEquals(10042, restoredFirewallRule.uid)
        assertEquals("com.example.firewalled", restoredFirewallRule.packageName)
        assertFalse(restoredFirewallRule.wifiAllowed)

        assertEquals(BlockMethod.VPN, prefs.blockMethod.first())
        assertFalse(prefs.dnsTrapEnabled.first())
        assertEquals("zero_ip", prefs.blockResponseType.first())
        assertTrue(prefs.edeEnabled.first())
        assertTrue(prefs.localWebserver.first())
        assertTrue(prefs.dotEnabled.first())
        assertEquals("quad9", prefs.dotProvider.first())
        assertTrue(prefs.doqEnabled.first())
        assertEquals("nextdns", prefs.doqProvider.first())
        assertEquals("9.9.9.9,1.1.1.1", prefs.customUpstreamDns.first())
        assertTrue(prefs.onlineGeoIpEnabled.first())
        assertFalse(prefs.threatIntelEnabled.first())
        assertTrue(prefs.safeSearchEnabled.first())
        assertEquals(setOf("adult", "gambling"), prefs.contentFilterCategories.first())
        assertTrue(prefs.parentalEnabled.first())
        assertEquals("TEEN", prefs.parentalAgeProfile.first())
        assertTrue(prefs.wireGuardEnabled.first())
        assertEquals("wg.example.com:51820", prefs.wireGuardEndpoint.first())
        assertEquals("10.8.0.2", prefs.wireGuardDnsIp.first())
        assertEquals("blue", prefs.accentColor.first())
        assertTrue(prefs.highContrastAmoled.first())
        assertFalse(prefs.showNotification.first())
        assertEquals(setOf("reports.example", "blocked.example"), prefs.pinnedDomains.first())
        assertEquals(listOf("blocked.example", "reports.example"), prefs.searchHistory.first())
        assertTrue(prefs.scheduleEnabled.first())
        assertEquals("08:00", prefs.scheduleStart.first())
        assertEquals("18:30", prefs.scheduleEnd.first())
        assertEquals("unblock", prefs.scheduleMode.first())
        assertTrue(prefs.autoBackupEnabled.first())
        assertEquals(3, prefs.autoBackupIntervalDays.first())
        assertEquals("https://dav.example.com/remote.php/dav/files/hostshield", prefs.webdavUrl.first())
        assertEquals("sync-user", prefs.webdavUsername.first())
        assertEquals("https://rules.example.com/a.txt,https://rules.example.com/b.txt", prefs.ruleSyncUrls.first())
        assertEquals(setOf("com.example.firewalled"), prefs.blockedApps.first())
    }

    private fun newDatabase(): HostShieldDatabase =
        Room.inMemoryDatabaseBuilder(context, HostShieldDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun appPreferences(context: Context): AppPreferences {
        val secureStore = SecureStore(context)
        return AppPreferences(
            context,
            BlockingPreferences(context),
            DnsPreferences(context),
            FirewallPreferences(context),
            SecurityPreferences(context, secureStore),
            UiPreferences(context),
            SyncPreferences(context, secureStore),
        )
    }

    private fun backupUtil(db: HostShieldDatabase): BackupRestoreUtil =
        BackupRestoreUtil(
            db.hostSourceDao(),
            db.userRuleDao(),
            db.profileDao(),
            db.firewallRuleDao(),
            prefs,
        )

    private suspend fun seedDatabase(db: HostShieldDatabase) {
        db.hostSourceDao().insert(
            HostSource(
                url = "https://lists.example.com/hosts.txt",
                label = "Example Malware",
                description = "Roundtrip source",
                enabled = true,
                category = SourceCategory.MALWARE,
                entryCount = 42,
            ),
        )
        db.userRuleDao().insert(
            UserRule(
                hostname = "ads.example.com",
                type = RuleType.BLOCK,
                comment = "Roundtrip rule",
                enabled = true,
                isWildcard = true,
            ),
        )
        db.profileDao().insert(
            BlockingProfile(
                name = "Travel",
                isActive = true,
                sourceIds = "1",
                scheduleStart = "08:00",
                scheduleEnd = "18:30",
                daysOfWeek = "1,2,3,4,5",
            ),
        )
        db.firewallRuleDao().insert(
            FirewallRule(
                uid = 10042,
                packageName = "com.example.firewalled",
                appLabel = "Example Firewall",
                wifiAllowed = false,
                mobileAllowed = true,
                vpnAllowed = false,
                isSystem = false,
                enabled = true,
            ),
        )
    }

    private suspend fun seedModernPreferences() {
        prefs.setBlockMethod(BlockMethod.VPN)
        prefs.setIpv4Redirect("127.0.0.1")
        prefs.setIpv6Redirect("::1")
        prefs.setIncludeIpv6(false)
        prefs.setAutoUpdate(false)
        prefs.setUpdateIntervalHours(12)
        prefs.setWifiOnly(false)
        prefs.setDnsLogging(false)
        prefs.setLogRetentionDays(30)
        prefs.setDohEnabled(true)
        prefs.setDohProvider("adguard")
        prefs.setExcludedApps(setOf("com.example.excluded"))
        prefs.setNetworkFirewallEnabled(true)
        prefs.setFirewallMode("WHITELIST")
        prefs.setAutoApplyFirewall(true)
        prefs.setConnectionLogEnabled(false)
        prefs.setDnsTrapEnabled(false)
        prefs.setBlockResponseType("zero_ip")
        prefs.setEdeEnabled(true)
        prefs.setLocalWebserver(true)
        prefs.setDotEnabled(true)
        prefs.setDotProvider("quad9")
        prefs.setDoqEnabled(true)
        prefs.setDoqProvider("nextdns")
        prefs.setCustomUpstreamDns("9.9.9.9 1.1.1.1")
        prefs.setDnsOnlyMode(true)
        prefs.setCaptivePortalHandling(false)
        prefs.setOnlineGeoIpEnabled(true)
        prefs.setThreatIntelEnabled(false)
        prefs.setSafeSearchEnabled(true)
        prefs.setContentFilterCategories(setOf("adult", "gambling"))
        prefs.setParentalEnabled(true)
        prefs.setParentalAgeProfile("TEEN")
        prefs.setWireGuardEnabled(true)
        prefs.setWireGuardEndpoint("wg.example.com:51820")
        prefs.setWireGuardDnsIp("10.8.0.2")
        prefs.setAccentColor("blue")
        prefs.setHighContrastAmoled(true)
        prefs.setShowNotification(false)
        prefs.setPinnedDomains(setOf("reports.example", "blocked.example"))
        prefs.setSearchHistory(listOf("blocked.example", "reports.example"))
        prefs.setScheduleEnabled(true)
        prefs.setScheduleStart("08:00")
        prefs.setScheduleEnd("18:30")
        prefs.setScheduleMode("unblock")
        prefs.setAutoBackupEnabled(true)
        prefs.setAutoBackupIntervalDays(3)
        prefs.setWebdavUrl("https://dav.example.com/remote.php/dav/files/hostshield")
        prefs.setWebdavUsername("sync-user")
        prefs.setRuleSyncUrls("https://rules.example.com/a.txt,https://rules.example.com/b.txt")
        prefs.setBlockedApps(setOf("com.example.firewalled"))
    }

    private suspend fun overwritePreferences() {
        resetPreferences()
        prefs.setBlockMethod(BlockMethod.ROOT_HOSTS)
        prefs.setBlockResponseType("nxdomain")
        prefs.setDotProvider("cloudflare")
        prefs.setCustomUpstreamDns("8.8.8.8")
        prefs.setContentFilterCategories(setOf("social"))
        prefs.setPinnedDomains(setOf("old.example"))
        prefs.setSearchHistory(listOf("old.example"))
    }

    private suspend fun resetPreferences() {
        prefs.setBlockMethod(BlockMethod.ROOT_HOSTS)
        prefs.setIpv4Redirect("0.0.0.0")
        prefs.setIpv6Redirect("::")
        prefs.setIncludeIpv6(true)
        prefs.setAutoUpdate(true)
        prefs.setUpdateIntervalHours(24)
        prefs.setWifiOnly(true)
        prefs.setDnsLogging(true)
        prefs.setLogRetentionDays(7)
        prefs.setDohEnabled(false)
        prefs.setDohProvider("cloudflare")
        prefs.setExcludedApps(emptySet())
        prefs.setNetworkFirewallEnabled(false)
        prefs.setFirewallMode("BLACKLIST")
        prefs.setAutoApplyFirewall(false)
        prefs.setConnectionLogEnabled(true)
        prefs.setDnsTrapEnabled(true)
        prefs.setBlockResponseType("nxdomain")
        prefs.setEdeEnabled(false)
        prefs.setLocalWebserver(false)
        prefs.setDotEnabled(false)
        prefs.setDotProvider("cloudflare")
        prefs.setDoqEnabled(false)
        prefs.setDoqProvider("adguard")
        prefs.setCustomUpstreamDns("")
        prefs.setDnsOnlyMode(false)
        prefs.setCaptivePortalHandling(true)
        prefs.setOnlineGeoIpEnabled(false)
        prefs.setThreatIntelEnabled(true)
        prefs.setSafeSearchEnabled(false)
        prefs.setContentFilterCategories(emptySet())
        prefs.setParentalEnabled(false)
        prefs.setParentalAgeProfile("ADULT")
        prefs.setWireGuardEnabled(false)
        prefs.setWireGuardEndpoint("")
        prefs.setWireGuardDnsIp("")
        prefs.setAccentColor("teal")
        prefs.setHighContrastAmoled(false)
        prefs.setShowNotification(true)
        prefs.setPinnedDomains(emptySet())
        prefs.clearSearchHistory()
        prefs.setScheduleEnabled(false)
        prefs.setScheduleStart("22:00")
        prefs.setScheduleEnd("07:00")
        prefs.setScheduleMode("block")
        prefs.setAutoBackupEnabled(false)
        prefs.setAutoBackupIntervalDays(7)
        prefs.setWebdavUrl("")
        prefs.setWebdavUsername("")
        prefs.setRuleSyncUrls("")
        prefs.setBlockedApps(emptySet())
    }

    private fun JSONArray.toStringSet(): Set<String> =
        (0 until length()).map { index -> getString(index) }.toSet()

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { index -> getString(index) }
}
