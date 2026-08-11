package com.hostshield.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hostshield.data.database.HostShieldDatabase
import com.hostshield.data.model.AppDnsRule
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.FirewallRule
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreRoundTripRobolectricTest {
    private lateinit var database: HostShieldDatabase
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HostShieldDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = mockk(relaxed = true)

        every { prefs.blockMethod } returns flowOf(BlockMethod.VPN)
        every { prefs.ipv4Redirect } returns flowOf("0.0.0.0")
        every { prefs.ipv6Redirect } returns flowOf("::")
        every { prefs.includeIpv6 } returns flowOf(true)
        every { prefs.autoUpdate } returns flowOf(true)
        every { prefs.updateIntervalHours } returns flowOf(24)
        every { prefs.wifiOnly } returns flowOf(true)
        every { prefs.dnsLogging } returns flowOf(true)
        every { prefs.logRetentionDays } returns flowOf(7)
        every { prefs.dohEnabled } returns flowOf(true)
        every { prefs.dohProvider } returns flowOf("cloudflare")
        every { prefs.excludedApps } returns flowOf(setOf("com.example.excluded"))
        every { prefs.networkFirewallEnabled } returns flowOf(true)
        every { prefs.firewallMode } returns flowOf("BLACKLIST")
        every { prefs.autoApplyFirewall } returns flowOf(false)
        every { prefs.connectionLogEnabled } returns flowOf(true)
        every { prefs.dnsTrapEnabled } returns flowOf(true)
        every { prefs.blockResponseType } returns flowOf("nxdomain")
        every { prefs.edeEnabled } returns flowOf(false)
        every { prefs.localWebserver } returns flowOf(false)
        every { prefs.dotEnabled } returns flowOf(true)
        every { prefs.dotProvider } returns flowOf("quad9")
        every { prefs.doqEnabled } returns flowOf(false)
        every { prefs.doqProvider } returns flowOf("adguard")
        every { prefs.customUpstreamDns } returns flowOf("9.9.9.9")
        every { prefs.dnsOnlyMode } returns flowOf(false)
        every { prefs.captivePortalHandling } returns flowOf(true)
        every { prefs.onlineGeoIpEnabled } returns flowOf(false)
        every { prefs.threatIntelEnabled } returns flowOf(true)
        every { prefs.safeSearchEnabled } returns flowOf(true)
        every { prefs.contentFilterCategories } returns flowOf(setOf("ADULT"))
        every { prefs.parentalEnabled } returns flowOf(true)
        every { prefs.parentalAgeProfile } returns flowOf("TEEN")
        every { prefs.wireGuardEnabled } returns flowOf(false)
        every { prefs.wireGuardDnsIp } returns flowOf("10.0.0.1")
        every { prefs.accentColor } returns flowOf("purple")
        every { prefs.highContrastAmoled } returns flowOf(false)
        every { prefs.dynamicColor } returns flowOf(true)
        every { prefs.themeMode } returns flowOf("dark")
        every { prefs.showNotification } returns flowOf(true)
        every { prefs.pinnedDomains } returns flowOf(setOf("pinned.example"))
        every { prefs.searchHistory } returns flowOf(listOf("ads.example"))
        every { prefs.scheduleEnabled } returns flowOf(true)
        every { prefs.scheduleStart } returns flowOf("22:00")
        every { prefs.scheduleEnd } returns flowOf("07:00")
        every { prefs.scheduleMode } returns flowOf("block")
        every { prefs.autoBackupEnabled } returns flowOf(false)
        every { prefs.autoBackupIntervalDays } returns flowOf(7)
        every { prefs.webdavUrl } returns flowOf("https://dav.example.com/hostshield")
        every { prefs.webdavUsername } returns flowOf("backup-user")
        every { prefs.ruleSyncUrls } returns flowOf("https://rules.example.com/list.txt")
        every { prefs.blockedApps } returns flowOf(setOf("com.example.blocked"))
        every { prefs.lanDnsEnabled } returns flowOf(true)
        every { prefs.lanDnsPort } returns flowOf(5354)
        every { prefs.lanDnsAllowExternalClients } returns flowOf(false)
        every { prefs.wireGuardPublicKey } returns flowOf("peer-public-key")
        every { prefs.wireGuardEndpoint } returns flowOf("wg.example.com:51820")
        every { prefs.wireGuardPrivateKey } returns flowOf("private-key")
        every { prefs.wireGuardPresharedKey } returns flowOf("preshared-key")
        every { prefs.webdavPassword } returns flowOf("webdav-password")
        every { prefs.parentalPinHash } returns flowOf("pin-hash")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `schema v2 creates encrypted-only secrets and restores real Room data`() = runBlocking {
        database.hostSourceDao().insert(
            HostSource(
                url = "https://lists.example.com/hosts.txt",
                label = "Example list"
            )
        )
        database.userRuleDao().insert(
            com.hostshield.data.model.UserRule(
                hostname = "*.ads.example",
                type = RuleType.BLOCK,
                isWildcard = true
            )
        )
        database.profileDao().insert(
            BlockingProfile(
                name = "Work",
                isActive = true,
                sourceIds = "1",
                scheduleStart = "09:00",
                scheduleEnd = "17:00",
                daysOfWeek = "1,2,3,4,5",
                wifiSsids = "Office"
            )
        )
        database.firewallRuleDao().insert(
            FirewallRule(uid = 10042, packageName = "com.example.app", appLabel = "Example")
        )
        database.appDnsRuleDao().insert(
            AppDnsRule(packageName = "com.example.app", domain = "*.ads.example", action = "block")
        )

        val util = BackupRestoreUtil(
            database,
            database.hostSourceDao(),
            database.userRuleDao(),
            database.profileDao(),
            database.firewallRuleDao(),
            database.appDnsRuleDao(),
            prefs
        )

        val plaintext = JSONObject(util.createBackup())
        assertEquals(BackupRestoreUtil.BACKUP_SCHEMA_VERSION, plaintext.getInt("backup_version"))
        assertFalse(plaintext.has("encrypted_secrets"))
        assertFalse(plaintext.getJSONObject("preferences").has("wireguard_endpoint"))
        assertEquals(5354, plaintext.getJSONObject("preferences").getInt("lan_dns_port"))

        val encryptedJson = util.createBackup(includeSecrets = true)
        val encryptedRoot = JSONObject(encryptedJson)
        assertEquals("wg.example.com:51820", encryptedRoot.getJSONObject("encrypted_secrets").getString("wireguard_endpoint"))
        assertEquals("private-key", encryptedRoot.getJSONObject("encrypted_secrets").getString("wireguard_private_key"))
        assertEquals("peer-public-key", encryptedRoot.getJSONObject("preferences").getString("wireguard_public_key"))

        val restorePrefs = mockk<AppPreferences>(relaxed = true)
        val restoreUtil = BackupRestoreUtil(
            database,
            database.hostSourceDao(),
            database.userRuleDao(),
            database.profileDao(),
            database.firewallRuleDao(),
            database.appDnsRuleDao(),
            restorePrefs
        )
        database.clearAllTables()

        val result = restoreUtil.restoreBackup(encryptedJson, allowSecrets = true)
        assertEquals(1, result.sourcesCount)
        assertEquals(1, result.rulesCount)
        assertEquals(1, result.profilesCount)
        assertEquals(1, result.firewallRulesCount)
        assertEquals(1, database.hostSourceDao().getAllSourcesList().size)
        assertEquals("*.ads.example", database.userRuleDao().getAllRulesList().single().hostname)
        assertEquals("Office", database.profileDao().getAllProfilesList().single().wifiSsids)
        assertEquals("*.ads.example", database.appDnsRuleDao().getAllRulesList().single().domain)
        coVerify { restorePrefs.setLanDnsPort(5354) }
        coVerify { restorePrefs.setWireGuardEndpoint("wg.example.com:51820") }
        coVerify { restorePrefs.setWireGuardPrivateKey("private-key") }
        coVerify { restorePrefs.setWebdavPassword("webdav-password") }
        coVerify { restorePrefs.setParentalPinHash("pin-hash") }
    }

    // Regression: profile restore deduped by name and skipped the insert, but had
    // already run deactivateAll() — so restoring your own backup, where the active
    // profile's name already exists, left the device with zero active profiles and
    // silently dropped per-profile source narrowing.
    @Test
    fun `restoring a backup whose active profile name already exists keeps it active`() = runBlocking {
        database.profileDao().insert(
            BlockingProfile(name = "Night", isActive = true, sourceIds = "1")
        )
        val util = BackupRestoreUtil(
            database,
            database.hostSourceDao(),
            database.userRuleDao(),
            database.profileDao(),
            database.firewallRuleDao(),
            database.appDnsRuleDao(),
            prefs
        )
        val backup = util.createBackup()

        // Restore onto the same install — the name collides with the existing row.
        val result = util.restoreBackup(backup)

        val active = database.profileDao().getActiveProfile()
        assertNotNull("an active profile must survive restore", active)
        assertEquals("Night", active!!.name)
        assertEquals(0, result.profilesCount) // deduped, not re-inserted
        assertEquals(1, database.profileDao().getAllProfilesList().size)
    }

    @Test
    fun `restoring a backup with no active profile leaves the current one active`() = runBlocking {
        database.profileDao().insert(
            BlockingProfile(name = "Existing", isActive = true)
        )
        val util = BackupRestoreUtil(
            database,
            database.hostSourceDao(),
            database.userRuleDao(),
            database.profileDao(),
            database.firewallRuleDao(),
            database.appDnsRuleDao(),
            prefs
        )
        val backup = JSONObject(util.createBackup()).apply {
            put("profiles", org.json.JSONArray().put(
                JSONObject()
                    .put("name", "Imported")
                    .put("is_active", false)
            ))
        }.toString()

        util.restoreBackup(backup)

        val active = database.profileDao().getActiveProfile()
        assertNotNull(active)
        assertEquals("Existing", active!!.name)
    }
}
