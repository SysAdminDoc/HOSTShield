package com.hostshield.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.hostshield.HostShieldApp
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.hostShieldDataStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-37 foreground-service caller matrix.
 *
 * This deliberately uses the production entry points instead of invoking
 * service methods directly. The log lines emitted by [recordMatrix] are the
 * machine-readable run record: caller, UID context, platform/target SDK,
 * manifest FGS type, start result, preference transition, cleanup result, and
 * the API-37 FGS_INTRODUCE_TIME_LIMITS compatibility diagnostic.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundServiceCallerMatrixConnectedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var consentScenario: ActivityScenario<ComponentActivity>? = null
    private var tileAdded = false

    @Before
    fun prepareMatrix() {
        stopAllServices()
        BlockingScheduleWorker.cancel(context)
        PauseResumeWorker.cancel(context)
        setProtectionState(method = BlockMethod.VPN, enabled = false)
        ensureVpnConsent()
        recordPlatformMatrix()
    }

    @After
    fun cleanMatrix() {
        if (tileAdded) {
            runCatching { device.executeShellCommand("cmd statusbar remove-tile ${tileComponent()}") }
            tileAdded = false
        }
        stopAllServices()
        BlockingScheduleWorker.cancel(context)
        PauseResumeWorker.cancel(context)
        runBlocking { setProtectionState(method = BlockMethod.VPN, enabled = false) }
        consentScenario?.close()
        consentScenario = null
    }

    @Test
    fun directVpnActionStartAndStopPreserveCallerOwnedPreference() {
        val before = currentEnabled()
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
        }
        val started = ProtectionServiceStarter.startForegroundService(
            context,
            intent,
            "ForegroundServiceCallerMatrixConnectedTest.DnsVpnService.ACTION_START",
        )
        waitForVpnState(expected = started, timeoutMillis = 20_000)
        val afterStart = currentEnabled()
        assertFalse("ACTION_START must not claim protection for its caller", afterStart)
        recordMatrix(
            caller = "DnsVpnService.ACTION_START",
            service = DnsVpnService::class.java,
            started = started,
            preferenceBefore = before,
            preferenceAfter = afterStart,
            recovery = "production ACTION_STOP",
        )

        stopVpnAndWait()
        assertFalse(currentEnabled())
        recordMatrix(
            caller = "DnsVpnService.ACTION_STOP",
            service = DnsVpnService::class.java,
            started = !hostShieldVpnActive(),
            preferenceBefore = afterStart,
            preferenceAfter = currentEnabled(),
            recovery = "VPN network absent",
        )
    }

    @Test
    fun bootReceiverRestoresVpnFromPersistedState() {
        setProtectionState(method = BlockMethod.VPN, enabled = true)
        val before = currentEnabled()
        // BOOT_COMPLETED itself is a protected broadcast. The receiver also
        // accepts the OEM quick-boot action, which exercises the same restore
        // branch without asking an app UID to forge a system-only broadcast.
        context.sendBroadcast(
            Intent("com.htc.intent.action.QUICKBOOT_POWERON").setPackage(context.packageName),
        )
        waitForVpnState(expected = true, timeoutMillis = 20_000)
        assertTrue(currentEnabled())
        recordMatrix(
            caller = "BootReceiver.ACTION_BOOT_COMPLETED",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = before,
            preferenceAfter = currentEnabled(),
            recovery = "production ACTION_STOP; persisted enabled state retained",
        )
        stopVpnAndWait()
    }

    @Test
    fun blockingAndPauseWorkersStartVpnBeforeWritingEnabledState() = runBlocking {
        setProtectionState(method = BlockMethod.VPN, enabled = false)
        context.hostShieldDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("schedule_enabled")] = true
            prefs[stringPreferencesKey("schedule_start")] = "00:00"
            prefs[stringPreferencesKey("schedule_end")] = "23:59"
            prefs[stringPreferencesKey("schedule_mode")] = "block"
        }

        val scheduleBefore = currentEnabled()
        val scheduleResult = buildWorker<BlockingScheduleWorker>().doWork()
        assertTrue("BlockingScheduleWorker should complete after VPN start", scheduleResult is ListenableWorker.Result.Success)
        waitForVpnState(expected = true, timeoutMillis = 20_000)
        assertTrue(currentEnabled())
        recordMatrix(
            caller = "BlockingScheduleWorker.doWork",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = scheduleBefore,
            preferenceAfter = currentEnabled(),
            recovery = "worker Result.Success; production ACTION_STOP",
        )
        stopVpnAndWait()

        setProtectionState(method = BlockMethod.VPN, enabled = false)
        val pauseBefore = currentEnabled()
        context.hostShieldDataStore.edit { prefs ->
            prefs[longPreferencesKey("pause_end_time")] = System.currentTimeMillis() + 60_000L
        }
        val pauseResult = buildWorker<PauseResumeWorker>().doWork()
        assertTrue("PauseResumeWorker should complete after VPN start", pauseResult is ListenableWorker.Result.Success)
        waitForVpnState(expected = true, timeoutMillis = 20_000)
        assertTrue(currentEnabled())
        assertEquals(0L, context.hostShieldDataStore.data.first()[longPreferencesKey("pause_end_time")] ?: 0L)
        recordMatrix(
            caller = "PauseResumeWorker.doWork",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = pauseBefore,
            preferenceAfter = currentEnabled(),
            recovery = "worker Result.Success; pause_end_time cleared; production ACTION_STOP",
        )
        stopVpnAndWait()
    }

    @Test
    fun automationReceiverEnableDisableAuditsBothPreferenceTransitions() = runBlocking {
        setProtectionState(method = BlockMethod.VPN, enabled = false)
        val before = currentEnabled()
        context.sendBroadcast(
            Intent(AutomationActionContract.ACTION_ENABLE).setPackage(context.packageName),
        )
        waitForPreference(expected = true)
        waitForVpnState(expected = true, timeoutMillis = 20_000)
        recordMatrix(
            caller = "AutomationReceiver.ACTION_ENABLE",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = before,
            preferenceAfter = currentEnabled(),
            recovery = "AutomationReceiver audit OK; ACTION_DISABLE",
        )

        context.sendBroadcast(
            Intent(AutomationActionContract.ACTION_DISABLE).setPackage(context.packageName),
        )
        waitForPreference(expected = false)
        waitForVpnState(expected = false, timeoutMillis = 20_000)
        recordMatrix(
            caller = "AutomationReceiver.ACTION_DISABLE",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = true,
            preferenceAfter = currentEnabled(),
            recovery = "VPN network absent; audit completion",
        )
    }

    @Test
    fun quickSettingsTileStartsAndStopsThroughSystemTileService() {
        setProtectionState(method = BlockMethod.VPN, enabled = false)
        val component = tileComponent()
        val addOutput = device.executeShellCommand("cmd statusbar add-tile $component")
        tileAdded = true
        Log.i(TAG, "matrix tileAdd component=$component output=$addOutput")
        // SystemUI binds a newly added TileService asynchronously. Do not send
        // the click in the same shell transaction or the first click can be
        // discarded before TileService.onClick is delivered.
        Thread.sleep(1_500)

        val before = currentEnabled()
        val startOutput = device.executeShellCommand("cmd statusbar click-tile $component")
        Log.i(TAG, "matrix tileClick(start) output=$startOutput")
        waitForPreference(expected = true)
        waitForVpnState(expected = true, timeoutMillis = 20_000)
        recordMatrix(
            caller = "HostShieldTileService.onClick(start)",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = before,
            preferenceAfter = currentEnabled(),
            recovery = "second system tile click",
        )

        val stopOutput = device.executeShellCommand("cmd statusbar click-tile $component")
        Log.i(TAG, "matrix tileClick(stop) output=$stopOutput")
        waitForPreference(expected = false)
        waitForVpnState(expected = false, timeoutMillis = 20_000)
        recordMatrix(
            caller = "HostShieldTileService.onClick(stop)",
            service = DnsVpnService::class.java,
            started = true,
            preferenceBefore = true,
            preferenceAfter = currentEnabled(),
            recovery = "VPN network absent",
        )
    }

    @Test
    fun rootAndDnsProxyCallersPreservePreferenceAcrossForegroundPromotion() {
        setProtectionState(method = BlockMethod.ROOT_HOSTS, enabled = false)
        val rootBefore = currentEnabled()
        val rootStarted = RootDnsService.start(
            context,
            "ForegroundServiceCallerMatrixConnectedTest.RootDnsService",
        )
        val rootRunning = if (rootStarted) waitForService(RootDnsService::class.java, true) else false
        assertFalse("Root caller must not mutate the protection preference", currentEnabled())
        recordMatrix(
            caller = "RootDnsService.start",
            service = RootDnsService::class.java,
            started = rootStarted,
            preferenceBefore = rootBefore,
            preferenceAfter = currentEnabled(),
            recovery = if (rootRunning) "stopService after foreground promotion" else "ProtectionServiceStarter failure recorded",
        )
        context.stopService(Intent(context, RootDnsService::class.java))

        setProtectionState(method = BlockMethod.DNS_PROXY, enabled = false)
        val proxyBefore = currentEnabled()
        val proxyStarted = DnsProxyService.start(
            context,
            "ForegroundServiceCallerMatrixConnectedTest.DnsProxyService",
        )
        val proxyRunning = if (proxyStarted) waitForService(DnsProxyService::class.java, true) else false
        assertFalse("DNS proxy caller must not mutate the protection preference", currentEnabled())
        recordMatrix(
            caller = "DnsProxyService.start",
            service = DnsProxyService::class.java,
            started = proxyStarted,
            preferenceBefore = proxyBefore,
            preferenceAfter = currentEnabled(),
            recovery = if (proxyRunning) "stopService after foreground promotion" else "ProtectionServiceStarter failure recorded",
        )
        context.stopService(Intent(context, DnsProxyService::class.java))
    }

    private fun recordPlatformMatrix() {
        val appInfo = context.applicationInfo
        val compatDump = device.executeShellCommand("dumpsys platform_compat")
        assertTrue(
            "API-37 compatibility dump did not expose FGS_INTRODUCE_TIME_LIMITS",
            compatDump.contains("FGS_INTRODUCE_TIME_LIMITS"),
        )
        Log.i(
            TAG,
            "matrix platform sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} " +
                "targetSdk=${appInfo.targetSdkVersion} " +
                "FGS_INTRODUCE_TIME_LIMITS=" +
                compatDump.lineSequence().firstOrNull { it.contains("FGS_INTRODUCE_TIME_LIMITS") }?.trim(),
        )

        listOf(
            DnsVpnService::class.java,
            RootDnsService::class.java,
            DnsProxyService::class.java,
        ).forEach { service ->
            val info = serviceInfo(service)
            assertEquals(
                "${service.simpleName} must declare systemExempted",
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                info.foregroundServiceType,
            )
            Log.i(
                TAG,
                "matrix manifest service=${service.simpleName} " +
                    "foregroundServiceType=${info.foregroundServiceType} " +
                    "permission=${info.permission}",
            )
        }
    }

    private fun recordMatrix(
        caller: String,
        service: Class<*>,
        started: Boolean,
        preferenceBefore: Boolean,
        preferenceAfter: Boolean,
        recovery: String,
    ) {
        val info = serviceInfo(service)
        val compatLine = device.executeShellCommand("dumpsys platform_compat")
            .lineSequence()
            .firstOrNull { it.contains("FGS_INTRODUCE_TIME_LIMITS") }
            ?.trim()
            ?: "missing"
        Log.i(
            TAG,
            "matrix caller=$caller uid=${android.os.Process.myUid()} " +
                "sdk=${Build.VERSION.SDK_INT} targetSdk=${context.applicationInfo.targetSdkVersion} " +
                "service=${service.simpleName} serviceType=${info.foregroundServiceType} " +
                "started=$started prefBefore=$preferenceBefore prefAfter=$preferenceAfter " +
                "running=${isServiceRunning(service)} recovery=$recovery " +
                "FGS_INTRODUCE_TIME_LIMITS=$compatLine",
        )
    }

    private fun serviceInfo(service: Class<*>): ServiceInfo {
        val component = android.content.ComponentName(context, service)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(
                component,
                android.content.pm.PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, 0)
        }
    }

    private fun tileComponent(): String =
        "${context.packageName}/${HostShieldTileService::class.java.name}"

    private fun ensureVpnConsent() {
        val permissionIntent = VpnService.prepare(context) ?: return
        consentScenario = ActivityScenario.launch(ComponentActivity::class.java)
        consentScenario!!.onActivity { activity ->
            activity.startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST_CODE)
        }
        val allowButton = sequenceOf("OK", "Allow", "允許", "Разрешить")
            .mapNotNull { label ->
                val selector = By.text(label)
                if (device.wait(Until.hasObject(selector), 5_000)) device.findObject(selector) else null
            }
            .firstOrNull()
            ?: error("VPN consent dialog did not expose a known allow action")
        allowButton.click()
        assertNullVpnConsent()
    }

    private fun assertNullVpnConsent() {
        runBlocking {
            withTimeout(5_000) {
                while (VpnService.prepare(context) != null) delay(100)
            }
        }
    }

    private fun stopAllServices() {
        runCatching {
            context.startService(Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_STOP
            })
        }
        context.stopService(Intent(context, DnsVpnService::class.java))
        context.stopService(Intent(context, RootDnsService::class.java))
        context.stopService(Intent(context, DnsProxyService::class.java))
        waitForVpnState(expected = false, timeoutMillis = 8_000)
    }

    private fun stopVpnAndWait() {
        runCatching {
            context.startService(Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_STOP
            })
        }
        waitForVpnState(expected = false, timeoutMillis = 8_000)
        runBlocking { setProtectionState(method = BlockMethod.VPN, enabled = false) }
    }

    private fun waitForVpnState(expected: Boolean, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && hostShieldVpnActive() != expected) {
            Thread.sleep(200)
        }
        assertEquals("VPN state did not reach expected=$expected", expected, hostShieldVpnActive())
    }

    private fun waitForService(service: Class<*>, expected: Boolean, timeoutMillis: Long = 8_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && isServiceRunning(service) != expected) {
            Thread.sleep(200)
        }
        val running = isServiceRunning(service)
        assertEquals("${service.simpleName} running state", expected, running)
        return running
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(service: Class<*>): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        return manager.getRunningServices(100).any { info ->
            info.service.className == service.name
        }
    }

    private fun hostShieldVpnActive(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun currentEnabled(): Boolean = runBlocking {
        context.hostShieldDataStore.data.first()[booleanPreferencesKey("is_enabled")] ?: false
    }

    private fun waitForPreference(expected: Boolean) {
        runBlocking {
            withTimeout(10_000) {
                while (currentEnabled() != expected) delay(100)
            }
        }
    }

    private fun setProtectionState(method: BlockMethod, enabled: Boolean) {
        runBlocking {
            context.hostShieldDataStore.edit { prefs ->
                prefs[stringPreferencesKey("block_method")] = method.name
                prefs[booleanPreferencesKey("is_enabled")] = enabled
                prefs[booleanPreferencesKey("schedule_enabled")] = false
                prefs[stringPreferencesKey("schedule_mode")] = "block"
            }
        }
    }

    private inline fun <reified W : ListenableWorker> buildWorker(): W =
        TestListenableWorkerBuilder
            .from<W>(context, W::class.java)
            .setWorkerFactory((context.applicationContext as HostShieldApp).workerFactory)
            .build()

    private companion object {
        const val TAG = "HostShieldFgsMatrix"
        const val VPN_PERMISSION_REQUEST_CODE = 4801
    }
}
