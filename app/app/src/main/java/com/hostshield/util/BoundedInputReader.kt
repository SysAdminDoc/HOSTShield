package com.hostshield.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

class InputLimitExceededException(message: String) : Exception(message)

object BoundedInputReader {
    private const val DEFAULT_BUFFER_BYTES = 8 * 1024

    fun readBytes(input: InputStream, maxBytes: Long, label: String): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }

        val out = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_BYTES.toLong()).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
        var total = 0L

        while (true) {
            val remaining = maxBytes - total
            val readLimit = minOf(buffer.size.toLong(), remaining + 1).toInt()
            val read = input.read(buffer, 0, readLimit)
            if (read == -1) break
            if (read == 0) continue

            if (total + read > maxBytes) {
                throw InputLimitExceededException("$label exceeds ${formatBytes(maxBytes)} limit")
            }

            out.write(buffer, 0, read)
            total += read
        }

        return out.toByteArray()
    }

    fun readUtf8(input: InputStream, maxBytes: Long, label: String): String =
        readBytes(input, maxBytes, label).toString(Charsets.UTF_8)

    private fun formatBytes(bytes: Long): String {
        val mib = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mib >= 1.0) {
            "%.1f MiB".format(Locale.US, mib)
        } else if (bytes == 1L) {
            "1 byte"
        } else {
            "$bytes bytes"
        }
    }
}
