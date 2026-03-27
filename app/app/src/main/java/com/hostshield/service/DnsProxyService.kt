package com.hostshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hostshield.MainActivity
import com.hostshield.R
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

// HostShield v6.0.0 - DNS Proxy Service (Tri-Mode)
//
// Lightweight local DNS proxy that runs as a foreground service on
// 127.0.0.1:5353. The user points Private DNS or per-app DNS settings
// at this address. Blocked domains receive NXDOMAIN (or configured
// block response); allowed queries are forwarded to the upstream DNS.
//
// This is the third blocking method ("DNS_PROXY"), complementing
// ROOT_HOSTS (needs root) and VPN (occupies the VPN slot). It works
// without root and without consuming the VPN slot, so it can coexist
// with commercial VPN apps.
//
// Limitations:
// - Only apps configured to use 127.0.0.1:5353 as their DNS are filtered.
// - Android Private DNS (DNS-over-TLS) cannot point to a local address on
//   most devices, so this mode is best suited for rooted devices that can
//   redirect port 53 via iptables, or for Wi-Fi proxy configurations.
// - No per-app filtering (unlike VPN mode which sees originating UIDs).

@AndroidEntryPoint
class DnsProxyService : Service() {

    companion object {
        const val ACTION_START = "com.hostshield.DNS_PROXY_START"
        const val ACTION_STOP = "com.hostshield.DNS_PROXY_STOP"
        private const val CHANNEL_ID = "hostshield_dns_proxy"
        private const val NOTIFICATION_ID = 3
        private const val TAG = "DnsProxyService"

        private const val DEFAULT_LISTEN_PORT = 5353
        private const val DNS_PACKET_MAX = 512
        private val DEFAULT_UPSTREAM = arrayOf("1.1.1.1", "8.8.8.8")
        private const val UPSTREAM_TIMEOUT_MS = 5_000

        // Live query stream for UI (mirrors DnsVpnService pattern).
        private val liveQueriesFlow = MutableSharedFlow<DnsLogEntry>(
            replay = 100,
            extraBufferCapacity = 200,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val liveQueries: SharedFlow<DnsLogEntry> = liveQueriesFlow

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, DnsProxyService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DnsProxyService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    @Inject lateinit var blocklist: BlocklistHolder
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var dnsLogDao: DnsLogDao
    @Inject lateinit var blockStatsDao: BlockStatsDao
    @Inject lateinit var dohResolver: DohResolver
    @Inject lateinit var dotResolver: DotResolver

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var isRunning = false
    private var serverSocket: DatagramSocket? = null

    private val blockedCount = AtomicInteger(0)
    private val allowedCount = AtomicInteger(0)
    private var blockResponseType = "nxdomain"
    private var upstreamServers = DEFAULT_UPSTREAM.toList()
    private var loggingEnabled = true
    private var useDoH = false
    private var dohProvider = DohResolver.Provider.CLOUDFLARE
    private var useDoT = false
    private var dotProvider = DotResolver.Provider.CLOUDFLARE

    // Batch log buffer (same pattern as DnsVpnService)
    private val logBuffer = java.util.concurrent.LinkedBlockingQueue<DnsLogEntry>(5000)
    @Volatile private var logFlushJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    Log.i(TAG, "Starting DNS proxy service")
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, buildNotification("Initializing..."),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                    serviceScope.launch { startProxy() }
                }
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping DNS proxy service")
                stopProxy()
            }
            else -> {
                // System restarted us (START_STICKY) -- resume
                Log.i(TAG, "System restarted DNS proxy service -- resuming")
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification("Resuming..."),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                serviceScope.launch { startProxy() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    // ── Proxy core ──────────────────────────────────────────────────

    private suspend fun startProxy() {
        if (isRunning) return

        // Load preferences
        blockResponseType = prefs.blockResponseType.first()
        loggingEnabled = prefs.dnsLogging.first()
        val customUpstream = prefs.getUpstreamDnsList()
        upstreamServers = if (customUpstream.isNotEmpty()) customUpstream else DEFAULT_UPSTREAM.toList()
        useDoH = prefs.dohEnabled.first()
        dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
        useDoT = prefs.dotEnabled.first()
        dotProvider = DotResolver.Provider.fromId(prefs.dotProvider.first())

        try {
            val socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), DEFAULT_LISTEN_PORT))
            serverSocket = socket
            isRunning = true
            Log.i(TAG, "DNS proxy listening on 127.0.0.1:$DEFAULT_LISTEN_PORT")
            updateNotification("DNS proxy active")

            startLogFlusher()
            listenLoop(socket)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start DNS proxy: ${e.message}", e)
            updateNotification("Error: ${e.message}")
        }
    }

    private suspend fun listenLoop(socket: DatagramSocket) {
        val buf = ByteArray(DNS_PACKET_MAX)
        while (isRunning && !socket.isClosed) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                // DatagramSocket.receive() is blocking; runs on Dispatchers.IO
                withContext(Dispatchers.IO) { socket.receive(packet) }

                val queryData = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val clientAddr = packet.address
                val clientPort = packet.port

                // Handle each query concurrently
                serviceScope.launch {
                    handleQuery(socket, queryData, clientAddr, clientPort)
                }
            } catch (e: java.net.SocketException) {
                if (isRunning) Log.e(TAG, "Socket error: ${e.message}")
                // Socket closed during stopProxy -- normal exit
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving packet: ${e.message}", e)
            }
        }
    }

    private suspend fun handleQuery(
        socket: DatagramSocket,
        queryData: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        val startTime = System.currentTimeMillis()
        val domain = DnsPacketBuilder.parseDomain(queryData)
        val queryType = DnsPacketBuilder.parseQueryType(queryData)
        val queryTypeLabel = DnsPacketBuilder.queryTypeLabel(queryType)

        if (domain == null) {
            // Malformed query -- forward as-is to upstream
            forwardAndReply(socket, queryData, clientAddr, clientPort)
            return
        }

        val blocked = blocklist.isBlocked(domain)

        if (blocked) {
            // Build block response
            val response = DnsPacketBuilder.buildBlockResponse(queryData, blockResponseType)
            val reply = DatagramPacket(response, response.size, clientAddr, clientPort)
            try {
                socket.send(reply)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending block response: ${e.message}")
            }
            val count = blockedCount.incrementAndGet()

            // Update notification every 10 blocks
            if (count % 10 == 0) {
                updateNotification("$count blocked")
            }

            logQuery(domain, queryTypeLabel, true, System.currentTimeMillis() - startTime)
        } else {
            // Forward to upstream
            val response = forwardToUpstream(queryData)
            if (response != null) {
                val reply = DatagramPacket(response, response.size, clientAddr, clientPort)
                try {
                    socket.send(reply)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending upstream response: ${e.message}")
                }
            }
            allowedCount.incrementAndGet()

            logQuery(domain, queryTypeLabel, false, System.currentTimeMillis() - startTime)
        }
    }

    private suspend fun forwardAndReply(
        socket: DatagramSocket,
        queryData: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        val response = forwardToUpstream(queryData) ?: return
        val reply = DatagramPacket(response, response.size, clientAddr, clientPort)
        try {
            socket.send(reply)
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding reply: ${e.message}")
        }
    }

    /**
     * Forward a DNS query to the configured upstream DNS servers.
     * Uses encrypted DNS (DoH/DoT) when enabled, otherwise plaintext UDP.
     * Tries each upstream in order with a timeout; returns the first
     * successful response or null if all fail.
     */
    private suspend fun forwardToUpstream(queryData: ByteArray): ByteArray? {
        // Try encrypted DNS first (DoT > DoH, matching VPN priority)
        if (useDoT) {
            try {
                val resp = dotResolver.resolve(queryData, dotProvider)
                if (resp != null) return resp
            } catch (e: Exception) {
                Log.w(TAG, "DoT proxy forward failed: ${e.message}")
            }
        }
        if (useDoH) {
            try {
                val resp = dohResolver.resolve(queryData, dohProvider)
                if (resp != null) return resp
            } catch (e: Exception) {
                Log.w(TAG, "DoH proxy forward failed: ${e.message}")
            }
        }

        // Plaintext UDP fallback
        for (upstream in upstreamServers) {
            var upstreamSocket: DatagramSocket? = null
            try {
                upstreamSocket = DatagramSocket()
                upstreamSocket.soTimeout = UPSTREAM_TIMEOUT_MS
                val addr = InetAddress.getByName(upstream)
                val outPacket = DatagramPacket(queryData, queryData.size, addr, 53)
                upstreamSocket.send(outPacket)

                val buf = ByteArray(DNS_PACKET_MAX)
                val inPacket = DatagramPacket(buf, buf.size)
                upstreamSocket.receive(inPacket)
                upstreamSocket.close()

                return buf.copyOfRange(inPacket.offset, inPacket.offset + inPacket.length)
            } catch (e: Exception) {
                Log.w(TAG, "Upstream $upstream failed: ${e.message}")
                try { upstreamSocket?.close() } catch (_: Exception) { }
            }
        }
        return null
    }

    // ── Logging ─────────────────────────────────────────────────────

    private fun logQuery(domain: String, queryType: String, blocked: Boolean, responseTimeMs: Long) {
        if (!loggingEnabled) return

        val entry = DnsLogEntry(
            hostname = domain,
            blocked = blocked,
            queryType = queryType,
            responseTimeMs = responseTimeMs.toInt(),
            upstreamServer = if (blocked) "local" else upstreamServers.firstOrNull() ?: ""
        )

        // Emit to live stream (non-blocking)
        liveQueriesFlow.tryEmit(entry)

        // Buffer for batch DB insert
        logBuffer.offer(entry)
    }

    private fun startLogFlusher() {
        logFlushJob?.cancel()
        logFlushJob = serviceScope.launch {
            while (isActive) {
                delay(2000)
                flushLogs()
            }
        }
    }

    private suspend fun flushLogs() {
        if (logBuffer.isEmpty()) return
        val batch = mutableListOf<DnsLogEntry>()
        logBuffer.drainTo(batch, 500)
        if (batch.isEmpty()) return

        try {
            dnsLogDao.insertAll(batch)

            // Update daily stats (upsert = INSERT OR REPLACE)
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val blocked = batch.count { it.blocked }
            val allowed = batch.size - blocked
            val existing = blockStatsDao.getStatsByDate(today)
            val updated = if (existing != null) {
                existing.copy(
                    blockedCount = existing.blockedCount + blocked,
                    allowedCount = existing.allowedCount + allowed,
                    totalQueries = existing.totalQueries + batch.size
                )
            } else {
                BlockStats(
                    date = today,
                    blockedCount = blocked,
                    allowedCount = allowed,
                    totalQueries = batch.size
                )
            }
            blockStatsDao.upsert(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing logs: ${e.message}")
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    private fun stopProxy() {
        isRunning = false
        logFlushJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        // Final log flush
        serviceScope.launch {
            flushLogs()
        }

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification ────────────────────────────────────────────────

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DnsProxyService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("HostShield DNS Proxy")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_shield, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationChannel(
            CHANNEL_ID, "DNS Proxy Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while DNS proxy blocking is active"
        }.let {
            getSystemService(NotificationManager::class.java).createNotificationChannel(it)
        }
    }
}
