package com.hostshield.util

import android.util.Log
import com.hostshield.data.source.BoundedResponseReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// GeoIP and ASN lookup helper
// Uses ipapi.co (free HTTPS, no key, ~1000 req/day) with in-memory cache.
// Rate limit enforcement: tracks requests per minute window + exponential backoff on 429.

@Singleton
class GeoIpLookup @Inject constructor() {

    companion object {
        private const val TAG = "GeoIpLookup"
        private const val MAX_REQUESTS_PER_MINUTE = 28 // Stay under ipapi.co ~30/min limit
        private const val WINDOW_MS = 60_000L
        private const val MAX_BACKOFF_MS = 120_000L
        private const val MAX_GEOIP_RESPONSE_BYTES = 32L * 1024L
    }

    data class GeoInfo(
        val ip: String,
        val country: String = "",
        val countryCode: String = "",
        val city: String = "",
        val isp: String = "",
        val org: String = "",
        val asn: String = "",
        val flag: String = "" // emoji flag
    )

    private val cache = ConcurrentHashMap<String, GeoInfo>(128)
    private val MAX_CACHE_SIZE = 4096

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    // Rate limiting state
    private val windowStart = AtomicLong(System.currentTimeMillis())
    private val requestCount = AtomicInteger(0)
    private val backoffUntil = AtomicLong(0)
    private val consecutiveBackoffs = AtomicInteger(0)

    /** Check if we're within rate limits. Thread-safe with CAS. */
    private fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()

        // Exponential backoff active?
        if (now < backoffUntil.get()) return false

        // Atomic window reset — only one thread wins the CAS
        val start = windowStart.get()
        if (now - start > WINDOW_MS) {
            if (windowStart.compareAndSet(start, now)) {
                requestCount.set(0)
            }
        }

        return requestCount.get() < MAX_REQUESTS_PER_MINUTE
    }

    /** Record a 429 and apply exponential backoff. */
    private fun applyBackoff() {
        val backoffs = consecutiveBackoffs.incrementAndGet()
        val delayMs = (10_000L * (1L shl (backoffs - 1).coerceAtMost(4))).coerceAtMost(MAX_BACKOFF_MS)
        backoffUntil.set(System.currentTimeMillis() + delayMs)
        Log.w(TAG, "Rate limited (429). Backoff #$backoffs: ${delayMs}ms")
    }

    /** Lookup GeoIP info for an IP address. Returns cached result if available. */
    suspend fun lookup(ip: String): GeoInfo? {
        if (ip.isBlank() || isPrivateOrReserved(ip)) {
            return GeoInfo(ip = ip, country = "Local", countryCode = "LO", isp = "Private network")
        }

        cache[ip]?.let { return it }

        if (!canMakeRequest()) return null

        return withContext(Dispatchers.IO) {
            try {
                requestCount.incrementAndGet()
                val request = Request.Builder()
                    .url("https://ipapi.co/$ip/json/")
                    .build()
                val body = client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        applyBackoff()
                        return@withContext null
                    }

                    // Successful response resets backoff
                    consecutiveBackoffs.set(0)

                    BoundedResponseReader.readUtf8(
                        response,
                        MAX_GEOIP_RESPONSE_BYTES,
                        "GeoIP response"
                    ).content
                }
                val json = JSONObject(body)
                if (json.has("error")) return@withContext null

                val countryCode = json.optString("country_code", "")
                val info = GeoInfo(
                    ip = ip,
                    country = json.optString("country_name", ""),
                    countryCode = countryCode,
                    city = json.optString("city", ""),
                    isp = json.optString("org", ""),
                    org = json.optString("org", ""),
                    asn = json.optString("asn", ""),
                    flag = countryCodeToFlag(countryCode)
                )
                if (cache.size >= MAX_CACHE_SIZE) {
                    val oldest = cache.keys.firstOrNull()
                    if (oldest != null) cache.remove(oldest)
                }
                cache[ip] = info
                info
            } catch (e: Exception) {
                PrivacyLog.d(TAG, "GeoIP lookup failed for $ip: ${e.message}")
                null
            }
        }
    }

    /** Batch lookup for multiple IPs (from resolved_ips field). */
    suspend fun lookupAll(ips: List<String>): List<GeoInfo> {
        return ips.mapNotNull { lookup(it.trim()) }
    }

    /** Cache size for diagnostics. */
    fun cacheSize(): Int = cache.size

    /** Whether rate limit backoff is currently active. */
    fun isBackingOff(): Boolean = System.currentTimeMillis() < backoffUntil.get()

    private fun isPrivateOrReserved(ip: String): Boolean {
        if (ip.contains(':')) {
            return ip.startsWith("::") || ip.startsWith("fc") ||
                ip.startsWith("fd") || ip.startsWith("fe80:") ||
                ip == "::1"
        }
        return ip == "0.0.0.0" || ip.startsWith("10.") ||
            ip.startsWith("127.") || ip.startsWith("192.168.") ||
            ip.startsWith("169.254.") || isRfc1918_172(ip)
    }

    private fun isRfc1918_172(ip: String): Boolean {
        if (!ip.startsWith("172.")) return false
        val secondOctet = ip.substringAfter("172.").substringBefore('.').toIntOrNull() ?: return false
        return secondOctet in 16..31
    }

    private fun countryCodeToFlag(code: String): String {
        if (code.length != 2) return ""
        val first = Character.toChars(0x1F1E6 + (code[0].uppercaseChar() - 'A'))
        val second = Character.toChars(0x1F1E6 + (code[1].uppercaseChar() - 'A'))
        return String(first) + String(second)
    }
}
