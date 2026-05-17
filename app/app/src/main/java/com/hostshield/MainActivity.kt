package com.hostshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.HostsUpdateWorker
import com.hostshield.service.LogCleanupWorker
import com.hostshield.service.ProfileScheduleWorker
import com.hostshield.service.SourceHealthWorker
import com.hostshield.ui.navigation.Screen
import com.hostshield.ui.navigation.SubScreen
import com.hostshield.ui.navigation.bottomNavScreens
import com.hostshield.ui.screens.home.HomeScreen
import com.hostshield.ui.screens.home.HomeViewModel
import com.hostshield.ui.screens.lists.RulesScreen
import com.hostshield.ui.screens.logs.LogsScreen
import com.hostshield.ui.screens.onboarding.OnboardingScreen
import com.hostshield.ui.screens.settings.AppExclusionsScreen
import com.hostshield.ui.screens.settings.HostsDiffScreen
import com.hostshield.ui.screens.settings.SettingsScreen
import com.hostshield.ui.screens.sources.SourcesScreen
import com.hostshield.ui.screens.stats.StatsScreen
import com.hostshield.ui.theme.*
import com.hostshield.util.RootUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// HostShield Android entry activity

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var rootUtil: RootUtil
    @Inject lateinit var privateDnsDetector: com.hostshield.util.PrivateDnsDetector

    // VPN permission result callback — stored so HomeViewModel can be notified
    private var vpnPermissionCallback: ((Boolean) -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // RESULT_OK means user approved VPN, anything else means denied
        val granted = result.resultCode == RESULT_OK
        vpnPermissionCallback?.invoke(granted)
        vpnPermissionCallback = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    /** Pending deep link from shortcut — consumed by NavHost on first composition. */
    var pendingDeepLink: String? = null
        private set

    fun consumeDeepLink(): String? {
        val link = pendingDeepLink
        pendingDeepLink = null
        return link
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            "com.hostshield.SHORTCUT_TOGGLE" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val enabled = prefs.isEnabled.first()
                    prefs.setEnabled(!enabled)
                }
            }
            "com.hostshield.SHORTCUT_REFRESH" -> {
                HostsUpdateWorker.runNow(this)
            }
            "com.hostshield.SHORTCUT_LOGS" -> {
                pendingDeepLink = SubScreen.LOGS
            }
            Intent.ACTION_VIEW -> {
                // Handle hostshield:// deep links
                // hostshield://logs, hostshield://stats, hostshield://settings, hostshield://sources
                val path = intent.data?.host ?: intent.data?.path?.removePrefix("/") ?: ""
                pendingDeepLink = when (path.lowercase()) {
                    "logs" -> SubScreen.LOGS
                    "stats" -> Screen.Stats.route
                    "settings" -> Screen.Settings.route
                    "sources" -> Screen.Sources.route
                    "rules" -> Screen.Rules.route
                    "firewall" -> SubScreen.FIREWALL
                    "dns-tools" -> SubScreen.DNS_TOOLS
                    "leak-test" -> SubScreen.DNS_LEAK_TEST
                    else -> null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    /** Called by HomeScreen when VPN permission is needed. */
    fun requestVpnPermission(onResult: (Boolean) -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionCallback = onResult
            vpnPermissionLauncher.launch(intent)
        } else {
            // Already granted
            onResult(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        SourceHealthWorker.schedule(this)
        LogCleanupWorker.schedule(this)
        ProfileScheduleWorker.schedule(this)

        // Handle app shortcuts and widget toggle
        handleShortcutIntent(intent)

        setContent {
            val highContrastAmoled by prefs.highContrastAmoled.collectAsState(initial = false)
            HostShieldTheme(highContrastAmoled = highContrastAmoled) {
                val isFirstLaunch by prefs.isFirstLaunch.collectAsState(initial = true)
                var isRootAvailable by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    isRootAvailable = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        rootUtil.isRootAvailable()
                    }
                }

                if (isFirstLaunch) {
                    val rootAvail = isRootAvailable
                    if (rootAvail != null) {
                        OnboardingScreen(
                            isRootAvailable = rootAvail,
                            privateDnsStatus = privateDnsDetector.detect(),
                            onComplete = { method, autoEnable, dnsChoice ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    prefs.setBlockMethod(method)
                                    // Persist the user's onboarding DNS choice so the
                                    // protection mode that starts in `if (autoEnable)`
                                    // actually uses the chosen upstream.
                                    val dnsServers = when (dnsChoice) {
                                        com.hostshield.ui.screens.onboarding.OnboardingDns.CLOUDFLARE -> "1.1.1.1,1.0.0.1"
                                        com.hostshield.ui.screens.onboarding.OnboardingDns.GOOGLE -> "8.8.8.8,8.8.4.4"
                                        com.hostshield.ui.screens.onboarding.OnboardingDns.QUAD9 -> "9.9.9.9,149.112.112.112"
                                        com.hostshield.ui.screens.onboarding.OnboardingDns.ADGUARD -> "94.140.14.14,94.140.15.15"
                                        com.hostshield.ui.screens.onboarding.OnboardingDns.DEFAULT -> ""
                                    }
                                    if (dnsServers.isNotEmpty()) {
                                        prefs.setCustomUpstreamDns(dnsServers)
                                    }
                                    if (autoEnable) prefs.setEnabled(true)
                                    prefs.setFirstLaunch(false)
                                }
                            },
                            onRequestVpnPermission = { onResult ->
                                requestVpnPermission(onResult)
                            }
                        )
                    } else {
                        StartupLoadingScreen()
                    }
                } else {
                    HostShieldMainApp(activity = this@MainActivity)
                }
            }
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1.copy(alpha = 0.9f))
                .border(1.dp, Surface3.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = Teal, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Preparing HostShield", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Checking device capabilities before setup.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = Teal, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun HostShieldMainApp(activity: MainActivity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavScreens.map { it.route }

    // Handle pending deep link from shortcuts/intents
    LaunchedEffect(Unit) {
        val deepLink = activity.consumeDeepLink()
        if (deepLink != null) {
            navController.navigate(deepLink) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (showBottomBar) {
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Surface3.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    NavigationBar(
                        containerColor = Surface0,
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavScreens.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        screen.title,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Teal,
                                    selectedTextColor = Teal,
                                    unselectedIconColor = TextDim,
                                    unselectedTextColor = TextDim,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToLogs = { navController.navigate(SubScreen.LOGS) },
                    onNavigateToApps = { navController.navigate(SubScreen.APPS) },
                    onNavigateToFirewall = { navController.navigate(SubScreen.FIREWALL) },
                    onNavigateToConnectionLog = { navController.navigate(SubScreen.CONNECTION_LOG) },
                    onRequestVpnPermission = { onResult -> activity.requestVpnPermission(onResult) },
                    onNavigateToAppLogs = { pkg -> navController.navigate("${SubScreen.APP_LOGS}?pkg=$pkg") }
                )
            }
            composable(Screen.Sources.route) {
                SourcesScreen(
                    onNavigateToGallery = { navController.navigate(SubScreen.BLOCKLIST_GALLERY) }
                )
            }
            composable(Screen.Rules.route) { RulesScreen() }
            composable(Screen.Stats.route) {
                StatsScreen(onNavigateToLogs = { navController.navigate(SubScreen.LOGS) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAppExclusions = { navController.navigate(SubScreen.APP_EXCLUSIONS) },
                    onNavigateToHostsDiff = { navController.navigate(SubScreen.HOSTS_DIFF) },
                    onNavigateToFirewall = { navController.navigate(SubScreen.FIREWALL) },
                    onNavigateToConnectionLog = { navController.navigate(SubScreen.CONNECTION_LOG) },
                    onNavigateToDnsTools = { navController.navigate(SubScreen.DNS_TOOLS) },
                    onNavigateToNetworkStats = { navController.navigate(SubScreen.NETWORK_STATS) },
                    onNavigateToOverlapAnalysis = { navController.navigate(SubScreen.OVERLAP_ANALYSIS) },
                    onNavigateToDnsLeakTest = { navController.navigate(SubScreen.DNS_LEAK_TEST) },
                    onNavigateToRuleTest = { navController.navigate(SubScreen.RULE_TEST) },
                    onNavigateToHostsEditor = { navController.navigate(SubScreen.HOSTS_EDITOR) },
                    onNavigateToAppPrivacy = { navController.navigate(SubScreen.APP_PRIVACY) },
                    onNavigateToAutomationAudit = { navController.navigate(SubScreen.AUTOMATION_AUDIT) },
                    onNavigateToContentFilter = { navController.navigate(SubScreen.CONTENT_FILTER) },
                    onNavigateToParentalControls = { navController.navigate(SubScreen.PARENTAL_CONTROLS) },
                    onNavigateToDnsBenchmark = { navController.navigate(SubScreen.DNS_BENCHMARK) },
                    onNavigateToWebDavSync = { navController.navigate(SubScreen.WEBDAV_SYNC) },
                    onNavigateToCrashReports = { navController.navigate(SubScreen.CRASH_REPORTS) },
                    onNavigateToQrConfig = { navController.navigate(SubScreen.QR_CONFIG) },
                    onNavigateToTlsFingerprints = { navController.navigate(SubScreen.TLS_FINGERPRINTS) }
                )
            }
            composable(SubScreen.APP_EXCLUSIONS) {
                AppExclusionsScreen(onBack = { navController.popBackStack() })
            }
            composable(SubScreen.HOSTS_DIFF) {
                HostsDiffScreen(onBack = { navController.popBackStack() })
            }
            composable(SubScreen.LOGS) {
                LogsScreen(onBack = { navController.popBackStack() })
            }
            composable(SubScreen.APPS) {
                com.hostshield.ui.screens.apps.AppsScreen(onBack = { navController.popBackStack() })
            }
            composable(SubScreen.FIREWALL) {
                com.hostshield.ui.screens.settings.FirewallScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.CONNECTION_LOG) {
                com.hostshield.ui.screens.logs.ConnectionLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.DNS_TOOLS) {
                com.hostshield.ui.screens.settings.DnsToolsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.NETWORK_STATS) {
                com.hostshield.ui.screens.stats.NetworkStatsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.OVERLAP_ANALYSIS) {
                com.hostshield.ui.screens.sources.OverlapAnalysisScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.DNS_LEAK_TEST) {
                com.hostshield.ui.screens.settings.DnsLeakTestScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.RULE_TEST) {
                com.hostshield.ui.screens.settings.RuleTestScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.HOSTS_EDITOR) {
                com.hostshield.ui.screens.settings.HostsEditorScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.APP_PRIVACY) {
                com.hostshield.ui.screens.apps.AppPrivacyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.BLOCKLIST_GALLERY) {
                com.hostshield.ui.screens.sources.BlocklistGalleryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.AUTOMATION_AUDIT) {
                com.hostshield.ui.screens.settings.AutomationAuditScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.CONTENT_FILTER) {
                com.hostshield.ui.screens.settings.ContentFilterScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.PARENTAL_CONTROLS) {
                com.hostshield.ui.screens.settings.ParentalControlScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.DNS_BENCHMARK) {
                com.hostshield.ui.screens.settings.DnsBenchmarkScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.WEBDAV_SYNC) {
                com.hostshield.ui.screens.settings.WebDavSyncScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.CRASH_REPORTS) {
                com.hostshield.ui.screens.settings.CrashReporterScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.QR_CONFIG) {
                com.hostshield.ui.screens.settings.QrConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.TLS_FINGERPRINTS) {
                com.hostshield.ui.screens.settings.TlsFingerprintScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "${SubScreen.APP_LOGS}?pkg={pkg}",
                arguments = listOf(androidx.navigation.navArgument("pkg") { defaultValue = "" })
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg") ?: ""
                com.hostshield.ui.screens.logs.AppLogsScreen(
                    packageName = pkg,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
