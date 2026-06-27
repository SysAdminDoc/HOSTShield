package com.hostshield.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DnsQueryDeduplicatorTest {
    private class FakeClock(var nowMs: Long = 0L) : DnsQueryDeduplicator.Clock {
        override fun currentTimeMillis(): Long = nowMs
    }

    @Test
    fun `concurrent identical queries share one resolver call`() = runTest {
        val clock = FakeClock()
        val deduplicator = DnsQueryDeduplicator<String>(clock = clock)
        val key = DnsQueryDeduplicator.Key("Example.COM", 1, "doh:CLOUDFLARE")
        val resolverCalls = AtomicInteger(0)
        val resolverStarted = CompletableDeferred<Unit>()
        val releaseResolver = CompletableDeferred<Unit>()

        val first = async {
            deduplicator.getOrRun(key) {
                resolverCalls.incrementAndGet()
                resolverStarted.complete(Unit)
                releaseResolver.await()
                "answer"
            }
        }
        resolverStarted.await()

        val second = async {
            deduplicator.getOrRun(key.copy(domain = "example.com")) {
                resolverCalls.incrementAndGet()
                "duplicate"
            }
        }

        releaseResolver.complete(Unit)

        val firstResult = first.await()
        val secondResult = second.await()
        assertEquals("answer", firstResult.value)
        assertEquals("answer", secondResult.value)
        assertFalse(firstResult.shared)
        assertTrue(secondResult.shared)
        assertEquals(1, resolverCalls.get())
    }

    @Test
    fun `different qtypes use different resolver calls`() = runTest {
        val deduplicator = DnsQueryDeduplicator<String>()
        val resolverCalls = AtomicInteger(0)

        val a = deduplicator.getOrRun(DnsQueryDeduplicator.Key("example.com", 1, "udp:1.1.1.1")) {
            resolverCalls.incrementAndGet()
            "a"
        }
        val aaaa = deduplicator.getOrRun(DnsQueryDeduplicator.Key("example.com", 28, "udp:1.1.1.1")) {
            resolverCalls.incrementAndGet()
            "aaaa"
        }

        assertEquals("a", a.value)
        assertEquals("aaaa", aaaa.value)
        assertFalse(a.shared)
        assertFalse(aaaa.shared)
        assertEquals(2, resolverCalls.get())
    }

    @Test
    fun `expired in-flight window lets a new resolver lead`() = runTest {
        val clock = FakeClock()
        val deduplicator = DnsQueryDeduplicator<String>(windowMs = 2_000L, clock = clock)
        val key = DnsQueryDeduplicator.Key("example.com", 1, "doh:QUAD9")
        val resolverCalls = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            deduplicator.getOrRun(key) {
                resolverCalls.incrementAndGet()
                firstStarted.complete(Unit)
                releaseFirst.await()
                "old"
            }
        }
        firstStarted.await()
        clock.nowMs = 2_001L

        val second = deduplicator.getOrRun(key) {
            resolverCalls.incrementAndGet()
            "new"
        }

        releaseFirst.complete(Unit)
        val firstResult = first.await()
        assertEquals("old", firstResult.value)
        assertEquals("new", second.value)
        assertFalse(firstResult.shared)
        assertFalse(second.shared)
        assertEquals(2, resolverCalls.get())
    }
}
