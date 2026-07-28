package com.hostshield.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hostshield.MainActivity
import com.hostshield.data.database.HostShieldDatabase
import com.hostshield.data.database.Migrations
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.hostShieldDataStore
import com.hostshield.ui.navigation.Screen
import com.hostshield.ui.screens.onboarding.OnboardingDns
import com.hostshield.ui.screens.onboarding.OnboardingScreen
import com.hostshield.ui.theme.HostShieldTheme
import com.hostshield.util.PrivateDnsDetector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    @Before
    fun prepareMainAppState() {
        runBlocking {
            context.hostShieldDataStore.edit { prefs ->
                prefs[booleanPreferencesKey("first_launch")] = false
                prefs[stringPreferencesKey("block_method")] = BlockMethod.VPN.name
                prefs[booleanPreferencesKey("is_enabled")] = false
                prefs[booleanPreferencesKey("schedule_enabled")] = false
                prefs[booleanPreferencesKey("parental_enabled")] = false
                prefs[stringPreferencesKey("parental_pin_hash")] = ""
                prefs[stringPreferencesKey("parental_age_profile")] = "ADULT"
            }
        }
    }

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
        componentScenario?.close()
        componentScenario = null
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
}
