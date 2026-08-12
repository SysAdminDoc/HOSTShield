package com.hostshield.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldInlineAction
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.navigation.HostShieldAdaptiveNavigationScaffold
import com.hostshield.ui.navigation.Screen
import com.hostshield.ui.navigation.bottomNavScreens
import com.hostshield.ui.screens.home.HomeStatsSection
import com.hostshield.ui.screens.home.ShieldOrb
import com.hostshield.ui.theme.Blue
import com.hostshield.ui.theme.HostShieldTheme
import com.hostshield.ui.theme.Red
import com.hostshield.ui.theme.Teal
import com.hostshield.util.PrivacyScorer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityRegressionConnectedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun closeScenario() {
        scenario?.let { activityScenario ->
            runCatching {
                activityScenario.onActivity { activity -> activity.finishAndRemoveTask() }
            }
            runCatching { activityScenario.close() }
        }
        scenario = null
    }

    @Test
    fun reusableTopFlowSurfacesExposeLabelsStatesAnnouncementsAndTargets() {
        launch {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                HostShieldPanelHeader(
                    icon = Icons.Filled.Dns,
                    title = "Protection modules",
                    subtitle = "DNS controls",
                    accent = Teal,
                )
                HostShieldSegmentedTabs(
                    options = listOf(
                        HostShieldSegmentOption("vpn", "VPN", Teal, Icons.Filled.Security),
                        HostShieldSegmentOption("proxy", "DNS proxy", Blue),
                    ),
                    selected = "vpn",
                    onSelected = {},
                    semanticsLabel = "Blocking mode",
                )
                HostShieldFilterChip(
                    label = "Blocked queries",
                    selected = true,
                    onClick = {},
                )
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh dashboard",
                    onClick = {},
                )
                HostShieldInlineAction(label = "Open settings", onClick = {})
                HostShieldStatusBanner(
                    icon = Icons.Filled.Info,
                    title = "Network warning",
                    message = "Encrypted DNS is unavailable",
                    accent = Red,
                    actionLabel = "Open logs",
                    onAction = {},
                )
                HostShieldEmptyState(
                    icon = Icons.Filled.Dns,
                    title = "No matching rows",
                    message = "Clear the filter to show DNS results.",
                    accent = Blue,
                    primaryActionLabel = "Clear filters",
                    onPrimaryAction = {},
                    secondaryActionLabel = "View settings",
                    onSecondaryAction = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Protection modules. DNS controls")
            .assertA11y(clickable = false, minimumTargetDp = null)
        compose.onNodeWithContentDescription("Blocking mode: VPN, selected")
            .assertA11y(state = "Selected", role = Role.Button)
        compose.onNodeWithContentDescription("Blocked queries")
            .assertA11y(state = "Selected", role = Role.Tab)
        compose.onNodeWithContentDescription("Refresh dashboard")
            .assertA11y(role = Role.Button)
        compose.onNodeWithContentDescription("View settings")
            .assertA11y(role = Role.Button)
        compose.onNodeWithContentDescription("Network warning. Encrypted DNS is unavailable")
            .assertA11y(
                clickable = false,
                liveRegion = LiveRegionMode.Polite,
                minimumTargetDp = null,
            )
        compose.onNodeWithContentDescription("Open logs")
            .assertA11y(role = Role.Button)
        compose.onNodeWithContentDescription("No matching rows. Clear the filter to show DNS results.")
            .assertA11y(clickable = false, minimumTargetDp = null)
        compose.onNodeWithContentDescription("Clear filters")
            .assertA11y(role = Role.Button)
        compose.onNodeWithContentDescription("Open settings")
            .assertA11y(role = Role.Button)
    }

    @Test
    fun homeFlowExposesProtectionStateLiveAnnouncementsAndActionTargets() {
        launch {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ShieldOrb(
                    isEnabled = false,
                    isApplying = false,
                    blockedCount = 42,
                    onToggle = {},
                )
                HomeStatsSection(
                    totalDomainsBlocked = 42,
                    blockedToday = 7,
                    totalQueriesToday = 19,
                    enabledSources = 3,
                    privacyScore = 82,
                    privacyItems = listOf(
                        PrivacyScorer.ScoreItem(
                            label = "Blocking enabled",
                            points = 25,
                            maxPoints = 25,
                            passed = true,
                        ),
                    ),
                    categoryCounts = mapOf("ADS" to (1 to 2)),
                    topApps = listOf(Triple("com.example.browser", "Browser", 12)),
                    onNavigateToLogs = {},
                    onToggleCategory = { _, _ -> },
                    onNavigateToAppLogs = {},
                )
            }
        }

        compose.onNodeWithContentDescription("HostShield protection is off. Tap to activate protection.")
            .assertA11y(state = "Inactive", role = Role.Button, minimumTargetDp = 48f)
        compose.onNodeWithContentDescription("Privacy score: 82/100")
            .assertA11y(
                clickable = false,
                liveRegion = LiveRegionMode.Polite,
                minimumTargetDp = null,
            )
        compose.onNodeWithContentDescription("Ads source category, 1 of 2 sources enabled")
            .assertA11y(state = "Not selected", role = Role.Tab)
        compose.onNodeWithContentDescription("Blocked Today, 7")
            .assertA11y(role = Role.Button)
        compose.onNodeWithContentDescription("Browser made 12 DNS queries")
            .assertA11y(role = Role.Button)
    }

    @Test
    fun topLevelNavigationKeepsAccessibleLabelsActionsAndTargets() {
        launch {
            Box(Modifier.size(393.dp, 852.dp)) {
                HostShieldAdaptiveNavigationScaffold(
                    screens = bottomNavScreens,
                    selectedRoute = Screen.Home.route,
                    showTopLevelNavigation = true,
                    onNavigate = {},
                    isSelected = { screen -> screen == Screen.Home },
                ) { }
            }
        }

        bottomNavScreens.forEach { screen ->
            compose.onNodeWithContentDescription(screen.title, useUnmergedTree = true)
                .assertExistsForAccessibilityLabel(screen.title)
            compose.onNodeWithTag(HostShieldTestTags.Nav.route(screen.route), useUnmergedTree = true)
                .assertActionTarget(screen.title)
        }
    }

    private fun launch(content: @Composable () -> Unit) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { activityScenario ->
            activityScenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        HostShieldTheme(content = content)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.assertA11y(
        state: String? = null,
        liveRegion: LiveRegionMode? = null,
        role: Role? = null,
        clickable: Boolean = true,
        minimumTargetDp: Float? = 48f,
    ): SemanticsNodeInteraction {
        val node = fetchSemanticsNode("accessibility contract")
        if (state != null) {
            assertEquals(state, node.config.valueOrNull(SemanticsProperties.StateDescription))
        }
        if (liveRegion != null) {
            assertEquals(liveRegion, node.config.valueOrNull(SemanticsProperties.LiveRegion))
        }
        if (role != null) {
            assertEquals(role, node.config.valueOrNull(SemanticsProperties.Role))
        }
        if (clickable) {
            assertTrue(
                "${node.config} must expose an OnClick action",
                node.config.valueOrNull(SemanticsActions.OnClick) != null,
            )
        }
        if (minimumTargetDp != null) {
            assertTrue(
                "${node.config} bounds ${node.boundsInRoot} are smaller than ${minimumTargetDp}dp",
                node.boundsInRoot.width >= minimumTargetDp - 0.5f &&
                    node.boundsInRoot.height >= minimumTargetDp - 0.5f,
            )
        }
        return this
    }

    private fun SemanticsNodeInteraction.assertExistsForAccessibilityLabel(label: String) {
        val node = fetchSemanticsNode("accessibility label '$label'")
        val descriptions = node.config.valueOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue(
            "Expected content description '$label', got $descriptions",
            descriptions.contains(label),
        )
    }

    private fun SemanticsNodeInteraction.assertActionTarget(label: String) {
        val node = fetchSemanticsNode("navigation action '$label'")
        assertTrue(
            "Navigation item '$label' must expose an OnClick action",
            node.config.valueOrNull(SemanticsActions.OnClick) != null,
        )
        assertTrue(
            "Navigation item '$label' bounds ${node.boundsInRoot} are smaller than 48dp",
            node.boundsInRoot.width >= 48f && node.boundsInRoot.height >= 48f,
        )
    }

    private fun <T> SemanticsConfiguration.valueOrNull(key: SemanticsPropertyKey<T>): T? =
        if (contains(key)) get(key) else null
}
