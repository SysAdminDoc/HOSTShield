package com.hostshield.service

import android.util.Log
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.domain.BlockDecision
import com.hostshield.util.NetworkTrackerDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the hot-path DNS event buffer and its periodic persistence.
 *
 * Packet processing should only construct an entry, update the in-memory
 * analytics streams, and enqueue it. Room and disk-cache writes stay behind
 * this boundary so the packet loop never waits on database I/O.
 */
internal class DnsLogManager(
    private val dnsLogDao: DnsLogDao,
    private val blockStatsDao: BlockStatsDao,
    private val dnsDiskCache: DnsDiskCache,
    private val networkTrackerDb: NetworkTrackerDb,
    private val connectionTracker: ConnectionTracker,
    private val dnsCache: DnsCache,
    private val loggingEnabled: () -> Boolean,
    private val emitLiveQuery: (DnsLogEntry) -> Unit,
    private val publishCacheStats: (DnsCache.CacheStats) -> Unit,
    private val publishDroppedQueries: (Int) -> Unit,
    private val tag: String = "HostShield",
) {
    companion object {
        private const val BUFFER_CAPACITY = 5_000
        private const val BATCH_SIZE = 500
        private const val FLUSH_INTERVAL_MS = 2_000L
        private const val DISK_PERSIST_CYCLES = 30
    }

    private val logBuffer = LinkedBlockingQueue<DnsLogEntry>(BUFFER_CAPACITY)
    private val pendingBlockedStats = AtomicInteger(0)
    private val pendingAllowedStats = AtomicInteger(0)
    val droppedQueries = AtomicInteger(0)
    val totalQueriesCount = AtomicInteger(0)
    private var flushJob: Job? = null

    fun record(
        domain: String,
        blocked: Boolean,
        app: Pair<String, String>,
        qtype: String,
        cnameChain: String = "",
        resolvedIps: String = "",
        responseTimeMs: Int = 0,
        upstreamServer: String = "",
        trackerCategory: String = "",
        trackerOwner: String = "",
        decision: BlockDecision? = null,
    ) {
        if (blocked) pendingBlockedStats.incrementAndGet() else pendingAllowedStats.incrementAndGet()

        var category = trackerCategory
        var owner = trackerOwner
        if (category.isEmpty()) {
            networkTrackerDb.lookup(domain)?.let { tracker ->
                category = tracker.category
                owner = tracker.owner
            }
        }

        val entry = DnsLogEntry(
            hostname = domain,
            blocked = blocked,
            appPackage = app.first,
            appLabel = app.second,
            queryType = qtype,
            cnameChain = cnameChain,
            resolvedIps = resolvedIps,
            responseTimeMs = responseTimeMs,
            upstreamServer = upstreamServer,
            trackerCategory = category,
            trackerOwner = owner,
            decisionReason = decision?.reason.orEmpty(),
            decisionSource = decision?.source.orEmpty(),
            matchedValue = decision?.matchedValue.orEmpty(),
            decisionPrecedence = decision?.precedence.orEmpty(),
        )

        connectionTracker.recordConnection(
            packageName = app.first,
            appLabel = app.second,
            domain = domain,
            queryType = qtype,
            resolvedIps = resolvedIps.split(",").filter { it.isNotBlank() },
            blocked = blocked,
            responseTimeMs = responseTimeMs,
            upstreamServer = upstreamServer,
        )
        emitLiveQuery(entry)
        totalQueriesCount.incrementAndGet()

        if (loggingEnabled() && !logBuffer.offer(entry)) {
            droppedQueries.incrementAndGet()
        }
    }

    val pendingEntries: Int
        get() = logBuffer.size

    fun start(scope: CoroutineScope) {
        cancel()
        flushJob = scope.launch(Dispatchers.IO) {
            Log.i(tag, "Log flusher started (logging=${loggingEnabled()})")
            var diskPersistCounter = 0
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                try {
                    val bufferSize = logBuffer.size
                    if (bufferSize > 0) {
                        flushLogs()
                        Log.d(tag, "Flushed $bufferSize log entries to DB")
                    }
                    flushStats()
                    publishCacheStats(dnsCache.getStats())
                    publishDroppedQueries(droppedQueries.get())

                    diskPersistCounter++
                    if (diskPersistCounter >= DISK_PERSIST_CYCLES) {
                        diskPersistCounter = 0
                        val entries = dnsCache.exportForDisk()
                        if (entries.isNotEmpty()) dnsDiskCache.persistBatch(entries)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Log flush cycle error: ${e.message}", e)
                }
            }
            Log.w(tag, "Log flusher stopped (isActive=false)")
        }
    }

    fun cancel() {
        flushJob?.cancel()
        flushJob = null
    }

    suspend fun flushForShutdown() {
        flushLogs()
        flushStats()
    }

    private suspend fun flushLogs() {
        val batch = mutableListOf<DnsLogEntry>()
        while (true) {
            val entry = logBuffer.poll() ?: break
            batch.add(entry)
            if (batch.size >= BATCH_SIZE) {
                insertBatch(batch)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) insertBatch(batch)
    }

    private suspend fun insertBatch(batch: List<DnsLogEntry>) {
        try {
            dnsLogDao.insertAll(batch.toList())
        } catch (e: Exception) {
            Log.e(tag, "Batch insert failed (${batch.size} entries): ${e.message}", e)
        }
    }

    /** Flush accumulated stats using an atomic drain so concurrent records are safe. */
    private suspend fun flushStats() {
        val blocked = pendingBlockedStats.getAndSet(0)
        val allowed = pendingAllowedStats.getAndSet(0)
        if (blocked == 0 && allowed == 0) return
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val existing = blockStatsDao.getStatsByDate(today) ?: BlockStats(date = today)
            blockStatsDao.upsert(existing.copy(
                blockedCount = existing.blockedCount + blocked,
                allowedCount = existing.allowedCount + allowed,
                totalQueries = existing.totalQueries + blocked + allowed,
            ))
        } catch (e: Exception) {
            Log.e(tag, "Stats flush failed: ${e.message}", e)
            pendingBlockedStats.addAndGet(blocked)
            pendingAllowedStats.addAndGet(allowed)
        }
    }
}
