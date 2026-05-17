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
// Blocklist source downloader
// ══════════════════════════════════════════════════════════════

data class DownloadResult(
    val content: String = "",
    val etag: String = "",
    val lastModified: String = "",
    val sizeBytes: Long = 0L,
    val notModified: Boolean = false
)

class SourceDownloadException(
    message: String,
    val httpStatus: Int = 0,
    cause: Throwable? = null
) : Exception(message, cause)

fun Throwable.sourceHttpStatus(): Int {
    if (this is SourceDownloadException) return httpStatus
    return Regex("""\bHTTP\s+(\d{3})\b""")
        .find(message.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0
}

@Singleton
class SourceDownloader @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Download a hosts source, using ETag/If-Modified-Since for cache validation.
     * @param forceDownload If true, skip conditional headers and always download fresh.
     *   Use during blocklist rebuilds where we need ALL domains, not just changes.
     */
    suspend fun download(source: HostSource, forceDownload: Boolean = false): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(source.url)

            // Conditional request headers for bandwidth savings
            // SKIP when forceDownload=true (blocklist rebuild needs ALL domains)
            if (!forceDownload) {
                if (source.etag.isNotEmpty()) {
                    requestBuilder.addHeader("If-None-Match", source.etag)
                }
                if (source.lastModifiedOnline.isNotEmpty()) {
                    requestBuilder.addHeader("If-Modified-Since", source.lastModifiedOnline)
                }
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    304 -> {
                        Result.success(DownloadResult(notModified = true, etag = source.etag))
                    }
                    200 -> {
                        val body = response.body?.string() ?: ""
                        val etag = response.header("ETag") ?: ""
                        val lastMod = response.header("Last-Modified") ?: ""
                        val size = body.length.toLong()
                        Result.success(DownloadResult(body, etag, lastMod, size))
                    }
                    else -> {
                        val msg = "HTTP ${response.code}: ${response.message}"
                        Result.failure(SourceDownloadException(msg, response.code))
                    }
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
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceDownloadException(
                        "HTTP ${response.code}: ${response.message}",
                        response.code
                    )
                }
                response.body?.string() ?: ""
            }

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
