package com.hostshield.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

// Compose navigation graph

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Dashboard", Icons.Filled.Shield, Icons.Outlined.Shield)
    data object Sources : Screen("sources", "Sources", Icons.Filled.CloudDownload, Icons.Outlined.CloudDownload)
    data object Rules : Screen("rules", "Rules", Icons.AutoMirrored.Filled.Rule, Icons.AutoMirrored.Outlined.Rule)
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
    const val OVERLAP_ANALYSIS = "overlap_analysis"
    const val DNS_LEAK_TEST = "dns_leak_test"
    const val RULE_TEST = "rule_test"
    const val HOSTS_EDITOR = "hosts_editor"
    const val APP_PRIVACY = "app_privacy"
    const val BLOCKLIST_GALLERY = "blocklist_gallery"
    const val AUTOMATION_AUDIT = "automation_audit"
    const val APP_LOGS = "app_logs" // arg: ?pkg=com.example.app
    const val CONTENT_FILTER = "content_filter"
    const val PARENTAL_CONTROLS = "parental_controls"
    const val DNS_BENCHMARK = "dns_benchmark"
    const val WEBDAV_SYNC = "webdav_sync"
    const val CRASH_REPORTS = "crash_reports"
    const val QR_CONFIG = "qr_config"
    const val TLS_FINGERPRINTS = "tls_fingerprints"
}
