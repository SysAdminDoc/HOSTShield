package com.hostshield.service

import android.util.Log
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.source.BoundedResponseReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote updater for DoH bypass domain list.
 *
 * DoH providers launch new endpoints regularly. Rather than waiting for app
 * updates, we can fetch a supplementary domain list from a hosted JSON file.
 * The list is additive — it supplements (never replaces) the hardcoded
 * dohBypassDomains in BlocklistHolder.
 *
 * Update flow:
 * 1. Fetch JSON from configured URL (default: HostShield GitHub repo)
 * 2. Parse domain list + version
 * 3. Store in DataStore preferences
 * 4. Next blocklist reload merges remote domains into trie
 *
 * JSON format (hosted on GitHub Pages or raw.githubusercontent.com):
 * {
 *   "version": 2,
 *   "updated": "2026-02-15",
 *   "domains": ["new-doh-provider.example.com", ...],
 *   "wildcards": ["new-provider.io", ...]
 * }
 */
@Singleton
class DohBypassUpdater @Inject constructor(
    private val prefs: AppPreferences,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "DohBypassUpdater"
        private const val DEFAULT_URL =
            "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/doh-bypass-list.json"
        private const val MAX_DOMAINS = 500 // safety cap
        private const val MAX_JSON_SIZE = 50_000L // 50KB max
    }

    data class RemoteList(
        val version: Int = 0,
        val updated: String = "",
        val domains: Set<String> = emptySet(),
        val wildcards: Set<String> = emptySet()
    )

    /**
     * Fetch remote DoH bypass list and store in preferences.
     *
     * @return The fetched list, or null on failure (existing cached list is preserved)
     */
    suspend fun fetchAndStore(): RemoteList? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(DEFAULT_URL)
                .addHeader("Accept", "application/json")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch failed: HTTP ${response.code}")
                    return@withContext null
                }
                BoundedResponseReader.readUtf8(
                    response,
                    MAX_JSON_SIZE,
                    "remote DoH bypass list"
                ).content
            }
            if (body.isBlank()) {
                Log.w(TAG, "Empty response body")
                return@withContext null
            }

            val list = parseJson(body)
            if (list != null) {
                prefs.setRemoteDohBypassList(
                    list.domains.joinToString(","),
                    list.wildcards.joinToString(","),
                    list.version
                )
                Log.i(TAG, "Updated remote DoH bypass list: v${list.version}, " +
                    "${list.domains.size} domains, ${list.wildcards.size} wildcards")
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Get cached remote list from preferences (no network).
     */
    suspend fun getCached(): RemoteList {
        val domains = prefs.getRemoteDohDomains()
        val wildcards = prefs.getRemoteDohWildcards()
        val version = prefs.getRemoteDohVersion()
        return RemoteList(
            version = version,
            domains = normalizeCachedSet(domains),
            wildcards = normalizeCachedSet(wildcards)
        )
    }

    /**
     * Merge the cached remote list into a blocklist rebuild snapshot.
     *
     * This is intentionally cache-only so rebuilds never depend on network
     * availability. HostsUpdateWorker is responsible for refreshing the cache.
     */
    suspend fun mergeCachedInto(
        blockDomains: MutableSet<String>,
        wildcardBlockDomains: MutableSet<String>
    ): RemoteList {
        val cached = getCached()
        blockDomains.addAll(cached.domains)
        wildcardBlockDomains.addAll(cached.wildcards)
        return cached
    }

    /**
     * Minimal JSON parser — avoids adding org.json or Gson dependency.
     * Parses the simple flat structure described in the class doc.
     */
    private fun parseJson(json: String): RemoteList? {
        return try {
            val obj = org.json.JSONObject(json)
            val version = obj.optInt("version", 0)
            val updated = obj.optString("updated", "")

            val domainsArray = obj.optJSONArray("domains")
            val domains = mutableSetOf<String>()
            if (domainsArray != null) {
                for (i in 0 until minOf(domainsArray.length(), MAX_DOMAINS)) {
                    val d = domainsArray.optString(i, "").trim().lowercase()
                    if (d.isNotBlank() && d.contains('.') && !d.contains(' ')) {
                        domains.add(d)
                    }
                }
            }

            val wildcardsArray = obj.optJSONArray("wildcards")
            val wildcards = mutableSetOf<String>()
            if (wildcardsArray != null) {
                for (i in 0 until minOf(wildcardsArray.length(), MAX_DOMAINS)) {
                    val w = wildcardsArray.optString(i, "").trim().lowercase()
                    if (w.isNotBlank() && w.contains('.') && !w.contains(' ')) {
                        wildcards.add(w)
                    }
                }
            }

            RemoteList(version, updated, domains, wildcards)
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse error: ${e.message}")
            null
        }
    }

    private fun normalizeCachedSet(value: String): Set<String> {
        if (value.isBlank()) return emptySet()
        return value.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it.contains('.') && !it.contains(' ') }
            .toSet()
    }
}
