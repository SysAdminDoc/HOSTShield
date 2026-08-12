package com.hostshield.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Text
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.core.content.FileProvider
import com.hostshield.BuildConfig
import com.hostshield.MainActivity
import com.hostshield.data.database.HostShieldDatabase
import com.hostshield.data.database.Migrations
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.BlockingPreferences
import com.hostshield.data.preferences.DnsPreferences
import com.hostshield.data.preferences.FirewallPreferences
import com.hostshield.data.preferences.SecureStore
import com.hostshield.data.preferences.SecurityPreferences
import com.hostshield.data.preferences.SyncPreferences
import com.hostshield.data.preferences.UiPreferences
import com.hostshield.data.preferences.hostShieldDataStore
import com.hostshield.ui.navigation.Screen
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.onboarding.OnboardingDns
import com.hostshield.ui.screens.onboarding.OnboardingScreen
import com.hostshield.ui.theme.HostShieldTheme
import com.hostshield.ui.theme.Blue
import com.hostshield.ui.theme.Red
import com.hostshield.ui.theme.Surface0
import com.hostshield.util.PrivateDnsDetector
import com.hostshield.util.BackupRestoreUtil
import com.hostshield.util.EncryptedBackupException
import com.hostshield.service.DnsVpnService
import com.hostshield.service.BlockingScheduleWorker
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile
import javax.crypto.AEADBadTagException

@RunWith(AndroidJUnit4::class)
class TopFlowComposeTest {

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var componentScenario: ActivityScenario<ComponentActivity>? = null
    private var originalAnimatorScale: String? = null

