package com.hostshield.data.source

import com.hostshield.data.model.HostSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v0.1.0 — Source Downloader
// ══════════════════════════════════════════════════════════════

data class DownloadResult(
    val content: String = "",
    val etag: String = "",
    val lastModified: String = "",
    val sizeBytes: Long = 0L,
    val notModified: Boolean = false
)

@Singleton
class SourceDownloader @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Download a hosts source, using ETag/If-Modified-Since for cache validation.
     */
    suspend fun download(source: HostSource): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(source.url)

            // Conditional request headers for bandwidth savings
            if (source.etag.isNotEmpty()) {
                requestBuilder.addHeader("If-None-Match", source.etag)
            }
            if (source.lastModifiedOnline.isNotEmpty()) {
                requestBuilder.addHeader("If-Modified-Since", source.lastModifiedOnline)
            }

            val response = client.newCall(requestBuilder.build()).execute()

            when (response.code) {
                304 -> {
                    response.close()
                    Result.success(DownloadResult(notModified = true, etag = source.etag))
                }
                200 -> {
                    val body = response.body?.string() ?: ""
                    val etag = response.header("ETag") ?: ""
                    val lastMod = response.header("Last-Modified") ?: ""
                    val size = body.length.toLong()
                    response.close()
                    Result.success(DownloadResult(body, etag, lastMod, size))
                }
                else -> {
                    val msg = "HTTP ${response.code}: ${response.message}"
                    response.close()
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Test if a URL is reachable and returns valid hosts content.
     */
    suspend fun validate(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val lineCount = body.lines().count { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#")
            }
            Result.success(lineCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
