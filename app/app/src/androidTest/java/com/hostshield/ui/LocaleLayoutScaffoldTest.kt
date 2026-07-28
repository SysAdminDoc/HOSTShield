package com.hostshield.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.R
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.ui.components.HostShieldDenseListJumpBar
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldSavedFilterBar
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.navigation.HostShieldAdaptiveNavigationScaffold
import com.hostshield.ui.navigation.Screen
import com.hostshield.ui.navigation.bottomNavScreens
import com.hostshield.ui.screens.home.HomeSearchSection
import com.hostshield.ui.theme.Blue
import com.hostshield.ui.theme.HostShieldTheme
import com.hostshield.ui.theme.Mauve
import com.hostshield.ui.theme.Red
import com.hostshield.ui.theme.Surface0
import com.hostshield.ui.theme.Teal
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleLayoutScaffoldTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun reusableSurfacesRenderUnderRtlAndPseudoExpandedCopy() {
        val header = pseudo("Protection modules")
        val subtitle = pseudo("Newest resolver decisions and export destinations")
        val warning = pseudo("Contains DNS hostnames and connection destinations")
        val emptyTitle = pseudo("Waiting for DNS traffic")

        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HostShieldTheme {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Rtl
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(320.dp)
                                    .background(Surface0)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HostShieldPanelHeader(
                                    icon = Icons.Filled.Dns,
                                    title = header,
                                    subtitle = subtitle,
                                    accent = Blue,
                                )
                                HostShieldSegmentedTabs(
                                    options = listOf(
                                        HostShieldSegmentOption("vpn", pseudo("VPN mode"), Teal, Icons.Filled.Security),
                                        HostShieldSegmentOption("settings", pseudo("Settings"), Mauve, Icons.Filled.Settings),
                                    ),
                                    selected = "vpn",
                                    onSelected = {},
                                    semanticsLabel = pseudo("Blocking mode"),
                                )
                                HostShieldStatusBanner(
                                    icon = Icons.Filled.Info,
                                    title = pseudo("Privacy warning"),
                                    message = warning,
                                    accent = Red,
                                    announce = false,
                                )
                                HostShieldEmptyState(
                                    icon = Icons.Filled.Dns,
                                    title = emptyTitle,
                                    message = pseudo("Recent queries will stream here as apps resolve domains"),
                                    accent = Blue,
                                    modifier = Modifier.fillMaxWidth(),
                                    primaryActionLabel = pseudo("Open logs"),
                                    onPrimaryAction = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(header).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "${pseudo("Blocking mode")}: ${pseudo("VPN mode")}, selected"
        ).assertIsDisplayed()
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.onNodeWithText(emptyTitle).assertIsDisplayed()
    }

    @Test
    fun adaptiveNavigationUsesRailAcrossDocumentedLargeScreenSizes() {
        val cases = listOf(
            "foldable-open" to (841.dp to 701.dp),
            "eight-inch-tablet" to (1024.dp to 640.dp),
            "ten-inch-tablet" to (1280.dp to 800.dp),
            "chromebook" to (1600.dp to 900.dp),
        )

        cases.forEach { (label, size) ->
            launchAdaptiveNavigation(width = size.first, height = size.second, contentLabel = label, fontScale = 1.3f)

            compose.onNodeWithTag(HostShieldTestTags.Nav.layout("rail"), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(HostShieldTestTags.Nav.route(Screen.Home.route), useUnmergedTree = true)
                .assertIsDisplayed()
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun adaptiveNavigationKeepsCompactBottomBarFallbacks() {
        val cases = listOf(
            "compact-phone" to (393.dp to 852.dp),
            "compact-height-split" to (841.dp to 420.dp),
        )

        cases.forEach { (label, size) ->
            launchAdaptiveNavigation(width = size.first, height = size.second, contentLabel = label)

            compose.onNodeWithTag(HostShieldTestTags.Nav.layout("bar"), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(HostShieldTestTags.Nav.route(Screen.Settings.route), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun resourceBackedTopFlowsRenderUnderRtlAndLargeFont() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                        LocalDensity provides Density(density = 1f, fontScale = 1.35f),
                    ) {
                        HostShieldTheme {
                            Column(
                                modifier = Modifier
                                    .width(360.dp)
                                    .background(Surface0)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HomeSearchSection(
                                    searchQuery = "ads",
                                    onSearchQueryChange = {},
                                    searchExpanded = true,
                                    onSearchExpandedChange = {},
                                    searchHistory = listOf("tracker"),
                                    onSaveSearch = {},
                                    onSearchLogs = {},
                                    onSearchApps = {},
                                )
                                HostShieldPanelHeader(
                                    icon = Icons.Filled.Dns,
                                    title = stringResource(R.string.dns_section_title),
                                    subtitle = stringResource(R.string.dns_over_https_sub),
                                    accent = Blue,
                                )
                                HostShieldPanelHeader(
                                    icon = Icons.Filled.QrCode2,
                                    title = stringResource(R.string.qr_export_configuration),
                                    subtitle = stringResource(R.string.qr_export_subtitle),
                                    accent = Teal,
                                )
                                Text(stringResource(R.string.settings_create_backup))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Search \"ads\" in DNS Logs").assertIsDisplayed()
        compose.onNodeWithText("DNS").assertIsDisplayed()
        compose.onNodeWithText("Share rules, sources, and DNS preferences as one code").assertIsDisplayed()
        compose.onNodeWithText("Create backup").assertIsDisplayed()
    }

    @Test
    fun denseListControlsRenderSavedFiltersNoResultsAndJumpTargetsAtLargeFont() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 1f, fontScale = 1.35f)
                    ) {
                        HostShieldTheme {
                            val listState = rememberLazyListState()
                            Column(
                                modifier = Modifier
                                    .width(360.dp)
                                    .background(Surface0)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HostShieldSavedFilterBar(
                                    screen = "logs",
                                    savedFilters = listOf(
                                        SavedDenseListFilter(
                                            screen = "logs",
                                            label = "Blocked A queries",
                                            payload = "{}",
                                            updatedAt = 1L
                                        )
                                    ),
                                    canSaveCurrent = true,
                                    onSaveCurrent = {},
                                    onApplyFilter = {},
                                    onClearSavedFilters = {},
                                )
                                HostShieldEmptyState(
                                    icon = Icons.Filled.Dns,
                                    title = "No matching rows",
                                    message = "Clear the saved filter to show dense list results again.",
                                    accent = Blue,
                                    primaryActionLabel = "Clear filters",
                                    onPrimaryAction = {},
                                )
                                HostShieldDenseListJumpBar(
                                    screen = "test_dense",
                                    label = "test rows",
                                    totalItems = 80,
                                    listState = listState,
                                    minItems = 20,
                                )
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.height(170.dp),
                                ) {
                                    items((0 until 80).toList()) { index ->
                                        Text("Dense row $index", color = Teal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Save filter").assertIsDisplayed()
        compose.onNodeWithText("Blocked A queries").assertIsDisplayed()
        compose.onNodeWithText("No matching rows").assertIsDisplayed()
        compose.onNodeWithContentDescription("Jump to end of test rows").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Dense row 79").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Dense row 79").assertIsDisplayed()
    }

    private fun launchAdaptiveNavigation(
        width: Dp,
        height: Dp,
        contentLabel: String,
        fontScale: Float = 1f,
    ) {
        closeScenario()
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 0.5f, fontScale = fontScale)
                    ) {
                        HostShieldTheme {
                            Box(
                                modifier = Modifier
                                    .size(width = width, height = height)
                                    .background(Surface0)
                            ) {
                                HostShieldAdaptiveNavigationScaffold(
                                    screens = bottomNavScreens,
                                    selectedRoute = Screen.Home.route,
                                    showTopLevelNavigation = true,
                                    onNavigate = {},
                                    modifier = Modifier.fillMaxSize(),
                                    isSelected = { screen -> screen == Screen.Home },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("adaptive:content")
                                    ) {
                                        Text(contentLabel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pseudo(value: String): String = "[!! $value :: wide wide wide !!]"
}
