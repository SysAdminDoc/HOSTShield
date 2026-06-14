package com.hostshield.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.components.HostShieldStatusBanner
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

    private fun pseudo(value: String): String = "[!! $value :: wide wide wide !!]"
}
