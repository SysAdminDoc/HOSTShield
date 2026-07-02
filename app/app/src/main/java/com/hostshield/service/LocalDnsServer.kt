package com.hostshield.service

import android.util.Log
import com.hostshield.util.PrivacyLog
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v6.0: Local DNS Server — "Portable Pi-hole" mode (roadmap #49).
 *
 * Runs a UDP DNS server on port 5353 (mDNS-safe port, no root required)
 * that other devices on the LAN can use as their DNS resolver.
 *
 * Flow: LAN client → LocalDnsServer:5353 → blocklist check → if allowed,
 * forward to upstream → return response to client.
 *
 * Usage: Other devices set their DNS to this phone's LAN IP on port 5353.
 * Some routers support custom DNS port; otherwise use DNS proxy on clients.
 */
@Singleton
class LocalDnsServer @Inject constructor(
    private val blocklist: BlocklistHolder,
    private val dohResolver: DohResolver,
    private val dotResolver: DotResolver,
    private val prefs: AppPreferences
) {
    data class Status(
        val isRunning: Boolean,
        val port: Int,
        val allowExternalClients: Boolean,
        val queriesHandled: Int,
        val queriesBlocked: Int,
        val message: String
    )

    companion object {
        private const val TAG = "LocalDnsServer"
        const val DEFAULT_PORT = LOCAL_DNS_DEFAULT_PORT
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_PORT = 53
        private const val BUFFER_SIZE = 1500
        private const val SOCKET_TIMEOUT_MS = 5000

        @Volatile var isRunning = false; private set
        @Volatile private var lastStatusMessage = "LAN DNS server stopped"
        val queriesHandledAtomic = AtomicInteger(0)
        val queriesBlockedAtomic = AtomicInteger(0)
        val queriesHandled: Int get() = queriesHandledAtomic.get()
        val queriesBlocked: Int get() = queriesBlockedAtomic.get()
    }

    private var serverSocket: DatagramSocket? = null
    private var serverJob: Job? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rateLimiter = LocalDnsClientRateLimiter()

    @Volatile var port = DEFAULT_PORT; private set
    @Volatile var upstreamDns = UPSTREAM_DNS
    @Volatile var allowExternalClients = false
    @Volatile var useDoH = false
    @Volatile var dohProvider = DohResolver.Provider.CLOUDFLARE
    @Volatile var useDoT = false
    @Volatile var dotProvider = DotResolver.Provider.CLOUDFLARE

    fun status(): Status = Status(
        isRunning = isRunning,
        port = port,
        allowExternalClients = allowExternalClients,
        queriesHandled = queriesHandled,
        queriesBlocked = queriesBlocked,
        message = lastStatusMessage
    )

    /**
     * Start the local DNS server on the given port.
     * Returns the actual port used, or -1 on failure.
     */
    fun start(
        listenPort: Int = DEFAULT_PORT,
        upstream: String = UPSTREAM_DNS,
        allowExternalClients: Boolean = false
    ): Int {
        if (isRunning) {
            Log.w(TAG, "Already running on port $port")
            return port
        }
        if (!isSupportedLocalDnsPort(listenPort)) {
            lastStatusMessage = "LAN DNS port must be between $LOCAL_DNS_MIN_UNPRIVILEGED_PORT and $LOCAL_DNS_MAX_PORT"
            Log.w(TAG, lastStatusMessage)
            return -1
        }

        return try {
            upstreamDns = upstream
            this.allowExternalClients = allowExternalClients
            rateLimiter.clear()
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(listenPort))
            }
            serverSocket = socket
            port = socket.localPort
            isRunning = true
            queriesHandledAtomic.set(0)
            queriesBlockedAtomic.set(0)

            serverJob = scope.launch { runServer(socket) }

            lastStatusMessage = "LAN DNS server running on UDP port $port"
            PrivacyLog.i(
                TAG,
                "Local DNS server started on port $port, upstream=$upstreamDns, " +
                    "allowExternalClients=$allowExternalClients"
            )
            port
        } catch (e: Exception) {
            lastStatusMessage = "LAN DNS server failed to start: ${e.message ?: "unknown error"}"
            Log.e(TAG, lastStatusMessage)
            isRunning = false
            -1
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // recreate for next start()
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        lastStatusMessage = "LAN DNS server stopped"
        Log.i(TAG, "Local DNS server stopped. Handled $queriesHandled queries, blocked $queriesBlocked")
    }

    private suspend fun runServer(socket: DatagramSocket) {
        refreshResolverPreferences()
        val buf = ByteArray(BUFFER_SIZE)
        while (isRunning && !socket.isClosed) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                withContext(Dispatchers.IO) { socket.receive(packet) }

                val queryData = buf.copyOf(packet.length)
                val clientAddr = packet.address
                val clientPort = packet.port

                // Handle each query concurrently
                scope.launch {
                    handleQuery(socket, queryData, clientAddr, clientPort)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Receive error: ${e.message}")
                }
            }
        }
    }

    private suspend fun refreshResolverPreferences() {
        try {
            val customUpstream = prefs.customUpstreamDns.first()
                .split(",")
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
            if (customUpstream != null) {
                upstreamDns = customUpstream
            }
            useDoH = prefs.dohEnabled.first()
            dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
            useDoT = prefs.dotEnabled.first()
            dotProvider = DotResolver.Provider.fromId(prefs.dotProvider.first())
            PrivacyLog.i(
                TAG,
                "Local DNS resolver chain configured: DoT=${if (useDoT) dotProvider.name else "off"}, " +
                    "DoH=${if (useDoH) dohProvider.name else "off"}, upstream=$upstreamDns"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local DNS resolver preferences: ${e.message}")
        }
    }

    private suspend fun handleQuery(
        serverSock: DatagramSocket,
        query: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        try {
            if (!isAllowedLocalDnsClient(clientAddr, allowExternalClients)) {
                PrivacyLog.w(TAG, "Dropping local DNS query from non-local client ${clientAddr.hostAddress}")
                return
            }
            if (!rateLimiter.tryAcquire(clientAddr)) {
                PrivacyLog.w(TAG, "Rate-limited local DNS client ${clientAddr.hostAddress}")
                return
            }
            queriesHandledAtomic.incrementAndGet()

            // Parse domain from DNS query
            val domain = DnsPacketBuilder.parseDomain(query)
            if (domain == null) {
                // Can't parse — forward as-is
                val resp = forwardToUpstream(query)
                if (resp != null) sendResponse(serverSock, query, resp, clientAddr, clientPort)
                return
            }

            val queryType = DnsPacketBuilder.parseQueryType(query)

            // Check blocklist
            if (blocklist.isBlocked(domain, queryType)) {
                queriesBlockedAtomic.incrementAndGet()
                PrivacyLog.d(TAG, "BLOCKED (local) $domain from ${clientAddr.hostAddress}")
                val blockResp = DnsPacketBuilder.buildNxdomain(query)
                sendResponse(serverSock, query, blockResp, clientAddr, clientPort)
                return
            }

            // Forward to upstream
            val resp = forwardToUpstream(query)
            if (resp != null) {
                sendResponse(serverSock, query, resp, clientAddr, clientPort)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query handling error: ${e.message}")
        }
    }

    private suspend fun forwardToUpstream(query: ByteArray): ByteArray? {
        val encryptedDnsEnabled = useDoT || useDoH

        // Try encrypted DNS first (DoT > DoH, matching VPN priority)
        if (useDoT) {
            try {
                val resp = dotResolver.resolve(query, dotProvider)
                if (resp != null) return resp
            } catch (e: Exception) {
                Log.w(TAG, "DoT local forward failed: ${e.message}")
            }
        }
        if (useDoH) {
            try {
                val resp = dohResolver.resolve(query, dohProvider)
                if (resp != null) return resp
            } catch (e: Exception) {
                Log.w(TAG, "DoH local forward failed: ${e.message}")
            }
        }

        // Fail closed: never downgrade to plaintext when encrypted DNS is configured
        if (encryptedDnsEnabled) {
            Log.w(TAG, "Encrypted DNS failed, returning SERVFAIL (fail-closed)")
            return DnsPacketBuilder.buildServfail(query)
        }

        // Plaintext UDP only when no encrypted transport is configured
        var sock: DatagramSocket? = null
        return try {
            sock = DatagramSocket()
            sock.soTimeout = SOCKET_TIMEOUT_MS
            val addr = InetAddress.getByName(upstreamDns)
            sock.send(DatagramPacket(query, query.size, addr, UPSTREAM_PORT))
            val buf = ByteArray(BUFFER_SIZE)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            buf.copyOf(resp.length)
        } catch (e: Exception) {
            Log.w(TAG, "Upstream forward failed: ${e.message}")
            null
        } finally {
            try { sock?.close() } catch (_: Exception) { }
        }
    }

    private val sendLock = Any()

    private fun sendResponse(
        serverSock: DatagramSocket,
        query: ByteArray,
        response: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        try {
            val udpResponse = localDnsUdpResponse(query, response)
            synchronized(sendLock) {
                serverSock.send(DatagramPacket(udpResponse, udpResponse.size, clientAddr, clientPort))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send response: ${e.message}")
        }
    }
}
