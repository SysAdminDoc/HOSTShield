package com.hostshield.util

import android.content.Context
import android.util.Log
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.AsnResponse
import com.maxmind.geoip2.model.CountryResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v5.0: Offline GeoIP + ASN lookup using MaxMind GeoLite2 databases.
 *
 * Replaces the rate-limited ip-api.com API (40 req/min) with bundled MMDB
 * databases for unlimited, zero-latency, offline lookups.
 *
 * Databases:
 * - GeoLite2-Country.mmdb (~6MB) — country + continent for any IP
 * - GeoLite2-ASN.mmdb (~8MB) — ASN number + organization name
 *
 * Bundled in app assets on first install. Updated weekly via WorkManager
 * from MaxMind or community mirrors (P3TERX/GeoLite.mmdb).
 *
 * Thread-safe: DatabaseReader is immutable after construction. The CHMCache
 * option adds ~2MB memory overhead but provides faster repeated lookups.
 *
 * Falls back to the existing ip-api.com GeoIpLookup for city-level detail
 * (GeoLite2-City is ~70MB, too large to bundle).
 */
@Singleton
class OfflineGeoIp @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OfflineGeoIp"
        private const val COUNTRY_DB = "GeoLite2-Country.mmdb"
        private const val ASN_DB = "GeoLite2-ASN.mmdb"
    }

    data class GeoResult(
        val country: String = "",
        val countryCode: String = "",
        val continent: String = "",
        val asn: Int = 0,
        val asnOrg: String = "",
        val flag: String = ""
    )

    private var countryReader: DatabaseReader? = null
    private var asnReader: DatabaseReader? = null
    @Volatile private var initialized = false

    /**
     * Initialize database readers. Call once on app startup (non-blocking).
     * Copies databases from assets to internal storage on first run.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        try {
            val countryFile = ensureDatabase(COUNTRY_DB)
            val asnFile = ensureDatabase(ASN_DB)

            if (countryFile != null) {
                countryReader = DatabaseReader.Builder(countryFile)
                    .withCache(com.maxmind.db.CHMCache())
                    .build()
                Log.i(TAG, "GeoLite2-Country loaded: ${countryFile.length() / 1024}KB")
            }

            if (asnFile != null) {
                asnReader = DatabaseReader.Builder(asnFile)
                    .withCache(com.maxmind.db.CHMCache())
                    .build()
                Log.i(TAG, "GeoLite2-ASN loaded: ${asnFile.length() / 1024}KB")
            }

            initialized = countryReader != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GeoIP databases: ${e.message}")
        }
    }

    /** Whether the databases are loaded and ready for lookups. */
    fun isReady(): Boolean = initialized

    /**
     * Look up country + ASN for an IP address. O(1), no network, no rate limits.
     *
     * @param ip IPv4 or IPv6 address string
     * @return GeoResult with country, ASN, and flag emoji, or null if databases not loaded
     */
    fun lookup(ip: String): GeoResult? {
        if (!initialized) return null
        if (ip.isBlank() || isPrivateIp(ip)) {
            return GeoResult(country = "Local", countryCode = "LO", asnOrg = "Private network")
        }

        return try {
            val addr = InetAddress.getByName(ip)
            val country = try { countryReader?.country(addr) } catch (_: Exception) { null }
            val asn = try { asnReader?.asn(addr) } catch (_: Exception) { null }

            val countryCode = country?.country?.isoCode ?: ""
            GeoResult(
                country = country?.country?.name ?: "",
                countryCode = countryCode,
                continent = country?.continent?.name ?: "",
                asn = (asn?.autonomousSystemNumber ?: 0L).toInt(),
                asnOrg = asn?.autonomousSystemOrganization ?: "",
                flag = countryCodeToFlag(countryCode)
            )
        } catch (e: Exception) {
            PrivacyLog.d(TAG, "Lookup failed for $ip: ${e.message}")
            null
        }
    }

    /**
     * Update databases from a downloaded file. Called by a WorkManager job
     * that fetches fresh MMDB files weekly.
     */
    suspend fun updateDatabase(dbName: String, data: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, dbName)
            file.writeBytes(data)
            // Re-initialize to pick up new database
            initialized = false
            countryReader?.close()
            asnReader?.close()
            countryReader = null
            asnReader = null
            initialize()
            Log.i(TAG, "Database updated: $dbName (${data.size / 1024}KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update $dbName: ${e.message}")
        }
    }

    /**
     * Copy database from assets to internal storage if not already present.
     * Returns the file, or null if the asset doesn't exist.
     */
    private fun ensureDatabase(dbName: String): File? {
        val file = File(context.filesDir, dbName)
        if (file.exists() && file.length() > 0) return file

        return try {
            context.assets.open(dbName).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Extracted $dbName from assets: ${file.length() / 1024}KB")
            file
        } catch (e: Exception) {
            // Database not bundled in assets — that's OK, will be downloaded later
            Log.d(TAG, "$dbName not found in assets (will download on first update)")
            null
        }
    }

    private fun isPrivateIp(ip: String): Boolean {
        if (ip == "0.0.0.0" || ip.startsWith("10.") || ip.startsWith("192.168.") ||
            ip.startsWith("127.") || ip.startsWith("::") || ip == "::1") return true
        if (ip.startsWith("172.")) {
            val secondOctet = ip.removePrefix("172.").substringBefore('.').toIntOrNull()
            if (secondOctet != null && secondOctet in 16..31) return true
        }
        return false
    }

    private fun countryCodeToFlag(code: String): String {
        if (code.length != 2) return ""
        val first = Character.toChars(0x1F1E6 + (code[0].uppercaseChar() - 'A'))
        val second = Character.toChars(0x1F1E6 + (code[1].uppercaseChar() - 'A'))
        return String(first) + String(second)
    }
}
