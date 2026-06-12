package com.hostshield.data.source

import okhttp3.Response
import java.io.ByteArrayOutputStream

data class BoundedResponseBody(
    val content: String,
    val sizeBytes: Long
)

object BoundedResponseReader {
    private const val DEFAULT_BUFFER_BYTES = 8 * 1024

    fun readUtf8(response: Response, maxBytes: Long, label: String = "response"): BoundedResponseBody {
        require(maxBytes > 0) { "maxBytes must be positive" }

        val body = response.body
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            throw SourceDownloadException(
                "$label exceeds ${formatBytes(maxBytes)} limit (Content-Length: $declaredLength bytes)",
                response.code
            )
        }

        val out = ByteArrayOutputStream(minOf(declaredLength.takeIf { it >= 0 } ?: 0L, maxBytes).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
        var total = 0L

        body.byteStream().use { input ->
            while (true) {
                val remaining = maxBytes - total
                val readLimit = minOf(buffer.size.toLong(), remaining + 1).toInt()
                val read = input.read(buffer, 0, readLimit)
                if (read == -1) break

                if (total + read > maxBytes) {
                    throw SourceDownloadException(
                        "$label exceeds ${formatBytes(maxBytes)} limit",
                        response.code
                    )
                }

                out.write(buffer, 0, read)
                total += read
            }
        }

        return BoundedResponseBody(
            content = out.toByteArray().toString(Charsets.UTF_8),
            sizeBytes = total
        )
    }

    private fun formatBytes(bytes: Long): String {
        val mib = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mib >= 1.0) {
            "%.1f MiB".format(java.util.Locale.US, mib)
        } else {
            "$bytes byte"
        }
    }
}
