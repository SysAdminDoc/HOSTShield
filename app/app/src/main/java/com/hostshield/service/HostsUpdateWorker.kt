package com.hostshield.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// HostShield v1.6.0 -- Auto-Update Worker

@HiltWorker
class HostsUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: HostShieldRepository,
    private val prefs: AppPreferences,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder,
    private val dohBypassUpdater: DohBypassUpdater
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "hostshield_update"
        const val TAG = "hosts_update"

        fun schedule(context: Context, intervalHours: Int, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<HostsUpdateWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Run an immediate one-shot update. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<HostsUpdateWorker>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        /** Alias for automation API. */
        fun runOnce(context: Context) = runNow(context)
    }

    override suspend fun doWork(): Result {
        return try {
            // Refresh remote DoH bypass list on every periodic run
            try { dohBypassUpdater.fetchAndStore() } catch (_: Exception) { }

            val isEnabled = prefs.isEnabled.first()
            if (!isEnabled) return Result.success()

            val method = prefs.blockMethod.first()

            when (method) {
                BlockMethod.ROOT_HOSTS, BlockMethod.VPN -> {
                    // Download block sources and rebuild in-memory blocklist.
                    // Both root (RootDnsLogger) and VPN (DnsVpnService) read from
                    // BlocklistHolder, so updates take effect immediately.
                    val blockSources = repository.getEnabledBlockSources()
                    val allDomains = mutableSetOf<String>()

                    for (source in blockSources) {
                        downloader.download(source).onSuccess { dl ->
                            if (!dl.notModified) {
                                HostsParser.parse(dl.content).forEach { allDomains.add(it.hostname) }
                            }
                        }
                    }

                    // Download allowlist sources and subtract their domains
                    val allowlistSources = repository.getEnabledAllowlistSources()
                    val sourceAllowDomains = mutableSetOf<String>()
                    for (source in allowlistSources) {
                        downloader.download(source).onSuccess { dl ->
                            if (!dl.notModified) {
                                HostsParser.parse(dl.content).forEach { sourceAllowDomains.add(it.hostname) }
                            }
                        }
                    }

                    // Fetch remote rule sync URLs and merge domains
                    val syncUrls = prefs.getRuleSyncUrlList()
                    for (url in syncUrls) {
                        try {
                            val request = okhttp3.Request.Builder().url(url).build()
                            val response = okhttp3.OkHttpClient().newCall(request).execute()
                            if (response.isSuccessful) {
                                val content = response.body?.string() ?: ""
                                HostsParser.parse(content).forEach { allDomains.add(it.hostname) }
                            }
                            response.close()
                        } catch (_: Exception) { }
                    }

                    val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                    blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
                    val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                    allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                    // Remove allowlist source domains
                    allDomains.removeAll(sourceAllowDomains)

                    val wildcards = repository.getEnabledWildcards()
                    val regexRules = repository.getEnabledRegexRules()
                    blocklistHolder.update(allDomains, wildcards, regexRules)

                    prefs.setLastApplyTime(System.currentTimeMillis())
                    prefs.setLastApplyCount(allDomains.size)
                }
                BlockMethod.DISABLED -> { }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
