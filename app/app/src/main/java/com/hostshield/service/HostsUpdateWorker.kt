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
import android.util.Log
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

// HostShield v4.0.0 -- Auto-Update Worker

@HiltWorker
class HostsUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: HostShieldRepository,
    private val prefs: AppPreferences,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder,
    private val dohBypassUpdater: DohBypassUpdater,
    private val cnameCloakUpdater: CnameCloakUpdater,
    private val httpClient: OkHttpClient
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

        /**
         * Run an immediate one-shot update. Marked expedited on API 31+ so it
         * runs through Doze; falls back to non-expedited if the quota is empty
         * (per `RUN_AS_NON_EXPEDITED_WORK_REQUEST`).
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<HostsUpdateWorker>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_oneshot",
                    ExistingWorkPolicy.KEEP,
                    request,
                )
        }

        /** Alias for automation API. */
        fun runOnce(context: Context) = runNow(context)
    }

    override suspend fun doWork(): Result {
        return try {
            // Refresh remote DoH bypass list on every periodic run
            try { dohBypassUpdater.fetchAndStore() } catch (_: Exception) { }
            // v5.0: Refresh CNAME cloak databases (AdGuard + NextDNS)
            try { cnameCloakUpdater.fetchAndUpdate() } catch (_: Exception) { }

            val isEnabled = prefs.isEnabled.first()
            if (!isEnabled) return Result.success()

            val method = prefs.blockMethod.first()

            when (method) {
                BlockMethod.ROOT_HOSTS, BlockMethod.VPN, BlockMethod.DNS_PROXY -> {
                    // Download block sources and rebuild in-memory blocklist.
                    // Both root (RootDnsLogger) and VPN (DnsVpnService) read from
                    // BlocklistHolder, so updates take effect immediately.
                    val blockSources = repository.getEnabledBlockSources()
                    val allDomains = mutableSetOf<String>()
                    // v5.0: Collect adblock-syntax allow rules from sources
                    val adblockAllowDomains = mutableSetOf<String>()

                    // Track per-source failures so the user can see why a blocklist
                    // is stale (silent swallow used to make this look like the lists
                    // were fresh when they were actually 404'ing for weeks).
                    val failedSources = mutableListOf<String>()
                    for (source in blockSources) {
                        downloader.download(source)
                            .onSuccess { dl ->
                                if (!dl.notModified) {
                                    // v5.0: Auto-detect adblock format and extract allow rules
                                    if (HostsParser.isAdblockFormat(dl.content)) {
                                        val parsed = HostsParser.parseAdblock(dl.content)
                                        allDomains.addAll(parsed.exactBlockDomains)
                                        adblockAllowDomains.addAll(parsed.exactAllowDomains)
                                    } else {
                                        HostsParser.parse(dl.content).forEach { allDomains.add(it.hostname) }
                                    }
                                }
                            }
                            .onFailure { err ->
                                failedSources += source.url
                                Log.w(TAG, "Block source download failed: ${source.url} — ${err.message}")
                            }
                    }

                    // Download allowlist sources and subtract their domains
                    val allowlistSources = repository.getEnabledAllowlistSources()
                    val sourceAllowDomains = mutableSetOf<String>()
                    for (source in allowlistSources) {
                        downloader.download(source)
                            .onSuccess { dl ->
                                if (!dl.notModified) {
                                    HostsParser.parse(dl.content).forEach { sourceAllowDomains.add(it.hostname) }
                                }
                            }
                            .onFailure { err ->
                                failedSources += source.url
                                Log.w(TAG, "Allowlist source download failed: ${source.url} — ${err.message}")
                            }
                    }

                    // Fetch remote rule sync URLs and merge domains
                    val syncUrls = prefs.getRuleSyncUrlList()
                    val maxSyncSizeBytes = 10 * 1024 * 1024 // 10 MB size limit
                    for (url in syncUrls) {
                        // Only allow HTTPS sync URLs for security
                        if (!url.startsWith("https://")) {
                            Log.w(TAG, "Skipping non-HTTPS sync URL: $url — only https:// URLs are allowed")
                            continue
                        }
                        try {
                            val request = okhttp3.Request.Builder().url(url).build()
                            httpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val content = response.body?.string() ?: ""

                                    // Reject content that exceeds the size limit
                                    if (content.toByteArray(Charsets.UTF_8).size > maxSyncSizeBytes) {
                                        Log.w(TAG, "Sync URL content exceeds 10 MB limit, rejecting: $url")
                                        return@use
                                    }

                                    // Compute SHA-256 hash for integrity tracking
                                    val digest = MessageDigest.getInstance("SHA-256")
                                    val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
                                        .joinToString("") { "%02x".format(it) }

                                    val previousHash = prefs.getSyncUrlHash(url)
                                    if (previousHash != null && previousHash != hash) {
                                        Log.i(TAG, "Sync URL content changed: $url (hash $previousHash -> $hash)")
                                    }
                                    prefs.setSyncUrlHash(url, hash)

                                    HostsParser.parse(content).forEach { allDomains.add(it.hostname) }
                                }
                            }
                        } catch (e: Exception) {
                            failedSources += url
                            Log.w(TAG, "Sync URL fetch failed: $url — ${e.message}")
                        }
                    }
                    if (failedSources.isNotEmpty()) {
                        Log.w(TAG, "Blocklist refresh completed with ${failedSources.size} failed source(s): " +
                            failedSources.joinToString(", "))
                    }

                    val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                    blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
                    val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                    allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                    // Remove allowlist source domains + adblock-syntax @@|| allow rules
                    allDomains.removeAll(sourceAllowDomains)
                    allDomains.removeAll(adblockAllowDomains)

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
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
