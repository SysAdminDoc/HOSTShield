package com.hostshield.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/**
 * Single-flight coordinator for concurrent DNS misses.
 *
 * Identical domain/qtype queries on the same upstream route share the first
 * in-flight resolver call. Each caller still patches its own DNS transaction ID
 * before sending the result back to the originating app.
 */
class DnsQueryDeduplicator<T>(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: Clock = Clock { System.currentTimeMillis() }
) {
    fun interface Clock {
        fun currentTimeMillis(): Long
    }

    data class Key(
        val domain: String,
        val qtype: Int,
        val route: String
    ) {
        init {
            require(domain.isNotBlank()) { "domain must not be blank" }
        }

        val normalizedDomain: String = domain.lowercase(Locale.ROOT)
    }

    data class DeduplicationResult<T>(
        val value: T,
        val shared: Boolean
    )

    private data class Entry<T>(
        val deferred: CompletableDeferred<T>,
        val startedAtMs: Long
    )

    private val inFlight = ConcurrentHashMap<Key, Entry<T>>()

    suspend fun getOrRun(key: Key, resolver: suspend () -> T): DeduplicationResult<T> {
        val normalizedKey = key.copy(domain = key.normalizedDomain)
        while (true) {
            val now = clock.currentTimeMillis()
            val existing = inFlight[normalizedKey]
            if (existing != null) {
                if (now - existing.startedAtMs <= windowMs) {
                    return DeduplicationResult(existing.deferred.await(), shared = true)
                }
                inFlight.remove(normalizedKey, existing)
                continue
            }

            val entry = Entry(CompletableDeferred<T>(), now)
            val previous = inFlight.putIfAbsent(normalizedKey, entry)
            if (previous != null) continue

            try {
                val value = resolver()
                entry.deferred.complete(value)
                return DeduplicationResult(value, shared = false)
            } catch (t: Throwable) {
                entry.deferred.completeExceptionally(t)
                throw t
            } finally {
                inFlight.remove(normalizedKey, entry)
            }
        }
    }

    fun clear() {
        inFlight.values.forEach { it.deferred.cancel() }
        inFlight.clear()
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
    }
}
