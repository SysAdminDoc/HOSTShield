package com.hostshield.service

/**
 * RFC 7766 TCP retry policy for UDP DNS responses with the TC bit set.
 *
 * Kept separate from DnsVpnService so truncation behavior is covered by fast
 * JVM tests instead of only by service-level network integration tests.
 */
object DnsTcpFallback {
    const val MAX_TCP_RETRY_START_DELAY_MS: Long = 200

    private const val DNS_HEADER_LENGTH = 12
    private const val TRUNCATED_FLAG_MASK = 0x02

    data class Result(
        val response: ByteArray,
        val retriedOverTcp: Boolean,
        val retryStartDelayMs: Long?
    ) {
        val retryStartedWithinDeadline: Boolean
            get() = retryStartDelayMs == null || retryStartDelayMs <= MAX_TCP_RETRY_START_DELAY_MS
    }

    fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    fun hasTruncationFlag(response: ByteArray): Boolean =
        response.size >= DNS_HEADER_LENGTH && (response[2].toInt() and TRUNCATED_FLAG_MASK) != 0

    fun resolveTruncatedUdpResponse(
        udpResponse: ByteArray,
        udpReceivedAtMs: Long,
        nowMs: () -> Long = { monotonicNowMs() },
        tcpRetry: () -> ByteArray?
    ): Result {
        if (!hasTruncationFlag(udpResponse)) {
            return Result(
                response = udpResponse,
                retriedOverTcp = false,
                retryStartDelayMs = null
            )
        }

        val retryStartedAtMs = nowMs()
        val tcpResponse = tcpRetry()
        val delayMs = (retryStartedAtMs - udpReceivedAtMs).coerceAtLeast(0)
        return Result(
            response = tcpResponse ?: udpResponse,
            retriedOverTcp = true,
            retryStartDelayMs = delayMs
        )
    }
}
