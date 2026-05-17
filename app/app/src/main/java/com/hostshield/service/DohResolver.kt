package com.hostshield.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
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
    private val doh3Resolver: Doh3Resolver
) {

    companion object {
        private const val TAG = "DohResolver"

        // RFC 8484 caps the wire-format response at 65535 bytes. Anything larger
        // is malformed or hostile. We also reject responses smaller than the
        // 12-byte DNS header to avoid handing partial messages to parsers.
        private const val MAX_DOH_RESPONSE = 65535
        private const val MIN_DNS_MESSAGE = 12
        // After this many consecutive failures, a provider is demoted to the end
        // of the failover order so we stop hitting a known-broken endpoint first.
        private const val FAILURE_DEMOTE_THRESHOLD = 3
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

    // Certificate pins for DoH providers.
    // These are SHA-256 hashes of the Subject Public Key Info (SPKI).
    // OkHttp requires at least 2 pins per host for rotation safety.
    // When a pin fails, OkHttp falls through to the next provider.
    //
    // Pin rotation: if a provider rotates certs, connections fail-safe
    // to the next provider via our failover logic. Update pins in the
    // next release. This is strictly better than no pinning.
    private val certificatePinner = CertificatePinner.Builder()
        // Cloudflare — DigiCert + Google Trust Services backup
        .add("cloudflare-dns.com",
            "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=", // DigiCert Global G2
            "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="  // Baltimore CyberTrust (backup)
        )
        // Google — GTS CA 1C3 + GlobalSign
        .add("dns.google",
            "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", // GTS Root R1
            "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="  // GlobalSign (backup)
        )
        // Quad9 — DigiCert
        .add("dns.quad9.net",
            "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=", // DigiCert Global G2
            "sha256/RRM1dGqnDFsCJXBTHky16vi1obOlCgFFn/yOhI/y+ho="  // DigiCert ECC (backup)
        )
        // NextDNS — Let's Encrypt
        .add("dns.nextdns.io",
            "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // ISRG Root X1
            "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="  // Baltimore (backup)
        )
        // AdGuard — DigiCert
        .add("dns.adguard-dns.com",
            "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=", // DigiCert Global G2
            "sha256/RRM1dGqnDFsCJXBTHky16vi1obOlCgFFn/yOhI/y+ho="  // DigiCert ECC (backup)
        )
        .build()

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
        val preferredProvider = getFastestProvider() ?: provider

        doh3Resolver.resolve(dnsQuery, Doh3Resolver.Provider.fromDohProvider(preferredProvider))?.let { doh3 ->
            updateLatency(doh3.provider.dohProvider, doh3.latencyMs)
            return@withContext DohResponse(
                response = doh3.response,
                provider = doh3.provider.dohProvider,
                transport = Transport.DOH3,
                negotiatedProtocol = doh3.negotiatedProtocol
            )
        }

        val result = doResolve(dnsQuery, preferredProvider, client)
        if (result != null) {
            consecutiveFailures[preferredProvider]?.set(0)
            return@withContext DohResponse(
                response = result,
                provider = preferredProvider,
                transport = Transport.DOH,
                negotiatedProtocol = "https"
            )
        }
        recordFailure(preferredProvider)

        Log.w(TAG, "${preferredProvider.name} failed, trying failover...")
        val orderedFallbacks = synchronized(failoverOrder) { failoverOrder.toList() }
            .sortedBy { latencyEma[it] ?: Long.MAX_VALUE }
        for (fallback in orderedFallbacks) {
            if (fallback == preferredProvider) continue
            val fbResult = doResolve(dnsQuery, fallback, client)
            if (fbResult != null) {
                consecutiveFailures[fallback]?.set(0)
                Log.i(TAG, "Failover to ${fallback.name} succeeded")
                return@withContext DohResponse(
                    response = fbResult,
                    provider = fallback,
                    transport = Transport.DOH,
                    negotiatedProtocol = "https"
                )
            }
            recordFailure(fallback)
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
    private fun readBoundedBody(resp: okhttp3.Response): ByteArray? {
        val body = resp.body ?: return null
        val declared = body.contentLength()
        if (declared > MAX_DOH_RESPONSE) {
            Log.w(TAG, "DoH response too large: declared=$declared")
            return null
        }
        val src = body.source()
        val read = src.readByteArray((MAX_DOH_RESPONSE + 1).toLong())
        if (read.size > MAX_DOH_RESPONSE) {
            Log.w(TAG, "DoH response exceeded $MAX_DOH_RESPONSE-byte cap")
            return null
        }
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
}
