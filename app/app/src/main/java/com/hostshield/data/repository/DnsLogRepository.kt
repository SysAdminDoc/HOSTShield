package com.hostshield.data.repository

import com.hostshield.data.database.*
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsLogRepository @Inject constructor(
    private val logDao: DnsLogDao,
    private val statsDao: BlockStatsDao
) {
    // Logs
    fun getRecentLogs(limit: Int = 500): Flow<List<DnsLogEntry>> = logDao.getRecentLogs(limit)
    fun getBlockedLogs(limit: Int = 500): Flow<List<DnsLogEntry>> = logDao.getBlockedLogs(limit)
    fun searchLogs(query: String, limit: Int = 200): Flow<List<DnsLogEntry>> = logDao.searchLogs(query, limit)
    fun getTopBlocked(limit: Int = 20): Flow<List<TopHostname>> = logDao.getTopBlocked(limit)
    fun getTopBlockedApps(limit: Int = 20): Flow<List<TopApp>> = logDao.getTopBlockedApps(limit)
    fun getBlockedCountSince(since: Long): Flow<Int> = logDao.getBlockedCountSince(since)
    fun getTotalCountSince(since: Long): Flow<Int> = logDao.getTotalCountSince(since)
    fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>> = logDao.getHourlyBlocked(since)
    fun getHourlyTotal(since: Long): Flow<List<HourlyStat>> = logDao.getHourlyTotal(since)
    fun getAllAppsWithCounts(): Flow<List<AppQueryStat>> = logDao.getAllAppsWithCounts()
    fun getDomainsForApp(pkg: String, limit: Int = 200): Flow<List<AppDomainStat>> = logDao.getDomainsForApp(pkg, limit)
    fun getMostQueriedDomains(since: Long, limit: Int = 30): Flow<List<TopHostname>> = logDao.getMostQueriedDomains(since, limit)
    fun getDailyBreakdown(since: Long): Flow<List<DailyBreakdown>> = logDao.getDailyBreakdown(since)
    fun getHourlyLatency(since: Long): Flow<List<HourlyLatency>> = logDao.getHourlyLatency(since)
    fun getLatencyValues(since: Long): Flow<List<SingleFloat>> = logDao.getLatencyValues(since)
    fun getQueryTypeDistribution(since: Long): Flow<List<QueryTypeStat>> = logDao.getQueryTypeDistribution(since)
    fun getThreatIntelFeedImpact(dayStart: Long, weekStart: Long, limit: Int = 8): Flow<List<ThreatIntelFeedImpact>> =
        logDao.getThreatIntelFeedImpact(dayStart, weekStart, limit)
    fun getThreatIntelTopDomains(since: Long, limit: Int = 8): Flow<List<ThreatIntelTopDomain>> =
        logDao.getThreatIntelTopDomains(since, limit)
    fun getThreatIntelTopApps(since: Long, limit: Int = 8): Flow<List<ThreatIntelTopApp>> =
        logDao.getThreatIntelTopApps(since, limit)
    fun getThreatIntelDailyImpact(since: Long): Flow<List<ThreatIntelDailyImpact>> =
        logDao.getThreatIntelDailyImpact(since)
    suspend fun logDnsQuery(entry: DnsLogEntry) = logDao.insert(entry)
    suspend fun clearOldLogs(olderThanMs: Long) = logDao.deleteOlderThan(System.currentTimeMillis() - olderThanMs)
    suspend fun clearAllLogs() = logDao.deleteAll()

    // Stats
    fun getRecentStats(days: Int = 30): Flow<List<BlockStats>> = statsDao.getRecentStats(days)
    fun getTotalBlocked(): Flow<Int?> = statsDao.getTotalBlocked()
    suspend fun upsertStats(stats: BlockStats) = statsDao.upsert(stats)
}
