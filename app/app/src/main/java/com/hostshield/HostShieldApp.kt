package com.hostshield

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hostshield.data.preferences.SecurityPreferences
import com.hostshield.data.preferences.SyncPreferences
import com.hostshield.service.CnameCloakUpdater
import com.hostshield.service.AutoBackupWorker
import com.hostshield.service.ThreatIntelWorker
import com.hostshield.util.OfflineGeoIp
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HostShieldApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cnameCloakUpdater: CnameCloakUpdater
    @Inject lateinit var offlineGeoIp: OfflineGeoIp
    @Inject lateinit var securityPreferences: SecurityPreferences
    @Inject lateinit var syncPreferences: SyncPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // v5.0: Non-blocking startup initialization
        appScope.launch { cnameCloakUpdater.loadCached() }
        appScope.launch { offlineGeoIp.initialize() }

        // v6.2: Migrate plaintext secrets to EncryptedSharedPreferences (Roadmap #30)
        appScope.launch {
            securityPreferences.migratePlaintextSecrets()
            syncPreferences.migratePlaintextSecrets()
        }

        // v6.0: Schedule daily threat intelligence feed updates
        try { ThreatIntelWorker.schedule(this) }
        catch (e: Exception) { android.util.Log.w("HostShieldApp", "WorkManager scheduling failed: ${e.message}") }

        // v6.3: Schedule weekly automatic backups (Task #54)
        try { AutoBackupWorker.schedule(this, 7) }
        catch (e: Exception) { android.util.Log.w("HostShieldApp", "AutoBackup scheduling failed: ${e.message}") }
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
