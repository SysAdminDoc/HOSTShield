package com.hostshield.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.BoundedResponseReader
import com.hostshield.data.source.SourceDownloadException
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.util.Log
import com.hostshield.util.PrivacyLog
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
    private val sourceCoordinator: BlocklistSourceCoordinator,
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
        private const val MAX_RULE_SYNC_BYTES = 10L * 1024L * 1024L

        fun schedule(context: Context, intervalHours: Int, wifiOnly: Boolean) {
            val request = PeriodicWorkRequestBuilder<HostsUpdateWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(syncNetworkConstraints(wifiOnly))
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
            try { dohBypassUpdater.fetchAndStore() } catch (e: Exception) { Log.w(TAG, "DoH bypass list refresh failed: ${e.message}") }
            // v5.0: Refresh CNAME cloak databases (AdGuard + NextDNS)
            try { cnameCloakUpdater.fetchAndUpdate() } catch (e: Exception) { Log.w(TAG, "CNAME cloak database refresh failed: ${e.message}") }

            val isEnabled = prefs.isEnabled.first()
            if (!isEnabled) return Result.success()

            val method = prefs.blockMethod.first()

            when (method) {
                BlockMethod.ROOT_HOSTS, BlockMethod.VPN, BlockMethod.DNS_PROXY -> {
                    // Download block sources and rebuild in-memory blocklist.
                    // Both root (RootDnsLogger) and VPN (DnsVpnService) read from
                    // BlocklistHolder, so updates take effect immediately.
                    val sourceSnapshot = sourceCoordinator.downloadEnabledSourcesForFullSnapshot()
                    val allDomains = sourceSnapshot.blockDomains.toMutableSet()
                    val sourceAllowDomains = sourceSnapshot.sourceExactAllows.toMutableSet()
                    val adblockWildcardBlocks = sourceSnapshot.sourceWildcardBlocks.toMutableSet()
                    val adblockWildcardAllows = sourceSnapshot.sourceWildcardAllows.toMutableSet()
                    val dnsTypeRules = sourceSnapshot.dnsTypeRules.toMutableList()
                    val exactBlockOrigins = sourceSnapshot.exactBlockOrigins.toMutableMap()
                    val wildcardBlockOrigins = sourceSnapshot.wildcardBlockOrigins.toMutableMap()

                    // Track per-source failures so the user can see why a blocklist
                    // is stale (silent swallow used to make this look like the lists
                    // were fresh when they were actually 404'ing for weeks).
                    val failedSources = sourceSnapshot.failedSources.toMutableList()
                    failedSources.forEach { notice ->
                        PrivacyLog.w(TAG, "Source download failed: ${notice.url} - ${notice.error}")
                        diagnosticEvents.record(
                            DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                            "Source download failed",
                            mapOf(
                                "source" to notice.url,
                                "error" to notice.error,
                                "http_status" to notice.httpStatus,
                                "failures" to notice.consecutiveFailures
                            )
                        )
                    }

                    // Fetch remote rule sync URLs and merge domains
                    val syncUrls = prefs.getRuleSyncUrlList()
                    for (url in syncUrls) {
                        // Only allow HTTPS sync URLs for security
                        if (!url.startsWith("https://")) {
                            PrivacyLog.w(TAG, "Skipping non-HTTPS sync URL: $url - only https:// URLs are allowed")
                            continue
                        }
                        try {
                            val request = okhttp3.Request.Builder().url(url).build()
                            httpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val content = BoundedResponseReader.readUtf8(
                                        response,
                                        MAX_RULE_SYNC_BYTES,
                                        "rule sync URL"
                                    ).content

                                    // Compute SHA-256 hash for integrity tracking
                                    val digest = MessageDigest.getInstance("SHA-256")
                                    val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
                                        .joinToString("") { "%02x".format(it) }

                                    val previousHash = prefs.getSyncUrlHash(url)
                                    if (previousHash != null && previousHash != hash) {
                                        PrivacyLog.i(TAG, "Sync URL content changed: $url (hash $previousHash -> $hash)")
                                    }
                                    prefs.setSyncUrlHash(url, hash)

                                    HostsParser.parse(content).forEach {
                                        allDomains.add(it.hostname)
                                        exactBlockOrigins[it.hostname] = "Rule sync URL ${url.substringAfter("://")}"
                                    }
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
                            PrivacyLog.w(TAG, "Sync URL fetch failed: $url - ${e.message}")
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
                        PrivacyLog.w(TAG, "Blocklist refresh completed with ${failedSources.size} failed source(s): " +
                            failedSources.joinToString(", ") { it.url })
                        sourceFailureNotifier.notifyFailures(failedSources)
                    }

                    val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                    blockRules.filter { !it.isWildcard }.forEach {
                        val hostname = it.hostname.lowercase()
                        allDomains.add(hostname)
                        exactBlockOrigins[hostname] = "User block rule"
                    }
                    val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                    allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                    // Remove allowlist source domains + adblock-syntax @@|| allow rules
                    allDomains.removeAll(sourceAllowDomains)
                    dohBypassUpdater.mergeCachedInto(
                        allDomains,
                        adblockWildcardBlocks,
                        exactBlockOrigins,
                        wildcardBlockOrigins
                    )

                    val wildcards = repository.getEnabledWildcards()
                    val regexRules = repository.getEnabledRegexRules()
                    val blockingDomainCount = allDomains.size + adblockWildcardBlocks.size
                    blocklistHolder.updateAsync(
                        allDomains,
                        wildcards,
                        regexRules,
                        sourceWildcardBlocks = adblockWildcardBlocks,
                        sourceWildcardAllows = adblockWildcardAllows,
                        exactBlockOrigins = exactBlockOrigins,
                        sourceWildcardBlockOrigins = wildcardBlockOrigins,
                        sourceExactAllows = sourceAllowDomains,
                        dnsTypeRules = dnsTypeRules
                    )
                    diagnosticEvents.record(
                        DiagnosticEventType.BLOCKLIST_SWAP,
                        "Blocklist snapshot swapped",
                        mapOf(
                            "domains" to blockingDomainCount,
                            "wildcards" to wildcards.size,
                            "regex_rules" to regexRules.size,
                            "source" to "hosts_update_worker",
                            "downloaded_sources" to sourceSnapshot.downloadedSourceCount
                        )
                    )

                    prefs.setLastApplyTime(System.currentTimeMillis())
                    prefs.setLastApplyCount(blockingDomainCount)
                }
                BlockMethod.DISABLED -> { }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Blocklist update failed (attempt $runAttemptCount): ${e.message}", e)
            diagnosticEvents.record(
                DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                "Blocklist update worker failed",
                mapOf(
                    "attempt" to runAttemptCount,
                    "error" to (e.message ?: e.javaClass.simpleName)
                )
            )
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

}
