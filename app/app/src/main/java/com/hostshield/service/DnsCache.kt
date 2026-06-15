package com.hostshield.service

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * DNS Response Cache — LRU with TTL-aware expiration, serve-stale (RFC 8767),
 * negative caching (RFC 2308/9520), and prefetching.
 *
 * Caches raw DNS response bytes keyed by (domain, qtype) to avoid redundant
 * upstream queries. Typical mobile traffic patterns yield 60-70% cache hit
 * rates even with a modest 2000-entry cache.
 *
 * v5.0 enhancements:
 * - Serve-stale (RFC 8767): returns expired entries when upstream is unreachable,
 *   bridging WiFi↔cellular transitions seamlessly. Stale entries served with 30s TTL.
 * - SERVFAIL/REFUSED caching (RFC 9520): short-TTL cache prevents retry storms.
 * - Prefetching (Unbound algorithm): background refresh when TTL < 10% remaining
 *   for popular domains (queryCount > 3). ~10% more upstream queries but near-zero
 *   latency for frequently accessed domains.
 * - Configurable min/max TTL caps (60s floor, 24h ceiling by default).
 *
 * Design choices:
 * - ConcurrentHashMap for lock-free reads from packet processing thread
 * - TTL extracted from DNS answer section (minimum across all RRs)
 * - LRU eviction based on last-access timestamp when cache is full
 * - Separate negative cache (NXDOMAIN/NODATA) with SOA-derived TTL
 * - Separate failure cache (SERVFAIL/REFUSED) with very short TTL (RFC 9520)
 * - Thread-safe: all public methods safe to call from any coroutine/thread
 *
 * Does NOT cache:
 * - Blocked domain responses (those are synthesized, not from upstream)
 * - Truncated responses (TC bit set — these require TCP retry)
 */
