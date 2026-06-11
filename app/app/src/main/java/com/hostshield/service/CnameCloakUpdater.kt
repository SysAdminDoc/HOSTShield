package com.hostshield.service

import android.util.Log
import com.hostshield.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote updater for CNAME cloak tracker databases.
 *
 * v5.0: Fetches known CNAME cloaking domains from two community-maintained sources:
 *
 * 1. AdGuard cname-trackers (github.com/AdguardTeam/cname-trackers)
 *    - Auto-updated list of CNAME-cloaked tracker domains discovered at scale
 *    - Format: one domain per line (disguised tracker targets)
 *
 * 2. NextDNS cname-cloaking-blocklist (github.com/nextdns/cname-cloaking-blocklist)
 *    - Known CNAME cloaking destinations
 *    - Format: one domain per line
 *
 * Update flow:
 * 1. Fetch both lists on each blocklist refresh cycle (via HostsUpdateWorker)
 * 2. Parse domain lists, validate, merge
 * 3. Store merged set in DataStore preferences
 * 4. Update CnameCloakDetector.cnameCloakDomains in memory
 *
 * These domains are checked during CNAME chain inspection — if any CNAME
 * target in a response chain matches one of these domains, the query is blocked
 * even if the original queried domain is not in any blocklist.
 */
@Singleton
class CnameCloakUpdater @Inject constructor(
    private val prefs: AppPreferences,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "CnameCloakUpdater"

        // AdGuard: combined list of known CNAME cloak tracker targets
        private const val ADGUARD_URL =
            "https://raw.githubusercontent.com/AdguardTeam/cname-trackers/master/combined_disguised_trackers.txt"

        // NextDNS: known CNAME cloaking destinations
        private const val NEXTDNS_URL =
            "https://raw.githubusercontent.com/nextdns/cname-cloaking-blocklist/master/domains"

        private const val MAX_DOMAINS = 5000     // safety cap per source
        private const val MAX_BODY_SIZE = 500_000 // 500KB max per source
    }

    /**
     * Fetch both CNAME cloak databases, merge, store, and update the detector.
     *
     * @return Number of unique domains loaded, or -1 on complete failure
     */
    suspend fun fetchAndUpdate(): Int = withContext(Dispatchers.IO) {
        val adguardDomains = fetchDomainList(ADGUARD_URL, "AdGuard")
        val nextdnsDomains = fetchDomainList(NEXTDNS_URL, "NextDNS")

        if (adguardDomains == null && nextdnsDomains == null) {
            Log.w(TAG, "Both CNAME cloak sources failed — using cached")
            // Load from cache if available
            val cached = getCached()
            if (cached.isNotEmpty()) {
                CnameCloakDetector.updateCloakDatabase(cached)
            }
            return@withContext -1
        }

        val merged = HashSet<String>(
            (adguardDomains?.size ?: 0) + (nextdnsDomains?.size ?: 0)
        )
        adguardDomains?.let { merged.addAll(it) }
        nextdnsDomains?.let { merged.addAll(it) }

        // Store in preferences
        prefs.setCnameCloakDomains(merged.joinToString(","))

        // Update detector in memory
        CnameCloakDetector.updateCloakDatabase(merged)

        Log.i(TAG, "CNAME cloak databases updated: ${merged.size} domains " +
            "(AdGuard: ${adguardDomains?.size ?: 0}, NextDNS: ${nextdnsDomains?.size ?: 0})")
        merged.size
    }

    /**
     * Load cached CNAME cloak domains from preferences (no network).
     */
    suspend fun getCached(): Set<String> {
        val stored = prefs.getCnameCloakDomains()
        if (stored.isBlank()) return emptySet()
        return stored.split(",").filter { it.isNotBlank() }.toSet()
    }

    /**
     * Load cached domains and update the detector on app startup.
     */
    suspend fun loadCached() {
        val cached = getCached()
        if (cached.isNotEmpty()) {
            CnameCloakDetector.updateCloakDatabase(cached)
            Log.i(TAG, "Loaded ${cached.size} cached CNAME cloak domains")
        }
    }

    /**
     * Fetch a plain-text domain list from a URL.
     * Expects one domain per line, # comments, blank lines.
     */
    private fun fetchDomainList(url: String, source: String): Set<String>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "text/plain")
                .build()

            val response = client.newCall(request).execute()
            val body = response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "$source fetch failed: HTTP ${resp.code}")
                    return null
                }
                val src = resp.body.source()
                src.request(MAX_BODY_SIZE.toLong())
                src.buffer.readUtf8(minOf(src.buffer.size, MAX_BODY_SIZE.toLong()))
            }

            val domains = HashSet<String>()
            body.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) return@forEach
                // Some lists have format "domain.com" or "||domain.com^" — handle both
                val domain = line
                    .removePrefix("||")
                    .removeSuffix("^")
                    .removeSuffix("\$important")
                    .trim()
                    .lowercase()
                if (domain.isNotBlank() && domain.contains('.') && !domain.contains(' ')
                    && domain.length in 3..253 && domains.size < MAX_DOMAINS) {
                    domains.add(domain)
                }
            }

            Log.i(TAG, "$source: parsed ${domains.size} CNAME cloak domains")
            domains
        } catch (e: Exception) {
            Log.w(TAG, "$source fetch failed: ${e.message}")
            null
        }
    }
}
