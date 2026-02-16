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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import javax.inject.Inject

// HostShield v1.0.0

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var rootUtil: RootUtil

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

        setContent {
            HostShieldTheme {
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
                            onComplete = { method, autoEnable ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    prefs.setBlockMethod(method)
                                    if (autoEnable) prefs.setEnabled(true)
                                    prefs.setFirstLaunch(false)
                                }
                            },
                            onRequestVpnPermission = { onResult ->
                                requestVpnPermission(onResult)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                } else {
                    HostShieldMainApp(activity = this@MainActivity)
                }
            }
        }
    }
}

@Composable
private fun HostShieldMainApp(activity: MainActivity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavScreens.map { it.route }

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
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Teal,
                                    selectedTextColor = Teal,
                                    unselectedIconColor = TextDim,
                                    unselectedTextColor = TextDim,
                                    indicatorColor = Teal.copy(alpha = 0.1f)
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
                    onRequestVpnPermission = { onResult -> activity.requestVpnPermission(onResult) }
                )
            }
            composable(Screen.Sources.route) { SourcesScreen() }
            composable(Screen.Rules.route) { RulesScreen() }
            composable(Screen.Stats.route) {
                StatsScreen(onNavigateToLogs = { navController.navigate(SubScreen.LOGS) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAppExclusions = { navController.navigate(SubScreen.APP_EXCLUSIONS) },
                    onNavigateToHostsDiff = { navController.navigate(SubScreen.HOSTS_DIFF) }
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
        }
    }
}
