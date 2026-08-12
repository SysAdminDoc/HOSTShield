package com.hostshield.data.database

import androidx.room.*
import com.hostshield.data.model.*
import com.hostshield.data.model.AppDnsRule
import com.hostshield.data.model.AutomationAuditEntry
import com.hostshield.data.model.TrackerScanCacheEntry
import com.hostshield.data.model.VpnStabilityEntry
import kotlinx.coroutines.flow.Flow

// Room data access objects

@Dao
interface HostSourceDao {
    @Query("SELECT * FROM host_sources ORDER BY category, label")
    fun getAllSources(): Flow<List<HostSource>>

    @Query("SELECT * FROM host_sources WHERE enabled = 1")
    suspend fun getEnabledSources(): List<HostSource>

    @Query("SELECT * FROM host_sources")
    suspend fun getAllSourcesList(): List<HostSource>

    @Query("SELECT * FROM host_sources WHERE id = :id")
    suspend fun getById(id: Long): HostSource?

    @Query("SELECT * FROM host_sources WHERE category = :category")
    fun getByCategory(category: SourceCategory): Flow<List<HostSource>>

    @Query("SELECT SUM(entry_count) FROM host_sources WHERE enabled = 1")
    fun getTotalEnabledEntries(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: HostSource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<HostSource>)

    @Update
    suspend fun update(source: HostSource)

    @Delete
    suspend fun delete(source: HostSource)

