package com.hostshield.service

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * DNS Response Cache — LRU with TTL-aware expiration.
 *
 * Caches raw DNS response bytes keyed by (domain, qtype) to avoid redundant
 * upstream queries. Typical mobile traffic patterns yield 60-70% cache hit
 * rates even with a modest 2000-entry cache.
 *
 * Design choices:
 * - ConcurrentHashMap for lock-free reads from packet processing thread
 * - TTL extracted from DNS answer section (minimum across all RRs)
 * - LRU eviction based on last-access timestamp when cache is full
 * - Separate negative cache (NXDOMAIN) with shorter TTL (60s default)
 * - Thread-safe: all public methods safe to call from any coroutine/thread
 *
 * Does NOT cache:
 * - Blocked domain responses (those are synthesized, not from upstream)
 * - Responses with RCODE != 0 and != 3 (server failures, etc.)
 * - Truncated responses (TC bit set — these require TCP retry)
 */
class DnsCache(
    private val maxEntries: Int = 2000,
    private val maxNegativeEntries: Int = 500,
    private val defaultTtlMs: Long = 300_000, // 5 minutes
    private val negativeTtlMs: Long = 60_000,  // 1 minute for NXDOMAIN
    private val minTtlMs: Long = 10_000,       // 10s floor (prevents 0-TTL thrash)
    private val maxTtlMs: Long = 3_600_000     // 1 hour ceiling
) {
    companion object {
        private const val TAG = "DnsCache"
    }

    data class CacheKey(val domain: String, val qtype: Int)

    private class CacheEntry(
        val response: ByteArray,
        val expiresAt: Long,
        val insertedAt: Long,
        @Volatile var lastAccess: Long
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>(maxEntries)
    private val negativeCache = ConcurrentHashMap<CacheKey, CacheEntry>(maxNegativeEntries)

    // Stats
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)

    /**
     * Look up a cached response.
     *
     * @param domain Query domain (lowercase)
     * @param qtype Query type (1=A, 28=AAAA, etc.)
     * @param transactionId Original query's transaction ID (will be patched into cached response)
     * @return Cached DNS response bytes with correct transaction ID, or null if cache miss
     */
    fun get(domain: String, qtype: Int, transactionId: ByteArray): ByteArray? {
        val key = CacheKey(domain.lowercase(), qtype)
        val now = System.currentTimeMillis()

        // Check positive cache
        val entry = cache[key]
        if (entry != null && now < entry.expiresAt) {
            entry.lastAccess = now
            hits.incrementAndGet()
            return patchTransactionId(entry.response, transactionId)
        }

        // Check negative cache
        val negEntry = negativeCache[key]
        if (negEntry != null && now < negEntry.expiresAt) {
            negEntry.lastAccess = now
            hits.incrementAndGet()
            return patchTransactionId(negEntry.response, transactionId)
        }

        // Expired entries — lazy cleanup
        if (entry != null) cache.remove(key)
        if (negEntry != null) negativeCache.remove(key)

        misses.incrementAndGet()
        return null
    }

    /**
     * Cache a DNS response from upstream.
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
        val now = System.currentTimeMillis()

        when (rcode) {
            0 -> { // NOERROR — positive cache
                val ttl = extractMinTtl(response)
                val ttlMs = (ttl * 1000L).coerceIn(minTtlMs, maxTtlMs)
                val entry = CacheEntry(
                    response = response.copyOf(),
                    expiresAt = now + ttlMs,
                    insertedAt = now,
                    lastAccess = now
                )
                if (cache.size >= maxEntries) evictLru(cache, maxEntries / 10)
                cache[CacheKey(domain.lowercase(), qtype)] = entry
            }
            3 -> { // NXDOMAIN — negative cache (shorter TTL)
                val entry = CacheEntry(
                    response = response.copyOf(),
                    expiresAt = now + negativeTtlMs,
                    insertedAt = now,
                    lastAccess = now
                )
                if (negativeCache.size >= maxNegativeEntries) evictLru(negativeCache, maxNegativeEntries / 5)
                negativeCache[CacheKey(domain.lowercase(), qtype)] = entry
            }
            // Don't cache SERVFAIL(2), REFUSED(5), etc.
        }
    }

    /** Clear all cached entries. */
    fun clear() {
        cache.clear()
        negativeCache.clear()
    }

    /** Get cache statistics. */
    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val total = h + m
        return CacheStats(
            size = cache.size,
            negativeSize = negativeCache.size,
            hits = h,
            misses = m,
            hitRate = if (total > 0) h.toFloat() / total else 0f,
            evictions = evictions.get()
        )
    }

    data class CacheStats(
        val size: Int,
        val negativeSize: Int,
        val hits: Long,
        val misses: Long,
        val hitRate: Float,
        val evictions: Long
    )

    // ── Internal ─────────────────────────────────────────────

    /**
     * Extract minimum TTL from all answer/authority/additional records.
     * This ensures we don't serve stale data for any record in the response.
     */
    private fun extractMinTtl(response: ByteArray): Int {
        try {
            val anCount = (response[6].toInt() and 0xFF shl 8) or (response[7].toInt() and 0xFF)
            val nsCount = (response[8].toInt() and 0xFF shl 8) or (response[9].toInt() and 0xFF)
            val arCount = (response[10].toInt() and 0xFF shl 8) or (response[11].toInt() and 0xFF)
            val totalRrs = anCount + nsCount + arCount

            // Skip question section
            var off = 12
            val qdCount = (response[4].toInt() and 0xFF shl 8) or (response[5].toInt() and 0xFF)
            for (i in 0 until qdCount) {
                off = skipName(response, off)
                off += 4 // QTYPE + QCLASS
            }

            var minTtl = 300 // default 5 min
            for (i in 0 until totalRrs.coerceAtMost(20)) {
                if (off >= response.size) break
                off = skipName(response, off)
                if (off + 10 > response.size) break

                // TYPE(2) + CLASS(2) + TTL(4) + RDLENGTH(2)
                val ttl = ((response[off + 4].toInt() and 0xFF) shl 24) or
                    ((response[off + 5].toInt() and 0xFF) shl 16) or
                    ((response[off + 6].toInt() and 0xFF) shl 8) or
                    (response[off + 7].toInt() and 0xFF)
                val rdLen = (response[off + 8].toInt() and 0xFF shl 8) or (response[off + 9].toInt() and 0xFF)

                if (ttl in 1 until minTtl) minTtl = ttl
                off += 10 + rdLen
            }
            return minTtl
        } catch (_: Exception) {
            return 300 // safe default
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

    /** Evict least-recently-used entries. */
    private fun evictLru(map: ConcurrentHashMap<CacheKey, CacheEntry>, count: Int) {
        val entries = map.entries.sortedBy { it.value.lastAccess }
        val toEvict = entries.take(count)
        for (e in toEvict) {
            map.remove(e.key)
            evictions.incrementAndGet()
        }
    }
}