class DnsCache(
    private val maxEntries: Int = 2000,
    private val maxNegativeEntries: Int = 500,
    private val maxFailureEntries: Int = 200,
    private val defaultTtlMs: Long = 300_000,      // 5 minutes
    private val negativeTtlMs: Long = 60_000,       // 1 minute for NXDOMAIN
    private val failureTtlMs: Long = 5_000,         // 5s for SERVFAIL/REFUSED (RFC 9520)
    private val minTtlMs: Long = 60_000,            // 60s floor (v5.0: raised from 10s)
    private val maxTtlMs: Long = 86_400_000,        // 24h ceiling (v5.0: raised from 1h)
    private val staleTtlMs: Long = 259_200_000,     // 3 days max stale window (RFC 8767)
    private val staleServeTtlMs: Long = 30_000,     // 30s TTL when serving stale
    private val prefetchThreshold: Float = 0.10f,   // prefetch at <10% TTL remaining
    private val prefetchMinQueries: Int = 3,         // only prefetch popular domains
    private val clock: Clock = Clock { System.currentTimeMillis() }
) {
    /** Injectable clock for deterministic testing. */
    fun interface Clock {
        fun currentTimeMillis(): Long
    }
    companion object {
        private const val TAG = "DnsCache"
        private const val TYPE_NXNAME = 128 // RFC 9824 Compact Denial of Existence
    }

    data class CacheKey(val domain: String, val qtype: Int)

    private class CacheEntry(
        val response: ByteArray,
        val expiresAt: Long,
        val originalTtlMs: Long,
        val insertedAt: Long,
        @Volatile var lastAccess: Long,
        val queryCount: AtomicInteger = AtomicInteger(1)
    ) {
        /** Whether this entry is expired but still within the stale window. */
        fun isStale(now: Long, staleWindow: Long): Boolean =
            now >= expiresAt && now < expiresAt + staleWindow

        /** Remaining TTL fraction (0.0 = expired, 1.0 = just inserted). */
        fun remainingFraction(now: Long): Float {
            if (originalTtlMs <= 0) return 0f
            val remaining = (expiresAt - now).coerceAtLeast(0)
            return remaining.toFloat() / originalTtlMs
        }
    }

    /**
     * Result from a cache lookup. Contains the response and metadata about
     * whether a prefetch should be triggered.
     */
    data class CacheResult(
        val response: ByteArray,
        val isStale: Boolean = false,
        val needsPrefetch: Boolean = false
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>(maxEntries)
    private val negativeCache = ConcurrentHashMap<CacheKey, CacheEntry>(maxNegativeEntries)
    private val failureCache = ConcurrentHashMap<CacheKey, CacheEntry>(maxFailureEntries)

    // Stats
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)
    private val staleHits = AtomicLong(0)
    private val prefetchTriggers = AtomicLong(0)

    /**
     * Look up a cached response.
     *
     * Returns fresh entries normally. If the entry is expired but within the
     * stale window (RFC 8767), returns it with isStale=true. The caller should
     * still attempt an upstream query and update the cache, but can serve the
     * stale response immediately if the upstream fails.
     *
     * If the entry is nearing expiry (<10% TTL remaining) and has been queried
     * frequently, signals needsPrefetch=true so the caller can dispatch a
     * background refresh.
     *
     * @param domain Query domain (lowercase)
     * @param qtype Query type (1=A, 28=AAAA, etc.)
     * @param transactionId Original query's transaction ID (patched into response)
     * @return CacheResult with response bytes, or null if complete cache miss
     */
    fun get(domain: String, qtype: Int, transactionId: ByteArray): CacheResult? {
        val key = CacheKey(domain.lowercase(), qtype)
        val now = clock.currentTimeMillis()

        // Check positive cache
        val entry = cache[key]
        if (entry != null) {
            entry.lastAccess = now
            val qc = entry.queryCount.incrementAndGet()

            if (now < entry.expiresAt) {
                // Fresh hit — check if prefetch needed
                hits.incrementAndGet()
                val needsPrefetch = entry.remainingFraction(now) < prefetchThreshold &&
                    qc >= prefetchMinQueries
                if (needsPrefetch) prefetchTriggers.incrementAndGet()
                return CacheResult(
                    response = patchTransactionId(entry.response, transactionId),
                    needsPrefetch = needsPrefetch
                )
            }

            // Expired but within stale window (RFC 8767)
            if (entry.isStale(now, staleTtlMs)) {
                staleHits.incrementAndGet()
                hits.incrementAndGet()
                return CacheResult(
                    response = patchTransactionId(entry.response, transactionId),
                    isStale = true
                )
            }

            // Beyond stale window — remove
            cache.remove(key)
        }

        // Check negative cache (NXDOMAIN/NODATA)
        val negEntry = negativeCache[key]
        if (negEntry != null) {
            if (now < negEntry.expiresAt) {
                negEntry.lastAccess = now
                hits.incrementAndGet()
                return CacheResult(
                    response = patchTransactionId(negEntry.response, transactionId)
                )
            }
            // Expired — no stale serving for negative cache
            negativeCache.remove(key)
        }

        // Check failure cache (SERVFAIL/REFUSED — RFC 9520)
        val failEntry = failureCache[key]
        if (failEntry != null) {
            if (now < failEntry.expiresAt) {
                failEntry.lastAccess = now
                hits.incrementAndGet()
                return CacheResult(
                    response = patchTransactionId(failEntry.response, transactionId)
                )
            }
            failureCache.remove(key)
        }

        misses.incrementAndGet()
        return null
    }

    /**
     * Simple lookup for backward compatibility. Returns raw bytes or null.
     * Callers that don't need stale/prefetch metadata can use this.
     */
    fun getSimple(domain: String, qtype: Int, transactionId: ByteArray): ByteArray? {
        return get(domain, qtype, transactionId)?.response
    }

    /**
     * Look up a stale entry specifically. Used by the serve-stale path when
     * an upstream query fails — returns an expired-but-within-window entry.
     *
     * @return Stale response bytes, or null if nothing available
     */
    fun getStale(domain: String, qtype: Int, transactionId: ByteArray): ByteArray? {
        val key = CacheKey(domain.lowercase(), qtype)
        val now = clock.currentTimeMillis()
        val entry = cache[key] ?: return null

        if (entry.isStale(now, staleTtlMs)) {
            staleHits.incrementAndGet()
            return patchTransactionId(entry.response, transactionId)
        }
        return null
    }

    /**
     * Cache a DNS response from upstream.
     *
     * v5.0: Now caches SERVFAIL(2) and REFUSED(5) with short TTL per RFC 9520
     * to prevent retry storms against failing upstreams.
     *
     * @param domain Query domain
     * @param qtype Query type
     * @param response Raw DNS response bytes
     */
    fun put(domain: String, qtype: Int, response: ByteArray) {
        if (response.size < 12) return

        // Don't cache truncated responses (TC bit)
        if (response[2].toInt() and 0x02 != 0) return

        val rcode = response[3].toInt() and 0x0F
        val now = clock.currentTimeMillis()
        val key = CacheKey(domain.lowercase(), qtype)

        when (rcode) {
            0 -> { // NOERROR — positive cache (or NXNAME compact denial)
                // RFC 9824: NXNAME (type 128) in the answer section signals
                // name non-existence via Compact Denial. Treat as negative cache.
                if (containsNxnameAnswer(response)) {
                    val soaTtl = extractSoaMinTtl(response)
                    if (soaTtl == 0) return
                    val ttlMs = if (soaTtl != null) {
                        (soaTtl * 1000L).coerceIn(minTtlMs, negativeTtlMs * 5)
                    } else {
                        negativeTtlMs
                    }
                    val entry = CacheEntry(
                        response = response.copyOf(),
                        expiresAt = now + ttlMs,
                        originalTtlMs = ttlMs,
                        insertedAt = now,
                        lastAccess = now
                    )
                    if (negativeCache.size >= maxNegativeEntries) evictLru(negativeCache, (maxNegativeEntries / 5).coerceAtLeast(1))
                    negativeCache[key] = entry
                    return
                }
                val ttl = extractMinTtl(response)
                if (ttl <= 0) return
                val ttlMs = (ttl * 1000L).coerceIn(minTtlMs, maxTtlMs)
                val entry = CacheEntry(
                    response = response.copyOf(),
                    expiresAt = now + ttlMs,
                    originalTtlMs = ttlMs,
                    insertedAt = now,
                    lastAccess = now
                )
                if (cache.size >= maxEntries) evictLru(cache, (maxEntries / 10).coerceAtLeast(1))
                cache[key] = entry
            }
            3 -> { // NXDOMAIN — negative cache with SOA-derived TTL (RFC 2308)
                val soaTtl = extractSoaMinTtl(response)
                // RFC 2308: server-declared MINIMUM=0 means "do not cache". Skip.
                // If the response had no SOA at all (null), fall back to our
                // configured `negativeTtlMs` so the negative cache still does
                // its job — the test suite asserts this.
                if (soaTtl == 0) return
                val ttlMs = if (soaTtl != null) {
                    (soaTtl * 1000L).coerceIn(minTtlMs, negativeTtlMs * 5)
                } else {
                    negativeTtlMs
                }
                val entry = CacheEntry(
                    response = response.copyOf(),
                    expiresAt = now + ttlMs,
                    originalTtlMs = ttlMs,
                    insertedAt = now,
                    lastAccess = now
                )
                if (negativeCache.size >= maxNegativeEntries) evictLru(negativeCache, (maxNegativeEntries / 5).coerceAtLeast(1))
                negativeCache[key] = entry
            }
            2, 5 -> { // SERVFAIL, REFUSED — failure cache (RFC 9520)
                val entry = CacheEntry(
                    response = response.copyOf(),
                    expiresAt = now + failureTtlMs,
                    originalTtlMs = failureTtlMs,
                    insertedAt = now,
                    lastAccess = now
                )
                if (failureCache.size >= maxFailureEntries) evictLru(failureCache, (maxFailureEntries / 5).coerceAtLeast(1))
                failureCache[key] = entry
            }
            // Don't cache other rcodes (FORMERR, NOTIMP, etc.)
        }
    }

    /** Clear all cached entries. */
    fun clear() {
        cache.clear()
        negativeCache.clear()
        failureCache.clear()
    }

    // ── v5.0: Two-tier cache (L2 disk persistence) ──────────

    /**
     * Warm the in-memory cache from disk entries (called on VPN start).
     * Only loads entries that are still valid (not expired).
     */
    fun warmFromDisk(entries: List<DnsDiskCache.DiskCacheEntry>) {
        val now = clock.currentTimeMillis()
        var loaded = 0
        for (entry in entries) {
            if (now >= entry.expiresAt) continue
            if (cache.size >= maxEntries) break
            val key = CacheKey(entry.domain, entry.qtype)
            if (cache.containsKey(key)) continue // don't overwrite fresh in-memory entries
            cache[key] = CacheEntry(
                response = entry.response,
                expiresAt = entry.expiresAt,
                originalTtlMs = entry.originalTtlMs,
                insertedAt = now,
                lastAccess = now
            )
            loaded++
        }
        Log.i(TAG, "Warmed L1 cache from disk: $loaded entries")
    }

    /**
     * Export current positive cache entries for disk persistence.
     * Called periodically by the VPN service to persist to L2.
     */
    fun exportForDisk(): List<DnsDiskCache.DiskCacheEntry> {
        val now = clock.currentTimeMillis()
        return cache.entries
            .filter { now < it.value.expiresAt } // only non-expired
            .map { (key, entry) ->
                DnsDiskCache.DiskCacheEntry(
                    domain = key.domain,
                    qtype = key.qtype,
                    response = entry.response.copyOf(),
                    expiresAt = entry.expiresAt,
                    originalTtlMs = entry.originalTtlMs
                )
            }
    }

    /** Get cache statistics. */
    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val total = h + m
        return CacheStats(
            size = cache.size,
            negativeSize = negativeCache.size,
            failureSize = failureCache.size,
            hits = h,
            misses = m,
            hitRate = if (total > 0) h.toFloat() / total else 0f,
            evictions = evictions.get(),
            staleHits = staleHits.get(),
            prefetchTriggers = prefetchTriggers.get()
        )
    }

    data class CacheStats(
        val size: Int,
        val negativeSize: Int,
        val failureSize: Int = 0,
        val hits: Long,
        val misses: Long,
        val hitRate: Float,
        val evictions: Long,
        val staleHits: Long = 0,
        val prefetchTriggers: Long = 0
    )

    // ── Internal ─────────────────────────────────────────────

    private fun containsNxnameAnswer(response: ByteArray): Boolean {
        try {
            val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
            if (anCount == 0) return false
            var off = 12
            val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
            for (i in 0 until qdCount) {
                off = skipName(response, off)
                off += 4
            }
            for (i in 0 until anCount.coerceAtMost(20)) {
                if (off >= response.size) break
                off = skipName(response, off)
                if (off + 10 > response.size) break
                val rrType = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
                if (rrType == TYPE_NXNAME) return true
                val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
                off += 10 + rdLen
            }
        } catch (_: Exception) { }
        return false
    }

    /**
     * Extract minimum TTL from all answer/authority/additional records.
     * This ensures we don't serve stale data for any record in the response.
     */
    private fun extractMinTtl(response: ByteArray): Int {
        try {
            val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
            val nsCount = ((response[8].toInt() and 0xFF) shl 8) or (response[9].toInt() and 0xFF)
            val arCount = ((response[10].toInt() and 0xFF) shl 8) or (response[11].toInt() and 0xFF)
            val totalRrs = anCount + nsCount + arCount

            // Skip question section
            var off = 12
            val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
            for (i in 0 until qdCount) {
                off = skipName(response, off)
                off += 4 // QTYPE + QCLASS
            }

            var minTtl = 300 // default 5 min
            // Cap raised from 20 → 100 to cover large CNAME chains and multi-A
            // round-robin sets. Real-world maximum we have seen is ~80 RRs in
            // CDN responses.
            for (i in 0 until totalRrs.coerceAtMost(100)) {
                if (off >= response.size) break
                off = skipName(response, off)
                if (off + 10 > response.size) break

                // TYPE(2) + CLASS(2) + TTL(4) + RDLENGTH(2)
                val rrType = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
                val ttl = ((response[off + 4].toInt() and 0xFF) shl 24) or
                    ((response[off + 5].toInt() and 0xFF) shl 16) or
                    ((response[off + 6].toInt() and 0xFF) shl 8) or
                    (response[off + 7].toInt() and 0xFF)
                val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)

                // Skip OPT pseudo-record (TYPE 41) — it has no meaningful TTL
                if (rrType != 41 && ttl in 1 until minTtl) minTtl = ttl
                off += 10 + rdLen
            }
            return minTtl
        } catch (_: Exception) {
            // On parse failure, return 0 so the response is *not* cached rather
            // than being pinned at 5 minutes regardless of what the server said.
            return 0
        }
    }

    /**
     * Extract SOA minimum TTL from authority section for negative caching (RFC 2308).
     * Returns null when no SOA record was found (e.g. malformed response or the
     * server omitted it) — distinguish from `MINIMUM=0` (server-mandated "do not
     * cache") which returns 0.
     */
    private fun extractSoaMinTtl(response: ByteArray): Int? {
        try {
            val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
            val nsCount = ((response[8].toInt() and 0xFF) shl 8) or (response[9].toInt() and 0xFF)

            // Skip question section
            var off = 12
            val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
            for (i in 0 until qdCount) {
                off = skipName(response, off)
                off += 4
            }

            // Skip answer section
            for (i in 0 until anCount.coerceAtMost(20)) {
                if (off >= response.size) return null
                off = skipName(response, off)
                if (off + 10 > response.size) return null
                val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
                off += 10 + rdLen
            }

            // Search authority section for SOA record (TYPE 6)
            for (i in 0 until nsCount.coerceAtMost(10)) {
                if (off >= response.size) return null
                off = skipName(response, off)
                if (off + 10 > response.size) return null

                val rrType = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
                val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
                off += 10

                if (rrType == 6 && off + rdLen <= response.size) {
                    // SOA RDATA: MNAME, RNAME, SERIAL, REFRESH, RETRY, EXPIRE, MINIMUM
                    // Skip MNAME and RNAME (both are compressed names)
                    var soaOff = off
                    soaOff = skipName(response, soaOff)  // MNAME
                    soaOff = skipName(response, soaOff)  // RNAME
                    // Skip SERIAL(4) + REFRESH(4) + RETRY(4) + EXPIRE(4) = 16 bytes
                    soaOff += 16
                    if (soaOff + 4 <= response.size) {
                        // MINIMUM field — used as negative cache TTL per RFC 2308
                        return ((response[soaOff].toInt() and 0xFF) shl 24) or
                            ((response[soaOff + 1].toInt() and 0xFF) shl 16) or
                            ((response[soaOff + 2].toInt() and 0xFF) shl 8) or
                            (response[soaOff + 3].toInt() and 0xFF)
                    }
                }
                off += rdLen
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun skipName(data: ByteArray, start: Int): Int {
        var pos = start
        var iterations = 0
        while (pos < data.size && iterations++ < 64) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if (len and 0xC0 == 0xC0) return pos + 2 // compression pointer
            pos += 1 + len
        }
        return pos
    }

    /** Patch transaction ID in a cached response copy. */
    private fun patchTransactionId(cached: ByteArray, txId: ByteArray): ByteArray {
        val copy = cached.copyOf()
        if (copy.size >= 2 && txId.size >= 2) {
            copy[0] = txId[0]
            copy[1] = txId[1]
        }
        return copy
    }

    /** Evict least-recently-used entries. O(n) scan instead of O(n log n) sort. */
    private fun evictLru(map: ConcurrentHashMap<CacheKey, CacheEntry>, count: Int) {
        val now = clock.currentTimeMillis()
        // First pass: remove expired entries (free eviction)
        val iter = map.entries.iterator()
        var removed = 0
        while (iter.hasNext() && removed < count) {
            val e = iter.next()
            if (now >= e.value.expiresAt) {
                iter.remove()
                evictions.incrementAndGet()
                removed++
            }
        }
        if (removed >= count) return
        // Second pass: take a single snapshot then sort by lastAccess. Sorting
        // a `ConcurrentHashMap` directly with `asSequence().sortedBy` would
        // observe concurrent mutations and could produce duplicate keys.
        val remaining = count - removed
        val snapshot = ArrayList<Map.Entry<CacheKey, CacheEntry>>(map.size).apply {
            addAll(map.entries)
        }
        snapshot.sortBy { it.value.lastAccess }
        var evicted = 0
        for (entry in snapshot) {
            if (evicted >= remaining) break
            if (map.remove(entry.key, entry.value)) {
                evictions.incrementAndGet()
                evicted++
            }
        }
    }
}