    @Before
    fun prepareMainAppState() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (originalAnimatorScale == null) {
            originalAnimatorScale = device.executeShellCommand("settings get global animator_duration_scale").trim()
        }
        device.executeShellCommand("settings put global animator_duration_scale 0")
        BlockingScheduleWorker.cancel(context)
        val scheduleStart = LocalTime.now().plusHours(2)
        val scheduleEnd = LocalTime.now().plusHours(3)
        runBlocking {
            context.hostShieldDataStore.edit { prefs ->
                prefs[booleanPreferencesKey("first_launch")] = false
                prefs[stringPreferencesKey("block_method")] = BlockMethod.VPN.name
                prefs[booleanPreferencesKey("is_enabled")] = false
                prefs[booleanPreferencesKey("schedule_enabled")] = false
                prefs[booleanPreferencesKey("parental_enabled")] = false
                prefs[stringPreferencesKey("parental_pin_hash")] = ""
                prefs[stringPreferencesKey("parental_age_profile")] = "ADULT"
                prefs[stringPreferencesKey("schedule_start")] = scheduleStart.format(DateTimeFormatter.ofPattern("HH:mm"))
                prefs[stringPreferencesKey("schedule_end")] = scheduleEnd.format(DateTimeFormatter.ofPattern("HH:mm"))
            }
        }
        stopVpnAndWait()
        BlockingScheduleWorker.cancel(context)
    }

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
        componentScenario?.close()
        componentScenario = null
        stopVpnAndWait()
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "diagnostics").deleteRecursively()
        originalAnimatorScale?.let { scale ->
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("settings put global animator_duration_scale ${if (scale == "null") "1" else scale}")
            originalAnimatorScale = null
        }
    }

    @Test
    fun onboardingCoversFirstLaunchPrivateDnsAndDeferredVpnActivation() {
        var completedMethod: BlockMethod? = null
        var completedAutoEnable: Boolean? = null
        var completedDns: OnboardingDns? = null

        componentScenario = ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HostShieldTheme {
                        OnboardingScreen(
                            isRootAvailable = false,
                            privateDnsStatus = PrivateDnsDetector.PrivateDnsStatus(
                                mode = PrivateDnsDetector.PrivateDnsMode.STRICT,
                                hostname = "dns.example.test",
                            ),
                            onComplete = { method, autoEnable, dnsChoice ->
                                completedMethod = method
                                completedAutoEnable = autoEnable
                                completedDns = dnsChoice
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        waitForText("HostShield")
        compose.onNodeWithText("Get started").performClick()

        waitForText("Choose protection mode")
        compose.onNodeWithText("VPN Mode").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()

        waitForText("Protection at a glance")
        compose.onNodeWithText("Continue").performClick()

        waitForText("Choose DNS resolver")
        compose.onNodeWithText("Cloudflare").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()

        waitForText("Private DNS Detected")
        compose.onNodeWithText("I understand, continue").performClick()

        waitForText("Ready to Go")
        compose.onNodeWithText("Set up later").performClick()

        compose.waitUntil(5_000) { completedMethod != null }
        assertEquals(BlockMethod.VPN, completedMethod)
        assertEquals(false, completedAutoEnable)
        assertEquals(OnboardingDns.CLOUDFLARE, completedDns)
    }

    @Test
    fun mainAppExposesVpnScheduleBackupAndDiagnosticAffordances() {
        launchMainApp()

        waitForTag(HostShieldTestTags.Nav.route(Screen.Home.route))
        waitForTag(HostShieldTestTags.Home.ShieldOrb)
        compose.onNodeWithTag(HostShieldTestTags.Home.ShieldOrb, useUnmergedTree = true)
            .assertIsDisplayed()

        compose.onNodeWithTag(HostShieldTestTags.Nav.route(Screen.Settings.route), useUnmergedTree = true)
            .performClick()
        waitForTag(HostShieldTestTags.Settings.toggle("Scheduled blocking"))

        compose.onNodeWithTag(HostShieldTestTags.Settings.toggle("Scheduled blocking"), useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitForText("Blocking is active", substring = true)

        listOf(
            "Create backup",
            "Restore backup",
            "Generate diagnostic package",
            "Export CSV",
            "Parental controls",
        ).forEach { label ->
            val tag = if (label == "Export CSV") {
                null
            } else {
                HostShieldTestTags.Settings.row(label)
            }
            if (tag != null) {
                compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            } else {
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun shieldOrbStartsAndStopsTheRealVpnOnApi37() {
        launchMainApp()
        ensureVpnConsent()
        waitForTag(HostShieldTestTags.Home.ShieldOrb)
        compose.onNodeWithTag(HostShieldTestTags.Home.ShieldOrb, useUnmergedTree = true)
            .performClick()

        waitForVpnState(expected = true, timeoutMillis = 30_000)

        // Once the real VPN network is up, use the production stop action
        // directly. Compose intentionally keeps a decorative active-state
        // frame loop alive, so querying the tree during that state would make
        // the Espresso/Compose idling bridge wait forever.
        context.startService(Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_STOP
        })
        waitForVpnState(expected = false, timeoutMillis = 20_000)
    }

    @Test
    fun backupFilesRoundTripAndEncryptedPayloadsRequireThePassphrase() = runBlocking {
        withBackupUtil { util ->
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val plainFile = File(exportDir, "top-flow-plain-${System.nanoTime()}.json")
            val encryptedFile = File(exportDir, "top-flow-encrypted-${System.nanoTime()}.hsbackup")
            try {
                val plainJson = util.createBackup()
                val plainUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    plainFile,
                )
                util.writeBackupToUri(context, plainUri, plainJson)
                val plainPayload = util.readBackupPayloadFromUri(context, plainUri)
                assertFalse(plainPayload.encrypted)
                assertEquals(plainJson, plainPayload.json)
                assertEquals(2, JSONObject(plainPayload.json).getInt("backup_version"))
                assertEquals(0, util.restoreBackup(plainPayload.json, allowSecrets = plainPayload.encrypted).sourcesCount)

                val encryptedJson = util.createBackup(includeSecrets = true)
                val encryptedUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    encryptedFile,
                )
                util.writeBackupToUri(context, encryptedUri, encryptedJson, "top-flow-passphrase")

                var missingPassphraseRejected = false
                try {
                    util.readBackupPayloadFromUri(context, encryptedUri)
                } catch (_: EncryptedBackupException) {
                    missingPassphraseRejected = true
                }
                assertTrue("Encrypted backup must not be read without a passphrase", missingPassphraseRejected)

                var wrongPassphraseRejected = false
                try {
                    util.readBackupPayloadFromUri(context, encryptedUri, "wrong-passphrase")
                } catch (_: AEADBadTagException) {
                    wrongPassphraseRejected = true
                }
                assertTrue("Encrypted backup must reject an incorrect passphrase", wrongPassphraseRejected)

                val encryptedPayload = util.readBackupPayloadFromUri(
                    context,
                    encryptedUri,
                    "top-flow-passphrase",
                )
                assertTrue(encryptedPayload.encrypted)
                assertTrue(JSONObject(encryptedPayload.json).has("encrypted_secrets"))
                assertEquals(0, util.restoreBackup(encryptedPayload.json, allowSecrets = encryptedPayload.encrypted).sourcesCount)
            } finally {
                plainFile.delete()
                encryptedFile.delete()
            }
        }
    }

    @Test
    fun settingsGeneratesDiagnosticZipAndPcapFromProductionState() {
        val suffix = System.currentTimeMillis().toString()
        runBlocking { seedDnsLogs("pcap-$suffix.example.test", "allowed-$suffix.example.test") }

        launchMainApp("settings")
        waitForTag(HostShieldTestTags.Settings.row("Generate diagnostic package"))
        compose.onNodeWithTag(
            HostShieldTestTags.Settings.row("Generate diagnostic package"),
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        waitForText("Diagnostic package ready:", substring = true, timeoutMillis = 30_000)

        val diagnostic = File(context.cacheDir, "diagnostics")
            .listFiles()
            ?.filter { it.name.endsWith(".zip") }
            ?.maxByOrNull { it.lastModified() }
        assertTrue("The diagnostic state must point to a generated ZIP", diagnostic?.isFile == true)
        ZipFile(diagnostic!!).use { zip ->
            assertTrue(zip.getEntry("hostshield-diagnostic.txt") != null)
            assertTrue(zip.getEntry("diagnostic-events.jsonl") != null)
            assertTrue(zip.getEntry("manifest.json") != null)
        }

        waitForText("All PCAP")
        compose.onNodeWithText("All PCAP", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitForText("export ready:", substring = true, timeoutMillis = 30_000)

        val pcap = File(context.cacheDir, "exports")
            .listFiles()
            ?.filter { it.name.endsWith(".pcap") }
            ?.maxByOrNull { it.lastModified() }
        assertTrue("The PCAP state must point to a generated capture", pcap?.isFile == true)
        assertTrue("Generated PCAP must contain its global header", pcap!!.length() > 24L)
    }

    @Test
    fun settingsPreferencesSurviveActivityRecreation() {
        launchMainApp("settings")
        waitForTag(HostShieldTestTags.Settings.toggle("Scheduled blocking"))
        compose.onNodeWithTag(
            HostShieldTestTags.Settings.toggle("Scheduled blocking"),
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        waitForText("Blocking is active", substring = true)

        scenario!!.recreate()
        compose.waitForIdle()
        waitForTag(HostShieldTestTags.Settings.toggle("Scheduled blocking"))
        waitForText("Blocking is active", substring = true)
    }

    @Test
    fun distributionFlavorKeepsPackageVisibilityContract() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()
        val isPlayFlavor = BuildConfig.FLAVOR == "play"
        assertEquals(
            "QUERY_ALL_PACKAGES must only be present in the full distribution",
            !isPlayFlavor,
            "android.permission.QUERY_ALL_PACKAGES" in requestedPermissions,
        )

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val visibleLaunchers = context.packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL,
        )
        assertTrue("The flavor must retain visibility of launcher apps", visibleLaunchers.isNotEmpty())
        assertTrue(
            "The app must always be able to resolve its own launcher activity",
            visibleLaunchers.any { it.activityInfo.packageName == context.packageName },
        )
    }

    @Test
    fun topSurfacesRemainReadableUnderRtlAndPseudoExpandedCopy() {
        val title = pseudo("Protection modules")
        val subtitle = pseudo("Newest resolver decisions and export destinations")
        val warning = pseudo("Contains DNS hostnames and connection destinations")

        componentScenario = ActivityScenario.launch(ComponentActivity::class.java).also { launched ->
            launched.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                        LocalDensity provides Density(density = 1f, fontScale = 1.35f),
                    ) {
                        HostShieldTheme {
                            Column(
                                modifier = androidx.compose.ui.Modifier
                                    .width(320.dp)
                                    .background(Surface0)
                                    .padding(12.dp),
                            ) {
                                HostShieldPanelHeader(
                                    icon = Icons.Filled.Dns,
                                    title = title,
                                    subtitle = subtitle,
                                    accent = Blue,
                                )
                                HostShieldStatusBanner(
                                    icon = Icons.Filled.BugReport,
                                    title = pseudo("Privacy warning"),
                                    message = warning,
                                    accent = Red,
                                    announce = false,
                                )
                                Text(pseudo("Create backup"))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(subtitle).assertIsDisplayed()
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.onNodeWithText(pseudo("Create backup")).assertIsDisplayed()
    }

    @Test
    fun sourceAddAndRemoveFlowUsesProductionScreen() {
        launchMainApp()
        compose.onNodeWithTag(HostShieldTestTags.Nav.route(Screen.Sources.route), useUnmergedTree = true)
            .performClick()
        waitForTag(HostShieldTestTags.Sources.AddButton)

        val suffix = System.currentTimeMillis().toString()
        val label = "UI Test Source $suffix"
        val url = "https://ui-test-$suffix.example.test/hosts.txt"

        compose.onNodeWithTag(HostShieldTestTags.Sources.AddButton, useUnmergedTree = true)
            .performClick()
        waitForTag(HostShieldTestTags.Sources.NameField)
        compose.onNodeWithTag(HostShieldTestTags.Sources.NameField, useUnmergedTree = true)
            .performTextInput(label)
        compose.onNodeWithTag(HostShieldTestTags.Sources.UrlField, useUnmergedTree = true)
            .performTextInput(url)
        compose.onNodeWithTag(HostShieldTestTags.Sources.ConfirmAddButton, useUnmergedTree = true)
            .performClick()

        waitForTag(HostShieldTestTags.Sources.SearchField)
        compose.onNodeWithTag(HostShieldTestTags.Sources.SearchField, useUnmergedTree = true)
            .performTextInput(label)
        waitForText(label)
        compose.onNodeWithContentDescription("Delete $label", useUnmergedTree = true)
            .performClick()
        waitForText("Delete source?")
        compose.onNodeWithText("Delete source").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Delete $label", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun ruleAddAndRemoveFlowUsesProductionScreen() {
        launchMainApp()
        compose.onNodeWithTag(HostShieldTestTags.Nav.route(Screen.Rules.route), useUnmergedTree = true)
            .performClick()
        waitForTag(HostShieldTestTags.Rules.AddButton)

        val hostname = "uitest-${System.currentTimeMillis()}.example.test"

        compose.onNodeWithTag(HostShieldTestTags.Rules.AddButton, useUnmergedTree = true)
            .performClick()
        waitForTag(HostShieldTestTags.Rules.HostnameField)
        compose.onNodeWithTag(HostShieldTestTags.Rules.HostnameField, useUnmergedTree = true)
            .performTextInput(hostname)
        compose.onNodeWithTag(HostShieldTestTags.Rules.ConfirmAddButton, useUnmergedTree = true)
            .performClick()

        waitForText(hostname)
        compose.onNodeWithContentDescription("Delete $hostname", useUnmergedTree = true)
            .performClick()
        waitForText("Delete rule?")
        compose.onNodeWithText("Delete rule").performClick()
        waitForTextGone(hostname)
    }

    @Test
    fun queryLogFilteringFlowUsesSeededDnsRows() {
        val suffix = System.currentTimeMillis().toString()
        val blockedHost = "blocked-$suffix.example.test"
        val allowedHost = "allowed-$suffix.example.test"
        runBlocking {
            seedDnsLogs(blockedHost, allowedHost)
        }

        launchMainApp("logs")
        waitForText("DNS Logs")
        waitForTag(HostShieldTestTags.Logs.SearchField)

        compose.onNodeWithTag(HostShieldTestTags.Logs.SearchField, useUnmergedTree = true)
            .performTextInput("blocked-$suffix")
        waitForText(blockedHost)
        waitForTextGone(allowedHost)

        compose.onNodeWithTag(HostShieldTestTags.Logs.SearchField, useUnmergedTree = true)
            .performTextClearance()
        compose.onNodeWithTag(HostShieldTestTags.Logs.SearchField, useUnmergedTree = true)
            .performTextInput("allowed-$suffix")
        compose.onNodeWithText("Allowed").performClick()
        waitForText(allowedHost)
        compose.onNodeWithText("Blocked").performClick()
        waitForTextGone(allowedHost)
    }

    @Test
    fun parentalPinSetWrongPinAndLockoutFlow() {
        launchMainApp("settings")
        waitForTag(HostShieldTestTags.Settings.row("Parental controls"))
        compose.onNodeWithTag(HostShieldTestTags.Settings.row("Parental controls"), useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        waitForText("Parental controls")
        compose.onNodeWithTag(HostShieldTestTags.Parental.EnableToggle, useUnmergedTree = true)
            .performClick()
        waitForText("PIN lock")
        compose.onNodeWithTag(HostShieldTestTags.Parental.PinField, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput("1234")
        compose.onNodeWithTag(HostShieldTestTags.Parental.SetPinButton, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitForText("PIN set successfully", timeoutMillis = 20_000)

        compose.onNodeWithTag(HostShieldTestTags.Parental.EnableToggle, useUnmergedTree = true)
            .performClick()
        waitForText("Enter PIN")

        repeat(5) { attempt ->
            compose.onNodeWithTag(HostShieldTestTags.Parental.DialogPinField, useUnmergedTree = true)
                .performTextInput("0000")
            compose.onNodeWithTag(HostShieldTestTags.Parental.DialogConfirmButton, useUnmergedTree = true)
                .performClick()
            if (attempt < 4) {
                waitForText("Incorrect PIN", timeoutMillis = 20_000)
                Thread.sleep(300)
            }
        }
        waitForText("Locked out", substring = true, timeoutMillis = 20_000)
    }

    private fun launchMainApp(deepLinkHost: String? = null) {
        scenario?.close()
        val intent = Intent(context, MainActivity::class.java).apply {
            if (deepLinkHost != null) {
                action = Intent.ACTION_VIEW
                data = Uri.parse("hostshield://$deepLinkHost")
            }
        }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    private suspend fun withBackupUtil(block: suspend (BackupRestoreUtil) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, HostShieldDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = AppPreferences(
                context,
                BlockingPreferences(context),
                DnsPreferences(context),
                FirewallPreferences(context),
                SecurityPreferences(context, SecureStore(context)),
                UiPreferences(context),
                SyncPreferences(context, SecureStore(context)),
            )
            block(
                BackupRestoreUtil(
                    db,
                    db.hostSourceDao(),
                    db.userRuleDao(),
                    db.profileDao(),
                    db.firewallRuleDao(),
                    db.appDnsRuleDao(),
                    prefs,
                )
            )
        } finally {
            db.close()
        }
    }

    private suspend fun seedDnsLogs(blockedHost: String, allowedHost: String) {
        val db = Room.databaseBuilder(context, HostShieldDatabase::class.java, "hostshield.db")
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            db.userRuleDao().insert(UserRule(hostname = blockedHost, type = RuleType.BLOCK))
            db.dnsLogDao().insertAll(
                listOf(
                    DnsLogEntry(
                        hostname = blockedHost,
                        blocked = true,
                        appPackage = "com.hostshield.test.blocked",
                        appLabel = "Blocked UI Test",
                        timestamp = System.currentTimeMillis(),
                    ),
                    DnsLogEntry(
                        hostname = allowedHost,
                        blocked = false,
                        appPackage = "com.hostshield.test.allowed",
                        appLabel = "Allowed UI Test",
                        timestamp = System.currentTimeMillis() - 1_000L,
                    ),
                ),
            )
        } finally {
            db.close()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long = 10_000,
    ) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTextGone(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long = 10_000,
    ) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun hostShieldVpnActive(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun waitForVpnState(expected: Boolean, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && hostShieldVpnActive() != expected) {
            Thread.sleep(200)
        }
        assertEquals("VPN network state did not reach the expected lifecycle state", expected, hostShieldVpnActive())
    }

    private fun stopVpnAndWait(timeoutMillis: Long = 8_000) {
        runCatching {
            context.startService(Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_STOP
            })
        }
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && hostShieldVpnActive()) {
            Thread.sleep(200)
        }
        if (hostShieldVpnActive()) {
            runCatching { context.stopService(Intent(context, DnsVpnService::class.java)) }
            while (System.currentTimeMillis() < deadline && hostShieldVpnActive()) {
                Thread.sleep(200)
            }
        }
        runBlocking {
            context.hostShieldDataStore.edit { prefs ->
                prefs[booleanPreferencesKey("is_enabled")] = false
            }
        }
    }

    private fun ensureVpnConsent() {
        val permissionIntent = VpnService.prepare(context) ?: return
        scenario!!.onActivity { activity ->
            activity.startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST_CODE)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val allowButton = sequenceOf("OK", "Allow", "允許", "Разрешить")
            .mapNotNull { label ->
                val selector = By.text(label)
                if (device.wait(Until.hasObject(selector), 5_000)) {
                    device.findObject(selector)
                } else {
                    null
                }
            }
            .firstOrNull()
            ?: error("VPN consent dialog did not expose a known allow action")
        allowButton.click()
        compose.waitForIdle()
        assertNull("VPN consent dialog should be dismissed", VpnService.prepare(context))
    }

    private fun pseudo(value: String): String = "[!! $value :: wide wide wide !!]"

    private companion object {
        const val VPN_PERMISSION_REQUEST_CODE = 4107
    }
}
