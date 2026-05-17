package com.hostshield.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloadException
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.util.Log
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

// Blocklist update worker

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
    private val httpClient: OkHttpClient,
    private val diagnosticEvents: DiagnosticEventStore,
    private val sourceFailureNotifier: SourceFailureNotifier
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "hostshield_update"
        const val TAG = "hosts_update"
        private const val DEAD_FAILURE_THRESHOLD = 5

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
                    val adblockWildcardBlocks = mutableSetOf<String>()
                    val adblockWildcardAllows = mutableSetOf<String>()

                    // Track per-source failures so the user can see why a blocklist
                    // is stale (silent swallow used to make this look like the lists
                    // were fresh when they were actually 404'ing for weeks).
                    val failedSources = mutableListOf<SourceFailureNotice>()
                    for (source in blockSources) {
                        downloader.download(source)
                            .onSuccess { dl ->
                                repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                                if (!dl.notModified) {
                                    val parsed = HostsParser.parseForBlocking(dl.content)
                                    allDomains.addAll(parsed.blockDomains)
                                    adblockAllowDomains.addAll(parsed.allowDomains)
                                    adblockWildcardBlocks.addAll(parsed.wildcardBlockDomains)
                                    adblockWildcardAllows.addAll(parsed.wildcardAllowDomains)
                                }
                            }
                            .onFailure { err ->
                                val failures = source.consecutiveFailures + 1
                                val health = if (failures >= DEAD_FAILURE_THRESHOLD) SourceHealth.DEAD else SourceHealth.ERROR
                                val httpStatus = err.sourceHttpStatus()
                                repository.updateSourceHealth(
                                    source.id,
                                    health,
                                    err.message ?: "Unknown error",
                                    failures,
                                    httpStatus
                                )
                                failedSources += SourceFailureNotice(
                                    label = source.label,
                                    url = source.url,
                                    error = err.message ?: err.javaClass.simpleName,
                                    httpStatus = httpStatus,
                                    lastSuccessfulUpdate = source.lastUpdated,
                                    consecutiveFailures = failures,
                                )
                                Log.w(TAG, "Block source download failed: ${source.url} — ${err.message}")
                                diagnosticEvents.record(
                                    DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                                    "Block source download failed",
                                    mapOf(
                                        "source" to source.url,
                                        "error" to (err.message ?: err.javaClass.simpleName),
                                        "http_status" to httpStatus,
                                        "failures" to failures
                                    )
                                )
                            }
                    }

                    // Download allowlist sources and subtract their domains
                    val allowlistSources = repository.getEnabledAllowlistSources()
                    val sourceAllowDomains = mutableSetOf<String>()
                    for (source in allowlistSources) {
                        downloader.download(source)
                            .onSuccess { dl ->
                                repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                                if (!dl.notModified) {
                                    HostsParser.parse(dl.content).forEach { sourceAllowDomains.add(it.hostname) }
                                }
                            }
                            .onFailure { err ->
                                val failures = source.consecutiveFailures + 1
                                val health = if (failures >= DEAD_FAILURE_THRESHOLD) SourceHealth.DEAD else SourceHealth.ERROR
                                val httpStatus = err.sourceHttpStatus()
                                repository.updateSourceHealth(
                                    source.id,
                                    health,
                                    err.message ?: "Unknown error",
                                    failures,
                                    httpStatus
                                )
                                failedSources += SourceFailureNotice(
                                    label = source.label,
                                    url = source.url,
                                    error = err.message ?: err.javaClass.simpleName,
                                    httpStatus = httpStatus,
                                    lastSuccessfulUpdate = source.lastUpdated,
                                    consecutiveFailures = failures,
                                )
                                Log.w(TAG, "Allowlist source download failed: ${source.url} — ${err.message}")
                                diagnosticEvents.record(
                                    DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                                    "Allowlist source download failed",
                                    mapOf(
                                        "source" to source.url,
                                        "error" to (err.message ?: err.javaClass.simpleName),
                                        "http_status" to httpStatus,
                                        "failures" to failures
                                    )
                                )
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
                                } else {
                                    throw SourceDownloadException(
                                        "HTTP ${response.code}: ${response.message}",
                                        response.code
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            val httpStatus = e.sourceHttpStatus()
                            failedSources += SourceFailureNotice(
                                label = url.substringAfter("://").take(48).ifBlank { "Rule sync URL" },
                                url = url,
                                error = e.message ?: e.javaClass.simpleName,
                                httpStatus = httpStatus,
                                lastSuccessfulUpdate = 0L,
                                consecutiveFailures = 1,
                            )
                            Log.w(TAG, "Sync URL fetch failed: $url — ${e.message}")
                            diagnosticEvents.record(
                                DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                                "Rule sync URL fetch failed",
                                mapOf(
                                    "source" to url,
                                    "error" to (e.message ?: e.javaClass.simpleName),
                                    "http_status" to httpStatus
                                )
                            )
                        }
                    }
                    if (failedSources.isNotEmpty()) {
                        Log.w(TAG, "Blocklist refresh completed with ${failedSources.size} failed source(s): " +
                            failedSources.joinToString(", ") { it.url })
                        sourceFailureNotifier.notifyFailures(failedSources)
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
                    val blockingDomainCount = allDomains.size + adblockWildcardBlocks.size
                    blocklistHolder.updateAsync(
                        allDomains,
                        wildcards,
                        regexRules,
                        sourceWildcardBlocks = adblockWildcardBlocks,
                        sourceWildcardAllows = adblockWildcardAllows
                    )
                    diagnosticEvents.record(
                        DiagnosticEventType.BLOCKLIST_SWAP,
                        "Blocklist snapshot swapped",
                        mapOf(
                            "domains" to blockingDomainCount,
                            "wildcards" to wildcards.size,
                            "regex_rules" to regexRules.size,
                            "source" to "hosts_update_worker"
                        )
                    )

                    prefs.setLastApplyTime(System.currentTimeMillis())
                    prefs.setLastApplyCount(blockingDomainCount)
                }
                BlockMethod.DISABLED -> { }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
