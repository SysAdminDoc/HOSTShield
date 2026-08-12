package com.hostshield.service

import android.util.Log
import com.hostshield.util.PrivacyLog
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Result of an upstream DNS attempt, including the transport used for logging. */
internal sealed class UpstreamResolveResult {
    data class Success(
        val response: ByteArray,
        val latencyMs: Int,
        val upstreamServer: String,
    ) : UpstreamResolveResult()

    data class EncryptedFailure(val transport: String) : UpstreamResolveResult()
    data object PlaintextFailure : UpstreamResolveResult()
}

/** Mutable DNS transport settings captured atomically for one forwarding attempt. */
internal data class DnsForwardingConfig(
    val useDoH: Boolean,
    val dohProvider: DohResolver.Provider,
    val useDoT: Boolean,
    val dotProvider: DotResolver.Provider,
    val useDoQ: Boolean,
    val doqProvider: DoqResolver.Provider,
    val useWireGuard: Boolean,
    val upstreamDnsServers: List<String>,
) {
    fun routeKey(): String = when {
        useWireGuard -> "wireguard|doq=$useDoQ:${doqProvider.name}|doh=$useDoH:${dohProvider.name}"
        useDoQ -> "doq:${doqProvider.name}|doh=$useDoH:${dohProvider.name}"
        useDoT -> "dot:${dotProvider.name}|doh=$useDoH:${dohProvider.name}"
        useDoH -> "doh:${dohProvider.name}"
        else -> "udp:${upstreamDnsServers.joinToString(",")}"
    }
}

/** One upstream transport implementation used by [DnsForwarder]. */
private fun interface DnsForwardingStrategy {
    suspend fun resolve(dns: ByteArray, domain: String): UpstreamResolveResult
}

/**
 * Selects and single-flights the configured DNS transport.
 *
 * The packet loop only needs to submit a query and consume a result. Socket
 * ownership, transport fallbacks, TCP retry for truncated UDP, and route-keyed
 * deduplication live here so each forwarding strategy can be tested in isolation.
 */
internal class DnsForwarder(
    private val dohResolver: DohResolver,
    private val dotResolver: DotResolver,
    private val doqResolver: DoqResolver,
    private val wireGuardProxy: WireGuardProxy,
    private val config: () -> DnsForwardingConfig,
    private val protectDatagram: (DatagramSocket) -> Boolean,
    private val protectSocket: (Socket) -> Boolean,
    private val defaultUpstreamDnsServers: List<String>,
    private val tag: String = "HostShield",
) {
    private val deduplicator = DnsQueryDeduplicator<UpstreamResolveResult>()

    private val dohStrategy = DnsOverHttpsStrategy(
        resolver = dohResolver,
        provider = { config().dohProvider },
        tag = tag,
    )

    private val dotStrategy = DnsOverTlsStrategy(
        resolver = dotResolver,
        provider = { config().dotProvider },
        fallback = { dns, domain ->
            if (config().useDoH) dohStrategy.resolve(dns, domain)
            else UpstreamResolveResult.EncryptedFailure("DoT")
        },
        tag = tag,
    )

    private val doqStrategy = DnsOverQuicStrategy(
        resolver = doqResolver,
        provider = { config().doqProvider },
        fallback = { dns, domain ->
            if (config().useDoH) dohStrategy.resolve(dns, domain)
            else UpstreamResolveResult.EncryptedFailure("DoQ")
        },
        tag = tag,
    )

    private val wireGuardStrategy = WireGuardDnsStrategy(
        proxy = wireGuardProxy,
        fallback = { dns, domain ->
            when {
                config().useDoQ -> doqStrategy.resolve(dns, domain)
                config().useDoH -> dohStrategy.resolve(dns, domain)
                else -> UpstreamResolveResult.EncryptedFailure("WireGuard")
            }
        },
        tag = tag,
    )

    private val plaintextStrategy = PlaintextUdpDnsStrategy(
        upstreamServers = {
            config().upstreamDnsServers.ifEmpty { defaultUpstreamDnsServers }
        },
        protectDatagram = protectDatagram,
        protectSocket = protectSocket,
    )

    suspend fun resolve(
        dns: ByteArray,
        domain: String,
    ): DnsQueryDeduplicator.DeduplicationResult<UpstreamResolveResult> {
        val qtype = DnsPacketBuilder.parseQueryType(dns)
        return deduplicator.getOrRun(
            DnsQueryDeduplicator.Key(domain, qtype, currentRouteKey()),
        ) {
            resolveConfigured(dns, domain)
        }
    }

    fun clear() = deduplicator.clear()

    internal fun currentRouteKey(): String = config().routeKey()

    private suspend fun resolveConfigured(
        dns: ByteArray,
        domain: String,
    ): UpstreamResolveResult {
        val current = config()
        return when {
            current.useWireGuard -> wireGuardStrategy.resolve(dns, domain)
            current.useDoQ -> doqStrategy.resolve(dns, domain)
            current.useDoT -> dotStrategy.resolve(dns, domain)
            current.useDoH -> dohStrategy.resolve(dns, domain)
            else -> plaintextStrategy.resolve(dns, domain)
        }
    }
}

