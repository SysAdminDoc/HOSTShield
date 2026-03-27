package com.hostshield.service

import android.util.Log
import com.hostshield.domain.BlocklistHolder
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
    private val dotResolver: DotResolver
) {
    companion object {
        private const val TAG = "LocalDnsServer"
        const val DEFAULT_PORT = 5353
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_PORT = 53
        private const val BUFFER_SIZE = 1500
        private const val SOCKET_TIMEOUT_MS = 5000

        @Volatile var isRunning = false; private set
        val queriesHandledAtomic = AtomicInteger(0)
        val queriesBlockedAtomic = AtomicInteger(0)
        val queriesHandled: Int get() = queriesHandledAtomic.get()
        val queriesBlocked: Int get() = queriesBlockedAtomic.get()
    }

    private var serverSocket: DatagramSocket? = null
    private var serverJob: Job? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var port = DEFAULT_PORT; private set
    @Volatile var upstreamDns = UPSTREAM_DNS
    @Volatile var useDoH = false
    @Volatile var dohProvider = DohResolver.Provider.CLOUDFLARE
    @Volatile var useDoT = false
    @Volatile var dotProvider = DotResolver.Provider.CLOUDFLARE

    /**
     * Start the local DNS server on the given port.
     * Returns the actual port used, or -1 on failure.
     */
    fun start(listenPort: Int = DEFAULT_PORT, upstream: String = UPSTREAM_DNS): Int {
        if (isRunning) {
            Log.w(TAG, "Already running on port $port")
            return port
        }

        return try {
            upstreamDns = upstream
            val socket = DatagramSocket(listenPort)
            socket.reuseAddress = true
            serverSocket = socket
            port = socket.localPort
            isRunning = true
            queriesHandledAtomic.set(0)
            queriesBlockedAtomic.set(0)

            serverJob = scope.launch { runServer(socket) }

            Log.i(TAG, "Local DNS server started on port $port, upstream=$upstreamDns")
            port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local DNS server: ${e.message}")
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
        Log.i(TAG, "Local DNS server stopped. Handled $queriesHandled queries, blocked $queriesBlocked")
    }

    private suspend fun runServer(socket: DatagramSocket) {
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

    private suspend fun handleQuery(
        serverSock: DatagramSocket,
        query: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        try {
            queriesHandledAtomic.incrementAndGet()

            // Parse domain from DNS query
            val domain = parseDnsQueryDomain(query)
            if (domain == null) {
                // Can't parse — forward as-is
                val resp = forwardToUpstream(query)
                if (resp != null) sendResponse(serverSock, resp, clientAddr, clientPort)
                return
            }

            // Check blocklist
            if (blocklist.isBlocked(domain)) {
                queriesBlockedAtomic.incrementAndGet()
                Log.d(TAG, "BLOCKED (local) $domain from ${clientAddr.hostAddress}")
                val blockResp = buildNxdomainResponse(query)
                if (blockResp != null) sendResponse(serverSock, blockResp, clientAddr, clientPort)
                return
            }

            // Forward to upstream
            val resp = forwardToUpstream(query)
            if (resp != null) {
                sendResponse(serverSock, resp, clientAddr, clientPort)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query handling error: ${e.message}")
        }
    }

    private fun forwardToUpstream(query: ByteArray): ByteArray? {
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

        // Plaintext UDP fallback
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
        response: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        try {
            synchronized(sendLock) {
                serverSock.send(DatagramPacket(response, response.size, clientAddr, clientPort))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send response: ${e.message}")
        }
    }

    /**
     * Parse the query domain from a DNS packet.
     * Minimal parser — just extracts the QNAME from the question section.
     */
    private fun parseDnsQueryDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
        if (qdCount == 0) return null

        val sb = StringBuilder()
        var pos = 12
        while (pos < dns.size) {
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) break // compression pointer — shouldn't appear in question
            pos++
            if (pos + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append(dns[pos + i].toInt().toChar())
            }
            pos += len
        }
        return if (sb.isNotEmpty()) sb.toString().lowercase() else null
    }

    /**
     * Build an NXDOMAIN response for blocked queries.
     */
    private fun buildNxdomainResponse(query: ByteArray): ByteArray? {
        if (query.size < 12) return null

        // Find end of question section
        var qEnd = 12
        while (qEnd < query.size) {
            val len = query[qEnd].toInt() and 0xFF
            if (len == 0) { qEnd++; break }
            if (len and 0xC0 == 0xC0) { qEnd += 2; break }
            qEnd += 1 + len
        }
        qEnd += 4 // QTYPE + QCLASS
        if (qEnd > query.size) return null

        val resp = query.copyOf(qEnd)
        // Set QR=1 (response), RD=1, RA=1, RCODE=3 (NXDOMAIN)
        resp[2] = 0x81.toByte()
        resp[3] = 0x83.toByte()
        // ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
        resp[6] = 0; resp[7] = 0
        resp[8] = 0; resp[9] = 0
        resp[10] = 0; resp[11] = 0
        return resp
    }
}
