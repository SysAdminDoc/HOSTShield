package com.hostshield.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time per-app connection tracker (Roadmap #46).
 *
 * Called from the VPN packet loop to record every DNS transaction,
 * then exposes live updates via [connectionFlow] and snapshot
 * queries for the UI / export layers.
 *
 * Ring-buffer limits:
 *  - [MAX_RECORDS_PER_APP] = 500 records per package
 *  - [MAX_TOTAL_RECORDS]   = 5 000 records across all apps
 */
@Singleton
class ConnectionTracker @Inject constructor() {

    // ── Data models ────────────────────────────────────────────

    data class ConnectionRecord(
        val packageName: String,
        val appLabel: String,
        val domain: String,
        val queryType: String,
        val resolvedIps: List<String>,
        val timestamp: Long,
        val blocked: Boolean,
        val responseTimeMs: Int,
        val upstreamServer: String,
    )

    data class AppConnectionSummary(
        val packageName: String,
        val appLabel: String,
        val totalQueries: Int,
        val blockedQueries: Int,
        val uniqueDomains: Int,
        val lastQueryTime: Long,
        val topDomains: List<Pair<String, Int>>,
    )

    // ── Constants ──────────────────────────────────────────────

    companion object {
        private const val MAX_RECORDS_PER_APP = 500
        private const val MAX_TOTAL_RECORDS = 5_000
        private const val TOP_DOMAINS_LIMIT = 10
    }

    // ── Storage ────────────────────────────────────────────────

    private val records = ConcurrentHashMap<String, MutableList<ConnectionRecord>>()

    @Volatile
    private var totalCount = 0

    // ── Live updates ───────────────────────────────────────────

    private val _connectionFlow = MutableSharedFlow<ConnectionRecord>(
        replay = 1,
        extraBufferCapacity = 100,
    )
    val connectionFlow: SharedFlow<ConnectionRecord> = _connectionFlow.asSharedFlow()

    // ── Public API ─────────────────────────────────────────────

    /**
     * Record a DNS connection. Called from the VPN packet loop.
     */
    fun recordConnection(
        packageName: String,
        appLabel: String,
        domain: String,
        queryType: String,
        resolvedIps: List<String>,
        blocked: Boolean,
        responseTimeMs: Int,
        upstreamServer: String,
    ) {
        val record = ConnectionRecord(
            packageName = packageName,
            appLabel = appLabel,
            domain = domain,
            queryType = queryType,
            resolvedIps = resolvedIps,
            timestamp = System.currentTimeMillis(),
            blocked = blocked,
            responseTimeMs = responseTimeMs,
            upstreamServer = upstreamServer,
        )

        synchronized(records) {
            val appList = records.getOrPut(packageName) { mutableListOf() }

            // Per-app ring buffer
            if (appList.size >= MAX_RECORDS_PER_APP) {
                appList.removeAt(0)
                totalCount--
            }

            // Global ring buffer — evict oldest record across all apps
            if (totalCount >= MAX_TOTAL_RECORDS) {
                evictOldestRecord()
            }

            appList.add(record)
            totalCount++
        }

        _connectionFlow.tryEmit(record)
    }

    /**
     * All records across every app, sorted newest-first.
     */
    fun getActiveConnections(): List<ConnectionRecord> {
        return snapshot().sortedByDescending { it.timestamp }
    }

    /**
     * All records for a single app, sorted newest-first.
     */
    fun getConnectionsForApp(packageName: String): List<ConnectionRecord> {
        synchronized(records) {
            return records[packageName]?.toList().orEmpty()
        }.sortedByDescending { it.timestamp }
    }

    /**
     * Aggregated per-app summaries, sorted by total queries descending.
     */
    fun getAppSummaries(): List<AppConnectionSummary> {
        val snap: Map<String, List<ConnectionRecord>>
        synchronized(records) {
            snap = records.mapValues { it.value.toList() }
        }
        return snap.map { (pkg, list) -> buildSummary(pkg, list) }
            .sortedByDescending { it.totalQueries }
    }

    /**
     * Summary for a single app, or null if no records exist.
     */
    fun getAppSummary(packageName: String): AppConnectionSummary? {
        val list: List<ConnectionRecord>
        synchronized(records) {
            list = records[packageName]?.toList() ?: return null
        }
        return buildSummary(packageName, list)
    }

    /**
     * Most recent [limit] records across all apps.
     */
    fun getRecentConnections(limit: Int = 50): List<ConnectionRecord> {
        return snapshot()
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * Total number of tracked records.
     */
    fun getTotalConnectionCount(): Int = totalCount

    /**
     * Clear all tracking data.
     */
    fun clear() {
        synchronized(records) {
            records.clear()
            totalCount = 0
        }
    }

    /**
     * Clear tracking data for a single app.
     */
    fun clearForApp(packageName: String) {
        synchronized(records) {
            val removed = records.remove(packageName)
            if (removed != null) {
                totalCount -= removed.size
            }
        }
    }

    // ── Internal helpers ───────────────────────────────────────

    /**
     * Flat snapshot of every record (inside synchronized block).
     */
    private fun snapshot(): List<ConnectionRecord> {
        synchronized(records) {
            return records.values.flatMap { it.toList() }
        }
    }

    /**
     * Evict the single oldest record across all apps.
     * Must be called inside `synchronized(records)`.
     */
    private fun evictOldestRecord() {
        var oldestTime = Long.MAX_VALUE
        var oldestPkg: String? = null

        for ((pkg, list) in records) {
            val first = list.firstOrNull() ?: continue
            if (first.timestamp < oldestTime) {
                oldestTime = first.timestamp
                oldestPkg = pkg
            }
        }

        oldestPkg?.let { pkg ->
            val list = records[pkg] ?: return
            list.removeAt(0)
            totalCount--
            if (list.isEmpty()) records.remove(pkg)
        }
    }

    /**
     * Build an [AppConnectionSummary] from a list of records.
     */
    private fun buildSummary(
        packageName: String,
        list: List<ConnectionRecord>,
    ): AppConnectionSummary {
        val appLabel = list.lastOrNull()?.appLabel ?: packageName
        val domainCounts = mutableMapOf<String, Int>()
        var blocked = 0

        for (r in list) {
            if (r.blocked) blocked++
            domainCounts[r.domain] = (domainCounts[r.domain] ?: 0) + 1
        }

        val topDomains = domainCounts.entries
            .sortedByDescending { it.value }
            .take(TOP_DOMAINS_LIMIT)
            .map { it.key to it.value }

        return AppConnectionSummary(
            packageName = packageName,
            appLabel = appLabel,
            totalQueries = list.size,
            blockedQueries = blocked,
            uniqueDomains = domainCounts.size,
            lastQueryTime = list.maxOf { it.timestamp },
            topDomains = topDomains,
        )
    }
}