private class DnsOverHttpsStrategy(
    private val resolver: DohResolver,
    private val provider: () -> DohResolver.Provider,
    private val tag: String,
) : DnsForwardingStrategy {
    override suspend fun resolve(dns: ByteArray, domain: String): UpstreamResolveResult {
        return try {
            val startMs = System.currentTimeMillis()
            val result = resolver.resolveWithMetadata(dns, provider())
            val response = result?.response
            if (response == null) {
                UpstreamResolveResult.EncryptedFailure("DoH")
            } else {
                val upstream = when (result.transport) {
                    DohResolver.Transport.DOH3 -> "DoH3:${result.provider.name}"
                    DohResolver.Transport.DOH -> "DoH:${result.provider.name}"
                }
                UpstreamResolveResult.Success(
                    response = response,
                    latencyMs = (System.currentTimeMillis() - startMs).toInt(),
                    upstreamServer = upstream,
                )
            }
        } catch (e: Exception) {
            PrivacyLog.w(tag, "DoH forward failed for $domain (${e.javaClass.simpleName}) — failing closed")
            UpstreamResolveResult.EncryptedFailure("DoH")
        }
    }
}

private class DnsOverTlsStrategy(
    private val resolver: DotResolver,
    private val provider: () -> DotResolver.Provider,
    private val fallback: suspend (ByteArray, String) -> UpstreamResolveResult,
    private val tag: String,
) : DnsForwardingStrategy {
    override suspend fun resolve(dns: ByteArray, domain: String): UpstreamResolveResult {
        return try {
            val startMs = System.currentTimeMillis()
            val response = resolver.resolve(dns, provider())
            if (response != null) {
                UpstreamResolveResult.Success(
                    response = response,
                    latencyMs = (System.currentTimeMillis() - startMs).toInt(),
                    upstreamServer = "DoT:${provider().name}",
                )
            } else {
                fallback(dns, domain)
            }
        } catch (e: Exception) {
            PrivacyLog.w(tag, "DoT forward failed for $domain (${e.javaClass.simpleName}: ${e.message}) — falling back")
            fallback(dns, domain)
        }
    }
}

private class DnsOverQuicStrategy(
    private val resolver: DoqResolver,
    private val provider: () -> DoqResolver.Provider,
    private val fallback: suspend (ByteArray, String) -> UpstreamResolveResult,
    private val tag: String,
) : DnsForwardingStrategy {
    override suspend fun resolve(dns: ByteArray, domain: String): UpstreamResolveResult {
        return try {
            val startMs = System.currentTimeMillis()
            val response = resolver.resolve(dns, provider())
            if (response != null) {
                UpstreamResolveResult.Success(
                    response = response,
                    latencyMs = (System.currentTimeMillis() - startMs).toInt(),
                    upstreamServer = "DoQ:${provider().name}",
                )
            } else {
                fallback(dns, domain)
            }
        } catch (e: Exception) {
            PrivacyLog.w(tag, "DoQ forward failed for $domain (${e.javaClass.simpleName}: ${e.message}) — falling back")
            fallback(dns, domain)
        }
    }
}

