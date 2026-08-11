package com.hostshield.service

import androidx.annotation.VisibleForTesting
import android.util.Log
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// DNS-over-HTTPS resolver
//
// Features:
// - RFC 8484 POST and GET wire format
// - Certificate pinning for all built-in providers
// - Automatic failover: retries with next provider on failure
// - Latency tracking: remembers fastest provider
// - Connection pooling via shared OkHttpClient
// ══════════════════════════════════════════════════════════════

@Singleton
class DohResolver @Inject constructor(
    private val doh3Resolver: Doh3Resolver,
    private val diagnosticEvents: DiagnosticEventStore
) {

    companion object {
        private const val TAG = "DohResolver"

        // RFC 8484 caps the wire-format response at 65535 bytes. Anything larger
        // is malformed or hostile. We also reject responses smaller than the
        // 12-byte DNS header to avoid handing partial messages to parsers.
        @VisibleForTesting
        internal const val MAX_DOH_RESPONSE = 65535
        private const val MIN_DNS_MESSAGE = 12
        // After this many consecutive failures, a provider is demoted to the end
        // of the failover order so we stop hitting a known-broken endpoint first.
        private const val FAILURE_DEMOTE_THRESHOLD = 3

        @Suppress("UNUSED_PARAMETER")
        internal fun choosePrimaryProvider(
            selectedProvider: Provider,
            fastestObservedProvider: Provider?
        ): Provider = selectedProvider
    }

    enum class Transport {
        DOH3,
        DOH
    }

    data class DohResponse(
        val response: ByteArray,
        val provider: Provider,
        val transport: Transport,
        val negotiatedProtocol: String
    )

    data class ResolverHealthSnapshot(
        val provider: Provider,
        val selected: Boolean,
        val activeTransport: String,
        val latencyMs: Long?,
        val doh3LatencyMs: Long?,
        val attempts: Int,
        val successes: Int,
        val failures: Int,
        val failovers: Int,
        val pinFailures: Int,
        val edeCount: Int = 0
    ) {
        val successRatePercent: Int?
            get() = if (attempts == 0) null else ((successes * 100.0) / attempts).toInt()
    }

    enum class Provider(val url: String, val hostname: String) {
        CLOUDFLARE("https://cloudflare-dns.com/dns-query", "cloudflare-dns.com"),
        GOOGLE("https://dns.google/dns-query", "dns.google"),
        QUAD9("https://dns.quad9.net/dns-query", "dns.quad9.net"),
        NEXTDNS("https://dns.nextdns.io/dns-query", "dns.nextdns.io"),
        ADGUARD("https://dns.adguard-dns.com/dns-query", "dns.adguard-dns.com");

        companion object {
            fun fromId(id: String): Provider = when (id.lowercase()) {
                "cloudflare" -> CLOUDFLARE
                "google" -> GOOGLE
                "quad9" -> QUAD9
                "nextdns" -> NEXTDNS
                "adguard" -> ADGUARD
                else -> CLOUDFLARE
            }
        }
    }

    private val certificatePinner = DohPinManifest.certificatePinner()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .certificatePinner(certificatePinner)
        .build()

    private val DNS_MESSAGE_TYPE = "application/dns-message".toMediaType()

    // Failover order — providers are demoted to the end of this list after
    // [FAILURE_DEMOTE_THRESHOLD] consecutive failures, so we stop trying known-broken
    // providers first.
    private val failoverOrder = java.util.Collections.synchronizedList(
        Provider.entries.toMutableList()
    )
    private val consecutiveFailures = java.util.concurrent.ConcurrentHashMap<Provider, AtomicInteger>()

    // Latency tracking — exponential moving average per provider
    private val latencyEma = java.util.concurrent.ConcurrentHashMap<Provider, Long>()
    private val EMA_ALPHA = 0.3 // Weight for new samples (higher = more responsive)

    private data class ResolverHealthEvent(
        val timestampMs: Long,
        val provider: Provider,
        val transport: Transport,
        val success: Boolean?,
        val failover: Boolean = false,
        val pinFailure: Boolean = false
    )

    private val healthEvents = java.util.concurrent.ConcurrentLinkedQueue<ResolverHealthEvent>()
    private val healthWindowMs = 24 * 60 * 60 * 1000L

    /**
     * Resolve a DNS query via DoH with automatic failover.
     *
     * Tries the preferred provider first. On failure, iterates through
     * remaining providers. After 3 consecutive failures on a provider,
     * it's moved to the end of the failover list.
     */
    suspend fun resolve(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): ByteArray? = resolveWithMetadata(dnsQuery, provider)?.response

    suspend fun resolveWithMetadata(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): DohResponse? = withContext(Dispatchers.IO) {
        val preferredProvider = choosePrimaryProvider(provider, getFastestProvider())

        // DoH3/QUIC transport is disabled until a maintained Cronet artifact is available.
        // Doh3Resolver.isAvailable is compile-time false; this guard exists for the
        // future re-enable path and is stripped by R8 in release builds.
        @Suppress("KotlinConstantConditions")
        if (doh3Resolver.isAvailable) {
            doh3Resolver.resolve(dnsQuery, Doh3Resolver.Provider.fromDohProvider(preferredProvider))?.let { doh3 ->
                updateLatency(doh3.provider.dohProvider, doh3.latencyMs)
                recordHealth(preferredProvider, Transport.DOH3, success = true)
                return@withContext DohResponse(
                    response = doh3.response,
                    provider = doh3.provider.dohProvider,
                    transport = Transport.DOH3,
                    negotiatedProtocol = doh3.negotiatedProtocol
                )
            }
        }

        val result = doResolve(dnsQuery, preferredProvider, client)
        if (result != null) {
            consecutiveFailures[preferredProvider]?.set(0)
            recordHealth(preferredProvider, Transport.DOH, success = true)
            return@withContext DohResponse(
                response = result,
                provider = preferredProvider,
                transport = Transport.DOH,
                negotiatedProtocol = "https"
            )
        }
        recordFailure(preferredProvider)
        recordHealth(preferredProvider, Transport.DOH, success = false)

        Log.w(TAG, "${preferredProvider.name} failed, trying failover...")
        val orderedFallbacks = synchronized(failoverOrder) { failoverOrder.toList() }
            .sortedBy { latencyEma[it] ?: Long.MAX_VALUE }
        for (fallback in orderedFallbacks) {
            if (fallback == preferredProvider) continue
            val fbResult = doResolve(dnsQuery, fallback, client)
            if (fbResult != null) {
                consecutiveFailures[fallback]?.set(0)
                Log.i(TAG, "Failover to ${fallback.name} succeeded")
                recordHealth(fallback, Transport.DOH, success = true, failover = true)
                diagnosticEvents.recordBlocking(
                    DiagnosticEventType.RESOLVER_FAILOVER,
                    "DoH resolver failover succeeded",
                    mapOf(
                        "from" to preferredProvider.name,
                        "to" to fallback.name,
                        "transport" to Transport.DOH.name
                    )
                )
                return@withContext DohResponse(
                    response = fbResult,
                    provider = fallback,
                    transport = Transport.DOH,
                    negotiatedProtocol = "https"
                )
            }
            recordFailure(fallback)
            recordHealth(fallback, Transport.DOH, success = false)
        }

        // Fail closed — never downgrade to unpinned resolution
        Log.e(TAG, "All pinned DoH providers failed — refusing to downgrade to unpinned. DNS resolution failed.")
        null
    }

    private fun recordFailure(provider: Provider) {
        val counter = consecutiveFailures.getOrPut(provider) { AtomicInteger(0) }
        val n = counter.incrementAndGet()
        if (n >= FAILURE_DEMOTE_THRESHOLD) {
            synchronized(failoverOrder) {
                if (failoverOrder.remove(provider)) failoverOrder.add(provider)
            }
            counter.set(0)
            Log.w(TAG, "Demoted ${provider.name} to end of failover order")
        }
    }

    /**
     * Resolve via GET with base64url encoding (alternative method).
     */
    suspend fun resolveGet(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val encoded = android.util.Base64.encodeToString(
                dnsQuery,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            val url = "${provider.url}?dns=$encoded"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/dns-message")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                readBoundedBody(resp)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read at most [MAX_DOH_RESPONSE] bytes from a DoH response body. Refuses to
     * load a multi-megabyte body into memory, which a hostile or misconfigured
     * endpoint could use to OOM the VPN process.
     */
    @VisibleForTesting
    internal fun readBoundedBody(resp: okhttp3.Response): ByteArray? {
        val body = resp.body
        val declared = body.contentLength()
        if (declared > MAX_DOH_RESPONSE) {
            Log.w(TAG, "DoH response too large: declared=$declared")
            return null
        }
        val src = body.source()
        // request() buffers UP TO cap+1 bytes and returns false when the stream
        // ends first — it does NOT throw on a short stream. The previous
        // readByteArray(cap+1) read EXACTLY cap+1 bytes and threw EOFException on
        // every normal-sized DoH response (~100 bytes), which silently broke DoH
        // for all providers and forced the plaintext fallback. If cap+1 bytes ARE
        // available the response is over the limit and we reject it.
        if (src.request((MAX_DOH_RESPONSE + 1).toLong())) {
            Log.w(TAG, "DoH response exceeded $MAX_DOH_RESPONSE-byte cap")
            return null
        }
        val read = src.readByteArray()
        if (read.size < MIN_DNS_MESSAGE) return null
        return read
    }

    private fun doResolve(dnsQuery: ByteArray, provider: Provider, httpClient: OkHttpClient): ByteArray? {
        return try {
            val start = System.nanoTime()
            val request = Request.Builder()
                .url(provider.url)
                .post(dnsQuery.toRequestBody(DNS_MESSAGE_TYPE))
                .addHeader("Accept", "application/dns-message")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = readBoundedBody(resp)
                if (bytes != null) {
                    val elapsedMs = (System.nanoTime() - start) / 1_000_000
                    updateLatency(provider, elapsedMs)
                }
                bytes
            }
        } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
            // Pin failure — distinct from network errors. Log loudly so a cert
            // rotation that breaks pinning is investigable from `logcat` rather
            // than silently appearing as "all DoH providers failed".
            Log.e(TAG, "Cert pin failure for ${provider.name}: ${e.message}")
            recordHealth(provider, Transport.DOH, success = null, pinFailure = true)
            val fields = mutableMapOf<String, Any>(
                "provider" to provider.name,
                "error" to (e.message ?: "SSLPeerUnverifiedException")
            )
            DohPinManifest.forProvider(provider)
                ?.diagnosticFields()
                ?.let { fields.putAll(it) }
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.CERT_PIN_FAILURE,
                "DoH certificate pin validation failed",
                fields
            )
            null
        } catch (e: Exception) {
            Log.d(TAG, "${provider.name} error: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun updateLatency(provider: Provider, ms: Long) {
        val prev = latencyEma[provider]
        val ema = if (prev == null) ms
        else ((1 - EMA_ALPHA) * prev + EMA_ALPHA * ms).toLong()
        latencyEma[provider] = ema
    }

    private fun recordHealth(
        provider: Provider,
        transport: Transport,
        success: Boolean?,
        failover: Boolean = false,
        pinFailure: Boolean = false
    ) {
        healthEvents.add(
            ResolverHealthEvent(
                timestampMs = System.currentTimeMillis(),
                provider = provider,
                transport = transport,
                success = success,
                failover = failover,
                pinFailure = pinFailure
            )
        )
        pruneHealthEvents()
    }

    private fun pruneHealthEvents(now: Long = System.currentTimeMillis()) {
        val cutoff = now - healthWindowMs
        while (true) {
            val head = healthEvents.peek() ?: return
            if (head.timestampMs >= cutoff) return
            healthEvents.poll()
        }
    }

    /**
     * Get the fastest provider based on measured latency.
     * Returns null if no latency data has been collected yet.
     */
    fun getFastestProvider(): Provider? {
        return latencyEma.minByOrNull { it.value }?.key
    }

    /** Get latency stats for all providers (for display in DNS Tools). */
    fun getLatencyStats(): Map<String, Long> {
        return latencyEma.map { (provider, ema) -> provider.name to ema }.toMap()
    }

    fun getHealthSnapshot(
        selectedProvider: Provider,
        doh3LatencyStats: Map<String, Long> = emptyMap()
    ): List<ResolverHealthSnapshot> {
        pruneHealthEvents()
        val events = healthEvents.toList()
        return Provider.entries.map { provider ->
            val providerEvents = events.filter { it.provider == provider }
            val attempts = providerEvents.count { it.success != null }
            val successes = providerEvents.count { it.success == true }
            val failures = providerEvents.count { it.success == false }
            val failovers = providerEvents.count { it.failover }
            val pinFailures = providerEvents.count { it.pinFailure }
            val doh3Latency = doh3LatencyStats[provider.name]
            val latency = latencyEma[provider] ?: doh3Latency
            val activeTransport = when {
                doh3Latency != null -> Transport.DOH3.name
                latency != null -> Transport.DOH.name
                else -> "not observed"
            }
            ResolverHealthSnapshot(
                provider = provider,
                selected = provider == selectedProvider,
                activeTransport = activeTransport,
                latencyMs = latency,
                doh3LatencyMs = doh3Latency,
                attempts = attempts,
                successes = successes,
                failures = failures,
                failovers = failovers,
                pinFailures = pinFailures
            )
        }
    }
}
