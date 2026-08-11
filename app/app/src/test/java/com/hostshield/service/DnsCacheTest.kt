package com.hostshield.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DnsCacheTest {

    private class FakeClock(var timeMs: Long = 0L) : DnsCache.Clock {
        override fun currentTimeMillis() = timeMs
        fun advance(ms: Long) { timeMs += ms }
    }

    private lateinit var fakeClock: FakeClock
    private lateinit var cache: DnsCache

    @Before
    fun setup() {
        fakeClock = FakeClock(1_000_000L) // start at a non-zero baseline
        cache = DnsCache(
            maxEntries = 100,
            maxNegativeEntries = 50,
            maxFailureEntries = 50,
            defaultTtlMs = 300_000,
            negativeTtlMs = 60_000,
            failureTtlMs = 5_000,
            minTtlMs = 1,        // tiny floor so short TTLs are honoured in tests
            maxTtlMs = 86_400_000,
            staleTtlMs = 200,
            staleServeTtlMs = 30_000,
            prefetchThreshold = 0.10f,
            prefetchMinQueries = 3,
            clock = fakeClock
        )
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildDnsResponse(
        txId: Int = 0x1234,
        rcode: Int = 0,
        ttl: Int = 300,
        truncated: Boolean = false,
        anCount: Int = 1
    ): ByteArray {
        val header = ByteArray(12)
        header[0] = (txId shr 8).toByte()
        header[1] = (txId and 0xFF).toByte()
        header[2] = (0x80 or (if (truncated) 0x02 else 0)).toByte()
        header[3] = (rcode and 0x0F).toByte()
        header[4] = 0; header[5] = 1 // QDCOUNT=1
        header[6] = (anCount shr 8).toByte(); header[7] = (anCount and 0xFF).toByte()
        // Question: \x07example\x03com\x00 TYPE=A CLASS=IN
        val question = byteArrayOf(
            7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1
        )
        if (anCount == 0) return header + question
        // Answer RR with compression pointer to offset 12
        val answer = ByteArray(16)
        answer[0] = 0xC0.toByte(); answer[1] = 12       // NAME (pointer)
        answer[2] = 0; answer[3] = 1                     // TYPE=A
        answer[4] = 0; answer[5] = 1                     // CLASS=IN
        answer[6] = (ttl shr 24).toByte()
        answer[7] = (ttl shr 16).toByte()
        answer[8] = (ttl shr 8).toByte()
        answer[9] = (ttl and 0xFF).toByte()
        answer[10] = 0; answer[11] = 4                   // RDLENGTH=4
        answer[12] = 93.toByte(); answer[13] = 184.toByte()
        answer[14] = 216.toByte(); answer[15] = 34       // 93.184.216.34
        return header + question + answer
    }

    private fun txId(id: Int): ByteArray =
        byteArrayOf((id shr 8).toByte(), (id and 0xFF).toByte())

    // ── Tests ────────────────────────────────────────────────

    @Test
    fun `empty cache returns null`() {
        val result = cache.get("example.com", 1, txId(0xAAAA))
        assertNull(result)
    }

    @Test
    fun `put and get returns cached response with patched transaction ID`() {
        val response = buildDnsResponse(txId = 0x1234, ttl = 300)
        cache.put("example.com", 1, response)

        val result = cache.get("example.com", 1, txId(0xBBBB))
        assertNotNull(result)
        // Transaction ID should be patched to 0xBBBB
        assertEquals(0xBB.toByte(), result!!.response[0])
        assertEquals(0xBB.toByte(), result.response[1])
        assertFalse(result.isStale)
    }

    @Test
    fun `cache miss increments miss counter`() {
        cache.get("miss1.com", 1, txId(0x0001))
        cache.get("miss2.com", 1, txId(0x0002))
        cache.get("miss3.com", 1, txId(0x0003))

        val stats = cache.getStats()
        assertEquals(3L, stats.misses)
        assertEquals(0L, stats.hits)
    }

    @Test
    fun `expired entry returns null for basic get`() {
        // Use a cache with a very small max TTL so the entry expires quickly
        val clock = FakeClock(fakeClock.timeMs)
        val tinyCache = DnsCache(
            maxEntries = 100,
            minTtlMs = 1,
            maxTtlMs = 50,
            staleTtlMs = 0,    // no stale window
            defaultTtlMs = 50,
            clock = clock
        )
        val response = buildDnsResponse(ttl = 1) // 1s -> clamped to minTtlMs=1ms
        tinyCache.put("example.com", 1, response)

        clock.advance(60)

        val result = tinyCache.get("example.com", 1, txId(0x0001))
        assertNull(result)
    }

    @Test
    fun `expired entry within stale window returns isStale true`() {
        // minTtlMs=1 means TTL of 1 second -> 1000ms, but we want faster.
        // TTL=1 => 1*1000=1000ms, coerced to min=1ms => 1ms. Actually coerceIn(1, maxTtlMs).
        // With minTtlMs=1, TTL of 1 => 1000ms coerced to [1, maxTtlMs].
        // We need very small effective TTL. TTL in DNS is seconds.
        // extractMinTtl returns seconds. put() does ttl * 1000. With minTtlMs=1, maxTtlMs=100:
        // ttl=1 => 1000ms coerced to [1,100] = 100ms
        val clock = FakeClock(fakeClock.timeMs)
        val staleCache = DnsCache(
            maxEntries = 100,
            minTtlMs = 1,
            maxTtlMs = 50,       // clamp to 50ms
            staleTtlMs = 5000,   // generous stale window
            defaultTtlMs = 50,
            clock = clock
        )
        val response = buildDnsResponse(ttl = 1)
        staleCache.put("example.com", 1, response)

        clock.advance(80) // past the 50ms TTL

        val result = staleCache.get("example.com", 1, txId(0x0001))
        assertNotNull("Expected stale entry but got null", result)
        assertTrue("Expected isStale=true", result!!.isStale)
    }

    @Test
    fun `entry beyond stale window returns null`() {
        val clock = FakeClock(fakeClock.timeMs)
        val tinyStaleCache = DnsCache(
            maxEntries = 100,
            minTtlMs = 1,
            maxTtlMs = 10,      // 10ms TTL cap
            staleTtlMs = 10,    // 10ms stale window
            defaultTtlMs = 10,
            clock = clock
        )
        val response = buildDnsResponse(ttl = 1)
        tinyStaleCache.put("example.com", 1, response)

        clock.advance(50) // well past TTL + stale window

        val result = tinyStaleCache.get("example.com", 1, txId(0x0001))
        assertNull(result)
    }

    @Test
    fun `NXDOMAIN goes to negative cache`() {
        val response = buildDnsResponse(rcode = 3, anCount = 0)
        cache.put("nonexistent.com", 1, response)

        val result = cache.get("nonexistent.com", 1, txId(0xCCCC))
        assertNotNull("NXDOMAIN should be cached in negative cache", result)
        assertFalse(result!!.isStale)

        val stats = cache.getStats()
        assertEquals(0, stats.size)          // not in positive cache
        assertEquals(1, stats.negativeSize)  // in negative cache
    }

    @Test
    fun `SERVFAIL goes to failure cache`() {
        val response = buildDnsResponse(rcode = 2, anCount = 0)
        cache.put("failing.com", 1, response)

        val result = cache.get("failing.com", 1, txId(0xDDDD))
        assertNotNull("SERVFAIL should be cached in failure cache", result)

        val stats = cache.getStats()
        assertEquals(0, stats.size)
        assertEquals(1, stats.failureSize)
    }

    @Test
    fun `REFUSED goes to failure cache`() {
        val response = buildDnsResponse(rcode = 5, anCount = 0)
        cache.put("refused.com", 1, response)

        val result = cache.get("refused.com", 1, txId(0xEEEE))
        assertNotNull("REFUSED should be cached in failure cache", result)

        val stats = cache.getStats()
        assertEquals(0, stats.size)
        assertEquals(1, stats.failureSize)
    }

    @Test
    fun `prefetch signaled when TTL nearly expired and query count sufficient`() {
        val clock = FakeClock(fakeClock.timeMs)
        val prefetchCache = DnsCache(
            maxEntries = 100,
            minTtlMs = 1,
            maxTtlMs = 200,     // 200ms TTL cap
            staleTtlMs = 5000,
            defaultTtlMs = 200,
            prefetchThreshold = 0.50f,  // high threshold so prefetch triggers easily
            prefetchMinQueries = 3,
            clock = clock
        )
        val response = buildDnsResponse(ttl = 1) // -> 200ms after clamping
        prefetchCache.put("popular.com", 1, response)

        // Query multiple times to build up queryCount (need >= 3)
        prefetchCache.get("popular.com", 1, txId(0x0001))
        prefetchCache.get("popular.com", 1, txId(0x0002))

        // Advance until TTL is nearly expired (< 50% remaining)
        clock.advance(120)

        val result = prefetchCache.get("popular.com", 1, txId(0x0003))
        assertNotNull(result)
        assertTrue("Expected prefetch signal", result!!.needsPrefetch)

        val stats = prefetchCache.getStats()
        assertTrue("Expected prefetchTriggers > 0", stats.prefetchTriggers > 0)
    }

    @Test
    fun `LRU eviction removes oldest entries when cache full`() {
        val smallCache = DnsCache(
            maxEntries = 5,
            minTtlMs = 1,
            maxTtlMs = 86_400_000,
            staleTtlMs = 0,
            defaultTtlMs = 300_000
        )

        // Fill cache with 5 entries
        for (i in 1..5) {
            smallCache.put("domain$i.com", 1, buildDnsResponse(ttl = 3600))
        }
        assertEquals(5, smallCache.getStats().size)

        // Adding one more should trigger eviction
        smallCache.put("overflow.com", 1, buildDnsResponse(ttl = 3600))

        // Cache should not exceed maxEntries (eviction removes maxEntries/10 = 0,
        // but the evictLru removes at least some). The new entry should be present.
        val overflowResult = smallCache.get("overflow.com", 1, txId(0x0001))
        assertNotNull("Newly added entry should be present", overflowResult)

        val stats = smallCache.getStats()
        assertTrue("Evictions should have occurred", stats.evictions > 0)
    }

    @Test
    fun `clear empties all caches`() {
        // Populate all three caches
        cache.put("normal.com", 1, buildDnsResponse(rcode = 0, ttl = 300))
        cache.put("nxdomain.com", 1, buildDnsResponse(rcode = 3, anCount = 0))
        cache.put("servfail.com", 1, buildDnsResponse(rcode = 2, anCount = 0))

        val beforeStats = cache.getStats()
        assertTrue(beforeStats.size > 0 || beforeStats.negativeSize > 0 || beforeStats.failureSize > 0)

        cache.clear()

        val afterStats = cache.getStats()
        assertEquals(0, afterStats.size)
        assertEquals(0, afterStats.negativeSize)
        assertEquals(0, afterStats.failureSize)

        // Verify lookups return null
        assertNull(cache.get("normal.com", 1, txId(0x0001)))
        assertNull(cache.get("nxdomain.com", 1, txId(0x0001)))
        assertNull(cache.get("servfail.com", 1, txId(0x0001)))
    }

    @Test
    fun `truncated response not cached`() {
        val truncated = buildDnsResponse(truncated = true)
        cache.put("truncated.com", 1, truncated)

        val result = cache.get("truncated.com", 1, txId(0x0001))
        assertNull("Truncated responses (TC bit set) should not be cached", result)
        assertEquals(0, cache.getStats().size)
    }

    @Test
    fun `getSimple returns raw bytes`() {
        val response = buildDnsResponse(txId = 0x1234, ttl = 300)
        cache.put("example.com", 1, response)

        val bytes = cache.getSimple("example.com", 1, txId(0xAAAA))
        assertNotNull(bytes)
        // Transaction ID should be patched
        assertEquals(0xAA.toByte(), bytes!![0])
        assertEquals(0xAA.toByte(), bytes[1])
        // Rest of header should match original (flags byte)
        assertEquals(response[2], bytes[2])
    }

    @Test
    fun `getStale returns stale entry`() {
        val clock = FakeClock(fakeClock.timeMs)
        val staleCache = DnsCache(
            maxEntries = 100,
            minTtlMs = 1,
            maxTtlMs = 50,
            staleTtlMs = 5000,
            defaultTtlMs = 50,
            clock = clock
        )
        val response = buildDnsResponse(ttl = 1)
        staleCache.put("example.com", 1, response)

        clock.advance(80) // past TTL but within stale window

        val staleBytes = staleCache.getStale("example.com", 1, txId(0xFFFF))
        assertNotNull("Expected stale entry", staleBytes)
        assertEquals(0xFF.toByte(), staleBytes!![0])
        assertEquals(0xFF.toByte(), staleBytes[1])
    }

    @Test
    fun `getStale returns null when no stale entry exists`() {
        val result = cache.getStale("nonexistent.com", 1, txId(0x0001))
        assertNull(result)
    }

    @Test
    fun `stats track hits misses and evictions correctly`() {
        // Start with zeroes
        var stats = cache.getStats()
        assertEquals(0L, stats.hits)
        assertEquals(0L, stats.misses)
        assertEquals(0L, stats.evictions)
        assertEquals(0f, stats.hitRate, 0.001f)

        // Generate misses
        cache.get("miss.com", 1, txId(0x0001))
        cache.get("miss2.com", 1, txId(0x0002))

        stats = cache.getStats()
        assertEquals(0L, stats.hits)
        assertEquals(2L, stats.misses)

        // Add entry and generate hits
        cache.put("hit.com", 1, buildDnsResponse(ttl = 3600))
        cache.get("hit.com", 1, txId(0x0001))
        cache.get("hit.com", 1, txId(0x0002))
        cache.get("hit.com", 1, txId(0x0003))

        stats = cache.getStats()
        assertEquals(3L, stats.hits)
        assertEquals(2L, stats.misses) // misses unchanged
        // hitRate = 3 / (3 + 2) = 0.6
        assertEquals(0.6f, stats.hitRate, 0.001f)
    }

    @Test
    fun `domain lookup is case insensitive`() {
        cache.put("Example.COM", 1, buildDnsResponse(ttl = 300))

        val result = cache.get("example.com", 1, txId(0x0001))
        assertNotNull("Lookup should be case-insensitive", result)
    }

    @Test
    fun `response too short is not cached`() {
        val tooShort = ByteArray(11) // less than 12 byte DNS header
        cache.put("short.com", 1, tooShort)

        assertNull(cache.get("short.com", 1, txId(0x0001)))
        assertEquals(0, cache.getStats().size)
    }

    @Test
    fun `different qtypes are cached separately`() {
        val responseA = buildDnsResponse(txId = 0x0001, ttl = 300)
        val responseAAAA = buildDnsResponse(txId = 0x0002, ttl = 300)

        cache.put("example.com", 1, responseA)      // A record
        cache.put("example.com", 28, responseAAAA)   // AAAA record

        val resultA = cache.get("example.com", 1, txId(0xAAAA))
        val resultAAAA = cache.get("example.com", 28, txId(0xBBBB))

        assertNotNull(resultA)
        assertNotNull(resultAAAA)

        val stats = cache.getStats()
        assertEquals(2, stats.size)
    }

    // ── RFC 9824 NXNAME (Compact Denial of Existence) ───────

    private fun buildNxnameResponse(txId: Int = 0x1234, ttl: Int = 120): ByteArray {
        val header = ByteArray(12)
        header[0] = (txId shr 8).toByte()
        header[1] = (txId and 0xFF).toByte()
        header[2] = 0x80.toByte() // QR=1, RCODE=0 (NOERROR)
        header[3] = 0x00
        header[4] = 0; header[5] = 1 // QDCOUNT=1
        header[6] = 0; header[7] = 1 // ANCOUNT=1 (the NXNAME RR)
        val question = byteArrayOf(
            7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1
        )
        // Answer: NXNAME RR (type 128) with RDLENGTH=0
        val answer = ByteArray(12)
        answer[0] = 0xC0.toByte(); answer[1] = 12       // NAME pointer
        answer[2] = 0; answer[3] = 128.toByte()          // TYPE=NXNAME (128)
        answer[4] = 0; answer[5] = 1                     // CLASS=IN
        answer[6] = (ttl shr 24).toByte()
        answer[7] = (ttl shr 16).toByte()
        answer[8] = (ttl shr 8).toByte()
        answer[9] = (ttl and 0xFF).toByte()
        answer[10] = 0; answer[11] = 0                   // RDLENGTH=0
        return header + question + answer
    }

    @Test
    fun `NXNAME response is cached as negative entry`() {
        val response = buildNxnameResponse(ttl = 60)
        cache.put("nxname.example.com", 1, response)

        // Should be retrievable (negative cache serves it)
        val result = cache.get("nxname.example.com", 1, txId(0xAAAA))
        assertNotNull("NXNAME response should be cached", result)
    }

    @Test
    fun `NXNAME response does not go to positive cache`() {
        val response = buildNxnameResponse(ttl = 300)
        cache.put("nxname2.example.com", 1, response)

        // Advance past the negative TTL window to confirm it expires like NXDOMAIN
        fakeClock.advance(400_000)
        val result = cache.get("nxname2.example.com", 1, txId(0xBBBB))
        assertNull("NXNAME entry should have expired from negative cache", result)
    }

    @Test
    fun `normal NOERROR response without NXNAME goes to positive cache`() {
        val response = buildDnsResponse(ttl = 300)
        cache.put("normal.example.com", 1, response)

        val result = cache.get("normal.example.com", 1, txId(0xCCCC))
        assertNotNull(result)
        assertFalse(result!!.isStale)
    }

    /**
     * NXDOMAIN carrying an SOA in the authority section, so the RFC 2308 MINIMUM
     * field can be exercised. The existing builder emits no authority records, so
     * no test could construct one.
     */
    private fun buildNxdomainWithSoa(minimumTtl: Int, soaTtl: Int = 3600): ByteArray {
        val header = ByteArray(12)
        header[0] = 0x12; header[1] = 0x34
        header[2] = 0x80.toByte()
        header[3] = 3                                   // RCODE=3 NXDOMAIN
        header[4] = 0; header[5] = 1                    // QDCOUNT=1
        header[6] = 0; header[7] = 0                    // ANCOUNT=0
        header[8] = 0; header[9] = 1                    // NSCOUNT=1
        val question = byteArrayOf(
            7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1
        )
        // SOA RR: NAME=pointer, TYPE=6, CLASS=1, TTL, RDLENGTH, RDATA
        // RDATA = MNAME(ptr) RNAME(ptr) SERIAL REFRESH RETRY EXPIRE MINIMUM
        val rdata = ByteArray(4 + 20)
        rdata[0] = 0xC0.toByte(); rdata[1] = 12         // MNAME pointer
        rdata[2] = 0xC0.toByte(); rdata[3] = 12         // RNAME pointer
        // serial/refresh/retry/expire left zero; MINIMUM is the last 4 bytes
        rdata[20] = (minimumTtl shr 24).toByte()
        rdata[21] = (minimumTtl shr 16).toByte()
        rdata[22] = (minimumTtl shr 8).toByte()
        rdata[23] = (minimumTtl and 0xFF).toByte()

        val rr = ByteArray(12 + rdata.size)
        rr[0] = 0xC0.toByte(); rr[1] = 12               // NAME pointer
        rr[2] = 0; rr[3] = 6                            // TYPE=SOA
        rr[4] = 0; rr[5] = 1                            // CLASS=IN
        rr[6] = (soaTtl shr 24).toByte()
        rr[7] = (soaTtl shr 16).toByte()
        rr[8] = (soaTtl shr 8).toByte()
        rr[9] = (soaTtl and 0xFF).toByte()
        rr[10] = (rdata.size shr 8).toByte()
        rr[11] = (rdata.size and 0xFF).toByte()
        System.arraycopy(rdata, 0, rr, 12, rdata.size)

        return header + question + rr
    }

    // RFC 2308: MINIMUM=0 means "do not cache this negative answer". The advertised
    // behavior had no test, so a regression re-caching it would have gone unnoticed.
    @Test
    fun `NXDOMAIN with SOA MINIMUM zero is not cached`() {
        val response = buildNxdomainWithSoa(minimumTtl = 0)
        cache.put("nx-zero.example.com", 1, response)
        assertNull(cache.get("nx-zero.example.com", 1, txId(0x1234)))
    }

    @Test
    fun `NXDOMAIN with a positive SOA MINIMUM is cached`() {
        val response = buildNxdomainWithSoa(minimumTtl = 120)
        cache.put("nx-positive.example.com", 1, response)
        assertNotNull(cache.get("nx-positive.example.com", 1, txId(0x1234)))
    }
}
