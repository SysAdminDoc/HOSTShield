package com.hostshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hostshield.MainActivity
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

// HostShield v1.6.0 - VPN DNS Blocking Service
//
// Architecture: DNS-only interception (DNS66-style TEST-NET routing)
//
// - VPN interface at 10.120.0.1/24 + fd00::1/120 (dual-stack)
// - Virtual DNS servers use RFC 5737 TEST-NET-1 addresses (192.0.2.x)
//   which are documentation-only IPs guaranteed to never conflict with
//   real servers. Each upstream DNS gets a unique TEST-NET alias.
// - Only /32 routes for each virtual DNS address, so ONLY DNS packets
//   traverse the TUN. All other traffic bypasses the VPN entirely.
// - DNS Trap: routes well-known public DNS IPs (8.8.8.8, 1.1.1.1,
//   etc.) so apps that hardcode DNS servers still get filtered.
// - DoT Trap: routes known DNS-over-TLS servers' port 853 traffic
//   through TUN and silently drops it, forcing DoT fallback to port 53.
// - DoH IP Block: blocks known DoH provider IPs by sending TCP RST-like
//   drops, forcing apps to fall back to standard DNS we can filter.
// - DoH upstream: when enabled, forwards allowed queries via HTTPS
//   instead of plaintext UDP, preventing ISP snooping.
// - Blocked queries receive NXDOMAIN with SOA for negative caching.
// - Per-app DNS blocking: apps in blockedApps get NXDOMAIN for all queries.
// - Domain matching uses trie-based BlocklistHolder.isBlocked() for O(m)
//   lookup instead of linear scans over 100K+ domain sets.
// - Network change listener auto-restarts VPN on connectivity changes.

