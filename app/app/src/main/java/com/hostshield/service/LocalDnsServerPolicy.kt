package com.hostshield.service

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

internal const val LOCAL_DNS_MAX_UDP_RESPONSE_BYTES = 1472

internal fun isAllowedLocalDnsClient(address: InetAddress, allowExternalClients: Boolean = false): Boolean {
    if (allowExternalClients) return true
    if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true

    val bytes = address.address
    return bytes.size == 16 && ((bytes[0].toInt() and 0xFE) == 0xFC)
}

internal fun localDnsUdpResponse(query: ByteArray, response: ByteArray): ByteArray {
    return if (response.size <= LOCAL_DNS_MAX_UDP_RESPONSE_BYTES) {
        response
    } else {
        buildTruncatedDnsResponse(query)
    }
}

internal fun buildTruncatedDnsResponse(query: ByteArray): ByteArray {
    if (query.size < 12) return query
    val nameEnd = DnsPacketParser.skipDnsName(query, 12)
    val questionEnd = if (nameEnd >= 0 && nameEnd + 4 <= query.size) nameEnd + 4 else 12
    val response = query.copyOf(questionEnd)

    response[2] = (0x80 or 0x02 or (query[2].toInt() and 0x01)).toByte()
    response[3] = 0x80.toByte()
    response[4] = 0
    response[5] = if (questionEnd > 12) 1 else 0
    response[6] = 0
    response[7] = 0
    response[8] = 0
    response[9] = 0
    response[10] = 0
    response[11] = 0
    return response
}

internal class LocalDnsClientRateLimiter(
    private val maxQueriesPerWindow: Int = 20,
    private val windowMillis: Long = 1_000L,
    private val maxTrackedClients: Int = 256,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Window(var startedAtMillis: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(address: InetAddress): Boolean {
        val now = nowMillis()
        val key = address.hostAddress ?: address.hostName
        var allowed = false

        windows.compute(key) { _, existing ->
            val window = if (existing == null || now - existing.startedAtMillis >= windowMillis) {
                Window(startedAtMillis = now, count = 0)
            } else {
                existing
            }
            if (window.count < maxQueriesPerWindow) {
                window.count += 1
                allowed = true
            }
            window
        }

        if (windows.size > maxTrackedClients) {
            windows.entries.removeIf { now - it.value.startedAtMillis >= windowMillis }
        }
        return allowed
    }

    fun clear() {
        windows.clear()
    }
}
