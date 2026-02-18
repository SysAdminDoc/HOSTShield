package com.hostshield.service

import android.content.Context
import android.util.Log
import com.hostshield.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
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
    private val prefs: AppPreferences
) {
    companion object {
        private const val TAG = "DohBypassUpdater"
        private const val DEFAULT_URL =
            "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/doh-bypass-list.json"
        private const val MAX_DOMAINS = 500 // safety cap
        private const val MAX_JSON_SIZE = 50_000L // 50KB max
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Fetch failed: HTTP ${response.code}")
                response.close()
                return@withContext null
            }

            val body = response.body?.string()?.take(MAX_JSON_SIZE.toInt())
            if (body.isNullOrBlank()) {
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
            domains = if (domains.isBlank()) emptySet()
                else domains.split(",").filter { it.isNotBlank() }.toSet(),
            wildcards = if (wildcards.isBlank()) emptySet()
                else wildcards.split(",").filter { it.isNotBlank() }.toSet()
        )
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
}
