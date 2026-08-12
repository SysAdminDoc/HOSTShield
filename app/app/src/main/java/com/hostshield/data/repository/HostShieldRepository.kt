package com.hostshield.data.repository

import com.hostshield.data.database.*
import com.hostshield.data.model.*
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.sourceHttpStatus
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
    suspend fun updateSourceHealth(
        id: Long,
        health: SourceHealth,
        error: String,
        failures: Int,
        httpStatus: Int = 0
    ) = sourceDao.updateHealth(id, health, error, failures, httpStatus)

    /**
     * Persist download metadata for a successfully refreshed source using
     * targeted column updates only. Unlike a full-row `@Update`, this never
     * rewrites user-editable columns (url/label/description/enabled/category),
     * so a concurrent toggle or edit made while a long download is in flight is
     * not silently reverted.
     */
    suspend fun updateSourceDownloadMeta(
        id: Long,
        entryCount: Int,
        etag: String,
        lastModified: String,
        sizeBytes: Long,
        parseWarning: String,
        prevEntryCount: Int,
        domainsAdded: Int,
        domainsRemoved: Int,
    ) {
        sourceDao.updateSourceMeta(id, entryCount, System.currentTimeMillis(), etag, lastModified, sizeBytes)
        sourceDao.updateHealth(id, SourceHealth.OK, parseWarning, 0, 0)
        sourceDao.updateChangelog(id, prevEntryCount, domainsAdded, domainsRemoved)
    }

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
    fun getTopAllowed(since: Long, limit: Int = 10): Flow<List<TopHostname>> = logs.getTopAllowed(since, limit)
    fun getTopTrackerOwners(since: Long, limit: Int = 10): Flow<List<TrackerOwnerStat>> = logs.getTopTrackerOwners(since, limit)
    fun getTopBlockedApps(limit: Int = 20): Flow<List<TopApp>> = logs.getTopBlockedApps(limit)
    fun getBlockedCountSince(since: Long): Flow<Int> = logs.getBlockedCountSince(since)
    fun getTotalCountSince(since: Long): Flow<Int> = logs.getTotalCountSince(since)
    fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>> = logs.getHourlyBlocked(since)
    fun getHourlyTotal(since: Long): Flow<List<HourlyStat>> = logs.getHourlyTotal(since)
    fun getAllAppsWithCounts(): Flow<List<AppQueryStat>> = logs.getAllAppsWithCounts()
    fun getTopAppsSince(since: Long, limit: Int = 10): Flow<List<AppQueryStat>> = logs.getTopAppsSince(since, limit)
    fun getDomainsForApp(pkg: String, limit: Int = 200): Flow<List<AppDomainStat>> = logs.getDomainsForApp(pkg, limit)
    fun getMostQueriedDomains(since: Long, limit: Int = 30): Flow<List<TopHostname>> = logs.getMostQueriedDomains(since, limit)
    fun getDailyBreakdown(since: Long): Flow<List<DailyBreakdown>> = logs.getDailyBreakdown(since)
    fun getHourlyLatency(since: Long): Flow<List<HourlyLatency>> = logs.getHourlyLatency(since)
    fun getLatencyValues(since: Long): Flow<List<SingleFloat>> = logs.getLatencyValues(since)
    fun getQueryTypeDistribution(since: Long): Flow<List<QueryTypeStat>> = logs.getQueryTypeDistribution(since)
    fun getBlockReasonCounts(since: Long): Flow<List<DecisionReasonCount>> = logs.getBlockReasonCounts(since)
    fun getThreatIntelFeedImpact(dayStart: Long, weekStart: Long, limit: Int = 8): Flow<List<ThreatIntelFeedImpact>> =
        logs.getThreatIntelFeedImpact(dayStart, weekStart, limit)
    fun getThreatIntelTopDomains(since: Long, limit: Int = 8): Flow<List<ThreatIntelTopDomain>> =
        logs.getThreatIntelTopDomains(since, limit)
    fun getThreatIntelTopApps(since: Long, limit: Int = 8): Flow<List<ThreatIntelTopApp>> =
        logs.getThreatIntelTopApps(since, limit)
    fun getThreatIntelDailyImpact(since: Long): Flow<List<ThreatIntelDailyImpact>> =
        logs.getThreatIntelDailyImpact(since)
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

    suspend fun disableBlocking(): Result<Unit> = rootUtil.restoreHostsFile()

    fun isRootAvailable(): Boolean = rootUtil.isRootAvailable()

    suspend fun seedDefaultSources() = sources.seedDefaultSources()
}
