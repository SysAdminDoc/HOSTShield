package com.hostshield.service

import android.app.AlarmManager
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
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
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
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

// HostShield v1.8.0 - VPN DNS Blocking Service
//
// Architecture: DNS-only interception (DNS66-style TEST-NET routing)
//
// - VPN interface at 10.120.0.1/24 + fd00::1/120 (dual-stack)
// - Virtual DNS servers use RFC 5737 TEST-NET addresses (192.0.2.x,
//   198.51.100.x, 203.0.113.x) with automatic fallback if a prefix
//   conflicts with an active network route.
// - Only /32 routes for each virtual DNS address, so ONLY DNS packets
//   traverse the TUN. All other traffic bypasses the VPN entirely.
// - Packet loop uses Os.poll() to multiplex the TUN fd with a shutdown
//   pipe, avoiding blocking reads that miss events.
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
// - Network change listener auto-restarts VPN on connectivity changes,
//   with TRANSPORT_VPN filtering to ignore the VPN's own network events.
// - Watchdog alarm every 10 minutes restarts VPN if killed by OEM
//   battery managers (Samsung Device Care, MIUI Security, etc.).

@AndroidEntryPoint
class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.hostshield.VPN_START"
        const val ACTION_STOP = "com.hostshield.VPN_STOP"
        const val ACTION_WATCHDOG = "com.hostshield.VPN_WATCHDOG"
        const val CHANNEL_ID = "hostshield_vpn"
        const val NOTIFICATION_ID = 1
        private const val TAG = "HostShield"
        private const val WATCHDOG_INTERVAL_MS = 600_000L  // 10 minutes
        private const val WATCHDOG_REQUEST_CODE = 99

        // Live query stream — hot SharedFlow for real-time log tail in UI.
        // Replays last 100 entries for late subscribers (e.g., screen rotation).
        private val liveQueriesFlow = kotlinx.coroutines.flow.MutableSharedFlow<DnsLogEntry>(
            replay = 100,
            extraBufferCapacity = 200,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        /** Collect this from UI to get real-time DNS query events. */
        val liveQueries: kotlinx.coroutines.flow.SharedFlow<DnsLogEntry> = liveQueriesFlow

        // VPN interface
        private const val VPN_ADDRESS = "10.120.0.1"
        private const val VPN_ADDRESS6 = "fd00::1"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53

        // Virtual DNS address prefixes (RFC 5737 + RFC 6890).
        // Non-routable documentation IPs -- guaranteed to never exist on real networks.
        // If the first prefix conflicts with an active route, we fall back to the next.
        private val DNS_PREFIXES = arrayOf("192.0.2", "198.51.100", "203.0.113")

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
            "76.76.2.0", "76.76.10.0",           // ControlD
            "185.228.168.9", "185.228.169.9",    // CleanBrowsing
            "194.242.2.2", "194.242.2.3",        // Mullvad
        )

        // DoT (DNS-over-TLS) trap: these IPs also run on port 853.
        // We route them through VPN and drop non-port-53 traffic,
        // forcing apps to fall back to port 53 where we can filter.
        // Note: The DNS_TRAP_IPS already route port 53 traffic. This
        // list is for hostname-based routing of additional DoT endpoints.
        private val DOT_TRAP_IPS = arrayOf(
            "dns.google",          // 8.8.8.8, 8.8.4.4
            "1dot1dot1dot1.cloudflare-dns.com", // 1.1.1.1
            "dns.quad9.net",       // 9.9.9.9
        )

        // Known DoH provider IPs. When DoH bypass prevention is on, we
        // route these through TUN and drop HTTPS (port 443) traffic so
        // apps can't use DoH to bypass DNS filtering.
        //
        // IMPORTANT: These IPs change periodically as CDNs rotate addresses.
        // This list is a best-effort snapshot. Domain-level blocking in
        // BlocklistHolder's dohBypassDomains is the primary defense;
        // IP blocking is a supplementary layer.
        private val DOH_BYPASS_IPS = arrayOf(
            // Cloudflare DoH (cloudflare-dns.com, 1.1.1.1)
            "104.16.248.249", "104.16.249.249",
            "172.64.36.1", "172.64.36.2",
            // Google DoH (dns.google)
            "142.250.80.14", "142.251.1.100",
            "8.8.8.8", "8.8.4.4",               // dns.google resolves to these too
            // Quad9 DoH (dns.quad9.net)
            "9.9.9.11", "149.112.112.11",
            // AdGuard DoH (dns.adguard-dns.com)
            "94.140.14.140", "94.140.14.141",
            // NextDNS DoH (dns.nextdns.io) — Anycast
            "45.90.28.0", "45.90.30.0",
            // OpenDNS DoH (doh.opendns.com)
            "146.112.41.2", "146.112.41.3",
            // CleanBrowsing DoH
            "185.228.168.168", "185.228.169.168",
            // Mullvad DoH (dns.mullvad.net)
            "194.242.2.2", "194.242.2.3",
            // ControlD DoH (freedns.controld.com)
            "76.76.2.11", "76.76.10.11",
        )

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

    // Shutdown pipe: writing any byte breaks the Os.poll() loop cleanly.
    // pipe[0] = read end (polled), pipe[1] = write end (signalled in stopVpn).
    private var shutdownPipeRead: java.io.FileDescriptor? = null
    private var shutdownPipeWrite: java.io.FileDescriptor? = null

    // Resolved virtual DNS addresses (set during startVpn based on prefix availability)
    private var vdns4Primary = ""
    private var vdns4Secondary = ""

    private var excludedApps = setOf<String>()
    private var blockedApps = setOf<String>()
    private var useDoH = false
    private var dohProvider = DohResolver.Provider.CLOUDFLARE
    private var dnsTrapEnabled = true
    // Block response: "nxdomain", "zero_ip", "refused"
    private var blockResponseType = "nxdomain"
    // Custom upstream DNS resolved at start
    private var upstreamDnsServers = UPSTREAM_DNS.toList()

    private var writeChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var blockedCount = 0
    private var allowedCount = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // DNS answer cache for heuristic UID attribution (4B).
    // When a DNS response returns an A/AAAA record, we cache (resolved_ip -> hostname).
    // When an app makes a TCP connection to that IP (visible in /proc/net/tcp),
    // we can attribute the earlier DNS query to the same UID.
    // Key: IP address string, Value: (hostname, timestamp_ms)
    private val dnsAnswerCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private val DNS_ANSWER_CACHE_TTL_MS = 30_000L  // 30s — enough for TCP connect after DNS

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

    // DNS Response Cache — LRU with TTL-aware expiration
    private val dnsCache = DnsCache(maxEntries = 2000, maxNegativeEntries = 500)

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
            ACTION_WATCHDOG -> {
                // OEM battery managers (Samsung, Xiaomi, Huawei) kill VPN services.
                // This alarm fires every 10 min to detect and recover.
                if (!isRunning) {
                    serviceScope.launch {
                        val shouldRun = prefs.isEnabled.first()
                        if (shouldRun) {
                            Log.i(TAG, "Watchdog: VPN was killed — restarting")
                            startVpn()
                        }
                    }
                } else {
                    // TUN health probe: verify the VPN tunnel is actually passing traffic.
                    // The TUN fd can silently die on some OEMs while isRunning stays true.
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val vfd = vpnInterface?.fileDescriptor
                            if (vfd == null || !vfd.valid()) {
                                Log.w(TAG, "Watchdog: TUN fd invalid — restarting VPN")
                                restartVpn()
                                return@launch
                            }
                            // Verify upstream connectivity with a quick DNS probe
                            val sock = java.net.DatagramSocket()
                            protect(sock)
                            sock.soTimeout = 3000
                            // Minimal DNS query for "." (root) — TYPE NS
                            val probe = byteArrayOf(
                                0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01
                            )
                            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
                            sock.send(java.net.DatagramPacket(
                                probe, probe.size, InetAddress.getByName(primary), DNS_PORT))
                            val buf = ByteArray(512)
                            sock.receive(java.net.DatagramPacket(buf, buf.size))
                            sock.close()
                            Log.d(TAG, "Watchdog: TUN + upstream healthy")
                        } catch (e: java.net.SocketTimeoutException) {
                            Log.w(TAG, "Watchdog: upstream probe timed out (may be network issue)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Watchdog: health probe failed: ${e.message}")
                        }
                    }
                }
                return START_STICKY
            }
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
        cancelWatchdog()
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
            blockResponseType = prefs.blockResponseType.first()

            // Resolve custom upstream DNS
            val customDns = prefs.customUpstreamDns.first().trim()
            upstreamDnsServers = if (customDns.isNotBlank()) {
                val servers = customDns.split(",", ";", " ").map { it.trim() }.filter { it.isNotBlank() }
                if (servers.isNotEmpty()) servers else UPSTREAM_DNS.toList()
            } else UPSTREAM_DNS.toList()

            if (blocklist.domainCount == 0) rebuildBlocklist()

            // Create shutdown pipe for clean Os.poll() exit
            val pipe = Os.pipe()
            shutdownPipeRead = pipe[0]
            shutdownPipeWrite = pipe[1]

            val builder = Builder()
                .setSession("HostShield")
                .setMtu(VPN_MTU)
                // IPv4 + IPv6 dual-stack
                .addAddress(VPN_ADDRESS, 24)
                .addAddress(VPN_ADDRESS6, 120)

            // Virtual DNS with RFC 5737 prefix fallback.
            // Try each TEST-NET prefix until one doesn't conflict with active routes.
            // DNS66 uses the same pattern to handle rare network collisions.
            vdns4Primary = ""
            vdns4Secondary = ""
            for (prefix in DNS_PREFIXES) {
                try {
                    val primary = "$prefix.1"
                    val secondary = "$prefix.2"
                    builder.addDnsServer(primary)
                    builder.addDnsServer(secondary)
                    builder.addRoute(primary, 32)
                    builder.addRoute(secondary, 32)
                    vdns4Primary = primary
                    vdns4Secondary = secondary
                    break
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "DNS prefix $prefix conflicts with active route, trying next")
                    continue
                }
            }
            if (vdns4Primary.isEmpty()) {
                Log.e(TAG, "All RFC 5737 prefixes exhausted — cannot start VPN")
                stopVpn(); return
            }

            // IPv6 virtual DNS
            builder.addDnsServer(VDNS6_PRIMARY)
            builder.addRoute(VDNS6_PRIMARY, 128)

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

            vpnEstablishedAt = SystemClock.elapsedRealtime()
            networkLost = false
            isRunning = true
            dnsAnswerCache.clear()
            serviceScope.launch { writeLoop() }
            serviceScope.launch { packetLoop() }
            startLogFlusher()
            registerNetworkCallback()
            scheduleWatchdog()

            Log.i(TAG, "VPN started -- ${blocklist.domainCount} domains, " +
                "DoH=${if (useDoH) dohProvider.name else "off"}, " +
                "upstream=${upstreamDnsServers.joinToString(",")}, " +
                "vdns=$vdns4Primary/$vdns4Secondary, " +
                "blockResponse=$blockResponseType, " +
                "trap=$dnsTrapEnabled (${DNS_TRAP_IPS.size}+${DOH_BYPASS_IPS.size} IPs), " +
                "excluded=${excludedApps.size}, firewalled=${blockedApps.size}")
        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed: ${e.message}", e); stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        // Signal the Os.poll() loop to exit by writing to the shutdown pipe
        try { shutdownPipeWrite?.let { Os.write(it, byteArrayOf(1), 0, 1) } } catch (_: Exception) { }
        try { shutdownPipeRead?.let { Os.close(it) } } catch (_: Exception) { }
        try { shutdownPipeWrite?.let { Os.close(it) } } catch (_: Exception) { }
        shutdownPipeRead = null; shutdownPipeWrite = null
        cancelWatchdog()
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
            // Signal poll loop exit
            try { shutdownPipeWrite?.let { Os.write(it, byteArrayOf(1), 0, 1) } } catch (_: Exception) { }
            try { shutdownPipeRead?.let { Os.close(it) } } catch (_: Exception) { }
            try { shutdownPipeWrite?.let { Os.close(it) } } catch (_: Exception) { }
            shutdownPipeRead = null; shutdownPipeWrite = null
            cancelWatchdog()
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
                    val elapsed = SystemClock.elapsedRealtime() - vpnEstablishedAt
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

                // Guard 3: Ignore VPN's own network events entirely.
                // NetGuard uses hasTransport(TRANSPORT_VPN) to filter these out.
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                }

                override fun onLost(network: Network) {
                    // Don't flag VPN's own network loss
                    val cm2 = getSystemService(ConnectivityManager::class.java) ?: return
                    val caps = cm2.getNetworkCapabilities(network)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                        Log.d(TAG, "Network onLost ignored (VPN's own network)")
                        return
                    }
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

    /**
     * Poll-based packet loop replacing the old blocking FileInputStream.read().
     *
     * Uses Os.poll() to multiplex the TUN file descriptor with a shutdown pipe:
     * - TUN fd: POLLIN when a packet arrives from the OS (DNS query)
     * - Shutdown pipe read end: becomes readable when stopVpn() writes to it
     *
     * This pattern (from DNS66/NetGuard) ensures:
     * 1. We never block indefinitely — poll returns on any event or timeout
     * 2. Clean shutdown — writing to the pipe breaks the loop immediately
     * 3. Periodic housekeeping during the 5s timeout gaps
     *
     * The old readLoop used FileInputStream.read() which blocked the thread
     * and couldn't be interrupted cleanly without closing the fd.
     */
    private suspend fun packetLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface?.fileDescriptor ?: return@withContext
        val pipeRead = shutdownPipeRead ?: return@withContext
        val packet = ByteArray(VPN_MTU)
        var count = 0L

        Log.i(TAG, "packetLoop started (Os.poll), ${blocklist.domainCount} domains")

        // Pre-allocate poll fd array: [0]=TUN, [1]=shutdown pipe
        val pollFds = arrayOf(
            StructPollfd().apply { fd = vpnFd; events = OsConstants.POLLIN.toShort() },
            StructPollfd().apply { fd = pipeRead; events = (OsConstants.POLLIN or OsConstants.POLLHUP).toShort() }
        )

        while (isRunning) {
            try {
                // Block until TUN has data, shutdown signalled, or 5s timeout
                val ready = Os.poll(pollFds, 5000)
                if (ready == 0) continue  // timeout — check isRunning, loop

                // Shutdown pipe signalled — exit cleanly
                if (pollFds[1].revents.toInt() != 0) {
                    Log.d(TAG, "packetLoop: shutdown pipe signalled")
                    break
                }

                // TUN has packet data
                if (pollFds[0].revents.toInt() and OsConstants.POLLIN != 0) {
                    val length = Os.read(vpnFd, packet, 0, packet.size)
                    if (length <= 0) continue
                    count++

                    val ipVer = (packet[0].toInt() and 0xF0) shr 4
                    when (ipVer) {
                        4 -> {
                            if (isIpv4UdpDns(packet, length)) processIpv4Dns(packet, length)
                            else if (isIpv4TcpDns(packet, length)) processIpv4TcpDns(packet, length)
                            // Drop non-DNS traffic to trapped IPs (DoT port 853,
                            // DoH port 443). The packets simply get absorbed without
                            // forwarding, causing a connection timeout that forces
                            // apps to fall back to standard DNS (which we filter).
                            // No explicit action needed -- not writing a response = drop.
                        }
                        6 -> {
                            if (isIpv6UdpDns(packet, length)) processIpv6Dns(packet, length)
                            // TCP DNS on IPv6 is rare; silently dropped for now.
                            // IPv6 TCP DNS would require constructing IPv6+TCP headers
                            // with proper flow labels. Apps fall back to UDP on timeout.
                        }
                    }

                    if (count <= 3 || count % 1000 == 0L)
                        Log.d(TAG, "Packets: $count ($blockedCount blocked, $allowedCount allowed)")
                }

                // Check for error conditions on TUN fd
                if (pollFds[0].revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP) != 0) {
                    Log.w(TAG, "packetLoop: TUN fd error/hangup")
                    break
                }
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) continue  // interrupted by signal, retry
                if (!isRunning) break
                Log.e(TAG, "Poll error: ${e.message}")
                delay(10)
            } catch (e: Exception) {
                if (!isRunning) break
                Log.w(TAG, "packetLoop error: ${e.message}")
                delay(10)
            }
        }
        Log.i(TAG, "packetLoop exited after $count packets")
    }

    private suspend fun processIpv4Dns(packet: ByteArray, length: Int) {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        val dns = extractDnsPayload(packet, length, ihl) ?: return
        val domain = parseDnsQueryDomain(dns) ?: return
        val qtype = parseDnsQueryType(dns)
        var app = resolveApp(packet, ihl)

        // Heuristic fallback: if primary UID lookup failed, check if any app
        // recently connected to an IP that resolved from this hostname.
        // This catches cases where getConnectionOwnerUid and /proc/net/udp miss.
        if (app.first.isEmpty()) {
            val heuristicUid = findUidByDnsCorrelation(domain)
            if (heuristicUid > 0) app = resolvePkg(heuristicUid)
        }

        // Per-app firewall: block ALL DNS for firewalled apps
        if (app.first.isNotEmpty() && app.first in blockedApps) {
            logAsync(domain, true, app, qtype)
            sendBlockResponse(dns, packet, ihl, false, qtype)
            return
        }

        val blocked = isDomainBlocked(domain)
        logAsync(domain, blocked, app, qtype)

        if (blocked) {
            Log.d(TAG, "BLOCKED $domain ($qtype) [${app.second.ifEmpty { "system" }}]")
            sendBlockResponse(dns, packet, ihl, false, qtype)
        } else {
            // Cache lookup — serve from cache if available
            val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
            val cached = dnsCache.get(domain, qtypeNum, txId)
            if (cached != null) {
                Log.d(TAG, "CACHE HIT $domain ($qtype)")
                wrapResponseV4(packet, ihl, cached)?.let { writeChannel.send(it) }
                allowedCount++
                return
            }

            Log.d(TAG, "ALLOWED $domain ($qtype)")
            val pCopy = packet.copyOf(length)
            if (useDoH) serviceScope.launch { forwardDoH(dns, domain, pCopy, ihl) }
            else serviceScope.launch { forwardUdp(dns, domain, pCopy, ihl) }
            allowedCount++
        }
    }

    private suspend fun processIpv6Dns(packet: ByteArray, length: Int) {
        val hdr = 40
        val dns = extractDnsPayloadV6(packet, length, hdr) ?: return
        val domain = parseDnsQueryDomain(dns) ?: return
        val qtype = parseDnsQueryType(dns)
        var app = resolveAppV6(packet, hdr)

        // Heuristic fallback: DNS answer -> TCP connection correlation
        if (app.first.isEmpty()) {
            val heuristicUid = findUidByDnsCorrelation(domain)
            if (heuristicUid > 0) app = resolvePkg(heuristicUid)
        }

        // Per-app firewall: block ALL DNS for firewalled apps
        if (app.first.isNotEmpty() && app.first in blockedApps) {
            logAsync(domain, true, app, qtype)
            val resp = buildBlockResponse(dns, qtype) ?: return
            val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
            writeChannel.send(wrapped); blockedCount++
            if (blockedCount % 100 == 0) updateNotification(blockedCount)
            return
        }

        val blocked = isDomainBlocked(domain)
        logAsync(domain, blocked, app, qtype)

        if (blocked) {
            val resp = buildBlockResponse(dns, qtype) ?: return
            val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
            writeChannel.send(wrapped); blockedCount++
            if (blockedCount % 100 == 0) updateNotification(blockedCount)
        } else {
            val pCopy = packet.copyOf(length)
            serviceScope.launch { forwardUdpV6(dns, domain, pCopy, hdr) }
            allowedCount++
        }
    }

    /**
     * Send a block response (NXDOMAIN, 0.0.0.0/::, or REFUSED) for an IPv4 packet.
     * The response type is controlled by the blockResponseType preference.
     */
    private suspend fun sendBlockResponse(dns: ByteArray, packet: ByteArray, ihl: Int, isV6: Boolean, qtype: String) {
        val resp = buildBlockResponse(dns, qtype) ?: return
        val wrapped = wrapResponseV4(packet, ihl, resp) ?: return
        writeChannel.send(wrapped); blockedCount++
        if (blockedCount % 100 == 0) updateNotification(blockedCount)
    }

    /**
     * Build a DNS block response based on the configured response type.
     *
     * - "nxdomain": RCODE=3 with SOA authority. Default. Some apps retry on
     *   NXDOMAIN with alternate resolvers, potentially bypassing blocking.
     * - "zero_ip": RCODE=0 with A=0.0.0.0 or AAAA=::. Connection fails
     *   immediately without DNS retry. NextDNS, Cloudflare Gateway, and
     *   AdGuard all use this approach.
     * - "refused": RCODE=5. Strong signal to the client but some apps
     *   interpret this as a server error and retry.
     */
    private fun buildBlockResponse(dns: ByteArray, qtype: String): ByteArray? {
        // Delegate to shared DnsPacketBuilder for consistent responses
        // across VPN and root mode services
        return DnsPacketBuilder.buildBlockResponse(dns, blockResponseType)
    }

    private fun logAsync(domain: String, blocked: Boolean, app: Pair<String, String>, qtype: String) {
        // Always count stats even if logging disabled
        if (blocked) pendingBlockedStats.incrementAndGet() else pendingAllowedStats.incrementAndGet()

        val entry = DnsLogEntry(
            hostname = domain, blocked = blocked,
            appPackage = app.first, appLabel = app.second, queryType = qtype
        )

        // Emit to live query stream (non-blocking, drops oldest if full)
        liveQueriesFlow.tryEmit(entry)

        if (!loggingEnabled) return
        logBuffer.add(entry)
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

    // ── TCP DNS (RFC 7766) ───────────────────────────────────

    /**
     * Detect IPv4 TCP packets destined for port 53.
     * Protocol 6 = TCP. Destination port at TCP header offset + 2.
     */
    private fun isIpv4TcpDns(p: ByteArray, len: Int): Boolean {
        if (len < 40) return false  // min IP(20) + TCP(20)
        val vih = p[0].toInt() and 0xFF
        if (vih shr 4 != 4) return false
        if (p[9].toInt() and 0xFF != 6) return false  // protocol = TCP
        val ihl = (vih and 0x0F) * 4
        if (len < ihl + 20) return false
        return ((p[ihl + 2].toInt() and 0xFF) shl 8 or (p[ihl + 3].toInt() and 0xFF)) == DNS_PORT
    }

    /**
     * Handle IPv4 TCP DNS packets (RFC 7766).
     *
     * TCP DNS is used by some resolvers for large responses and zone transfers.
     * Full TCP state machine handling is complex (NetGuard does it in native C).
     * We take a pragmatic approach:
     *
     * - SYN packets: If the DNS payload would be blocked (we check the TCP
     *   data for DNS query when present), send RST. For SYN-only (no data),
     *   send RST to reject the connection immediately.
     * - Data packets: Extract the DNS query (2-byte length prefix + DNS message),
     *   check against blocklist. If blocked, send RST. If allowed, drop the
     *   packet — app times out and retries with UDP (which we fully handle).
     *
     * This prevents TCP DNS bypass of blocking without implementing a full
     * TCP state machine. Allowed TCP DNS queries fall back to UDP on timeout
     * (standard DNS client behavior per RFC 7766 §6.2.2).
     */
    private suspend fun processIpv4TcpDns(packet: ByteArray, length: Int) {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        val tcpOff = ihl
        if (length < tcpOff + 20) return

        val dataOff = ((packet[tcpOff + 12].toInt() and 0xF0) shr 4) * 4
        val tcpFlags = packet[tcpOff + 13].toInt() and 0xFF
        val isSyn = (tcpFlags and 0x02) != 0
        val isRst = (tcpFlags and 0x04) != 0

        // Don't respond to RST packets
        if (isRst) return

        val payloadStart = tcpOff + dataOff
        val payloadLen = length - payloadStart

        // Try to extract DNS hostname from payload (if present)
        var hostname: String? = null
        if (payloadLen > 14) { // 2-byte length prefix + minimum DNS header (12 bytes)
            // TCP DNS: 2-byte big-endian length prefix, then standard DNS message
            val dnsLen = (packet[payloadStart].toInt() and 0xFF shl 8) or
                (packet[payloadStart + 1].toInt() and 0xFF)
            if (dnsLen > 0 && payloadStart + 2 + dnsLen <= length) {
                val dns = packet.copyOfRange(payloadStart + 2, payloadStart + 2 + dnsLen)
                hostname = parseDnsQueryDomain(dns)
            }
        }

        val blocked = if (hostname != null) isDomainBlocked(hostname) else true // block unknown

        if (blocked) {
            // Send TCP RST — immediate connection rejection
            val rst = buildTcpRst(packet, ihl) ?: return
            writeChannel.send(rst)
            blockedCount++
            if (hostname != null) {
                Log.d(TAG, "TCP-DNS BLOCKED (RST) $hostname")
                logAsync(hostname, true, "" to "", "TCP")
            }
        } else {
            // Allowed but we can't fully proxy TCP DNS without state tracking.
            // Drop the packet — app will timeout and retry with UDP per RFC 7766.
            if (hostname != null) {
                Log.d(TAG, "TCP-DNS allowed (drop→UDP fallback) $hostname")
            }
        }
    }

    /**
     * Build a TCP RST packet by swapping src/dst addresses and ports,
     * setting RST+ACK flags, and computing correct checksums.
     */
    private fun buildTcpRst(orig: ByteArray, ihl: Int): ByteArray? {
        if (orig.size < ihl + 20) return null
        val rstLen = ihl + 20 // IP header + minimal TCP header (no options)
        val rst = ByteArray(rstLen)

        // ── IP header ──
        rst[0] = ((4 shl 4) or (ihl / 4)).toByte() // version + IHL
        rst[1] = 0 // DSCP/ECN
        rst[2] = (rstLen shr 8 and 0xFF).toByte()
        rst[3] = (rstLen and 0xFF).toByte()
        rst[4] = 0; rst[5] = 0 // identification
        rst[6] = 0x40.toByte(); rst[7] = 0 // DF flag, no fragment
        rst[8] = 64 // TTL
        rst[9] = 6 // protocol = TCP
        rst[10] = 0; rst[11] = 0 // checksum (computed below)
        // Swap src/dst IP
        System.arraycopy(orig, 16, rst, 12, 4) // orig dst → rst src
        System.arraycopy(orig, 12, rst, 16, 4) // orig src → rst dst

        // ── TCP header ──
        val t = ihl
        // Swap src/dst port
        rst[t] = orig[t + 2]; rst[t + 1] = orig[t + 3] // orig dst port → rst src
        rst[t + 2] = orig[t]; rst[t + 3] = orig[t + 1] // orig src port → rst dst

        // Sequence number = 0
        rst[t + 4] = 0; rst[t + 5] = 0; rst[t + 6] = 0; rst[t + 7] = 0

        // ACK number = orig SEQ + payload length (or +1 for SYN)
        val origSeq = (orig[t + 4].toLong() and 0xFF shl 24) or
            (orig[t + 5].toLong() and 0xFF shl 16) or
            (orig[t + 6].toLong() and 0xFF shl 8) or
            (orig[t + 7].toLong() and 0xFF)
        val origDataOff = ((orig[t + 12].toInt() and 0xF0) shr 4) * 4
        val origPayload = orig.size - ihl - origDataOff
        val origFlags = orig[t + 13].toInt() and 0xFF
        val synBit = if ((origFlags and 0x02) != 0) 1 else 0
        val finBit = if ((origFlags and 0x01) != 0) 1 else 0
        val ackNum = origSeq + origPayload + synBit + finBit
        rst[t + 8] = (ackNum shr 24 and 0xFF).toByte()
        rst[t + 9] = (ackNum shr 16 and 0xFF).toByte()
        rst[t + 10] = (ackNum shr 8 and 0xFF).toByte()
        rst[t + 11] = (ackNum and 0xFF).toByte()

        rst[t + 12] = 0x50.toByte() // data offset = 5 (20 bytes, no options)
        rst[t + 13] = 0x14.toByte() // RST + ACK flags
        rst[t + 14] = 0; rst[t + 15] = 0 // window = 0
        rst[t + 16] = 0; rst[t + 17] = 0 // checksum (computed below)
        rst[t + 18] = 0; rst[t + 19] = 0 // urgent pointer

        // TCP checksum (pseudo-header + TCP header)
        computeTcpChecksum(rst, ihl, 20)

        // IP checksum
        computeIpChecksum(rst, ihl)

        return rst
    }

    /** Compute and write TCP checksum into packet[ihl+16..ihl+17]. */
    private fun computeTcpChecksum(pkt: ByteArray, ihl: Int, tcpLen: Int) {
        var sum = 0L
        // Pseudo-header: src IP + dst IP + 0 + protocol(6) + TCP length
        for (i in 12 until 20 step 2) {
            sum += (pkt[i].toInt() and 0xFF shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        sum += 6 // protocol
        sum += tcpLen // TCP length
        // TCP header
        pkt[ihl + 16] = 0; pkt[ihl + 17] = 0 // zero checksum field
        for (i in ihl until ihl + tcpLen step 2) {
            val hi = pkt[i].toInt() and 0xFF
            val lo = if (i + 1 < pkt.size) pkt[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val cksum = sum.toInt().inv() and 0xFFFF
        pkt[ihl + 16] = (cksum shr 8 and 0xFF).toByte()
        pkt[ihl + 17] = (cksum and 0xFF).toByte()
    }

    /** Compute and write IP header checksum into pkt[10..11]. */
    private fun computeIpChecksum(pkt: ByteArray, ihl: Int) {
        pkt[10] = 0; pkt[11] = 0
        var sum = 0L
        for (i in 0 until ihl step 2) {
            sum += (pkt[i].toInt() and 0xFF shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val cksum = sum.toInt().inv() and 0xFFFF
        pkt[10] = (cksum shr 8 and 0xFF).toByte()
        pkt[11] = (cksum and 0xFF).toByte()
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

    private suspend fun forwardUdp(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int) {
        try {
            val startMs = System.currentTimeMillis()
            val sock = DatagramSocket(); protect(sock)
            sock.soTimeout = 5000
            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            try {
                sock.receive(rp); sock.close()
                val respBytes = buf.copyOf(rp.length)
                val latencyMs = (System.currentTimeMillis() - startMs).toInt()

                // CNAME cloaking detection — block if any CNAME target is in blocklist
                val cnameResult = CnameCloakDetector.inspect(respBytes, blocklist)
                if (cnameResult.blocked) {
                    Log.i(TAG, "CNAME CLOAK blocked: $domain -> ${cnameResult.blockedCname}")
                    val blockResp = buildBlockResponse(dns, DnsPacketBuilder.parseQueryType(dns).let {
                        when (it) { 1 -> "A"; 28 -> "AAAA"; else -> "A" }
                    })
                    if (blockResp != null) wrapResponseV4(orig, ihl, blockResp)?.let { writeChannel.send(it) }
                    blockedCount++
                    return
                }

                cacheDnsAnswerIps(domain, respBytes)
                // Cache the response
                val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
                dnsCache.put(domain, qtypeNum, respBytes)

                wrapResponseV4(orig, ihl, respBytes)?.let { writeChannel.send(it) }
            } catch (_: java.net.SocketTimeoutException) {
                sock.close(); forwardUdpFallback(dns, domain, orig, ihl)
            }
        } catch (_: Exception) { }
    }

    private suspend fun forwardUdpFallback(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int) {
        try {
            val fallback = upstreamDnsServers.getOrElse(1) { UPSTREAM_DNS[1] }
            val sock = DatagramSocket(); protect(sock); sock.soTimeout = 5000
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(fallback), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            sock.receive(rp); sock.close()
            val respBytes = buf.copyOf(rp.length)

            val cnameResult = CnameCloakDetector.inspect(respBytes, blocklist)
            if (cnameResult.blocked) {
                val blockResp = buildBlockResponse(dns, DnsPacketBuilder.parseQueryType(dns).let {
                    when (it) { 1 -> "A"; 28 -> "AAAA"; else -> "A" }
                })
                if (blockResp != null) wrapResponseV4(orig, ihl, blockResp)?.let { writeChannel.send(it) }
                blockedCount++
                return
            }

            cacheDnsAnswerIps(domain, respBytes)
            dnsCache.put(domain, DnsPacketBuilder.parseQueryType(dns), respBytes)
            wrapResponseV4(orig, ihl, respBytes)?.let { writeChannel.send(it) }
        } catch (_: Exception) { }
    }

    private suspend fun forwardDoH(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int) {
        try {
            val resp = dohResolver.resolve(dns, dohProvider)
            if (resp != null) {
                // CNAME cloaking detection
                val cnameResult = CnameCloakDetector.inspect(resp, blocklist)
                if (cnameResult.blocked) {
                    Log.i(TAG, "CNAME CLOAK (DoH) blocked: $domain -> ${cnameResult.blockedCname}")
                    val blockResp = buildBlockResponse(dns, DnsPacketBuilder.parseQueryType(dns).let {
                        when (it) { 1 -> "A"; 28 -> "AAAA"; else -> "A" }
                    })
                    if (blockResp != null) wrapResponseV4(orig, ihl, blockResp)?.let { writeChannel.send(it) }
                    blockedCount++
                    return
                }

                cacheDnsAnswerIps(domain, resp)
                val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
                dnsCache.put(domain, qtypeNum, resp)

                wrapResponseV4(orig, ihl, resp)?.let { writeChannel.send(it) }
            }
            else forwardUdp(dns, domain, orig, ihl) // DoH failed, fallback to plaintext
        } catch (_: Exception) { forwardUdp(dns, domain, orig, ihl) }
    }

    private suspend fun forwardUdpV6(dns: ByteArray, domain: String, orig: ByteArray, hdr: Int) {
        try {
            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
            val sock = DatagramSocket(); protect(sock); sock.soTimeout = 5000
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            sock.receive(rp); sock.close()
            val respBytes = buf.copyOf(rp.length)

            val cnameResult = CnameCloakDetector.inspect(respBytes, blocklist)
            if (cnameResult.blocked) {
                val blockResp = buildBlockResponse(dns, DnsPacketBuilder.parseQueryType(dns).let {
                    when (it) { 1 -> "A"; 28 -> "AAAA"; else -> "A" }
                })
                if (blockResp != null) wrapResponseV6(orig, hdr, blockResp)?.let { writeChannel.send(it) }
                blockedCount++
                return
            }

            cacheDnsAnswerIps(domain, respBytes)
            dnsCache.put(domain, DnsPacketBuilder.parseQueryType(dns), respBytes)
            wrapResponseV6(orig, hdr, respBytes)?.let { writeChannel.send(it) }
        } catch (_: Exception) { }
    }

    // ── DNS Answer Cache (Heuristic UID Attribution) ────────

    /**
     * Extract A/AAAA answer IPs from a DNS response and cache them.
     *
     * When the DNS response contains A (0.0.0.0-style) or AAAA (::) records,
     * we store (resolved_ip -> hostname). Later, when we see a TCP connection
     * to one of these IPs in /proc/net/tcp, we can correlate the UID of that
     * TCP connection back to the DNS query that resolved it.
     *
     * This is the RethinkDNS heuristic approach. It's probabilistic — the
     * TCP connection must happen within the cache TTL (30s) and the IP
     * must not be shared by multiple hostnames.
     */
    private fun cacheDnsAnswerIps(hostname: String, response: ByteArray) {
        try {
            if (response.size < 12) return
            val anCount = (response[6].toInt() and 0xFF shl 8) or (response[7].toInt() and 0xFF)
            if (anCount == 0) return

            // Skip query section to reach answer section
            var off = 12
            val qdCount = (response[4].toInt() and 0xFF shl 8) or (response[5].toInt() and 0xFF)
            for (i in 0 until qdCount) {
                off = skipDnsName(response, off)
                if (off < 0) return
                off += 4 // QTYPE + QCLASS
            }

            val now = System.currentTimeMillis()
            var cached = 0
            for (i in 0 until anCount.coerceAtMost(10)) { // cap at 10 answers
                if (off >= response.size) break
                off = skipDnsName(response, off)
                if (off < 0 || off + 10 > response.size) break

                val rtype = (response[off].toInt() and 0xFF shl 8) or (response[off + 1].toInt() and 0xFF)
                val rdLen = (response[off + 8].toInt() and 0xFF shl 8) or (response[off + 9].toInt() and 0xFF)
                off += 10 // TYPE(2) + CLASS(2) + TTL(4) + RDLENGTH(2)

                if (off + rdLen > response.size) break

                val ip: String? = when {
                    rtype == 1 && rdLen == 4 -> {  // A record
                        "${response[off].toInt() and 0xFF}.${response[off+1].toInt() and 0xFF}." +
                        "${response[off+2].toInt() and 0xFF}.${response[off+3].toInt() and 0xFF}"
                    }
                    rtype == 28 && rdLen == 16 -> {  // AAAA record
                        try {
                            InetAddress.getByAddress(response.copyOfRange(off, off + 16)).hostAddress
                        } catch (_: Exception) { null }
                    }
                    else -> null
                }

                if (ip != null && ip != "0.0.0.0" && ip != "::") {
                    dnsAnswerCache[ip] = hostname to now
                    cached++
                }
                off += rdLen
            }

            // Periodic eviction (every 100 cache inserts, remove stale entries)
            if (cached > 0 && dnsAnswerCache.size > 500) {
                dnsAnswerCache.entries.removeAll { now - it.value.second > DNS_ANSWER_CACHE_TTL_MS }
            }
        } catch (_: Exception) { }
    }

    /** Skip a DNS name (labels or compressed pointer) and return offset after it. */
    private fun skipDnsName(buf: ByteArray, start: Int): Int {
        var off = start
        while (off < buf.size) {
            val len = buf[off].toInt() and 0xFF
            if (len == 0) return off + 1                     // end of name
            if (len and 0xC0 == 0xC0) return off + 2         // compressed pointer
            off += 1 + len
        }
        return -1 // malformed
    }

    /**
     * Heuristic UID lookup: scan /proc/net/tcp and /proc/net/tcp6 for a
     * connection to an IP in our DNS answer cache, and return the UID of
     * that TCP socket.
     *
     * This correlates "which app made a DNS query" by observing which app
     * subsequently connects to the resolved IP. The cache TTL (30s) limits
     * false positives.
     */
    private fun findUidByDnsCorrelation(hostname: String): Int {
        val now = System.currentTimeMillis()
        // Find all IPs that resolved to this hostname (within TTL)
        val targetIps = mutableSetOf<String>()
        for ((ip, pair) in dnsAnswerCache) {
            if (pair.first == hostname && now - pair.second < DNS_ANSWER_CACHE_TTL_MS) {
                targetIps.add(ip)
            }
        }
        if (targetIps.isEmpty()) return -1

        // Convert IPs to hex for /proc/net/tcp{,6} matching
        val hexIpsV4 = mutableSetOf<String>()
        val hexIpsV6 = mutableSetOf<String>()
        for (ip in targetIps) {
            try {
                val addr = InetAddress.getByName(ip)
                val bytes = addr.address
                if (bytes.size == 4) {
                    // IPv4: /proc/net/tcp uses little-endian 32-bit hex
                    hexIpsV4.add(String.format("%02X%02X%02X%02X",
                        bytes[3].toInt() and 0xFF, bytes[2].toInt() and 0xFF,
                        bytes[1].toInt() and 0xFF, bytes[0].toInt() and 0xFF))
                } else if (bytes.size == 16) {
                    // IPv6: /proc/net/tcp6 uses four 32-bit words, each little-endian
                    // e.g., 2001:4860:4860::8888 → bytes[0..15] → four LE groups
                    val sb = StringBuilder(32)
                    for (w in 0 until 4) {
                        val off = w * 4
                        sb.append(String.format("%02X%02X%02X%02X",
                            bytes[off + 3].toInt() and 0xFF,
                            bytes[off + 2].toInt() and 0xFF,
                            bytes[off + 1].toInt() and 0xFF,
                            bytes[off].toInt() and 0xFF))
                    }
                    hexIpsV6.add(sb.toString())
                }
            } catch (_: Exception) { }
        }

        // Scan /proc/net/tcp for IPv4 connections
        if (hexIpsV4.isNotEmpty()) {
            try {
                for (line in java.io.File("/proc/net/tcp").readLines()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 8) continue
                    val remAddr = parts[2].substringBefore(":").uppercase()
                    if (remAddr in hexIpsV4) {
                        val uid = parts[7].toIntOrNull() ?: continue
                        if (uid > 0) return uid
                    }
                }
            } catch (_: Exception) { }
        }

        // Scan /proc/net/tcp6 for IPv6 connections
        if (hexIpsV6.isNotEmpty()) {
            try {
                for (line in java.io.File("/proc/net/tcp6").readLines()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 8) continue
                    val remAddr = parts[2].substringBefore(":").uppercase()
                    if (remAddr in hexIpsV6) {
                        val uid = parts[7].toIntOrNull() ?: continue
                        if (uid > 0) return uid
                    }
                }
            } catch (_: Exception) { }
        }

        return -1
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

    /**
     * Resolve the requesting app from an IPv6 DNS packet.
     * IPv6 header: src at bytes 8..23, dst at 24..39. UDP header starts at byte 40.
     */
    private fun resolveAppV6(p: ByteArray, hdr: Int): Pair<String, String> {
        try {
            val srcPort = (p[hdr].toInt() and 0xFF shl 8) or (p[hdr + 1].toInt() and 0xFF)
            if (srcPort == 0) return "" to ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = getSystemService(ConnectivityManager::class.java) ?: return "" to ""
                val src = InetAddress.getByAddress(p.sliceArray(8 until 24))
                val dst = InetAddress.getByAddress(p.sliceArray(24 until 40))
                val uid = cm.getConnectionOwnerUid(
                    android.system.OsConstants.IPPROTO_UDP,
                    InetSocketAddress(src, srcPort), InetSocketAddress(dst, DNS_PORT)
                )
                if (uid > 0) return resolvePkg(uid)
            }
            // Fallback: /proc/net/udp6 port lookup
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

    // ── VPN Watchdog ──────────────────────────────────────────

    /**
     * Schedule a repeating alarm that fires every 10 minutes to check if
     * the VPN is still alive. OEM battery managers (Samsung Device Care,
     * MIUI Security, EMUI Power Manager, etc.) aggressively kill background
     * services even with battery optimization disabled. NetGuard uses the
     * same pattern with a 10-15 minute interval.
     */
    private fun scheduleWatchdog() {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getService(
                this, WATCHDOG_REQUEST_CODE,
                Intent(this, DnsVpnService::class.java).apply { action = ACTION_WATCHDOG },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
                WATCHDOG_INTERVAL_MS,
                pi
            )
            Log.d(TAG, "Watchdog scheduled (${WATCHDOG_INTERVAL_MS / 60000}min interval)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule watchdog: ${e.message}")
        }
    }

    private fun cancelWatchdog() {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getService(
                this, WATCHDOG_REQUEST_CODE,
                Intent(this, DnsVpnService::class.java).apply { action = ACTION_WATCHDOG },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        } catch (_: Exception) { }
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
