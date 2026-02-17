package com.hostshield.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

// HostShield v1.6.0 - Navigation

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Dashboard", Icons.Filled.Shield, Icons.Outlined.Shield)
    data object Sources : Screen("sources", "Sources", Icons.Filled.CloudDownload, Icons.Outlined.CloudDownload)
    data object Rules : Screen("rules", "Rules", Icons.Filled.Rule, Icons.Outlined.Rule)
    data object Stats : Screen("stats", "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Sources,
    Screen.Rules,
    Screen.Stats,
    Screen.Settings
)

object SubScreen {
    const val APP_EXCLUSIONS = "app_exclusions"
    const val HOSTS_DIFF = "hosts_diff"
    const val LOGS = "logs"
    const val APPS = "apps"
    const val ONBOARDING = "onboarding"
    const val FIREWALL = "firewall"
    const val CONNECTION_LOG = "connection_log"
    const val DNS_TOOLS = "dns_tools"
    const val NETWORK_STATS = "network_stats"
}
