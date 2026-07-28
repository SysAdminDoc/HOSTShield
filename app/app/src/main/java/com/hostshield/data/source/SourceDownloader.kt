package com.hostshield.data.source

import com.hostshield.data.model.HostSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
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

    companion object {
        const val MAX_SOURCE_DOWNLOAD_BYTES = 80L * 1024L * 1024L
        const val MAX_SOURCE_VALIDATION_SAMPLE_BYTES = 256L * 1024L
    }

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
            val requestBuilder = Request.Builder()
                .url(SourceUrlPolicy.requireDownloadable(source.url))

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
                        val body = BoundedResponseReader.readUtf8(
                            response,
                            MAX_SOURCE_DOWNLOAD_BYTES,
                            "source ${source.label.ifBlank { source.url }}"
                        )
                        val etag = response.header("ETag") ?: ""
                        val lastMod = response.header("Last-Modified") ?: ""
                        Result.success(DownloadResult(body.content, etag, lastMod, body.sizeBytes))
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
            val request = Request.Builder()
                .url(SourceUrlPolicy.requireDownloadable(url))
                .build()
            val lineCount = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceDownloadException(
                        "HTTP ${response.code}: ${response.message}",
                        response.code
                    )
                }
                SourceValidationSampler.countCandidateLines(
                    response,
                    MAX_SOURCE_VALIDATION_SAMPLE_BYTES
                )
            }

            Result.success(lineCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Reads only the beginning of a source when checking reachability.
 *
 * Health validation only needs proof that a successful response contains
 * candidate list entries. Materializing the whole response made every valid
 * source larger than the validation cap look like an HTTP-200 download failure.
 */
internal object SourceValidationSampler {
    private const val BUFFER_BYTES = 8L * 1024L

    fun countCandidateLines(response: Response, maxBytes: Long): Int {
        require(maxBytes > 0) { "maxBytes must be positive" }

        val sample = Buffer()
        val source = response.body.source()
        var sampledBytes = 0L
        while (sampledBytes < maxBytes) {
            val read = source.read(
                sample,
                minOf(BUFFER_BYTES, maxBytes - sampledBytes)
            )
            if (read == -1L) break
            sampledBytes += read
        }

        return sample.readString(Charsets.UTF_8)
            .lineSequence()
            .count { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#")
            }
    }
}
