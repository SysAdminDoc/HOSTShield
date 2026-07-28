package com.hostshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.DnsProxyService
import com.hostshield.service.DnsVpnService
import com.hostshield.service.HostShieldWidgetProvider
import com.hostshield.service.HostsUpdateWorker
import com.hostshield.service.LogCleanupWorker
import com.hostshield.service.ProfileScheduleWorker
import com.hostshield.service.ProtectionServiceStarter
import com.hostshield.service.RootDnsService
import com.hostshield.service.SourceHealthWorker
import com.hostshield.ui.navigation.HostShieldAdaptiveNavigationScaffold
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

    /**
     * Pending deep link from shortcut — observed as Compose state so that
     * singleTop deliveries via [onNewIntent] navigate too, not just the
     * first composition.
     */
    var pendingDeepLink: String? by mutableStateOf(null)
        private set

    fun consumeDeepLink() {
        pendingDeepLink = null
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            "com.hostshield.SHORTCUT_TOGGLE" -> {
                // MainActivity is exported for the launcher, so any app could send
                // this action. Only honor it from the launcher/system or ourselves
                // so a third-party app cannot flip protection.
                if (isTrustedShortcutCaller()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        toggleProtectionFromShortcut()
                    }
                } else {
                    Log.w("MainActivity", "Ignoring SHORTCUT_TOGGLE from untrusted caller")
                    android.widget.Toast.makeText(
                        this,
                        "Toggle Protection can only be triggered from your launcher.",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
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

    /**
     * True when a SHORTCUT_TOGGLE launch originated from a trusted source:
     * ourselves, the OS, or the actual default launcher.
     *
     * On API 34+ the launched-from identity is authoritative and cannot be
     * spoofed via `EXTRA_REFERRER`. On older releases only the referrer is
     * available (spoofable), so we narrow trust to the resolved home/launcher
     * package instead of any `FLAG_SYSTEM` app — closing the "claim to be
     * com.android.settings" bypass while still honoring third-party launchers.
     */
    private fun isTrustedShortcutCaller(): Boolean {
        val homePackage = resolveHomePackage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callerUid = try { launchedFromUid } catch (_: Exception) { return false }
            if (callerUid == android.os.Process.myUid()) return true
            if (callerUid == android.os.Process.SYSTEM_UID) return true
            val callerPackage = try { launchedFromPackage } catch (_: Exception) { null }
            return callerPackage != null && callerPackage == homePackage
        }
        // Pre-34 fallback: referrer is the only signal (spoofable). Trust
        // system-delivered (null), ourselves, or the default launcher only.
        val caller = referrer?.host ?: return true
        if (caller == packageName) return true
        return homePackage != null && caller == homePackage
    }

    /** The current default launcher (home) package, or null if unresolved. */
    private fun resolveHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    /**
     * Launcher-shortcut toggle. Mirrors [com.hostshield.service.HostShieldTileService.onClick]:
     * stop/start the service matching the configured block method so the pref
     * and the actually-running protection never diverge.
     */
    private suspend fun toggleProtectionFromShortcut() {
        val enabled = prefs.isEnabled.first()
        val method = prefs.blockMethod.first()
        if (enabled) {
            when (method) {
                BlockMethod.VPN -> {
                    val intent = Intent(this, DnsVpnService::class.java)
                        .apply { action = DnsVpnService.ACTION_STOP }
                    startService(intent)
                }
                BlockMethod.ROOT_HOSTS -> RootDnsService.stop(this)
                BlockMethod.DNS_PROXY -> stopService(Intent(this, DnsProxyService::class.java))
                BlockMethod.DISABLED -> { }
            }
            prefs.setEnabled(false)
            HostShieldWidgetProvider.updateWidget(applicationContext, false, 0)
        } else {
            when (method) {
                BlockMethod.VPN -> {
                    val intent = Intent(this, DnsVpnService::class.java)
                        .apply { action = DnsVpnService.ACTION_START }
                    ProtectionServiceStarter.startForegroundService(
                        this,
                        intent,
                        "MainActivity.shortcutToggle"
                    )
                }
                BlockMethod.ROOT_HOSTS -> RootDnsService.start(this, "MainActivity.shortcutToggle")
                BlockMethod.DNS_PROXY -> DnsProxyService.start(this, "MainActivity.shortcutToggle")
                BlockMethod.DISABLED -> { }
            }
            prefs.setEnabled(true)
            val count = prefs.lastApplyCount.first()
            HostShieldWidgetProvider.updateWidget(applicationContext, true, count)
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

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            SourceHealthWorker.schedule(this@MainActivity, prefs.wifiOnly.first())
        }
        LogCleanupWorker.schedule(this)
        ProfileScheduleWorker.schedule(this)

        // Handle app shortcuts and widget toggle
        handleShortcutIntent(intent)

        setContent {
            val highContrastAmoled by prefs.highContrastAmoled.collectAsState(initial = false)
            val accentColor by prefs.accentColor.collectAsState(initial = "teal")
            val dynamicColor by prefs.dynamicColor.collectAsState(initial = false)
            val themeMode by prefs.themeMode.collectAsState(initial = "dark")
            HostShieldTheme(
                highContrastAmoled = highContrastAmoled,
                accentColor = accentColor,
                dynamicColor = dynamicColor,
                themeMode = themeMode,
            ) {
                // null = DataStore hasn't emitted yet — render the splash-like
                // loading state instead of flashing onboarding at existing users.
                val isFirstLaunch by prefs.isFirstLaunch.collectAsState(initial = null as Boolean?)
                var isRootAvailable by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    isRootAvailable = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        rootUtil.isRootAvailable()
                    }
                }

                if (isFirstLaunch == null) {
                    StartupLoadingScreen()
                } else if (isFirstLaunch == true) {
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
                                    if (autoEnable) requestNotificationPermissionIfNeeded()
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
    val parentalPinRehashRequired by activity.prefs.parentalPinRehashRequired.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            activity.prefs.refreshParentalPinRehashRequired()
        }
    }

    // Handle pending deep links from shortcuts/intents. Keyed on the pending
    // value so singleTop deliveries via onNewIntent navigate too.
    val pendingDeepLink = activity.pendingDeepLink
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            navController.navigate(pendingDeepLink) {
                launchSingleTop = true
            }
            activity.consumeDeepLink()
        }
    }

    LaunchedEffect(parentalPinRehashRequired, currentDestination?.route) {
        val route = currentDestination?.route
        if (
            parentalPinRehashRequired &&
            route != null &&
            route != SubScreen.PARENTAL_CONTROLS
        ) {
            navController.navigate(SubScreen.PARENTAL_CONTROLS) {
                launchSingleTop = true
            }
        }
    }

    HostShieldAdaptiveNavigationScaffold(
        screens = bottomNavScreens,
        selectedRoute = currentDestination?.route,
        showTopLevelNavigation = showBottomBar,
        onNavigate = { screen ->
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        isSelected = { screen ->
            currentDestination?.hierarchy?.any { it.route == screen.route } == true
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .background(Black),
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
                    onRequestNotificationPermission = { activity.requestNotificationPermissionIfNeeded() },
                    onNavigateToAppLogs = { pkg -> navController.navigate("${SubScreen.APP_LOGS}?pkg=$pkg") },
                    onSearchLogs = { q ->
                        navController.navigate("${SubScreen.LOGS}?query=${android.net.Uri.encode(q)}")
                    },
                    onSearchApps = { q ->
                        navController.navigate("${SubScreen.APPS}?query=${android.net.Uri.encode(q)}")
                    }
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
            composable(
                "${SubScreen.LOGS}?query={query}",
                arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = "" })
            ) {
                LogsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "${SubScreen.APPS}?query={query}",
                arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = "" })
            ) {
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
