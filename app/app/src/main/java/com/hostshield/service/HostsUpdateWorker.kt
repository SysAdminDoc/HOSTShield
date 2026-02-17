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
    private val blocklistHolder: BlocklistHolder
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
    }

    override suspend fun doWork(): Result {
        return try {
            val isEnabled = prefs.isEnabled.first()
            if (!isEnabled) return Result.success()

            val method = prefs.blockMethod.first()

            when (method) {
                BlockMethod.ROOT_HOSTS -> {
                    // Root mode: re-download sources and rebuild in-memory blocklist.
                    // The running DNS proxy (RootDnsLogger) reads from BlocklistHolder,
                    // so this takes effect immediately. No hosts file write needed —
                    // blocking is handled entirely by the proxy.
                    val sources = repository.getEnabledSourcesList()
                    val allDomains = mutableSetOf<String>()

                    for (source in sources) {
                        val result = downloader.download(source)
                        result.onSuccess { dl ->
                            if (!dl.notModified) {
                                val parsed = HostsParser.parse(dl.content)
                                parsed.forEach { allDomains.add(it.hostname) }
                            }
                        }
                    }

                    val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                    blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
                    val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                    allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                    val wildcards = repository.getEnabledWildcards()
                    blocklistHolder.update(allDomains, wildcards)

                    prefs.setLastApplyTime(System.currentTimeMillis())
                    prefs.setLastApplyCount(allDomains.size)
                }
                BlockMethod.VPN -> {
                    // VPN mode: re-download sources and rebuild in-memory blocklist.
                    // The running VPN service reads from BlocklistHolder, so this
                    // takes effect immediately for all future DNS queries.
                    val sources = repository.getEnabledSourcesList()
                    val allDomains = mutableSetOf<String>()

                    for (source in sources) {
                        val result = downloader.download(source)
                        result.onSuccess { dl ->
                            if (!dl.notModified) {
                                val parsed = HostsParser.parse(dl.content)
                                parsed.forEach { allDomains.add(it.hostname) }
                            }
                        }
                    }

                    val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                    blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
                    val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                    allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                    val wildcards = repository.getEnabledWildcards()
                    blocklistHolder.update(allDomains, wildcards)

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
