package com.hostshield.data.repository

import com.hostshield.data.database.*
import com.hostshield.data.model.*
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.parser.HostsParser
import com.hostshield.domain.parser.ParsedHost
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import com.hostshield.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// Repository facade for domain, database, and preference operations
//
// Backward-compatible facade over domain-specific repositories.
// New code should inject the specific repository it needs:
//   SourceRepository, RuleRepository, DnsLogRepository,
//   ProfileRepository
//
// Business logic (applyBlocking, disableBlocking) remains here
// since it coordinates across multiple domains.
// ══════════════════════════════════════════════════════════════

@Singleton
class HostShieldRepository @Inject constructor(
    private val sourceDao: HostSourceDao,
    private val ruleDao: UserRuleDao,
    private val logDao: DnsLogDao,
    private val statsDao: BlockStatsDao,
    private val profileDao: ProfileDao,
    private val downloader: SourceDownloader,
    private val rootUtil: RootUtil,
    val sources: SourceRepository,
    val rules: RuleRepository,
    val logs: DnsLogRepository,
    val profiles: ProfileRepository,
    private val diagnosticEvents: DiagnosticEventStore
) {
    // ── Sources (delegated) ─────────────────────────────────
    fun getAllSources(): Flow<List<HostSource>> = sources.getAllSources()
    fun getSourcesByCategory(cat: SourceCategory): Flow<List<HostSource>> = sources.getSourcesByCategory(cat)
    fun getTotalEnabledEntries(): Flow<Int?> = sources.getTotalEnabledEntries()
    fun getUnhealthySources(): Flow<List<HostSource>> = sources.getUnhealthySources()
    suspend fun getEnabledSourcesList(): List<HostSource> = sources.getEnabledSourcesList()
    suspend fun getEnabledBlockSources(): List<HostSource> = sources.getEnabledBlockSources()
    suspend fun getEnabledAllowlistSources(): List<HostSource> = sources.getEnabledAllowlistSources()
    suspend fun addSource(source: HostSource): Long = sources.addSource(source)
    suspend fun updateSource(source: HostSource) = sources.updateSource(source)
    suspend fun deleteSource(source: HostSource) = sources.deleteSource(source)
    suspend fun toggleSource(id: Long, enabled: Boolean) = sources.toggleSource(id, enabled)

    // ── User Rules (delegated) ──────────────────────────────
    fun getAllRules(): Flow<List<UserRule>> = rules.getAllRules()
    fun getRulesByType(type: RuleType): Flow<List<UserRule>> = rules.getRulesByType(type)
    fun searchRules(query: String): Flow<List<UserRule>> = rules.searchRules(query)
    fun getRuleCount(type: RuleType): Flow<Int> = rules.getRuleCount(type)
    suspend fun addRule(rule: UserRule): Long = rules.addRule(rule)
    suspend fun updateRule(rule: UserRule) = rules.updateRule(rule)
    suspend fun deleteRule(rule: UserRule) = rules.deleteRule(rule)
    suspend fun toggleRule(id: Long, enabled: Boolean) = rules.toggleRule(id, enabled)
    suspend fun ruleExists(hostname: String): Boolean = rules.ruleExists(hostname)
    suspend fun getEnabledWildcards(): List<UserRule> = rules.getEnabledWildcards()
    suspend fun getEnabledRegexRules(): List<UserRule> = rules.getEnabledRegexRules()
    suspend fun getEnabledRulesByType(type: RuleType): List<UserRule> = rules.getEnabledRulesByType(type)

    // ── DNS Logs (delegated) ────────────────────────────────
    fun getRecentLogs(limit: Int = 500): Flow<List<DnsLogEntry>> = logs.getRecentLogs(limit)
    fun getBlockedLogs(limit: Int = 500): Flow<List<DnsLogEntry>> = logs.getBlockedLogs(limit)
    fun searchLogs(query: String, limit: Int = 200): Flow<List<DnsLogEntry>> = logs.searchLogs(query, limit)
    fun getTopBlocked(limit: Int = 20): Flow<List<TopHostname>> = logs.getTopBlocked(limit)
    fun getTopBlockedApps(limit: Int = 20): Flow<List<TopApp>> = logs.getTopBlockedApps(limit)
    fun getBlockedCountSince(since: Long): Flow<Int> = logs.getBlockedCountSince(since)
    fun getTotalCountSince(since: Long): Flow<Int> = logs.getTotalCountSince(since)
    fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>> = logs.getHourlyBlocked(since)
    fun getHourlyTotal(since: Long): Flow<List<HourlyStat>> = logs.getHourlyTotal(since)
    fun getAllAppsWithCounts(): Flow<List<AppQueryStat>> = logs.getAllAppsWithCounts()
    fun getDomainsForApp(pkg: String, limit: Int = 200): Flow<List<AppDomainStat>> = logs.getDomainsForApp(pkg, limit)
    fun getMostQueriedDomains(since: Long, limit: Int = 30): Flow<List<TopHostname>> = logs.getMostQueriedDomains(since, limit)
    fun getDailyBreakdown(since: Long): Flow<List<DailyBreakdown>> = logs.getDailyBreakdown(since)
    fun getHourlyLatency(since: Long): Flow<List<HourlyLatency>> = logs.getHourlyLatency(since)
    fun getQueryTypeDistribution(since: Long): Flow<List<QueryTypeStat>> = logs.getQueryTypeDistribution(since)
    suspend fun logDnsQuery(entry: DnsLogEntry) = logs.logDnsQuery(entry)
    suspend fun clearOldLogs(olderThanMs: Long) = logs.clearOldLogs(olderThanMs)
    suspend fun clearAllLogs() = logs.clearAllLogs()

    // ── Stats (delegated) ───────────────────────────────────
    fun getRecentStats(days: Int = 30): Flow<List<BlockStats>> = logs.getRecentStats(days)
    fun getTotalBlocked(): Flow<Int?> = logs.getTotalBlocked()
    suspend fun upsertStats(stats: BlockStats) = logs.upsertStats(stats)

    // ── Profiles (delegated) ────────────────────────────────
    fun getAllProfiles(): Flow<List<BlockingProfile>> = profiles.getAllProfiles()
    suspend fun getAllProfilesList(): List<BlockingProfile> = profiles.getAllProfilesList()
    suspend fun getActiveProfile(): BlockingProfile? = profiles.getActiveProfile()
    suspend fun addProfile(profile: BlockingProfile): Long = profiles.addProfile(profile)
    suspend fun updateProfile(profile: BlockingProfile) = profiles.updateProfile(profile)
    suspend fun deleteProfile(profile: BlockingProfile) = profiles.deleteProfile(profile)
    suspend fun deactivateAllProfiles() = profiles.deactivateAllProfiles()
    suspend fun activateProfile(id: Long) = profiles.activateProfile(id)

    // ── Core Operations (cross-domain business logic) ───────

    suspend fun applyBlocking(
        redirectIp4: String = "0.0.0.0",
        redirectIp6: String = "::",
        includeIpv6: Boolean = true,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val enabledSources = sourceDao.getEnabledSources()
            val parsedSets = mutableListOf<Set<ParsedHost>>()

            onProgress("Downloading ${enabledSources.size} sources...")

            for ((index, source) in enabledSources.withIndex()) {
                onProgress("Downloading ${source.label} (${index + 1}/${enabledSources.size})...")
                val result = downloader.download(source)
                result.onSuccess { dl ->
                    if (!dl.notModified) {
                        val parsed = HostsParser.parse(dl.content)
                        parsedSets.add(parsed)
                        sourceDao.updateSourceMeta(
                            id = source.id,
                            count = parsed.size,
                            timestamp = System.currentTimeMillis(),
                            etag = dl.etag,
                            size = dl.sizeBytes
                        )
                    } else {
                        val fresh = downloader.download(source.copy(etag = "", lastModifiedOnline = ""))
                        fresh.onSuccess { f -> parsedSets.add(HostsParser.parse(f.content)) }
                    }
                    sourceDao.updateHealth(source.id, SourceHealth.OK, "", 0)
                }.onFailure { err ->
                    val failures = source.consecutiveFailures + 1
                    val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                    sourceDao.updateHealth(source.id, health, err.message ?: "Unknown", failures)
                    diagnosticEvents.record(
                        DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                        "Source download failed during manual apply",
                        mapOf(
                            "source" to source.url,
                            "label" to source.label,
                            "error" to (err.message ?: err.javaClass.simpleName),
                            "failures" to failures
                        )
                    )
                    onProgress("Failed: ${source.label} — ${err.message}")
                }
            }

            onProgress("Merging and deduplicating...")
            val userRules = ruleDao.getEnabledByType(RuleType.BLOCK) +
                    ruleDao.getEnabledByType(RuleType.ALLOW) +
                    ruleDao.getEnabledByType(RuleType.REDIRECT) +
                    ruleDao.getEnabledWildcards()

            val hostsContent = HostsParser.buildHostsFile(
                parsedSets, userRules, redirectIp4, redirectIp6, includeIpv6
            )

            val totalDomains = HostsParser.countUniqueDomains(parsedSets)

            onProgress("Writing hosts file ($totalDomains domains)...")
            val writeResult = rootUtil.writeHostsFile(hostsContent)
            writeResult.onFailure { return@withContext Result.failure(it) }

            onProgress("Done! $totalDomains domains blocked.")
            Result.success(totalDomains)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disableBlocking(): Result<Unit> = rootUtil.restoreHostsFile()

    fun isRootAvailable(): Boolean = rootUtil.isRootAvailable()

    suspend fun seedDefaultSources() = sources.seedDefaultSources()
}
