package com.hostshield.service

import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the read that broke DoH for every provider (GitHub #1,
 * fixed in v6.9.10).
 *
 * `readBoundedBody` previously used `readByteArray(cap + 1)`, which reads EXACTLY
 * that many bytes and throws `EOFException` on a short stream — i.e. on every
 * normal ~100-byte DoH answer. DoH silently failed everywhere and the plaintext
 * fallback took over. Nothing in the suite covered it until now.
 */
class DohResolverBoundedBodyTest {

    private val resolver = DohResolver(mockk(relaxed = true), mockk(relaxed = true))

    private val dnsMediaType = "application/dns-message".toMediaType()

    private fun response(body: ByteArray, declaredLength: Long? = null): Response {
        val responseBody = if (declaredLength == null) {
            body.toResponseBody(dnsMediaType)
        } else {
            object : okhttp3.ResponseBody() {
                override fun contentType() = dnsMediaType
                override fun contentLength() = declaredLength
                override fun source() = okio.Buffer().write(body)
            }
        }
        return Response.Builder()
            .request(Request.Builder().url("https://dns.example/dns-query").build())
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()
    }

    // The exact regression: a normal short DoH answer must come back intact.
    @Test
    fun `a normal sized DNS answer is returned in full`() {
        val body = ByteArray(100) { (it and 0xFF).toByte() }
        val read = resolver.readBoundedBody(response(body))
        assertArrayEquals(body, read)
    }

    @Test
    fun `a minimal 12 byte DNS message is accepted`() {
        val body = ByteArray(12) { 1 }
        assertEquals(12, resolver.readBoundedBody(response(body))?.size)
    }

    @Test
    fun `a body shorter than a DNS header is rejected`() {
        assertNull(resolver.readBoundedBody(response(ByteArray(11))))
    }

    @Test
    fun `an empty body is rejected`() {
        assertNull(resolver.readBoundedBody(response(ByteArray(0))))
    }

    @Test
    fun `a body at the cap is accepted`() {
        val body = ByteArray(DohResolver.MAX_DOH_RESPONSE) { 7 }
        assertEquals(DohResolver.MAX_DOH_RESPONSE, resolver.readBoundedBody(response(body))?.size)
    }

    @Test
    fun `a body over the cap is rejected`() {
        val body = ByteArray(DohResolver.MAX_DOH_RESPONSE + 1) { 7 }
        assertNull(resolver.readBoundedBody(response(body)))
    }

    // A hostile endpoint declaring a huge Content-Length must be refused before
    // the body is buffered into memory.
    @Test
    fun `an oversized declared content length is rejected without reading`() {
        val body = ByteArray(64) { 3 }
        assertNull(resolver.readBoundedBody(response(body, declaredLength = 10L * 1024 * 1024)))
    }

    @Test
    fun `an unknown content length still reads a valid body`() {
        val body = ByteArray(80) { 5 }
        assertArrayEquals(body, resolver.readBoundedBody(response(body, declaredLength = -1L)))
    }
}
