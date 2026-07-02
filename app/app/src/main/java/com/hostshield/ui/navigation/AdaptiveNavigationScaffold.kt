package com.hostshield.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.theme.Black

internal enum class HostShieldAdaptiveNavigationLayout(val testName: String) {
    None("none"),
    Bar("bar"),
    Rail("rail"),
}

internal fun hostShieldAdaptiveNavigationLayout(
    width: Dp,
    height: Dp,
    showTopLevelNavigation: Boolean,
): HostShieldAdaptiveNavigationLayout {
    if (!showTopLevelNavigation) return HostShieldAdaptiveNavigationLayout.None
    return if (width < 600.dp || height < 480.dp) {
        HostShieldAdaptiveNavigationLayout.Bar
    } else {
        HostShieldAdaptiveNavigationLayout.Rail
    }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
internal fun HostShieldAdaptiveNavigationScaffold(
    screens: List<Screen>,
    selectedRoute: String?,
    showTopLevelNavigation: Boolean,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (Screen) -> Boolean = { screen -> screen.route == selectedRoute },
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layout = hostShieldAdaptiveNavigationLayout(maxWidth, maxHeight, showTopLevelNavigation)
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                if (layout != HostShieldAdaptiveNavigationLayout.None) {
                    screens.forEach { screen ->
                        val selected = isSelected(screen)
                        item(
                            selected = selected,
                            onClick = { onNavigate(screen) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            },
                            alwaysShowLabel = true,
                            modifier = Modifier.testTag(HostShieldTestTags.Nav.route(screen.route)),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag(HostShieldTestTags.Nav.layout(layout.testName)),
            layoutType = layout.toNavigationSuiteType(),
            containerColor = Black,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                content()
            }
        }
    }
}

private fun HostShieldAdaptiveNavigationLayout.toNavigationSuiteType(): NavigationSuiteType =
    when (this) {
        HostShieldAdaptiveNavigationLayout.None -> NavigationSuiteType.None
        HostShieldAdaptiveNavigationLayout.Bar -> NavigationSuiteType.NavigationBar
        HostShieldAdaptiveNavigationLayout.Rail -> NavigationSuiteType.NavigationRail
    }
