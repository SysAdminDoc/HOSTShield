package com.hostshield.data.database

import androidx.room.*
import com.hostshield.data.model.*
import kotlinx.coroutines.flow.Flow

// HostShield v0.3.0 - Data Access Objects

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

    @Query("UPDATE host_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE host_sources SET entry_count = :count, last_updated = :timestamp, etag = :etag, size_bytes = :size WHERE id = :id")
    suspend fun updateSourceMeta(id: Long, count: Int, timestamp: Long, etag: String, size: Long)

    @Query("UPDATE host_sources SET health = :health, last_error = :error, consecutive_failures = :failures WHERE id = :id")
    suspend fun updateHealth(id: Long, health: SourceHealth, error: String, failures: Int)

    @Query("SELECT * FROM host_sources WHERE health = 'ERROR' OR health = 'DEAD'")
    fun getUnhealthySources(): Flow<List<HostSource>>
}

@Dao
interface UserRuleDao {
    @Query("SELECT * FROM user_rules ORDER BY type, hostname")
    fun getAllRules(): Flow<List<UserRule>>

    @Query("SELECT * FROM user_rules ORDER BY type, hostname")
    suspend fun getAllRulesList(): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE type = :type AND enabled = 1")
    suspend fun getEnabledByType(type: RuleType): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE type = :type")
    fun getByType(type: RuleType): Flow<List<UserRule>>

    @Query("SELECT * FROM user_rules WHERE is_wildcard = 1 AND enabled = 1")
    suspend fun getEnabledWildcards(): List<UserRule>

    @Query("SELECT * FROM user_rules WHERE hostname LIKE '%' || :query || '%'")
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

    @Query("SELECT EXISTS(SELECT 1 FROM user_rules WHERE hostname = :hostname)")
    suspend fun exists(hostname: String): Boolean
}

@Dao
interface DnsLogDao {
    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 500): Flow<List<DnsLogEntry>>

    @Query("SELECT * FROM dns_logs WHERE blocked = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getBlockedLogs(limit: Int = 500): Flow<List<DnsLogEntry>>

    @Query("SELECT * FROM dns_logs WHERE hostname LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun searchLogs(query: String, limit: Int = 200): Flow<List<DnsLogEntry>>

    @Query("SELECT hostname, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 GROUP BY hostname ORDER BY cnt DESC LIMIT :limit")
    fun getTopBlocked(limit: Int = 20): Flow<List<TopHostname>>

    @Query("SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE blocked = 1 AND app_package != '' GROUP BY app_package ORDER BY cnt DESC LIMIT :limit")
    fun getTopBlockedApps(limit: Int = 20): Flow<List<TopApp>>

    @Query("SELECT app_package, app_label, COUNT(*) as cnt FROM dns_logs WHERE app_package != '' GROUP BY app_package ORDER BY cnt DESC LIMIT :limit")
    fun getTopQueryApps(limit: Int = 20): Flow<List<TopApp>>

    @Query("SELECT COUNT(*) FROM dns_logs WHERE blocked = 1 AND timestamp > :since")
    fun getBlockedCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dns_logs WHERE timestamp > :since")
    fun getTotalCountSince(since: Long): Flow<Int>

    // Hourly breakdown for charts: returns hour (0-23) and count
    @Query("""
        SELECT CAST((timestamp / 3600000) % 24 AS INTEGER) as hour, COUNT(*) as cnt
        FROM dns_logs WHERE blocked = 1 AND timestamp > :since
        GROUP BY hour ORDER BY hour
    """)
    fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>>

    @Query("""
        SELECT CAST((timestamp / 3600000) % 24 AS INTEGER) as hour, COUNT(*) as cnt
        FROM dns_logs WHERE timestamp > :since
        GROUP BY hour ORDER BY hour
    """)
    fun getHourlyTotal(since: Long): Flow<List<HourlyStat>>

    @Insert
    suspend fun insert(entry: DnsLogEntry)

    @Insert
    suspend fun insertAll(entries: List<DnsLogEntry>)

    @Query("DELETE FROM dns_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM dns_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM dns_logs")
    suspend fun getTotalLogCount(): Int

    /** Get all domains queried by a specific app package. */
    @Query("""
        SELECT hostname, blocked, COUNT(*) as cnt
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

    /** Top most-queried domains overall (trackers detection). */
    @Query("""
        SELECT hostname, COUNT(*) as cnt
        FROM dns_logs WHERE timestamp > :since
        GROUP BY hostname ORDER BY cnt DESC LIMIT :limit
    """)
    fun getMostQueriedDomains(since: Long, limit: Int = 30): Flow<List<TopHostname>>
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
}

// Projection classes
data class TopHostname(val hostname: String, val cnt: Int)

data class TopApp(
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    val cnt: Int
)

data class HourlyStat(val hour: Int, val cnt: Int)

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