    @Query("DELETE FROM host_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        UPDATE host_sources
        SET enabled = :enabled,
            health = CASE WHEN :enabled = 0 THEN 'UNKNOWN' ELSE health END,
            last_error = CASE WHEN :enabled = 0 THEN '' ELSE last_error END,
            last_http_status = CASE WHEN :enabled = 0 THEN 0 ELSE last_http_status END,
            consecutive_failures = CASE WHEN :enabled = 0 THEN 0 ELSE consecutive_failures END
        WHERE id = :id
        """
    )
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE host_sources SET entry_count = :count, last_updated = :timestamp, etag = :etag, last_modified_online = :lastModified, size_bytes = :size WHERE id = :id")
    suspend fun updateSourceMeta(id: Long, count: Int, timestamp: Long, etag: String, lastModified: String, size: Long)

    @Query("UPDATE host_sources SET health = :health, last_error = :error, last_http_status = :httpStatus, consecutive_failures = :failures WHERE id = :id")
    suspend fun updateHealth(id: Long, health: SourceHealth, error: String, failures: Int, httpStatus: Int = 0)

    @Query("UPDATE host_sources SET prev_entry_count = :prevCount, domains_added = :added, domains_removed = :removed WHERE id = :id")
    suspend fun updateChangelog(id: Long, prevCount: Int, added: Int, removed: Int)

    @Query("SELECT * FROM host_sources WHERE enabled = 1 AND (health = 'ERROR' OR health = 'DEAD')")
    fun getUnhealthySources(): Flow<List<HostSource>>

    @Query("SELECT * FROM host_sources WHERE enabled = 1 AND category = 'ALLOWLIST'")
    suspend fun getEnabledAllowlistSources(): List<HostSource>

    @Query("SELECT * FROM host_sources WHERE enabled = 1 AND category != 'ALLOWLIST'")
    suspend fun getEnabledBlockSources(): List<HostSource>
}

@Dao
interface UserRuleDao {
    @Query("SELECT * FROM user_rules ORDER BY type, hostname")
    fun getAllRules(): Flow<List<UserRule>>

    @Query("SELECT * FROM user_rules ORDER BY type, hostname")
    suspend fun getAllRulesList(): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE type = :type AND enabled = 1 AND (expires_at = 0 OR expires_at > :now)")
    suspend fun getEnabledByType(type: RuleType, now: Long): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE type = :type")
    fun getByType(type: RuleType): Flow<List<UserRule>>

    @Query("SELECT * FROM user_rules WHERE is_wildcard = 1 AND enabled = 1 AND (expires_at = 0 OR expires_at > :now)")
    suspend fun getEnabledWildcards(now: Long): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE is_regex = 1 AND enabled = 1 AND (expires_at = 0 OR expires_at > :now)")
    suspend fun getEnabledRegexRules(now: Long): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE hostname LIKE '%' || :query || '%' ESCAPE '\'")
    fun search(query: String): Flow<List<UserRule>>

    @Query("SELECT COUNT(*) FROM user_rules WHERE type = :type")
    fun countByType(type: RuleType): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: UserRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<UserRule>)

    @Update
    suspend fun update(rule: UserRule)

    @Delete
    suspend fun delete(rule: UserRule)

    @Query("DELETE FROM user_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE user_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE user_rules SET enabled = 0 WHERE enabled = 1 AND expires_at > 0 AND expires_at <= :now")
    suspend fun disableExpired(now: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM user_rules WHERE hostname = :hostname)")
    suspend fun exists(hostname: String): Boolean
}

@Dao
interface DnsLogDao {
    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 500): Flow<List<DnsLogEntry>>

    @Query("SELECT * FROM dns_logs WHERE timestamp BETWEEN :since AND :until ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsForEvidenceExport(since: Long, until: Long, limit: Int): List<DnsLogEntry>

    @Query("SELECT * FROM dns_logs WHERE blocked = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getBlockedLogs(limit: Int = 500): Flow<List<DnsLogEntry>>

    /** Highest blocked-log ID currently present, used to detect new rows without replaying history. */
    @Query("SELECT MAX(id) FROM dns_logs WHERE blocked = 1")
    suspend fun getLatestBlockedLogId(): Long?

    /** Blocked rows inserted after [afterId], in insertion order for notification processing. */
    @Query("SELECT * FROM dns_logs WHERE blocked = 1 AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getBlockedLogsAfterId(afterId: Long, limit: Int = 50): List<DnsLogEntry>

    @Query("SELECT * FROM dns_logs WHERE hostname LIKE '%' || :query || '%' ESCAPE '\' ORDER BY timestamp DESC LIMIT :limit")
    fun searchLogs(query: String, limit: Int = 200): Flow<List<DnsLogEntry>>

    @Query("SELECT hostname, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 GROUP BY hostname ORDER BY cnt DESC LIMIT :limit")
    fun getTopBlocked(limit: Int = 20): Flow<List<TopHostname>>

    @Query("SELECT hostname, COUNT(*) as cnt FROM dns_logs WHERE blocked = 0 AND timestamp > :since GROUP BY hostname ORDER BY cnt DESC LIMIT :limit")
    fun getTopAllowed(since: Long, limit: Int = 10): Flow<List<TopHostname>>

    @Query("SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 AND app_package != '' GROUP BY app_package ORDER BY cnt DESC LIMIT :limit")
    fun getTopBlockedApps(limit: Int = 20): Flow<List<TopApp>>

    @Query("SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 AND app_package != '' AND timestamp > :since GROUP BY app_package ORDER BY cnt DESC LIMIT :limit")
    fun getTopBlockedAppsSince(since: Long, limit: Int = 20): Flow<List<TopApp>>

    @Query("SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE app_package != '' GROUP BY app_package ORDER BY cnt DESC LIMIT :limit")
    fun getTopQueryApps(limit: Int = 20): Flow<List<TopApp>>

    @Query("SELECT COUNT(*) FROM dns_logs WHERE blocked = 1 AND timestamp > :since")
    fun getBlockedCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dns_logs WHERE timestamp > :since")
    fun getTotalCountSince(since: Long): Flow<Int>

    // Hourly breakdown for charts: returns hour (0-23) and count
    @Query("""
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour, COUNT(*) as cnt
        FROM dns_logs WHERE blocked = 1 AND timestamp > :since
        GROUP BY hour ORDER BY hour
    """)
    fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>>

    @Query("""
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour, COUNT(*) as cnt
        FROM dns_logs WHERE timestamp > :since
        GROUP BY hour ORDER BY hour
    """)
    fun getHourlyTotal(since: Long): Flow<List<HourlyStat>>

    @Insert
    suspend fun insert(entry: DnsLogEntry)

    @Insert
    suspend fun insertAndGetId(entry: DnsLogEntry): Long

    @Query("UPDATE dns_logs SET app_package = :pkg, app_label = :label WHERE id = :id AND app_package = ''")
    suspend fun updateAppInfo(id: Long, pkg: String, label: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DnsLogEntry>)

    @Query("DELETE FROM dns_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    /** Batch delete for ANR-safe cleanup. Returns number of rows deleted. */
    @Transaction
    @Query("DELETE FROM dns_logs WHERE id IN (SELECT id FROM dns_logs WHERE timestamp < :before LIMIT :batchSize)")
    suspend fun deleteOldestBatch(before: Long, batchSize: Int = 1000): Int

    @Query("DELETE FROM dns_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM dns_logs")
    suspend fun getTotalLogCount(): Int

    /** Get logs filtered by app package with full detail. */
    @Query("SELECT * FROM dns_logs WHERE app_package = :pkg ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsForApp(pkg: String, limit: Int = 500): Flow<List<DnsLogEntry>>

    /** Get a single log entry by ID (for detail view). */
    @Query("SELECT * FROM dns_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DnsLogEntry?

    /** Daily breakdown: blocked + total per day for trend chart. */
    @Query("""
        SELECT date(timestamp / 1000, 'unixepoch', 'localtime') as day,
            COUNT(*) as total,
            SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked
        FROM dns_logs WHERE timestamp > :since
        GROUP BY day ORDER BY day ASC
    """)
    fun getDailyBreakdown(since: Long): Flow<List<DailyBreakdown>>

    /** Get all domains queried by a specific app package. */
    @Query("""
        SELECT hostname, MAX(blocked) as blocked, COUNT(*) as cnt
        FROM dns_logs WHERE app_package = :pkg
        GROUP BY hostname ORDER BY cnt DESC LIMIT :limit
    """)
    fun getDomainsForApp(pkg: String, limit: Int = 200): Flow<List<AppDomainStat>>

    /** Get all apps with total + blocked counts. */
    @Query("""
        SELECT app_package, app_label,
            COUNT(*) as total_queries,
            SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked_queries
        FROM dns_logs WHERE app_package != ''
        GROUP BY app_package ORDER BY total_queries DESC
    """)
    fun getAllAppsWithCounts(): Flow<List<AppQueryStat>>

    @Query("""
        SELECT app_package, app_label,
            COUNT(*) as total_queries,
            SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked_queries
        FROM dns_logs WHERE app_package != '' AND timestamp > :since
        GROUP BY app_package ORDER BY total_queries DESC LIMIT :limit
    """)
    fun getTopAppsSince(since: Long, limit: Int = 10): Flow<List<AppQueryStat>>

    /** Trending blocked domains: compare last 24h vs previous 24h. */
    @Query("""
        SELECT hostname, COUNT(*) as cnt FROM dns_logs
        WHERE blocked = 1 AND timestamp > :recentStart
        GROUP BY hostname ORDER BY cnt DESC LIMIT :limit
    """)
    suspend fun getRecentBlockedDomains(recentStart: Long, limit: Int = 30): List<TopHostname>

    @Query("""
        SELECT hostname, COUNT(*) as cnt FROM dns_logs
        WHERE blocked = 1 AND timestamp BETWEEN :olderStart AND :olderEnd
        GROUP BY hostname ORDER BY cnt DESC LIMIT :limit
    """)
    suspend fun getOlderBlockedDomains(olderStart: Long, olderEnd: Long, limit: Int = 30): List<TopHostname>

    /** Query type distribution (A, AAAA, CNAME, MX, etc). */
    @Query("""
        SELECT query_type as queryType, COUNT(*) as cnt
        FROM dns_logs WHERE timestamp > :since
        GROUP BY query_type ORDER BY cnt DESC LIMIT :limit
    """)
    fun getQueryTypeDistribution(since: Long, limit: Int = 10): Flow<List<QueryTypeStat>>

    /** Blocked-query counts grouped by raw provenance for reason facets/charts. */
    @Query("""
        SELECT decision_reason, decision_source, COUNT(*) as cnt
        FROM dns_logs
        WHERE blocked = 1 AND timestamp > :since AND decision_reason != ''
        GROUP BY decision_reason, decision_source
        ORDER BY cnt DESC
    """)
    fun getBlockReasonCounts(since: Long): Flow<List<DecisionReasonCount>>

    /** Average DNS response time per hour (for latency chart). */
    @Query("""
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour,
            AVG(response_time_ms) as avgMs,
            MAX(response_time_ms) as maxMs,
            COUNT(*) as cnt
        FROM dns_logs WHERE timestamp > :since AND response_time_ms > 0 AND blocked = 0
        GROUP BY hour ORDER BY hour
    """)
    fun getHourlyLatency(since: Long): Flow<List<HourlyLatency>>

    @Query("""
        SELECT response_time_ms as value
        FROM dns_logs WHERE timestamp > :since AND response_time_ms > 0 AND blocked = 0
        ORDER BY response_time_ms ASC
    """)
    fun getLatencyValues(since: Long): Flow<List<SingleFloat>>

    /** Top most-queried domains overall (trackers detection). */
    @Query("""
        SELECT hostname, COUNT(*) as cnt
        FROM dns_logs WHERE timestamp > :since
        GROUP BY hostname ORDER BY cnt DESC LIMIT :limit
    """)
    fun getMostQueriedDomains(since: Long, limit: Int = 30): Flow<List<TopHostname>>

    /** v6.0: Tracker category breakdown — how many queries per tracker category. */
    @Query("""
        SELECT tracker_category as category, COUNT(*) as cnt
        FROM dns_logs WHERE tracker_category != '' AND timestamp > :since
        GROUP BY tracker_category ORDER BY cnt DESC
    """)
    fun getTrackerCategoryBreakdown(since: Long): Flow<List<TrackerCategoryStat>>

    /** v6.0: Top tracker owners by query count. */
    @Query("""
        SELECT tracker_owner as owner, tracker_category as category, COUNT(*) as cnt
        FROM dns_logs WHERE tracker_owner != '' AND timestamp > :since
        GROUP BY tracker_owner ORDER BY cnt DESC LIMIT :limit
    """)
    fun getTopTrackerOwners(since: Long, limit: Int = 20): Flow<List<TrackerOwnerStat>>

    /** v6.0: Per-app tracker breakdown — which tracker categories does each app contact. */
    @Query("""
        SELECT app_package, app_label, tracker_category as category, COUNT(*) as cnt
        FROM dns_logs WHERE app_package != '' AND tracker_category != '' AND timestamp > :since
        GROUP BY app_package, tracker_category ORDER BY cnt DESC LIMIT :limit
    """)
    fun getAppTrackerBreakdown(since: Long, limit: Int = 100): Flow<List<AppTrackerStat>>

    /** v6.0: Count of tracker queries vs total for a given app. */
    @Query("""
        SELECT COUNT(*) FROM dns_logs
        WHERE app_package = :pkg AND tracker_category != '' AND timestamp > :since
    """)
    fun getTrackerCountForApp(pkg: String, since: Long): Flow<Int>

    /** Threat-intel impact by feed over the last 24h and 7d. */
    @Query("""
        SELECT decision_source as feed_name,
            CAST(SUM(CASE WHEN timestamp > :dayStart THEN 1 ELSE 0 END) AS INTEGER) as blocks_24h,
            COUNT(*) as blocks_7d,
            MAX(timestamp) as last_matched
        FROM dns_logs
        WHERE blocked = 1
            AND timestamp > :weekStart
            AND decision_source != ''
            AND decision_reason IN ('threat_intel_domain', 'threat_intel_ip')
        GROUP BY decision_source
        ORDER BY blocks_7d DESC, blocks_24h DESC
        LIMIT :limit
    """)
    fun getThreatIntelFeedImpact(
        dayStart: Long,
        weekStart: Long,
        limit: Int = 8
    ): Flow<List<ThreatIntelFeedImpact>>

    /** Top domains affected by threat-intel blocks. */
    @Query("""
        SELECT decision_source as feed_name,
            hostname,
            matched_value as matched_value,
            COUNT(*) as cnt,
            MAX(timestamp) as last_matched
        FROM dns_logs
        WHERE blocked = 1
            AND timestamp > :since
            AND decision_source != ''
            AND decision_reason IN ('threat_intel_domain', 'threat_intel_ip')
        GROUP BY decision_source, hostname, matched_value
        ORDER BY cnt DESC, last_matched DESC
        LIMIT :limit
    """)
    fun getThreatIntelTopDomains(
        since: Long,
        limit: Int = 8
    ): Flow<List<ThreatIntelTopDomain>>

    /** Top apps affected by threat-intel blocks. */
    @Query("""
        SELECT decision_source as feed_name,
            app_package as app_package,
            app_label as app_label,
            COUNT(*) as cnt,
            MAX(timestamp) as last_matched
        FROM dns_logs
        WHERE blocked = 1
            AND timestamp > :since
            AND app_package != ''
            AND decision_source != ''
            AND decision_reason IN ('threat_intel_domain', 'threat_intel_ip')
        GROUP BY decision_source, app_package, app_label
        ORDER BY cnt DESC, last_matched DESC
        LIMIT :limit
    """)
    fun getThreatIntelTopApps(
        since: Long,
        limit: Int = 8
    ): Flow<List<ThreatIntelTopApp>>

    /** Daily threat-intel block counts by feed for trend panels. */
    @Query("""
        SELECT date(timestamp / 1000, 'unixepoch', 'localtime') as day,
            decision_source as feed_name,
            COUNT(*) as cnt
        FROM dns_logs
        WHERE blocked = 1
            AND timestamp > :since
            AND decision_source != ''
            AND decision_reason IN ('threat_intel_domain', 'threat_intel_ip')
        GROUP BY day, decision_source
        ORDER BY day ASC, cnt DESC
    """)
    fun getThreatIntelDailyImpact(since: Long): Flow<List<ThreatIntelDailyImpact>>
}

@Dao
interface BlockStatsDao {
    @Query("SELECT * FROM block_stats ORDER BY date DESC LIMIT :days")
    fun getRecentStats(days: Int = 30): Flow<List<BlockStats>>

    @Query("SELECT * FROM block_stats WHERE date = :date LIMIT 1")
    suspend fun getStatsByDate(date: String): BlockStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: BlockStats)

    @Query("SELECT SUM(blocked_count) FROM block_stats")
    fun getTotalBlocked(): Flow<Int?>

    @Query("SELECT SUM(total_queries) FROM block_stats")
    fun getTotalQueries(): Flow<Int?>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name")
    fun getAllProfiles(): Flow<List<BlockingProfile>>

    @Query("SELECT * FROM profiles ORDER BY name")
    suspend fun getAllProfilesList(): List<BlockingProfile>

    @Query("SELECT * FROM profiles WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveProfile(): BlockingProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: BlockingProfile): Long

    @Update
    suspend fun update(profile: BlockingProfile)

    @Delete
    suspend fun delete(profile: BlockingProfile)

    @Query("UPDATE profiles SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE profiles SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Transaction
    suspend fun activateExclusive(id: Long) {
        deactivateAll()
        activate(id)
    }
}

// Projection classes
data class TopHostname(val hostname: String, val cnt: Int)

data class TopApp(
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    val cnt: Int
)

data class HourlyStat(val hour: Int, val cnt: Int)

data class SingleFloat(val value: Float)

data class AppDomainStat(
    val hostname: String,
    val blocked: Boolean,
    val cnt: Int
)

data class AppQueryStat(
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    @ColumnInfo(name = "total_queries") val totalQueries: Int,
    @ColumnInfo(name = "blocked_queries") val blockedQueries: Int
)

@Dao
interface FirewallRuleDao {
    @Query("SELECT * FROM firewall_rules ORDER BY app_label COLLATE NOCASE")
    fun getAllRules(): Flow<List<FirewallRule>>

    @Query("SELECT * FROM firewall_rules ORDER BY app_label COLLATE NOCASE")
    suspend fun getAllRulesList(): List<FirewallRule>

    @Query("SELECT * FROM firewall_rules WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: Int): FirewallRule?

    @Query("SELECT * FROM firewall_rules WHERE wifi_allowed = 0 OR mobile_allowed = 0 OR vpn_allowed = 0")
    fun getBlockedRules(): Flow<List<FirewallRule>>

    @Query("SELECT COUNT(*) FROM firewall_rules WHERE wifi_allowed = 0 OR mobile_allowed = 0 OR vpn_allowed = 0")
    fun getBlockedCount(): Flow<Int>

    @Query("SELECT * FROM firewall_rules WHERE app_label LIKE '%' || :query || '%' ESCAPE '\' OR package_name LIKE '%' || :query || '%' ESCAPE '\'")
    fun search(query: String): Flow<List<FirewallRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: FirewallRule): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<FirewallRule>)

    @Update
    suspend fun update(rule: FirewallRule)

    @Query("UPDATE firewall_rules SET wifi_allowed = :allowed, updated_at = :ts WHERE uid = :uid")
    suspend fun setWifi(uid: Int, allowed: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET mobile_allowed = :allowed, updated_at = :ts WHERE uid = :uid")
    suspend fun setMobile(uid: Int, allowed: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET vpn_allowed = :allowed, updated_at = :ts WHERE uid = :uid")
    suspend fun setVpn(uid: Int, allowed: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET wifi_allowed = 0, mobile_allowed = 0, vpn_allowed = 0, updated_at = :ts WHERE uid = :uid")
    suspend fun blockAll(uid: Int, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET wifi_allowed = 1, mobile_allowed = 1, vpn_allowed = 1, updated_at = :ts WHERE uid = :uid")
    suspend fun allowAll(uid: Int, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET wifi_allowed = 1, mobile_allowed = 1, vpn_allowed = 1")
    suspend fun resetAll()

    @Delete
    suspend fun delete(rule: FirewallRule)

    @Query("DELETE FROM firewall_rules WHERE uid = :uid")
    suspend fun deleteByUid(uid: Int)

    @Query("UPDATE firewall_rules SET block_screen_off = :block, updated_at = :ts WHERE uid = :uid")
    suspend fun setBlockScreenOff(uid: Int, block: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET block_background = :block, updated_at = :ts WHERE uid = :uid")
    suspend fun setBlockBackground(uid: Int, block: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET block_metered = :block, updated_at = :ts WHERE uid = :uid")
    suspend fun setBlockMetered(uid: Int, block: Boolean, ts: Long = System.currentTimeMillis())

    // v5.1: Country-based blocking
    @Query("UPDATE firewall_rules SET blocked_countries = :countries, updated_at = :ts WHERE uid = :uid")
    suspend fun setBlockedCountries(uid: Int, countries: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM firewall_rules WHERE blocked_countries != ''")
    fun getCountryBlockRules(): Flow<List<FirewallRule>>

    // v5.1: LAN access toggle
    @Query("UPDATE firewall_rules SET lan_allowed = :allowed, updated_at = :ts WHERE uid = :uid")
    suspend fun setLanAllowed(uid: Int, allowed: Boolean, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM firewall_rules WHERE block_screen_off = 1 OR block_background = 1 OR block_metered = 1 OR blocked_countries != '' OR lan_allowed = 0")
    fun getContextAwareRules(): Flow<List<FirewallRule>>
}

@Dao
interface ConnectionLogDao {
    @Query("SELECT * FROM connection_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 500): Flow<List<ConnectionLogEntry>>

    @Query("SELECT * FROM connection_log WHERE timestamp BETWEEN :since AND :until ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsForEvidenceExport(since: Long, until: Long, limit: Int): List<ConnectionLogEntry>

    @Query("SELECT * FROM connection_log WHERE action = 'REJECT' ORDER BY timestamp DESC LIMIT :limit")
    fun getBlockedLogs(limit: Int = 500): Flow<List<ConnectionLogEntry>>

    @Query("SELECT * FROM connection_log WHERE uid = :uid ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsForApp(uid: Int, limit: Int = 200): Flow<List<ConnectionLogEntry>>

    @Query("""
        SELECT uid, package_name, app_label, COUNT(*) as cnt
        FROM connection_log WHERE action = 'REJECT' AND timestamp > :since
        GROUP BY uid ORDER BY cnt DESC LIMIT :limit
    """)
    fun getTopBlockedApps(since: Long, limit: Int = 20): Flow<List<FirewallTopApp>>

    @Insert
    suspend fun insert(entry: ConnectionLogEntry)

    @Query("DELETE FROM connection_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM connection_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM connection_log WHERE action = 'REJECT'")
    fun getTotalBlockedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM connection_log WHERE action = 'REJECT' AND timestamp > :since")
    fun getBlockedCountSince(since: Long): Flow<Int>
}

data class FirewallTopApp(
    val uid: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    val cnt: Int
)

data class QueryTypeStat(
    val queryType: String,
    val cnt: Int
)

data class HourlyLatency(
    val hour: Int,
    val avgMs: Float,
    val maxMs: Int,
    val cnt: Int
)

data class DailyBreakdown(
    val day: String,
    val total: Int,
    val blocked: Int
)

data class TrackerCategoryStat(
    val category: String,
    val cnt: Int
)

data class TrackerOwnerStat(
    val owner: String,
    val category: String,
    val cnt: Int
)

data class AppTrackerStat(
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    val category: String,
    val cnt: Int
)

data class ThreatIntelFeedImpact(
    @ColumnInfo(name = "feed_name") val feedName: String,
    @ColumnInfo(name = "blocks_24h") val blocks24h: Int,
    @ColumnInfo(name = "blocks_7d") val blocks7d: Int,
    @ColumnInfo(name = "last_matched") val lastMatched: Long
)

data class ThreatIntelTopDomain(
    @ColumnInfo(name = "feed_name") val feedName: String,
    val hostname: String,
    @ColumnInfo(name = "matched_value") val matchedValue: String,
    val cnt: Int,
    @ColumnInfo(name = "last_matched") val lastMatched: Long
)

data class ThreatIntelTopApp(
    @ColumnInfo(name = "feed_name") val feedName: String,
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    val cnt: Int,
    @ColumnInfo(name = "last_matched") val lastMatched: Long
)

data class ThreatIntelDailyImpact(
    val day: String,
    @ColumnInfo(name = "feed_name") val feedName: String,
    val cnt: Int
)

data class DecisionReasonCount(
    @ColumnInfo(name = "decision_reason") val reason: String,
    @ColumnInfo(name = "decision_source") val source: String,
    val cnt: Int,
)

@Dao
interface AppDnsRuleDao {
    @Query("SELECT * FROM app_dns_rules WHERE package_name = :pkg AND enabled = 1")
    fun getRulesForApp(pkg: String): Flow<List<AppDnsRule>>

    @Query("SELECT * FROM app_dns_rules ORDER BY package_name, domain")
    fun getAllRules(): Flow<List<AppDnsRule>>

    @Query("SELECT * FROM app_dns_rules ORDER BY package_name, domain")
    suspend fun getAllRulesList(): List<AppDnsRule>

    @Query("SELECT DISTINCT package_name FROM app_dns_rules WHERE enabled = 1")
    suspend fun getAppsWithRules(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AppDnsRule): Long

    @Update
    suspend fun update(rule: AppDnsRule)

    @Delete
    suspend fun delete(rule: AppDnsRule)

    @Query("DELETE FROM app_dns_rules WHERE package_name = :pkg")
    suspend fun deleteAllForApp(pkg: String)

    @Query("SELECT COUNT(*) FROM app_dns_rules")
    fun getRuleCount(): Flow<Int>
}

@Dao
interface TrackerScanCacheDao {
    @Query("SELECT * FROM tracker_scan_cache WHERE package_name = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): TrackerScanCacheEntry?

    @Query("SELECT * FROM tracker_scan_cache ORDER BY tracker_count DESC")
    fun getAll(): Flow<List<TrackerScanCacheEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TrackerScanCacheEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<TrackerScanCacheEntry>)

    @Query("DELETE FROM tracker_scan_cache WHERE package_name = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("DELETE FROM tracker_scan_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tracker_scan_cache")
    suspend fun count(): Int
}

@Dao
interface AutomationAuditDao {
    @Query("SELECT * FROM automation_audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<AutomationAuditEntry>>

    @Insert
    suspend fun insert(entry: AutomationAuditEntry)

    @Query("DELETE FROM automation_audit_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM automation_audit_log WHERE action = :action AND timestamp > :since")
    suspend fun countActionSince(action: String, since: Long): Int
}

@Dao
interface VpnStabilityDao {
    @Query("SELECT * FROM vpn_stability WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): VpnStabilityEntry?

    @Query("SELECT * FROM vpn_stability ORDER BY date DESC LIMIT :days")
    fun getRecent(days: Int = 7): Flow<List<VpnStabilityEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VpnStabilityEntry)

    @Query("""
        SELECT COALESCE(SUM(uptime_ms), 0) as total_uptime,
            COALESCE(SUM(rebuild_count), 0) as total_rebuilds,
            COALESCE(SUM(fd_errors), 0) as total_errors,
            COALESCE(SUM(dropped_queries), 0) as total_dropped,
            COALESCE(SUM(total_queries), 0) as total_queries
        FROM vpn_stability WHERE date >= :since
    """)
    suspend fun getAggregated(since: String): VpnStabilityAggregated
}

data class VpnStabilityAggregated(
    @ColumnInfo(name = "total_uptime") val totalUptime: Long,
    @ColumnInfo(name = "total_rebuilds") val totalRebuilds: Int,
    @ColumnInfo(name = "total_errors") val totalErrors: Int,
    @ColumnInfo(name = "total_dropped") val totalDropped: Int,
    @ColumnInfo(name = "total_queries") val totalQueries: Int
)