@AndroidEntryPoint
class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.hostshield.VPN_START"
        const val ACTION_STOP = "com.hostshield.VPN_STOP"
        const val CHANNEL_ID = "hostshield_vpn"
        const val NOTIFICATION_ID = 1
        private const val TAG = "HostShield"

        // VPN interface
        private const val VPN_ADDRESS = "10.120.0.1"
        private const val VPN_ADDRESS6 = "fd00::1"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53

        // Virtual DNS aliases (RFC 5737 TEST-NET-1: 192.0.2.0/24)
        // Non-routable documentation IPs -- never exist on real networks.
        private const val VDNS4_PRIMARY = "192.0.2.1"
        private const val VDNS4_SECONDARY = "192.0.2.2"
        // IPv6 virtual DNS (ULA fd00::/8)
        private const val VDNS6_PRIMARY = "fd00::10"

        // Real upstream DNS (for forwarding allowed queries)
        private val UPSTREAM_DNS = arrayOf("8.8.8.8", "1.1.1.1")

        // DNS Trap: well-known public DNS IPs that apps hardcode.
        // Routing these through VPN ensures queries to them get filtered.
        private val DNS_TRAP_IPS = arrayOf(
            "8.8.8.8", "8.8.4.4",               // Google
            "1.1.1.1", "1.0.0.1",               // Cloudflare
            "9.9.9.9", "149.112.112.112",        // Quad9
            "208.67.222.222", "208.67.220.220",  // OpenDNS
            "94.140.14.14", "94.140.15.15",      // AdGuard
        )

        // DoT (DNS-over-TLS) servers that also run on port 853.
        // We route these through VPN and drop non-port-53 traffic to them,
        // forcing apps to fall back to port 53 where we can filter.
        private val DOT_TRAP_IPS = arrayOf(
            "dns.google",          // 8.8.8.8, 8.8.4.4
            "1dot1dot1dot1.cloudflare-dns.com", // 1.1.1.1
            "dns.quad9.net",       // 9.9.9.9
        )

        // Known DoH provider IPs. These are the IPs behind DoH endpoints.
        // When DoH bypass prevention is on, we route these through TUN and
        // drop HTTPS (port 443) traffic to them so apps can't use DoH to
        // bypass our DNS filtering. The same IPs on port 53 still get
        // filtered normally through DNS Trap above.
        private val DOH_BYPASS_IPS = arrayOf(
            // Cloudflare DoH (cloudflare-dns.com / 1.1.1.1)
            "104.16.248.249", "104.16.249.249",
            // Google DoH (dns.google)
            "142.250.80.14", "142.251.1.100",
            // Quad9 DoH (dns.quad9.net)
            "9.9.9.11", "149.112.112.11",
            // Mozilla Cloudflare (mozilla.cloudflare-dns.com)
            "104.16.248.249", "104.16.249.249",
        )

        // SOA RDATA for NXDOMAIN negative caching
        private val SOA_RDATA: ByteArray by lazy { buildSoaRdata() }

        private fun buildSoaRdata(): ByteArray {
            val out = mutableListOf<Byte>()
            for (label in arrayOf("hostshield", "local")) {
                out.add(label.length.toByte())
                label.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }
            }
            out.add(0)
            for (label in arrayOf("admin", "hostshield", "local")) {
                out.add(label.length.toByte())
                label.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }
            }
            out.add(0)
            fun Int.bytes() = listOf(
                ((this shr 24) and 0xFF).toByte(), ((this shr 16) and 0xFF).toByte(),
                ((this shr 8) and 0xFF).toByte(), (this and 0xFF).toByte()
            )
            out.addAll(LocalDate.now().let { d ->
                val serial = d.year * 1000000 + d.monthValue * 10000 + d.dayOfMonth * 100
                serial
            }.bytes()) // SERIAL (YYYYMMDD00)
            out.addAll(3600.bytes())       // REFRESH
            out.addAll(600.bytes())        // RETRY
            out.addAll(86400.bytes())      // EXPIRE
            out.addAll(300.bytes())        // MINIMUM (5 min negative cache)
            return out.toByteArray()
        }
    }

    @Inject lateinit var dnsLogDao: DnsLogDao
    @Inject lateinit var blockStatsDao: BlockStatsDao
    @Inject lateinit var blocklist: BlocklistHolder
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var repository: HostShieldRepository
    @Inject lateinit var downloader: SourceDownloader
    @Inject lateinit var dohResolver: DohResolver

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var excludedApps = setOf<String>()
    private var blockedApps = setOf<String>()
    private var useDoH = false
    private var dohProvider = DohResolver.Provider.CLOUDFLARE
    private var dnsTrapEnabled = true
    // Custom upstream DNS resolved at start
    private var upstreamDnsServers = UPSTREAM_DNS.toList()

    private var writeChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var blockedCount = 0
    private var allowedCount = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Network change debounce — prevents infinite VPN restart loop.
    // When VPN establishes, Android fires onAvailable() for the VPN's own
    // network interface. Without this guard, that triggers restartVpn() which
    // re-establishes the VPN, firing onAvailable() again → infinite cycle.
    @Volatile private var vpnEstablishedAt = 0L          // SystemClock.elapsedRealtime()
    @Volatile private var networkLost = false             // true after onLost() fires
    private val NETWORK_RESTART_COOLDOWN_MS = 5000L      // ignore events within 5s of start

    // Batch DNS log buffer — flushes every 2s or at 500 entries
    private val logBuffer = java.util.concurrent.ConcurrentLinkedQueue<DnsLogEntry>()
    @Volatile private var logFlushJob: Job? = null
    private var loggingEnabled = true  // read from prefs at startVpn()

    // Stats accumulator — AtomicInteger for thread-safe increment from packet thread
    private val pendingBlockedStats = java.util.concurrent.atomic.AtomicInteger(0)
    private val pendingAllowedStats = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
            ACTION_START -> {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(0),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                )
                serviceScope.launch { startVpn() }
            }
            else -> {
                // Null intent = system restarted us after process death (START_STICKY).
                // Re-promote to foreground and restart the VPN if prefs say we should be on.
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(0),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                )
                serviceScope.launch {
                    val shouldRun = prefs.isEnabled.first()
                    if (shouldRun && !isRunning) {
                        Log.i(TAG, "System restarted service -- resuming VPN")
                        startVpn()
                    } else if (!shouldRun) {
                        stopVpn()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() { stopVpn(); super.onRevoke() }

    /**
     * Called when user swipes app from recents. Do NOT stop the VPN.
     * The foreground service continues independently of the UI process.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Don't call stopVpn() or super default behavior that might stop us.
        // The foreground notification keeps us alive.
        Log.i(TAG, "App task removed -- VPN continues running")
    }

    /**
     * Only clean up in-memory resources. Do NOT call stopVpn() here.
     * If the system kills our process, START_STICKY will restart us.
     * If we explicitly stopped (ACTION_STOP), stopVpn() already ran.
     */
    override fun onDestroy() {
        isRunning = false
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── VPN Lifecycle ─────────────────────────────────────────

    private suspend fun startVpn() {
        if (isRunning) return
        try {
            // Fresh channel for each VPN session (previous may be closed)
            writeChannel = Channel(Channel.UNLIMITED)
            blockedCount = 0
            allowedCount = 0

            excludedApps = prefs.excludedApps.first()
            blockedApps = prefs.blockedApps.first()
            useDoH = prefs.dohEnabled.first()
            dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
            dnsTrapEnabled = prefs.dnsTrapEnabled.first()
            loggingEnabled = prefs.dnsLogging.first()

            // Resolve custom upstream DNS
            val customDns = prefs.customUpstreamDns.first().trim()
            upstreamDnsServers = if (customDns.isNotBlank()) {
                val servers = customDns.split(",", ";", " ").map { it.trim() }.filter { it.isNotBlank() }
                if (servers.isNotEmpty()) servers else UPSTREAM_DNS.toList()
            } else UPSTREAM_DNS.toList()

            if (blocklist.domainCount == 0) rebuildBlocklist()

            val builder = Builder()
                .setSession("HostShield")
                .setMtu(VPN_MTU)
                .setBlocking(true)
                // IPv4 + IPv6 dual-stack
                .addAddress(VPN_ADDRESS, 24)
                .addAddress(VPN_ADDRESS6, 120)
                // Virtual DNS (TEST-NET addresses the OS sends queries to)
                .addDnsServer(VDNS4_PRIMARY)
                .addDnsServer(VDNS4_SECONDARY)
                .addDnsServer(VDNS6_PRIMARY)
                // /32 routes so ONLY DNS arrives on TUN
                .addRoute(VDNS4_PRIMARY, 32)
                .addRoute(VDNS4_SECONDARY, 32)
                .addRoute(VDNS6_PRIMARY, 128)

            // DNS Trap: route well-known public DNS through TUN
            if (dnsTrapEnabled) {
                for (ip in DNS_TRAP_IPS) {
                    try { builder.addRoute(ip, 32) } catch (_: Exception) { }
                }
                // Route known DoH provider IPs too -- we'll drop port 443
                // traffic to these IPs so apps can't bypass DNS filtering
                // via DNS-over-HTTPS to hardcoded resolver IPs.
                for (ip in DOH_BYPASS_IPS) {
                    try { builder.addRoute(ip, 32) } catch (_: Exception) { }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            for (pkg in excludedApps) {
                try { builder.addDisallowedApplication(pkg) }
                catch (_: PackageManager.NameNotFoundException) { }
            }
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) { }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN establish() returned null -- permission revoked?")
                stopSelf(); return
            }

            vpnEstablishedAt = android.os.SystemClock.elapsedRealtime()
            networkLost = false
            isRunning = true
            serviceScope.launch { writeLoop() }
            serviceScope.launch { readLoop() }
            startLogFlusher()
            registerNetworkCallback()

            Log.i(TAG, "VPN started -- ${blocklist.domainCount} domains, " +
                "DoH=${if (useDoH) dohProvider.name else "off"}, " +
                "upstream=${upstreamDnsServers.joinToString(",")}, " +
                "trap=$dnsTrapEnabled (${DNS_TRAP_IPS.size}+${DOH_BYPASS_IPS.size} IPs), " +
                "excluded=${excludedApps.size}, firewalled=${blockedApps.size}")
        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed: ${e.message}", e); stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        unregisterNetworkCallback()
        logFlushJob?.cancel(); logFlushJob = null
        // Flush remaining logs SYNCHRONOUSLY — serviceScope dies with the service,
        // so a launched coroutine would be cancelled before completing.
        try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                val pending = logBuffer.size
                if (pending > 0) Log.i(TAG, "Flushing $pending remaining log entries on stop")
                flushLogBuffer()
                flushStats()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Final log flush failed: ${e.message}")
        }
        try { writeChannel.close() } catch (_: Exception) { }
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun restartVpn() {
        if (!isRunning) return
        serviceScope.launch {
            Log.i(TAG, "Restarting VPN (network change)")
            isRunning = false
            unregisterNetworkCallback()
            // Flush buffered logs before restart — don't lose entries
            logFlushJob?.cancel(); logFlushJob = null
            try { flushLogBuffer(); flushStats() } catch (_: Exception) { }
            try { writeChannel.close() } catch (_: Exception) { }
            try { vpnInterface?.close() } catch (_: Exception) { }
            vpnInterface = null
            delay(500)
            // blocklist is preserved in memory — no need to re-download
            startVpn()
        }
    }

    // ── Network Monitor ──────────────────────────────────────

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Guard 1: Ignore the onAvailable fired by VPN's own network interface.
                    // The VPN creates a network when it establishes, and Android fires
                    // onAvailable for it. Without this cooldown, that triggers restartVpn()
                    // which re-establishes → onAvailable → restart → infinite loop.
                    val elapsed = android.os.SystemClock.elapsedRealtime() - vpnEstablishedAt
                    if (elapsed < NETWORK_RESTART_COOLDOWN_MS) {
                        Log.d(TAG, "Network onAvailable ignored (${elapsed}ms since VPN start)")
                        return
                    }
                    // Guard 2: Only restart if we actually lost a network first.
                    // Plain onAvailable (without prior onLost) means the system is just
                    // reporting an existing network — not an actual connectivity change.
                    if (!networkLost) {
                        Log.d(TAG, "Network onAvailable ignored (no prior onLost)")
                        return
                    }
                    networkLost = false
                    Log.i(TAG, "Network restored after loss — restarting VPN")
                    restartVpn()
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "Network lost — flagging for VPN restart on reconnect")
                    networkLost = true
                }
            }
            networkCallback = cb
            cm.registerNetworkCallback(request, cb)
        } catch (e: Exception) { Log.w(TAG, "Network callback failed: ${e.message}") }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
            }; networkCallback = null
        } catch (_: Exception) { }
    }

    // ── Blocklist ────────────────────────────────────────────

    private suspend fun rebuildBlocklist() {
        try {
            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()
            for (source in sources) {
                // forceDownload=true: must get ALL domains, not just changes.
                // Without this, 304 responses silently drop entire sources.
                downloader.download(source, forceDownload = true).onSuccess { dl ->
                    HostsParser.parse(dl.content).forEach { allDomains.add(it.hostname) }
                }
            }
            repository.getEnabledRulesByType(RuleType.BLOCK).filter { !it.isWildcard }
                .forEach { allDomains.add(it.hostname.lowercase()) }
            repository.getEnabledRulesByType(RuleType.ALLOW).filter { !it.isWildcard }
                .forEach { allDomains.remove(it.hostname.lowercase()) }
            blocklist.update(allDomains, repository.getEnabledWildcards())
        } catch (e: Exception) { Log.w(TAG, "Blocklist rebuild failed: ${e.message}") }
    }

    // ── Packet Processing ────────────────────────────────────

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface ?: return@withContext
        val input = FileInputStream(vpnFd.fileDescriptor)
        val packet = ByteArray(VPN_MTU)
        var count = 0L

        Log.i(TAG, "readLoop started, ${blocklist.domainCount} domains")

        while (isRunning) {
            try {
                val length = input.read(packet)
                if (length <= 0) { delay(1); continue }
                count++

                val ipVer = (packet[0].toInt() and 0xF0) shr 4
                when (ipVer) {
                    4 -> {
                        if (isIpv4UdpDns(packet, length)) processIpv4Dns(packet, length)
                        // Drop non-DNS traffic to trapped IPs (DoT port 853,
                        // DoH port 443). The packets simply get absorbed without
                        // forwarding, causing a connection timeout that forces
                        // apps to fall back to standard DNS (which we filter).
                        // No explicit action needed -- not writing a response = drop.
                    }
                    6 -> { if (isIpv6UdpDns(packet, length)) processIpv6Dns(packet, length) }
                }

                if (count <= 3 || count % 1000 == 0L)
                    Log.d(TAG, "Packets: $count ($blockedCount blocked, $allowedCount allowed)")
            } catch (e: Exception) {
                if (!isRunning) break; delay(10)
            }
        }
    }

    private suspend fun processIpv4Dns(packet: ByteArray, length: Int) {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        val dns = extractDnsPayload(packet, length, ihl) ?: return
        val domain = parseDnsQueryDomain(dns) ?: return
        val qtype = parseDnsQueryType(dns)
        val app = resolveApp(packet, ihl)

        // Per-app firewall: block ALL DNS for firewalled apps
        if (app.first.isNotEmpty() && app.first in blockedApps) {
            logAsync(domain, true, app, qtype)
            sendNxdomain(dns, packet, ihl, false)
            return
        }

        val blocked = isDomainBlocked(domain)
        logAsync(domain, blocked, app, qtype)

        if (blocked) {
            Log.d(TAG, "BLOCKED $domain ($qtype) [${app.second.ifEmpty { "system" }}]")
            sendNxdomain(dns, packet, ihl, false)
        } else {
            Log.d(TAG, "ALLOWED $domain ($qtype)")
            val pCopy = packet.copyOf(length)
            if (useDoH) serviceScope.launch { forwardDoH(dns, pCopy, ihl) }
            else serviceScope.launch { forwardUdp(dns, pCopy, ihl) }
            allowedCount++
        }
    }

    private suspend fun processIpv6Dns(packet: ByteArray, length: Int) {
        val hdr = 40
        val dns = extractDnsPayloadV6(packet, length, hdr) ?: return
        val domain = parseDnsQueryDomain(dns) ?: return
        val qtype = parseDnsQueryType(dns)
        val blocked = isDomainBlocked(domain)

        logAsync(domain, blocked, "" to "", qtype)

        if (blocked) {
            val nx = buildNxdomain(dns) ?: return
            val wrapped = wrapResponseV6(packet, hdr, nx) ?: return
            writeChannel.send(wrapped); blockedCount++
            if (blockedCount % 100 == 0) updateNotification(blockedCount)
        } else {
            val pCopy = packet.copyOf(length)
            serviceScope.launch { forwardUdpV6(dns, pCopy, hdr) }
            allowedCount++
        }
    }

    private suspend fun sendNxdomain(dns: ByteArray, packet: ByteArray, ihl: Int, isV6: Boolean) {
        val nx = buildNxdomain(dns) ?: return
        val wrapped = wrapResponseV4(packet, ihl, nx) ?: return
        writeChannel.send(wrapped); blockedCount++
        if (blockedCount % 100 == 0) updateNotification(blockedCount)
    }

    private fun logAsync(domain: String, blocked: Boolean, app: Pair<String, String>, qtype: String) {
        // Always count stats even if logging disabled
        if (blocked) pendingBlockedStats.incrementAndGet() else pendingAllowedStats.incrementAndGet()

        if (!loggingEnabled) return

        logBuffer.add(DnsLogEntry(
            hostname = domain, blocked = blocked,
            appPackage = app.first, appLabel = app.second, queryType = qtype
        ))
    }

    /** Batch-flush DNS log buffer to Room. 10-50x faster than individual inserts. */
    private suspend fun flushLogBuffer() {
        val batch = mutableListOf<DnsLogEntry>()
        while (true) {
            val entry = logBuffer.poll() ?: break
            batch.add(entry)
            if (batch.size >= 500) {
                try {
                    dnsLogDao.insertAll(batch.toList())  // immutable snapshot
                } catch (e: Exception) {
                    Log.e(TAG, "Batch insert failed (${batch.size} entries): ${e.message}", e)
                }
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            try {
                dnsLogDao.insertAll(batch.toList())
            } catch (e: Exception) {
                Log.e(TAG, "Batch insert failed (${batch.size} entries): ${e.message}", e)
            }
        }
    }

    /** Flush accumulated stats to Room. Uses getAndSet(0) for atomic drain. */
    private suspend fun flushStats() {
        val blocked = pendingBlockedStats.getAndSet(0)
        val allowed = pendingAllowedStats.getAndSet(0)
        if (blocked == 0 && allowed == 0) return
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val e = blockStatsDao.getStatsByDate(today) ?: BlockStats(date = today)
            blockStatsDao.upsert(e.copy(
                blockedCount = e.blockedCount + blocked,
                allowedCount = e.allowedCount + allowed,
                totalQueries = e.totalQueries + blocked + allowed
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Stats flush failed: ${e.message}", e)
            // Put stats back so they aren't lost
            pendingBlockedStats.addAndGet(blocked)
            pendingAllowedStats.addAndGet(allowed)
        }
    }

    /** Periodic log flusher — every 2 seconds. Crash-resistant: catches per-cycle errors. */
    private fun startLogFlusher() {
        logFlushJob?.cancel()
        logFlushJob = serviceScope.launch {
            Log.i(TAG, "Log flusher started (logging=${loggingEnabled})")
            while (isActive) {
                delay(2000)
                try {
                    val bufSize = logBuffer.size
                    if (bufSize > 0) {
                        flushLogBuffer()
                        Log.d(TAG, "Flushed $bufSize log entries to DB")
                    }
                    flushStats()
                } catch (e: Exception) {
                    // Catch per-cycle so the flusher never dies permanently
                    Log.e(TAG, "Log flush cycle error: ${e.message}", e)
                }
            }
            Log.w(TAG, "Log flusher stopped (isActive=false)")
        }
    }

    private suspend fun writeLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface ?: return@withContext
        val output = FileOutputStream(vpnFd.fileDescriptor)
        for (packet in writeChannel) {
            if (!isRunning) break
            try { output.write(packet) } catch (_: Exception) { if (!isRunning) break }
        }
    }

    // ── Domain Blocking ──────────────────────────────────────

    /**
     * Trie-based O(m) domain lookup via BlocklistHolder.
     * Handles exact match, www. prefix, wildcard allow/block.
     * Replaces the old linear Set.contains() + wildcard scan.
     */
    private fun isDomainBlocked(domain: String): Boolean = blocklist.isBlocked(domain)

    // ── Packet Parsing ───────────────────────────────────────

    private fun isIpv4UdpDns(p: ByteArray, len: Int): Boolean {
        if (len < 28) return false
        val vih = p[0].toInt() and 0xFF
        if (vih shr 4 != 4) return false
        if (p[9].toInt() and 0xFF != 17) return false
        val ihl = (vih and 0x0F) * 4
        if (len < ihl + 8) return false
        return ((p[ihl + 2].toInt() and 0xFF) shl 8 or (p[ihl + 3].toInt() and 0xFF)) == DNS_PORT
    }

    private fun isIpv6UdpDns(p: ByteArray, len: Int): Boolean {
        if (len < 48) return false
        if (p[6].toInt() and 0xFF != 17) return false
        return ((p[42].toInt() and 0xFF) shl 8 or (p[43].toInt() and 0xFF)) == DNS_PORT
    }

    private fun extractDnsPayload(p: ByteArray, len: Int, ihl: Int): ByteArray? {
        if (len < ihl + 8) return null
        val udpLen = (p[ihl + 4].toInt() and 0xFF shl 8) or (p[ihl + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8; val dnsStart = ihl + 8
        if (dnsLen < 12 || dnsStart + dnsLen > len) return null
        return p.copyOfRange(dnsStart, dnsStart + dnsLen)
    }

    private fun extractDnsPayloadV6(p: ByteArray, len: Int, hdr: Int): ByteArray? {
        if (len < hdr + 8) return null
        val udpLen = (p[hdr + 4].toInt() and 0xFF shl 8) or (p[hdr + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8; val dnsStart = hdr + 8
        if (dnsLen < 12 || dnsStart + dnsLen > len) return null
        return p.copyOfRange(dnsStart, dnsStart + dnsLen)
    }

    private fun parseDnsQueryDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        var off = 12; val parts = mutableListOf<String>()
        while (off < dns.size) {
            val l = dns[off].toInt() and 0xFF
            if (l == 0) break
            if (l > 63 || off + 1 + l > dns.size) return null
            parts.add(String(dns, off + 1, l, Charsets.US_ASCII)); off += 1 + l
        }
        return if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else null
    }

    private fun parseDnsQueryType(dns: ByteArray): String {
        if (dns.size < 14) return "?"
        var off = 12
        while (off < dns.size) {
            val l = dns[off].toInt() and 0xFF; if (l == 0) { off++; break }; off += 1 + l
        }
        if (off + 2 > dns.size) return "?"
        val qt = (dns[off].toInt() and 0xFF shl 8) or (dns[off + 1].toInt() and 0xFF)
        return when (qt) {
            1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 15 -> "MX"; 16 -> "TXT"
            2 -> "NS"; 6 -> "SOA"; 33 -> "SRV"; 65 -> "HTTPS"; 257 -> "CAA"
            else -> "TYPE$qt"
        }
    }

    // ── DNS Response Construction ────────────────────────────

    /** NXDOMAIN with SOA authority for negative caching (RFC 2308). */
    private fun buildNxdomain(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val soaName = byteArrayOf(0)
        val soaType = byteArrayOf(0, 6); val soaClass = byteArrayOf(0, 1)
        val soaTtl = byteArrayOf(0, 0, 1.toByte(), 0x2C.toByte()) // 300s
        val rdLen = byteArrayOf((SOA_RDATA.size shr 8 and 0xFF).toByte(), (SOA_RDATA.size and 0xFF).toByte())
        val soa = soaName + soaType + soaClass + soaTtl + rdLen + SOA_RDATA

        val resp = ByteArray(query.size + soa.size)
        System.arraycopy(query, 0, resp, 0, query.size)
        resp[2] = (0x84 or (query[2].toInt() and 0x01)).toByte() // QR=1, AA=1, RD=preserved
        resp[3] = 0x83.toByte()  // RA=1, RCODE=3
        resp[6] = 0; resp[7] = 0    // ANCOUNT=0
        resp[8] = 0; resp[9] = 1    // NSCOUNT=1 (SOA)
        resp[10] = 0; resp[11] = 0  // ARCOUNT=0
        System.arraycopy(soa, 0, resp, query.size, soa.size)
        return resp
    }

    private fun wrapResponseV4(orig: ByteArray, ihl: Int, dns: ByteArray): ByteArray? {
        try {
            val total = ihl + 8 + dns.size
            val r = ByteArray(total)
            System.arraycopy(orig, 0, r, 0, ihl)
            System.arraycopy(orig, 12, r, 16, 4)
            System.arraycopy(orig, 16, r, 12, 4)
            r[2] = (total shr 8 and 0xFF).toByte(); r[3] = (total and 0xFF).toByte()
            r[6] = 0; r[7] = 0; r[8] = 64
            r[ihl] = orig[ihl + 2]; r[ihl + 1] = orig[ihl + 3]
            r[ihl + 2] = orig[ihl]; r[ihl + 3] = orig[ihl + 1]
            val udpLen = 8 + dns.size
            r[ihl + 4] = (udpLen shr 8 and 0xFF).toByte(); r[ihl + 5] = (udpLen and 0xFF).toByte()
            r[ihl + 6] = 0; r[ihl + 7] = 0
            System.arraycopy(dns, 0, r, ihl + 8, dns.size)
            // IP checksum
            r[10] = 0; r[11] = 0
            var sum = 0L
            for (i in 0 until ihl step 2) sum += (r[i].toInt() and 0xFF shl 8) or (r[i + 1].toInt() and 0xFF)
            while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
            val ck = sum.inv().toInt() and 0xFFFF
            r[10] = (ck shr 8 and 0xFF).toByte(); r[11] = (ck and 0xFF).toByte()
            return r
        } catch (_: Exception) { return null }
    }

    private fun wrapResponseV6(orig: ByteArray, hdr: Int, dns: ByteArray): ByteArray? {
        try {
            val udpLen = 8 + dns.size; val total = hdr + udpLen
            val r = ByteArray(total)
            System.arraycopy(orig, 0, r, 0, hdr)
            System.arraycopy(orig, 8, r, 24, 16)
            System.arraycopy(orig, 24, r, 8, 16)
            r[4] = (udpLen shr 8 and 0xFF).toByte(); r[5] = (udpLen and 0xFF).toByte()
            r[7] = 64
            r[hdr] = orig[hdr + 2]; r[hdr + 1] = orig[hdr + 3]
            r[hdr + 2] = orig[hdr]; r[hdr + 3] = orig[hdr + 1]
            r[hdr + 4] = (udpLen shr 8 and 0xFF).toByte(); r[hdr + 5] = (udpLen and 0xFF).toByte()
            r[hdr + 6] = 0; r[hdr + 7] = 0
            System.arraycopy(dns, 0, r, hdr + 8, dns.size)
            return r
        } catch (_: Exception) { return null }
    }

    // ── DNS Forwarding ───────────────────────────────────────

    private suspend fun forwardUdp(dns: ByteArray, orig: ByteArray, ihl: Int) {
        try {
            val sock = DatagramSocket(); protect(sock)
            sock.soTimeout = 5000
            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            try {
                sock.receive(rp); sock.close()
                wrapResponseV4(orig, ihl, buf.copyOf(rp.length))?.let { writeChannel.send(it) }
            } catch (_: java.net.SocketTimeoutException) {
                sock.close(); forwardUdpFallback(dns, orig, ihl)
            }
        } catch (_: Exception) { }
    }

    private suspend fun forwardUdpFallback(dns: ByteArray, orig: ByteArray, ihl: Int) {
        try {
            val fallback = upstreamDnsServers.getOrElse(1) { UPSTREAM_DNS[1] }
            val sock = DatagramSocket(); protect(sock); sock.soTimeout = 5000
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(fallback), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            sock.receive(rp); sock.close()
            wrapResponseV4(orig, ihl, buf.copyOf(rp.length))?.let { writeChannel.send(it) }
        } catch (_: Exception) { }
    }

    private suspend fun forwardDoH(dns: ByteArray, orig: ByteArray, ihl: Int) {
        try {
            val resp = dohResolver.resolve(dns, dohProvider)
            if (resp != null) wrapResponseV4(orig, ihl, resp)?.let { writeChannel.send(it) }
            else forwardUdp(dns, orig, ihl) // DoH failed, fallback to plaintext
        } catch (_: Exception) { forwardUdp(dns, orig, ihl) }
    }

    private suspend fun forwardUdpV6(dns: ByteArray, orig: ByteArray, hdr: Int) {
        try {
            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
            val sock = DatagramSocket(); protect(sock); sock.soTimeout = 5000
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            sock.receive(rp); sock.close()
            wrapResponseV6(orig, hdr, buf.copyOf(rp.length))?.let { writeChannel.send(it) }
        } catch (_: Exception) { }
    }

    // ── App Resolution ───────────────────────────────────────

    private fun resolveApp(p: ByteArray, ihl: Int): Pair<String, String> {
        try {
            val srcPort = (p[ihl].toInt() and 0xFF shl 8) or (p[ihl + 1].toInt() and 0xFF)
            if (srcPort == 0) return "" to ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = getSystemService(ConnectivityManager::class.java) ?: return "" to ""
                val src = InetAddress.getByAddress(p.sliceArray(12 until 16))
                val dst = InetAddress.getByAddress(p.sliceArray(16 until 20))
                val uid = cm.getConnectionOwnerUid(
                    android.system.OsConstants.IPPROTO_UDP,
                    InetSocketAddress(src, srcPort), InetSocketAddress(dst, DNS_PORT)
                )
                if (uid > 0) return resolvePkg(uid)
            }
            val uid = findUidFromPort(srcPort)
            if (uid > 0) return resolvePkg(uid)
        } catch (_: Exception) { }
        return "" to ""
    }

    private fun resolvePkg(uid: Int): Pair<String, String> {
        try {
            val pkg = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return "" to ""
            return pkg to packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { }
        return "" to ""
    }

    private fun findUidFromPort(port: Int): Int {
        try {
            val hex = String.format("%04X", port)
            for (path in arrayOf("/proc/net/udp", "/proc/net/udp6")) {
                for (line in java.io.File(path).readLines()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 8 && parts[1].endsWith(":$hex"))
                        return parts[7].toIntOrNull() ?: -1
                }
            }
        } catch (_: Exception) { }
        return -1
    }

    // ── Stats ────────────────────────────────────────────────

    // Stats are now batched via flushStats() called by startLogFlusher()

    // ── Notifications ────────────────────────────────────────

    private fun createNotificationChannel() {
        NotificationChannel(CHANNEL_ID, "HostShield VPN", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VPN blocking status"; setShowBadge(false)
        }.let { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
    }

    private fun buildNotification(blocked: Int): Notification {
        val ci = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val si = PendingIntent.getService(this, 1,
            Intent(this, DnsVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        val sub = buildString {
            append(if (blocked > 0) "$blocked blocked" else "DNS filtering active")
            if (useDoH) append(" | DoH")
            if (dnsTrapEnabled) append(" | Trap")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HostShield Active").setContentText(sub)
            .setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true)
            .setContentIntent(ci)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", si)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateNotification(blocked: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(blocked))
    }
}
