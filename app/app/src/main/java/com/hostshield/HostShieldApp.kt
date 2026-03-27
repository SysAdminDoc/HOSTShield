package com.hostshield

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hostshield.service.CnameCloakUpdater
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // v5.0: Non-blocking startup initialization
        appScope.launch { cnameCloakUpdater.loadCached() }
        appScope.launch { offlineGeoIp.initialize() }

        // v6.0: Schedule daily threat intelligence feed updates
        try { ThreatIntelWorker.schedule(this) }
        catch (e: Exception) { android.util.Log.w("HostShieldApp", "WorkManager scheduling failed: ${e.message}") }
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
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }
    }
}
