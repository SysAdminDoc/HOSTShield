package com.hostshield

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hostshield.data.preferences.SecurityPreferences
import com.hostshield.data.preferences.SyncPreferences
import com.hostshield.service.CnameCloakUpdater
import com.hostshield.service.AutoBackupWorker
import com.hostshield.service.RuleExpiryWorker
import com.hostshield.service.ThreatIntelWorker
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HostShieldApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cnameCloakUpdater: CnameCloakUpdater
    @Inject lateinit var securityPreferences: SecurityPreferences
    @Inject lateinit var syncPreferences: SyncPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // v5.0: Non-blocking startup initialization
        appScope.launch { cnameCloakUpdater.loadCached() }
        // v6.2+: Migrate plaintext/legacy secrets into SecureStore.
        appScope.launch {
            securityPreferences.migratePlaintextSecrets()
            syncPreferences.migratePlaintextSecrets()
        }

        // v6.0: Schedule daily threat intelligence feed updates
        appScope.launch {
            try {
                ThreatIntelWorker.schedule(this@HostShieldApp, syncPreferences.wifiOnly.first())
            } catch (e: Exception) {
                android.util.Log.w("HostShieldApp", "WorkManager scheduling failed: ${e.message}")
            }
        }

        // v6.3: Schedule automatic backups honoring the user's configured interval.
        // AutoBackupWorker.schedule uses ExistingPeriodicWorkPolicy.UPDATE, so a
        // hardcoded interval here would stomp the user's setting on every app start.
        // The worker itself checks autoBackupEnabled and no-ops when disabled.
        appScope.launch {
            try {
                AutoBackupWorker.schedule(this@HostShieldApp, syncPreferences.autoBackupIntervalDays.first())
            } catch (e: Exception) {
                android.util.Log.w("HostShieldApp", "AutoBackup scheduling failed: ${e.message}")
            }
        }

        // User-rule expiry is database-backed and must continue while the UI
        // process is stopped. WorkManager persists the periodic reconciliation
        // and BootReceiver re-registers it after a device restore/reboot.
        appScope.launch {
            try {
                RuleExpiryWorker.schedule(this@HostShieldApp)
            } catch (e: Exception) {
                android.util.Log.w("HostShieldApp", "Rule expiry scheduling failed: ${e.message}")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.ERROR
            )
            .build()

    companion object {
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.enableLegacyStderrRedirection = true
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(10)
            )
        }
    }
}
