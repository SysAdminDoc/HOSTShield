package com.hostshield.util

import android.util.Log
import com.hostshield.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.6.0 -- GitHub Release Update Checker

@Singleton
class UpdateChecker @Inject constructor() {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val RELEASES_URL =
            "https://api.github.com/repos/SysAdminDoc/HostShield/releases"
    }

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String,
        val htmlUrl: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Check GitHub releases for a newer APK.
     * Compares semantic version from the latest release tag against BuildConfig.VERSION_NAME.
     */
    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("GitHub API returned ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val releases = JSONArray(body)
            if (releases.length() == 0) {
                return@withContext Result.success(UpdateInfo(
                    hasUpdate = false,
                    latestVersion = BuildConfig.VERSION_NAME,
                    currentVersion = BuildConfig.VERSION_NAME,
                    downloadUrl = "",
                    releaseNotes = "",
                    publishedAt = "",
                    htmlUrl = ""
                ))
            }

            // Find the latest non-prerelease, non-draft release
            var latestRelease = releases.getJSONObject(0)
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                if (!rel.optBoolean("draft", false) && !rel.optBoolean("prerelease", false)) {
                    latestRelease = rel
                    break
                }
            }

            val tagName = latestRelease.optString("tag_name", "")
            val latestVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val currentVersion = BuildConfig.VERSION_NAME

            // Find APK asset download URL
            val assets = latestRelease.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            // Fall back to release page if no APK asset
            val htmlUrl = latestRelease.optString("html_url", "")
            if (apkUrl.isEmpty()) apkUrl = htmlUrl

            val releaseNotes = latestRelease.optString("body", "").take(500)
            val publishedAt = latestRelease.optString("published_at", "")
                .replace("T", " ").replace("Z", "").take(16)

            val hasUpdate = isNewer(latestVersion, currentVersion)

            Log.i(TAG, "Update check: current=$currentVersion, latest=$latestVersion, " +
                "hasUpdate=$hasUpdate, apkUrl=${apkUrl.take(60)}")

            Result.success(UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                downloadUrl = apkUrl,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Semantic version comparison: is [remote] newer than [local]?
     * Handles formats like "1.6.0", "1.6.1-beta", "2.0".
     */
    private fun isNewer(remote: String, local: String): Boolean {
        try {
            val r = remote.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val l = local.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(r.size, l.size)
            for (i in 0 until maxLen) {
                val rv = r.getOrElse(i) { 0 }
                val lv = l.getOrElse(i) { 0 }
                if (rv > lv) return true
                if (rv < lv) return false
            }
        } catch (_: Exception) { }
        return false
    }
}