private class WireGuardDnsStrategy(
    private val proxy: WireGuardProxy,
    private val fallback: suspend (ByteArray, String) -> UpstreamResolveResult,
    private val tag: String,
) : DnsForwardingStrategy {
    override suspend fun resolve(dns: ByteArray, domain: String): UpstreamResolveResult {
        return try {
            val startMs = System.currentTimeMillis()
            val response = proxy.resolveDns(dns)
            if (response != null) {
                UpstreamResolveResult.Success(
                    response = response,
                    latencyMs = (System.currentTimeMillis() - startMs).toInt(),
                    upstreamServer = "WireGuard",
                )
            } else {
                fallback(dns, domain)
            }
        } catch (e: Exception) {
            PrivacyLog.w(tag, "WireGuard forward failed for $domain (${e.javaClass.simpleName}: ${e.message}) — falling back")
            fallback(dns, domain)
        }
    }
}

private class PlaintextUdpDnsStrategy(
    private val upstreamServers: () -> List<String>,
    private val protectDatagram: (DatagramSocket) -> Boolean,
    private val protectSocket: (Socket) -> Boolean,
) : DnsForwardingStrategy {
    override suspend fun resolve(dns: ByteArray, domain: String): UpstreamResolveResult {
        val socket = DatagramSocket()
        return try {
            val startMs = System.currentTimeMillis()
            val servers = upstreamServers()
            val primary = servers.firstOrNull() ?: "8.8.8.8"
            var responseUpstream = primary
            protectDatagram(socket)
            socket.soTimeout = 5_000
            socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), 53))
            val buffer = ByteArray(1500)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(responsePacket)
            } catch (_: java.net.SocketTimeoutException) {
                socket.close()
                val fallback = servers.getOrElse(1) { "1.1.1.1" }
                val fallbackSocket = DatagramSocket()
                protectDatagram(fallbackSocket)
                fallbackSocket.soTimeout = 5_000
                try {
                    fallbackSocket.send(
                        DatagramPacket(dns, dns.size, InetAddress.getByName(fallback), 53),
                    )
                    fallbackSocket.receive(responsePacket)
                    responseUpstream = fallback
                } finally {
                    try { fallbackSocket.close() } catch (_: Exception) { }
                }
            }
            val response = retryTruncatedUdpOverTcp(dns, buffer.copyOf(responsePacket.length), responseUpstream)
            UpstreamResolveResult.Success(
                response = response,
                latencyMs = (System.currentTimeMillis() - startMs).toInt(),
                upstreamServer = responseUpstream,
            )
        } catch (_: Exception) {
            UpstreamResolveResult.PlaintextFailure
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun retryTruncatedUdpOverTcp(
        dns: ByteArray,
        udpResponse: ByteArray,
        upstream: String,
    ): ByteArray {
        val result = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = udpResponse,
            udpReceivedAtMs = DnsTcpFallback.monotonicNowMs(),
        ) {
            forwardOverTcp(dns, upstream)
        }
        if (result.retriedOverTcp && !result.retryStartedWithinDeadline) {
            Log.w(
                "HostShield",
                "TCP DNS fallback for TC=1 started after ${result.retryStartDelayMs}ms " +
                    "(expected <= ${DnsTcpFallback.MAX_TCP_RETRY_START_DELAY_MS}ms)",
            )
        }
        return result.response
    }

    private fun forwardOverTcp(dns: ByteArray, upstream: String): ByteArray? {
        val socket = Socket()
        return try {
            protectSocket(socket)
            socket.connect(InetSocketAddress(InetAddress.getByName(upstream), 53), 3_000)
            socket.soTimeout = 4_000
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            output.writeShort(dns.size)
            output.write(dns)
            output.flush()
            val responseLength = input.readUnsignedShort()
            if (responseLength < 12 || responseLength > 65_535) return null
            ByteArray(responseLength).also { input.readFully(it) }
        } catch (_: Exception) {
            null
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }
}
