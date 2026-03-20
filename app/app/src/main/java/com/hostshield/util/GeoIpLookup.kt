package com.hostshield.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v3.8.0 -- GeoIP + ASN Lookup
// Uses ip-api.com (free, no key, 45 req/min) with in-memory cache.

@Singleton
class GeoIpLookup @Inject constructor() {

    companion object {
        private const val TAG = "GeoIpLookup"
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /** Lookup GeoIP info for an IP address. Returns cached result if available. */
    suspend fun lookup(ip: String): GeoInfo? {
        if (ip.isBlank() || ip == "0.0.0.0" || ip.startsWith("10.") ||
            ip.startsWith("192.168.") || ip.startsWith("127.") || ip.startsWith("::")) {
            return GeoInfo(ip = ip, country = "Local", countryCode = "LO", isp = "Private network")
        }

        cache[ip]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://ip-api.com/json/$ip?fields=status,country,countryCode,city,isp,org,as")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("status") != "success") return@withContext null

                val countryCode = json.optString("countryCode", "")
                val info = GeoInfo(
                    ip = ip,
                    country = json.optString("country", ""),
                    countryCode = countryCode,
                    city = json.optString("city", ""),
                    isp = json.optString("isp", ""),
                    org = json.optString("org", ""),
                    asn = json.optString("as", ""),
                    flag = countryCodeToFlag(countryCode)
                )
                cache[ip] = info
                info
            } catch (e: Exception) {
                Log.d(TAG, "GeoIP lookup failed for $ip: ${e.message}")
                null
            }
        }
    }

    /** Batch lookup for multiple IPs (from resolved_ips field). */
    suspend fun lookupAll(ips: List<String>): List<GeoInfo> {
        return ips.mapNotNull { lookup(it.trim()) }
    }

    private fun countryCodeToFlag(code: String): String {
        if (code.length != 2) return ""
        val first = Character.toChars(0x1F1E6 + (code[0].uppercaseChar() - 'A'))
        val second = Character.toChars(0x1F1E6 + (code[1].uppercaseChar() - 'A'))
        return String(first) + String(second)
    }
}
