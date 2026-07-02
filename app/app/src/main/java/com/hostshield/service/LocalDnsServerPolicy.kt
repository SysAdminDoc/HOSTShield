package com.hostshield.service

import java.net.InetAddress

internal const val LOCAL_DNS_DEFAULT_PORT = 5353
internal const val LOCAL_DNS_MIN_UNPRIVILEGED_PORT = 1024
internal const val LOCAL_DNS_MAX_PORT = 65535
internal const val LOCAL_DNS_MAX_UDP_RESPONSE_BYTES = 1472

internal fun isSupportedLocalDnsPort(port: Int): Boolean =
    port in LOCAL_DNS_MIN_UNPRIVILEGED_PORT..LOCAL_DNS_MAX_PORT

internal fun parseSupportedLocalDnsPort(value: String): Int? =
    value.trim().toIntOrNull()?.takeIf(::isSupportedLocalDnsPort)

internal fun localDnsRequiresLocalNetworkPermission(
    platformSdk: Int,
    targetSdk: Int,
    listenPort: Int
): Boolean =
    platformSdk >= 37 && targetSdk >= 37 && listenPort != 53

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
    private val maxGlobalQueriesPerWindow: Int = 100,
    private val windowMillis: Long = 1_000L,
    private val maxTrackedClients: Int = 256,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Window(var startedAtMillis: Long, var count: Int)

    private val windows = HashMap<String, Window>()
    private var globalWindow = Window(startedAtMillis = 0L, count = 0)

    @Synchronized
    fun tryAcquire(address: InetAddress): Boolean {
        val now = nowMillis()
        val key = address.hostAddress ?: address.hostName
        val clientWindow = currentWindow(windows[key], now)
        val currentGlobalWindow = currentWindow(globalWindow, now)

        if (clientWindow.count >= maxQueriesPerWindow) return false
        if (currentGlobalWindow.count >= maxGlobalQueriesPerWindow) return false

        clientWindow.count += 1
        currentGlobalWindow.count += 1
        windows[key] = clientWindow
        globalWindow = currentGlobalWindow

        if (windows.size > maxTrackedClients) {
            windows.entries.removeIf { now - it.value.startedAtMillis >= windowMillis }
        }
        return true
    }

    @Synchronized
    fun clear() {
        windows.clear()
        globalWindow = Window(startedAtMillis = 0L, count = 0)
    }

    private fun currentWindow(existing: Window?, now: Long): Window =
        if (existing == null || now - existing.startedAtMillis >= windowMillis) {
            Window(startedAtMillis = now, count = 0)
        } else {
            existing
        }
}
