package com.hostshield.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTcpFallbackTest {

    @Test
    fun `truncated IPv6 path MTU UDP response retries TCP within 200 ms`() {
        val truncatedUdpResponse = buildPathMtuSizedResponse(truncated = true)
        val fullTcpResponse = buildFullTcpResponse()
        var tcpRetryCount = 0

        val result = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = truncatedUdpResponse,
            udpReceivedAtMs = 1_000,
            nowMs = { 1_125 }
        ) {
            tcpRetryCount++
            fullTcpResponse
        }

        assertEquals(1, tcpRetryCount)
        assertTrue(result.retriedOverTcp)
        assertEquals(125L, result.retryStartDelayMs)
        assertTrue(result.retryStartedWithinDeadline)
        assertArrayEquals(fullTcpResponse, result.response)
    }

    @Test
    fun `non truncated UDP response does not retry TCP`() {
        val udpResponse = buildPathMtuSizedResponse(truncated = false)
        var tcpRetryCount = 0

        val result = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = udpResponse,
            udpReceivedAtMs = 1_000,
            nowMs = { 1_010 }
        ) {
            tcpRetryCount++
            buildFullTcpResponse()
        }

        assertEquals(0, tcpRetryCount)
        assertFalse(result.retriedOverTcp)
        assertSame(udpResponse, result.response)
        assertTrue(result.retryStartedWithinDeadline)
    }

    @Test
    fun `truncated UDP response keeps original response when TCP retry fails`() {
        val truncatedUdpResponse = buildPathMtuSizedResponse(truncated = true)

        val result = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = truncatedUdpResponse,
            udpReceivedAtMs = 2_000,
            nowMs = { 2_020 }
        ) {
            null
        }

        assertTrue(result.retriedOverTcp)
        assertEquals(20L, result.retryStartDelayMs)
        assertSame(truncatedUdpResponse, result.response)
    }

    @Test
    fun `retry deadline is inclusive at 200 ms`() {
        val onDeadline = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = buildPathMtuSizedResponse(truncated = true),
            udpReceivedAtMs = 5_000,
            nowMs = { 5_200 }
        ) {
            buildFullTcpResponse()
        }

        val afterDeadline = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = buildPathMtuSizedResponse(truncated = true),
            udpReceivedAtMs = 5_000,
            nowMs = { 5_201 }
        ) {
            buildFullTcpResponse()
        }

        assertTrue(onDeadline.retryStartedWithinDeadline)
        assertFalse(afterDeadline.retryStartedWithinDeadline)
    }

    @Test
    fun `malformed short response does not trigger TCP retry`() {
        assertFalse(DnsTcpFallback.hasTruncationFlag(ByteArray(3).apply { this[2] = 0x02 }))
    }

    private fun buildPathMtuSizedResponse(truncated: Boolean): ByteArray =
        ByteArray(1_500) { 0 }.apply {
            this[0] = 0x12
            this[1] = 0x34
            this[2] = if (truncated) 0x82.toByte() else 0x80.toByte()
            this[3] = 0x80.toByte()
            this[4] = 0x00
            this[5] = 0x01
        }

    private fun buildFullTcpResponse(): ByteArray =
        ByteArray(96) { index -> (index and 0x7F).toByte() }.apply {
            this[0] = 0x12
            this[1] = 0x34
            this[2] = 0x80.toByte()
            this[3] = 0x80.toByte()
        }
}
