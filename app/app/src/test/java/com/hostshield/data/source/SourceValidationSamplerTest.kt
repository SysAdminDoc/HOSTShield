package com.hostshield.data.source

import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Test

class SourceValidationSamplerTest {

    @Test
    fun `large declared response is validated from its sample`() {
        val response = responseWith(
            DeclaredLengthBody(
                content = "# header\n0.0.0.0 ads.example.test\n",
                declaredLength = 9L * 1024L * 1024L
            )
        )

        val count = SourceValidationSampler.countCandidateLines(response, maxBytes = 64)

        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `sampling stops at byte budget without treating truncation as failure`() {
        val body = TrackingBody(
            buildString {
                appendLine("0.0.0.0 first.example.test")
                repeat(200) { appendLine("0.0.0.0 domain-$it.example.test") }
            }
        )
        val response = responseWith(body)

        val count = SourceValidationSampler.countCandidateLines(response, maxBytes = 64)

        assertThat(count).isAtLeast(1)
        assertThat(body.remainingBytes()).isGreaterThan(0)
    }

    @Test
    fun `comment only sample reports no candidate entries`() {
        val response = responseWith(DeclaredLengthBody("# one\n# two\n", 12))

        val count = SourceValidationSampler.countCandidateLines(response, maxBytes = 64)

        assertThat(count).isEqualTo(0)
    }

    private fun responseWith(body: ResponseBody): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.test/list.txt").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

    private class DeclaredLengthBody(
        content: String,
        private val declaredLength: Long
    ) : ResponseBody() {
        private val buffer = Buffer().writeUtf8(content)

        override fun contentType() = "text/plain".toMediaType()
        override fun contentLength(): Long = declaredLength
        override fun source(): BufferedSource = buffer
    }

    private class TrackingBody(content: String) : ResponseBody() {
        private val buffer = Buffer().writeUtf8(content)

        override fun contentType() = "text/plain".toMediaType()
        override fun contentLength(): Long = buffer.size
        override fun source(): BufferedSource = buffer
        fun remainingBytes(): Long = buffer.size
    }
}
