package com.hostshield.data.source

import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.fail
import org.junit.Test

class BoundedResponseReaderTest {

    @Test
    fun `rejects declared content length before materializing body`() {
        val response = responseWith("abcdef")

        val error = expectSourceDownloadException {
            BoundedResponseReader.readUtf8(response, maxBytes = 5, label = "test body")
        }

        assertThat(error.message).contains("test body exceeds")
        assertThat(error.message).contains("Content-Length")
        assertThat(error.httpStatus).isEqualTo(200)
    }

    @Test
    fun `rejects unknown length body when streaming passes cap`() {
        val response = responseWithBody(UnknownLengthBody("abcdef"))

        val error = expectSourceDownloadException {
            BoundedResponseReader.readUtf8(response, maxBytes = 5, label = "chunked body")
        }

        assertThat(error.message).contains("chunked body exceeds")
        assertThat(error.httpStatus).isEqualTo(200)
    }

    @Test
    fun `reads exact limit body and returns byte count`() {
        val response = responseWith("abcde")

        val body = BoundedResponseReader.readUtf8(response, maxBytes = 5, label = "exact body")

        assertThat(body.content).isEqualTo("abcde")
        assertThat(body.sizeBytes).isEqualTo(5)
    }

    private fun expectSourceDownloadException(block: () -> Unit): SourceDownloadException {
        return try {
            block()
            fail("Expected SourceDownloadException")
            error("unreachable")
        } catch (e: SourceDownloadException) {
            e
        }
    }

    private fun responseWith(content: String): Response {
        return responseWithBody(content.toResponseBody("text/plain".toMediaType()))
    }

    private fun responseWithBody(body: ResponseBody): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://example.test/list.txt").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private class UnknownLengthBody(private val content: String) : ResponseBody() {
        override fun contentType() = "text/plain".toMediaType()
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = Buffer().writeUtf8(content)
    }
}
