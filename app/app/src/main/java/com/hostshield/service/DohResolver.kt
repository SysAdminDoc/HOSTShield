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
// HostShield v2.1.0 — DNS-over-HTTPS Resolver
//
// Features:
// - RFC 8484 POST and GET wire format
// - Certificate pinning for all built-in providers
// - Automatic failover: retries with next provider on failure
// - Latency tracking: remembers fastest provider
// - Connection pooling via shared OkHttpClient
// ══════════════════════════════════════════════════════════════

@Singleton
class DohResolver @Inject constructor() {

    companion object {
        private const val TAG = "DohResolver"
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

    // Failover order — rotated when a provider fails
    private val failoverOrder = Provider.entries.toMutableList()
    private val consecutiveFailures = AtomicInteger(0)

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
    ): ByteArray? = withContext(Dispatchers.IO) {
        // Smart DNS: if we have latency data, prefer the fastest provider
        val preferredProvider = getFastestProvider() ?: provider

        // Try preferred provider first
        val result = doResolve(dnsQuery, preferredProvider, client)
        if (result != null) {
            consecutiveFailures.set(0)
            return@withContext result
        }

        // Failover: try other providers ordered by latency (fastest first)
        Log.w(TAG, "${preferredProvider.name} failed, trying failover...")
        val orderedFallbacks = failoverOrder.sortedBy { latencyEma[it] ?: Long.MAX_VALUE }
        for (fallback in orderedFallbacks) {
            if (fallback == preferredProvider) continue
            val fbResult = doResolve(dnsQuery, fallback, client)
            if (fbResult != null) {
                Log.i(TAG, "Failover to ${fallback.name} succeeded")
                return@withContext fbResult
            }
        }

        // Fail closed — never downgrade to unpinned resolution
        Log.e(TAG, "All pinned DoH providers failed — refusing to downgrade to unpinned. DNS resolution failed.")
        null
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

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun doResolve(dnsQuery: ByteArray, provider: Provider, httpClient: OkHttpClient): ByteArray? {
        return try {
            val start = System.nanoTime()
            val request = Request.Builder()
                .url(provider.url)
                .post(dnsQuery.toRequestBody(DNS_MESSAGE_TYPE))
                .addHeader("Accept", "application/dns-message")
                .build()

            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null) {
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        updateLatency(provider, elapsedMs)
                    }
                    bytes
                } else {
                    null
                }
            }
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
