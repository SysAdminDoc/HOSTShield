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
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlockDecision
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.util.Android16VpnRecoveryDetector
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import com.hostshield.util.PrivacyLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong

// VPN DNS blocking service
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

data class VpnRecoveryAdvisory(
    val title: String,
    val message: String,
    val detectedAtMillis: Long
)

@AndroidEntryPoint
class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.hostshield.VPN_START"
        const val ACTION_STOP = "com.hostshield.VPN_STOP"
        const val ACTION_PAUSE = "com.hostshield.VPN_PAUSE"
        const val ACTION_WATCHDOG = "com.hostshield.VPN_WATCHDOG"
        const val CHANNEL_ID = "hostshield_vpn"
        const val ALERT_CHANNEL_ID = "hostshield_alerts"
        const val NOTIFICATION_ID = 1
        private const val TAG = "HostShield"
        private const val WATCHDOG_INTERVAL_MS = 60_000L  // Doze/App Standby heartbeat
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val WATCHDOG_REQUEST_CODE = 99
        private const val WRITE_CHANNEL_CAPACITY = 512

        // Live query stream — hot SharedFlow for real-time log tail in UI.
        // Replays last 100 entries for late subscribers (e.g., screen rotation).
        private val liveQueriesFlow = kotlinx.coroutines.flow.MutableSharedFlow<DnsLogEntry>(
            replay = 100,
            extraBufferCapacity = 200,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        /** Collect this from UI to get real-time DNS query events. */
        val liveQueries: kotlinx.coroutines.flow.SharedFlow<DnsLogEntry> = liveQueriesFlow

        /** DNS cache stats accessor for UI. Returns null if VPN not running. */
        @Volatile var currentCacheStats: DnsCache.CacheStats? = null
            private set

        /** Dropped query count since last flush. */
        @Volatile var currentDroppedQueries: Int = 0
            private set

        /** Clear DNS cache from UI. Safe to call when VPN is not running (no-op). */
        @Volatile var clearCacheCallback: (() -> Unit)? = null
            private set

        /** Live blocked-query count for the current session. Read from QS tile / widgets. */
        @Volatile var currentBlockedCount: Int = 0
            private set

        private val vpnRecoveryAdvisoryState =
            kotlinx.coroutines.flow.MutableStateFlow<VpnRecoveryAdvisory?>(null)
        val vpnRecoveryAdvisory: kotlinx.coroutines.flow.StateFlow<VpnRecoveryAdvisory?> =
            vpnRecoveryAdvisoryState

        fun dismissVpnRecoveryAdvisory() {
            vpnRecoveryAdvisoryState.value = null
        }

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
    @Inject lateinit var firewallRuleDao: com.hostshield.data.database.FirewallRuleDao
    @Inject lateinit var vpnStabilityDao: com.hostshield.data.database.VpnStabilityDao
    @Inject lateinit var dnsDiskCache: DnsDiskCache
    @Inject lateinit var captivePortalHandler: CaptivePortalHandler
    @Inject lateinit var threatIntelManager: ThreatIntelManager
    @Inject lateinit var networkTrackerDb: com.hostshield.util.NetworkTrackerDb
    @Inject lateinit var safeSearchEnforcer: SafeSearchEnforcer
    @Inject lateinit var appDnsRuleEngine: AppDnsRuleEngine
    @Inject lateinit var contentFilterManager: ContentFilterManager
    @Inject lateinit var parentalControlManager: ParentalControlManager
    @Inject lateinit var connectionTracker: ConnectionTracker
    @Inject lateinit var tlsFingerprinter: com.hostshield.util.TlsFingerprinter
    @Inject lateinit var dotResolver: DotResolver
    @Inject lateinit var doqResolver: DoqResolver
    @Inject lateinit var wireGuardProxy: WireGuardProxy
    @Inject lateinit var diagnosticEvents: DiagnosticEventStore
    @Inject lateinit var sourceFailureNotifier: SourceFailureNotifier
    @Inject lateinit var dohBypassUpdater: DohBypassUpdater

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

    @Volatile private var excludedApps = setOf<String>()
    @Volatile private var blockedApps = setOf<String>()
    // Context-aware firewall: maps package_name -> FirewallRule for apps with context rules
    @Volatile private var contextRules = mapOf<String, com.hostshield.data.model.FirewallRule>()
    // DNS transport config — @Volatile because startDnsConfigObserver() updates
    // these live (off the forwarding threads) so provider/upstream changes apply
    // without restarting protection (GitHub issue #1).
    @Volatile private var useDoH = false
    @Volatile private var dohProvider = DohResolver.Provider.CLOUDFLARE
    @Volatile private var useDoT = false
    @Volatile private var dotProvider = DotResolver.Provider.CLOUDFLARE
    @Volatile private var useDoQ = false
    @Volatile private var doqProvider = DoqResolver.Provider.ADGUARD
    private var useWireGuard = false
    private var dnsTrapEnabled = true
    @Volatile private var threatIntelEnabled = false
    @Volatile private var dnsOnlyMode = false
    @Volatile private var safeSearchEnabled = false
    @Volatile private var contentFilterCategories: Set<ContentCategory> = emptySet()
    // Block response: "nxdomain", "zero_ip", "refused"
    private var blockResponseType = "nxdomain"
    private var edeEnabled = false
    // Custom upstream DNS — updated live by startDnsConfigObserver()
    @Volatile private var upstreamDnsServers = UPSTREAM_DNS.toList()
    private var dnsConfigJob: Job? = null

    private var writeChannel = Channel<ByteArray>(WRITE_CHANNEL_CAPACITY)
    private val blockedCount = AtomicInteger(0)
    private val allowedCount = AtomicInteger(0)
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

    // Batch DNS log buffer — flushes every 2s or at 500 entries.
    // Bounded to 5000 entries to prevent OOM under extreme query volume.
    private val logBuffer = java.util.concurrent.LinkedBlockingQueue<DnsLogEntry>(5000)
    @Volatile private var logFlushJob: Job? = null
    private var loggingEnabled = true  // read from prefs at startVpn()

    // DNS Response Cache — LRU with TTL-aware expiration
    private val dnsCache = DnsCache(maxEntries = 2000, maxNegativeEntries = 500)

    // Stats accumulator — AtomicInteger for thread-safe increment from packet thread
    private val pendingBlockedStats = java.util.concurrent.atomic.AtomicInteger(0)
    private val pendingAllowedStats = java.util.concurrent.atomic.AtomicInteger(0)

    // VPN Stability tracking
    private val droppedQueries = java.util.concurrent.atomic.AtomicInteger(0)
    private val totalQueriesCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val fdErrorCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val rebuildCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val tunInboundPacketCount = AtomicLong(0L)
    private var vpnStartTime = 0L
    @Volatile private var stabilityFlushJob: Job? = null
    @Volatile private var vpnRecoveryMonitorJob: Job? = null
    @Volatile private var tunnelHeartbeatJob: Job? = null

    // Pause state: when paused, all queries are allowed (no blocking)
    @Volatile private var isPaused = false
    @Volatile private var pauseResumeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
            ACTION_PAUSE -> {
                val mins = intent.getIntExtra("pause_minutes", 5)
                if (mins <= 0) {
                    // Resume immediately
                    isPaused = false
                    pauseResumeJob?.cancel(); pauseResumeJob = null
                    Log.i(TAG, "Blocking resumed (manual)")
                    updateNotification(blockedCount.get())
                } else {
                    pauseBlocking(mins)
                }
                return START_STICKY
            }
            ACTION_WATCHDOG -> {
                // OEM battery managers (Samsung, Xiaomi, Huawei) kill VPN services.
                // This alarm fires every 10 min to detect and recover.
                if (!isRunning) {
                    serviceScope.launch {
                        val shouldRun = prefs.isEnabled.first()
                        if (shouldRun) {
                            logStructuredVpnEvent("vpn_os_kill", mapOf(
                                "source" to "watchdog",
                                "action" to "restart"
                            ))
                            recordEvent(
                                DiagnosticEventType.DOZE_RESUME,
                                "Watchdog resumed VPN after service was not running",
                                mapOf("source" to "watchdog")
                            )
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
                                recordEvent(
                                    DiagnosticEventType.TUN_FD_INVALID,
                                    "Watchdog detected invalid TUN fd",
                                    mapOf("source" to "watchdog")
                                )
                                restartVpn()
                                return@launch
                            }
                            // Verify upstream connectivity with a quick DNS probe
                            val sock = java.net.DatagramSocket()
                            try {
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
                                Log.d(TAG, "Watchdog: TUN + upstream healthy")
                            } finally {
                                sock.close()
                            }
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
                    ProtectionForegroundServiceTypes.runtimeType()
                )
                serviceScope.launch { startVpn() }
            }
            else -> {
                // Null intent = system restarted us after process death (START_STICKY).
                // Re-promote to foreground and restart the VPN if prefs say we should be on.
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(0),
                    ProtectionForegroundServiceTypes.runtimeType()
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

    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout(startId, 0)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout(startId, fgsType)
    }

    private fun handleForegroundServiceTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout received (startId=$startId, type=$fgsType)")
        diagnosticEvents.recordBlocking(
            DiagnosticEventType.FOREGROUND_SERVICE_TIMEOUT,
            "VPN foreground service timeout",
            mapOf(
                "service" to "DnsVpnService",
                "start_id" to startId,
                "fgs_type" to fgsType,
                "uptime_ms" to if (vpnStartTime > 0) System.currentTimeMillis() - vpnStartTime else 0L
            )
        )
        stopVpn()
    }

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
        cancelTunnelHeartbeat()
        cancelVpnRecoveryMonitor()
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── VPN Lifecycle ─────────────────────────────────────────

    private fun VpnService.Builder.addCanonicalRoute(address: String, prefixLength: Int) {
        val route = VpnRouteCanonicalizer.canonicalize(address, prefixLength)
        addRoute(route.address, route.prefixLength)
    }

    private suspend fun startVpn() {
        if (isRunning) return
        try {
            // Fresh channel for each VPN session (previous may be closed)
            writeChannel = Channel(WRITE_CHANNEL_CAPACITY)
            blockedCount.set(0)
            currentBlockedCount = 0
            allowedCount.set(0)
            tunInboundPacketCount.set(0L)
            vpnRecoveryAdvisoryState.value = null

            excludedApps = prefs.excludedApps.first()
            blockedApps = prefs.blockedApps.first()
            // Load context-aware firewall rules
            val ctxRules = firewallRuleDao.getContextAwareRules().first()
            contextRules = ctxRules.associateBy { it.packageName }
            ContextState.register(this)
            useDoH = prefs.dohEnabled.first()
            dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
            useDoT = prefs.dotEnabled.first()
            dotProvider = DotResolver.Provider.fromId(prefs.dotProvider.first())
            useDoQ = prefs.doqEnabled.first()
            doqProvider = DoqResolver.Provider.fromId(prefs.doqProvider.first())
            useWireGuard = prefs.wireGuardEnabled.first()
            // Initialize WireGuard proxy if enabled
            if (useWireGuard) {
                val wgEndpoint = prefs.wireGuardEndpoint.first()
                val wgPrivKey = prefs.wireGuardPrivateKey.first()
                val wgPubKey = prefs.wireGuardPublicKey.first()
                val wgPsk = prefs.wireGuardPresharedKey.first()
                val wgDnsIp = prefs.wireGuardDnsIp.first()
                if (wgEndpoint.isNotBlank() && wgPrivKey.isNotBlank() && wgPubKey.isNotBlank()) {
                    val parts = wgEndpoint.split(":")
                    val host = parts.getOrElse(0) { "" }
                    val port = parts.getOrElse(1) { "51820" }.toIntOrNull() ?: 51820
                    val config = WireGuardProxy.WgConfig(
                        privateKey = android.util.Base64.decode(wgPrivKey, android.util.Base64.NO_WRAP),
                        peerPublicKey = android.util.Base64.decode(wgPubKey, android.util.Base64.NO_WRAP),
                        presharedKey = if (wgPsk.isBlank()) null else android.util.Base64.decode(wgPsk, android.util.Base64.NO_WRAP),
                        endpoint = "$host:$port",
                        dnsServer = wgDnsIp.ifBlank { "1.1.1.1" }
                    )
                    try {
                        wireGuardProxy.connect(config)
                        PrivacyLog.i(TAG, "WireGuard proxy connected to $host:$port")
                    } catch (e: Exception) {
                        Log.w(TAG, "WireGuard connect failed, disabling: ${e.message}")
                        useWireGuard = false
                    }
                } else {
                    Log.w(TAG, "WireGuard enabled but config incomplete, disabling")
                    useWireGuard = false
                }
            }
            dnsTrapEnabled = prefs.dnsTrapEnabled.first()
            threatIntelEnabled = prefs.threatIntelEnabled.first()
            dnsOnlyMode = prefs.dnsOnlyMode.first()
            safeSearchEnabled = prefs.safeSearchEnabled.first()
            contentFilterCategories = prefs.contentFilterCategories.first()
                .mapNotNull { name ->
                    try { ContentCategory.valueOf(name) } catch (_: Exception) { null }
                }.toSet()
            loggingEnabled = prefs.dnsLogging.first()
            blockResponseType = prefs.blockResponseType.first()
            edeEnabled = prefs.edeEnabled.first()

            // Resolve custom upstream DNS
            val customDns = prefs.getUpstreamDnsList()
            upstreamDnsServers = if (customDns.isNotEmpty()) customDns else UPSTREAM_DNS.toList()

            if (blocklist.domainCount == 0) rebuildBlocklist()

            // v6.0: Warm threat intelligence cache from disk
            try {
                threatIntelManager.loadCached()
            } catch (e: Exception) {
                Log.w(TAG, "Threat intel cache warm failed: ${e.message}")
            }

            // v6.1: Warm per-app DNS rule engine from database
            try {
                appDnsRuleEngine.loadRules()
            } catch (e: Exception) {
                Log.w(TAG, "App DNS rule engine warm failed: ${e.message}")
            }

            // v6.1: Load parental control state
            try {
                parentalControlManager.loadState()
            } catch (e: Exception) {
                Log.w(TAG, "Parental control state load failed: ${e.message}")
            }

            // v5.0: Warm L1 DNS cache from persistent disk cache (L2)
            try {
                val diskEntries = dnsDiskCache.loadAll()
                if (diskEntries.isNotEmpty()) dnsCache.warmFromDisk(diskEntries)
            } catch (e: Exception) {
                Log.w(TAG, "Disk cache warm failed: ${e.message}")
            }

            // Create shutdown pipe for clean Os.poll() exit
            val pipe = Os.pipe()
            shutdownPipeRead = pipe[0]
            shutdownPipeWrite = pipe[1]

            val builder = Builder()
                .setSession("HostShield")
                .setMtu(VPN_MTU)
            // IPv4 + IPv6 dual-stack. Some OEM builds reject the IPv6 ULA
            // address on `addAddress` — skip it gracefully and continue v4-only
            // so the VPN still establishes.
            try {
                builder.addAddress(VPN_ADDRESS, 24)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "addAddress(IPv4) rejected: ${e.message}")
                stopVpn(); return
            }
            try {
                builder.addAddress(VPN_ADDRESS6, 120)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "addAddress(IPv6) rejected by OEM, continuing v4-only: ${e.message}")
            }

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
                    builder.addCanonicalRoute(primary, 32)
                    builder.addCanonicalRoute(secondary, 32)
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
            builder.addCanonicalRoute(VDNS6_PRIMARY, 128)

            // DNS Trap: route well-known public DNS through TUN
            // v6.0: Skip trap routes in DNS-only mode for lower battery (~0.5%)
            if (dnsTrapEnabled && !dnsOnlyMode) {
                for (ip in DNS_TRAP_IPS) {
                    try { builder.addCanonicalRoute(ip, 32) } catch (_: Exception) { }
                }
                // Route known DoH provider IPs too -- we'll drop port 443
                // traffic to these IPs so apps can't bypass DNS filtering
                // via DNS-over-HTTPS to hardcoded resolver IPs.
                for (ip in DOH_BYPASS_IPS) {
                    try { builder.addCanonicalRoute(ip, 32) } catch (_: Exception) { }
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
            vpnStartTime = System.currentTimeMillis()
            networkLost = false
            isRunning = true
            dnsAnswerCache.clear()
            droppedQueries.set(0)
            totalQueriesCount.set(0)
            clearCacheCallback = {
                dnsCache.clear()
                // v5.0: Also clear persistent disk cache
                serviceScope.launch { dnsDiskCache.clear() }
            }
            serviceScope.launch { writeLoop() }
            serviceScope.launch { packetLoop() }
            startLogFlusher()
            startStabilityFlusher()
            registerNetworkCallback()
            scheduleWatchdog()
            startTunnelHeartbeat()
            startVpnRecoveryMonitor()
            startDnsConfigObserver()

            // Captive portal handling
            serviceScope.launch {
                if (prefs.captivePortalHandling.first()) {
                    captivePortalHandler.register()
                }
            }

            PrivacyLog.i(TAG, "VPN started -- ${blocklist.domainCount} domains, " +
                "DoH=${if (useDoH) dohProvider.name else "off"}, " +
                "DoT=${if (useDoT) dotProvider.name else "off"}, " +
                "DoQ=${if (useDoQ) doqProvider.name else "off"}, " +
                "WireGuard=${if (useWireGuard) "on" else "off"}, " +
                "upstream=${upstreamDnsServers.joinToString(",")}, " +
                "vdns=$vdns4Primary/$vdns4Secondary, " +
                "blockResponse=$blockResponseType, " +
                "trap=${dnsTrapEnabled && !dnsOnlyMode} (${DNS_TRAP_IPS.size}+${DOH_BYPASS_IPS.size} IPs), " +
                "dnsOnly=$dnsOnlyMode, " +
                "excluded=${excludedApps.size}, firewalled=${if (dnsOnlyMode) "off(dns-only)" else "${blockedApps.size}"}")
            recordEvent(
                DiagnosticEventType.VPN_START,
                "VPN started",
                mapOf(
                    "domains" to blocklist.domainCount,
                    "doh" to if (useDoH) dohProvider.name else "off",
                    "dot" to if (useDoT) dotProvider.name else "off",
                    "doq" to if (useDoQ) doqProvider.name else "off",
                    "wireguard" to useWireGuard,
                    "dns_only" to dnsOnlyMode,
                    "vdns4" to "$vdns4Primary/$vdns4Secondary"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed: ${e.message}", e); stopVpn()
        }
    }

    private fun stopVpn() {
        val wasRunning = isRunning
        if (wasRunning) {
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.VPN_STOP,
                "VPN stopped",
                mapOf(
                    "uptime_ms" to if (vpnStartTime > 0) System.currentTimeMillis() - vpnStartTime else 0L,
                    "blocked" to blockedCount.get(),
                    "allowed" to allowedCount.get(),
                    "dropped" to droppedQueries.get(),
                    "fd_errors" to fdErrorCount.get()
                )
            )
        }
        captivePortalHandler.unregister()
        isRunning = false
        // Signal the Os.poll() loop to exit by writing to the shutdown pipe
        try { shutdownPipeWrite?.let { Os.write(it, byteArrayOf(1), 0, 1) } } catch (_: Exception) { }
        try { shutdownPipeRead?.let { Os.close(it) } } catch (_: Exception) { }
        try { shutdownPipeWrite?.let { Os.close(it) } } catch (_: Exception) { }
        shutdownPipeRead = null; shutdownPipeWrite = null
        cancelWatchdog()
        cancelTunnelHeartbeat()
        cancelVpnRecoveryMonitor()
        unregisterNetworkCallback()
        dnsConfigJob?.cancel(); dnsConfigJob = null
        logFlushJob?.cancel(); logFlushJob = null
        stabilityFlushJob?.cancel(); stabilityFlushJob = null
        // Flush remaining logs SYNCHRONOUSLY — serviceScope dies with the service,
        // so a launched coroutine would be cancelled before completing.
        // Timeout-guarded to prevent ANR if DB write hangs.
        try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    val pending = logBuffer.size
                    if (pending > 0) Log.i(TAG, "Flushing $pending remaining log entries on stop")
                    flushLogBuffer()
                    flushStats()
                    flushStability()
                } ?: Log.w(TAG, "Final log flush timed out after 3s — some entries may be lost")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Final log flush failed: ${e.message}")
        }
        // Disconnect WireGuard proxy if active
        try { if (useWireGuard) wireGuardProxy.disconnect() } catch (_: Exception) { }
        ContextState.unregister(this)
        dnsAnswerCache.clear()
        dnsCache.clear()
        clearCacheCallback = null
        try { writeChannel.close() } catch (_: Exception) { }
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun restartVpn() {
        if (!isRunning) return
        rebuildCount.incrementAndGet()
        serviceScope.launch {
            Log.i(TAG, "Restarting VPN (network change)")
            isRunning = false
            // Signal poll loop exit
            try { shutdownPipeWrite?.let { Os.write(it, byteArrayOf(1), 0, 1) } } catch (_: Exception) { }
            try { shutdownPipeRead?.let { Os.close(it) } } catch (_: Exception) { }
            try { shutdownPipeWrite?.let { Os.close(it) } } catch (_: Exception) { }
            shutdownPipeRead = null; shutdownPipeWrite = null
            cancelWatchdog()
            cancelTunnelHeartbeat()
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
            val blockSources = repository.getEnabledBlockSources()
            val allowlistSources = repository.getEnabledAllowlistSources()
            val allDomains = mutableSetOf<String>()
            val sourceAllowDomains = mutableSetOf<String>()
            val sourceWildcardBlocks = mutableSetOf<String>()
            val sourceWildcardAllows = mutableSetOf<String>()
            val exactBlockOrigins = mutableMapOf<String, String>()
            val wildcardBlockOrigins = mutableMapOf<String, String>()
            val failedSources = mutableListOf<SourceFailureNotice>()
            for (source in blockSources) {
                // forceDownload=true: must get ALL domains, not just changes.
                // Without this, 304 responses silently drop entire sources.
                downloader.download(source, forceDownload = true).onSuccess { dl ->
                    val parsed = HostsParser.parseForBlocking(dl.content)
                    allDomains.addAll(parsed.blockDomains)
                    parsed.blockDomains.forEach { exactBlockOrigins.putIfAbsent(it, source.label) }
                    sourceAllowDomains.addAll(parsed.allowDomains)
                    sourceWildcardBlocks.addAll(parsed.wildcardBlockDomains)
                    parsed.wildcardBlockDomains.forEach {
                        wildcardBlockOrigins.putIfAbsent(it, source.label)
                    }
                    sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                    repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                }.onFailure { err ->
                    val failures = source.consecutiveFailures + 1
                    val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                    val httpStatus = err.sourceHttpStatus()
                    repository.updateSourceHealth(
                        source.id,
                        health,
                        err.message ?: "Unknown error",
                        failures,
                        httpStatus
                    )
                    failedSources += SourceFailureNotice(
                        label = source.label,
                        url = source.url,
                        error = err.message ?: err.javaClass.simpleName,
                        httpStatus = httpStatus,
                        lastSuccessfulUpdate = source.lastUpdated,
                        consecutiveFailures = failures,
                    )
                    recordEvent(
                        DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                        "Source download failed during VPN blocklist rebuild",
                        mapOf(
                            "source" to source.url,
                            "error" to (err.message ?: err.javaClass.simpleName),
                            "http_status" to httpStatus,
                            "failures" to failures
                        )
                    )
                }
            }
            for (source in allowlistSources) {
                downloader.download(source, forceDownload = true).onSuccess { dl ->
                    val parsed = HostsParser.parseForAllowing(dl.content)
                    sourceAllowDomains.addAll(parsed.allowDomains)
                    sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                    repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                }.onFailure { err ->
                    val failures = source.consecutiveFailures + 1
                    val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                    val httpStatus = err.sourceHttpStatus()
                    repository.updateSourceHealth(
                        source.id,
                        health,
                        err.message ?: "Unknown error",
                        failures,
                        httpStatus
                    )
                    failedSources += SourceFailureNotice(
                        label = source.label,
                        url = source.url,
                        error = err.message ?: err.javaClass.simpleName,
                        httpStatus = httpStatus,
                        lastSuccessfulUpdate = source.lastUpdated,
                        consecutiveFailures = failures,
                    )
                }
            }
            sourceFailureNotifier.notifyFailures(failedSources)
            repository.getEnabledRulesByType(RuleType.BLOCK).filter { !it.isWildcard }
                .forEach {
                    val hostname = it.hostname.lowercase()
                    allDomains.add(hostname)
                    exactBlockOrigins[hostname] = "User block rule"
                }
            repository.getEnabledRulesByType(RuleType.ALLOW).filter { !it.isWildcard }
                .forEach { allDomains.remove(it.hostname.lowercase()) }
            allDomains.removeAll(sourceAllowDomains)
            dohBypassUpdater.mergeCachedInto(
                allDomains,
                sourceWildcardBlocks,
                exactBlockOrigins,
                wildcardBlockOrigins
            )
            blocklist.updateAsync(
                allDomains,
                repository.getEnabledWildcards(),
                sourceWildcardBlocks = sourceWildcardBlocks,
                sourceWildcardAllows = sourceWildcardAllows,
                exactBlockOrigins = exactBlockOrigins,
                sourceWildcardBlockOrigins = wildcardBlockOrigins,
                sourceExactAllows = sourceAllowDomains
            )
            val blockingDomainCount = allDomains.size + sourceWildcardBlocks.size
            recordEvent(
                DiagnosticEventType.BLOCKLIST_SWAP,
                "Blocklist snapshot swapped",
                mapOf("domains" to blockingDomainCount, "source" to "vpn_rebuild")
            )
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
                    tunInboundPacketCount.incrementAndGet()
                    if (vpnRecoveryAdvisoryState.value != null) {
                        vpnRecoveryAdvisoryState.value = null
                    }
                    count++

                    val ipVer = (packet[0].toInt() and 0xF0) shr 4
                    when (ipVer) {
                        4 -> {
                            if (isIpv4UdpDns(packet, length)) processIpv4Dns(packet, length)
                            else if (isIpv4TcpDns(packet, length)) processIpv4TcpDns(packet, length)
                            else tryTlsFingerprint(packet, length)
                            // Drop non-DNS traffic to trapped IPs (DoT port 853,
                            // DoH port 443). The packets simply get absorbed without
                            // forwarding, causing a connection timeout that forces
                            // apps to fall back to standard DNS (which we filter).
                            // No explicit action needed -- not writing a response = drop.
                        }
                        6 -> {
                            if (isIpv6UdpDns(packet, length)) processIpv6Dns(packet, length)
                            else if (isIpv6TcpDns(packet, length)) processIpv6TcpDns(packet, length)
                            else tryTlsFingerprintV6(packet, length)
                        }
                    }

                    if (count <= 3 || count % 1000 == 0L)
                        Log.d(TAG, "Packets: $count ($blockedCount blocked, $allowedCount allowed)")
                }

                // Check for error conditions on TUN fd
                if (pollFds[0].revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP) != 0) {
                    Log.w(TAG, "packetLoop: TUN fd error/hangup")
                    fdErrorCount.incrementAndGet()
                    break
                }
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) continue  // interrupted by signal, retry
                if (!isRunning) break
                fdErrorCount.incrementAndGet()
                Log.e(TAG, "Poll error: ${e.message}")
                delay(10)
            } catch (e: Exception) {
                if (!isRunning) break
                Log.w(TAG, "packetLoop error: ${e.message}")
                delay(10)
            }
        }
        Log.i(TAG, "packetLoop exited after $count packets")
        // Auto-restart on fd error (not clean shutdown)
        if (isRunning) {
            Log.w(TAG, "packetLoop exited unexpectedly while running — restarting VPN")
            restartVpn()
        }
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
        // v6.0: Skip firewall checks in DNS-only mode (no per-app blocking)
        if (!dnsOnlyMode && app.first.isNotEmpty() && app.first in blockedApps) {
            logAsync(domain, true, app, qtype, explicitDecision(
                blocked = true,
                reason = "app_firewall",
                source = "Per-app DNS firewall",
                matchedValue = app.first,
                precedence = "per-app firewall runs before DNS policy"
            ))
            sendBlockResponse(dns, packet, ihl, false, qtype)
            return
        }

        // Context-aware firewall: block based on screen state, foreground, metered
        if (!dnsOnlyMode && app.first.isNotEmpty()) {
            val ctxRule = contextRules[app.first]
            if (ctxRule != null && shouldBlockByContext(ctxRule, app.first)) {
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "context_firewall",
                    source = "Context-aware firewall",
                    matchedValue = app.first,
                    precedence = "context firewall runs before DNS policy"
                ))
                sendBlockResponse(dns, packet, ihl, false, qtype)
                return
            }
        }

        // v6.0: Safe Search enforcement — intercept search engine queries
        if (safeSearchEnabled && safeSearchEnforcer.isSafeSearchDomain(domain)) {
            val safeResp = safeSearchEnforcer.buildSafeResponse(dns, domain)
            if (safeResp != null) {
                PrivacyLog.d(TAG, "SAFE-SEARCH $domain")
                logAsync(domain, false, app, qtype, explicitDecision(
                    blocked = false,
                    reason = "safe_search",
                    source = "Safe Search enforcer",
                    matchedValue = domain,
                    precedence = "safe-search rewrite runs before blocklist lookup"
                ))
                wrapResponseV4(packet, ihl, safeResp)?.let { sendToTun(it) }
                allowedCount.incrementAndGet()
                return
            }
        }

        // v6.1: Per-app domain rules (Roadmap #12)
        if (app.first.isNotEmpty()) {
            val ruleAction = appDnsRuleEngine.checkDomain(app.first, domain)
            if (ruleAction == AppDnsRuleEngine.RuleAction.BLOCK) {
                PrivacyLog.d(TAG, "APP-RULE blocked $domain for ${app.second}")
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "app_rule_block",
                    source = "Per-app DNS rule",
                    matchedValue = app.first,
                    precedence = "per-app block rule runs before shared blocklist"
                ))
                sendBlockResponse(dns, packet, ihl, false, qtype)
                return
            }
            if (ruleAction == AppDnsRuleEngine.RuleAction.ALLOW) {
                // Explicitly allowed by per-app rule — skip blocklist
                PrivacyLog.d(TAG, "APP-RULE allowed $domain for ${app.second}")
                logAsync(domain, false, app, qtype, explicitDecision(
                    blocked = false,
                    reason = "app_rule_allow",
                    source = "Per-app DNS rule",
                    matchedValue = app.first,
                    precedence = "per-app allow rule skips shared blocklist"
                ))
                val pCopy = packet.copyOf()
                serviceScope.launch { forwardEncrypted(dns, domain, pCopy, ihl, app) }
                allowedCount.incrementAndGet()
                return
            }
        }

        // v6.1: Content filter categories (Roadmap #40)
        if (contentFilterCategories.isNotEmpty() && contentFilterManager.isBlocked(domain, contentFilterCategories)) {
            val cat = contentFilterManager.lookupCategory(domain)?.displayName ?: "Unknown"
            PrivacyLog.d(TAG, "CONTENT-FILTER blocked $domain ($qtype) category=$cat")
            logAsyncRich(domain, true, app, qtype,
                trackerCategory = "ContentFilter:$cat",
                decision = explicitDecision(
                    blocked = true,
                    reason = "content_filter",
                    source = cat,
                    matchedValue = domain,
                    precedence = "content category policy runs before shared blocklist"
                ))
            sendBlockResponse(dns, packet, ihl, false, qtype)
            return
        }

        // v6.1: Parental controls — age-profile category blocking (Roadmap #48)
        if (parentalControlManager.shouldBlock(domain)) {
            val cat = contentFilterManager.lookupCategory(domain)?.displayName ?: "Unknown"
            PrivacyLog.d(TAG, "PARENTAL blocked $domain ($qtype) category=$cat profile=${parentalControlManager.currentProfile.name}")
            logAsyncRich(domain, true, app, qtype,
                trackerCategory = "Parental:$cat",
                decision = explicitDecision(
                    blocked = true,
                    reason = "parental_control",
                    source = parentalControlManager.currentProfile.name,
                    matchedValue = cat,
                    precedence = "parental profile runs before shared blocklist"
                ))
            sendBlockResponse(dns, packet, ihl, false, qtype)
            return
        }

        val blockDecision = domainDecision(domain)
        val blocked = blockDecision.blocked

        // v6.0: Threat intelligence domain check
        if (!blocked && threatIntelEnabled) {
            val threat = threatIntelManager.isDomainMalicious(domain)
            if (threat != null) {
                PrivacyLog.i(TAG, "THREAT-INTEL blocked domain: $domain (${threat.feedName})")
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "threat_intel_domain",
                    source = threat.feedName,
                    matchedValue = domain,
                    precedence = "threat intel runs after blocklist miss"
                ))
                sendBlockResponse(dns, packet, ihl, false, qtype)
                return
            }
        }

        if (blocked) {
            logAsync(domain, true, app, qtype, blockDecision)
            PrivacyLog.d(TAG, "BLOCKED $domain ($qtype) [${app.second.ifEmpty { "system" }}]")
            sendBlockResponse(dns, packet, ihl, false, qtype)
        } else {
            // Cache lookup — serve from cache if available (v5.0: CacheResult with stale/prefetch)
            val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
            val cacheResult = dnsCache.get(domain, qtypeNum, txId)
            if (cacheResult != null) {
                if (!cacheResult.isStale) {
                    // Fresh cache hit
                    PrivacyLog.d(TAG, "CACHE HIT $domain ($qtype)")
                    logAsync(domain, false, app, qtype)
                    wrapResponseV4(packet, ihl, cacheResult.response)?.let { sendToTun(it) }
                    allowedCount.incrementAndGet()
                    // v5.0: Trigger background prefetch if entry is nearing expiry
                    if (cacheResult.needsPrefetch) {
                        val pCopy = packet.copyOf(length)
                        serviceScope.launch {
                            try {
                                forwardEncrypted(dns, domain, pCopy, ihl, app)
                            } catch (_: Exception) { /* prefetch failure is non-fatal */ }
                        }
                    }
                    return
                } else {
                    // v5.0: Stale entry — serve immediately per RFC 8767, re-query in background
                    PrivacyLog.d(TAG, "SERVE-STALE $domain ($qtype) — refreshing in background")
                    wrapResponseV4(packet, ihl, cacheResult.response)?.let { sendToTun(it) }
                    allowedCount.incrementAndGet()
                    // Background refresh to update the cache
                    val pCopy = packet.copyOf(length)
                    serviceScope.launch {
                        try {
                            forwardEncrypted(dns, domain, pCopy, ihl, app)
                        } catch (_: Exception) { /* refresh failure is non-fatal, stale was already served */ }
                    }
                    return
                }
            }

            // Cache miss — forward to upstream
            PrivacyLog.d(TAG, "ALLOWED $domain ($qtype)")
            val pCopy = packet.copyOf(length)
            serviceScope.launch { forwardEncrypted(dns, domain, pCopy, ihl, app) }
            allowedCount.incrementAndGet()
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
        // v6.0: Skip firewall checks in DNS-only mode
        if (!dnsOnlyMode && app.first.isNotEmpty() && app.first in blockedApps) {
            logAsync(domain, true, app, qtype, explicitDecision(
                blocked = true,
                reason = "app_firewall",
                source = "Per-app DNS firewall",
                matchedValue = app.first,
                precedence = "per-app firewall runs before DNS policy"
            ))
            val resp = buildBlockResponse(dns, qtype) ?: return
            val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
            sendToTun(wrapped); blockedCount.incrementAndGet()
            if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
            return
        }

        // Context-aware firewall
        if (!dnsOnlyMode && app.first.isNotEmpty()) {
            val ctxRule = contextRules[app.first]
            if (ctxRule != null && shouldBlockByContext(ctxRule, app.first)) {
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "context_firewall",
                    source = "Context-aware firewall",
                    matchedValue = app.first,
                    precedence = "context firewall runs before DNS policy"
                ))
                val resp = buildBlockResponse(dns, qtype) ?: return
                val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
                sendToTun(wrapped); blockedCount.incrementAndGet()
                if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
                return
            }
        }

        // v6.0: Safe Search enforcement (IPv6)
        if (safeSearchEnabled && safeSearchEnforcer.isSafeSearchDomain(domain)) {
            val safeResp = safeSearchEnforcer.buildSafeResponse(dns, domain)
            if (safeResp != null) {
                PrivacyLog.d(TAG, "SAFE-SEARCH (v6) $domain")
                logAsync(domain, false, app, qtype, explicitDecision(
                    blocked = false,
                    reason = "safe_search",
                    source = "Safe Search enforcer",
                    matchedValue = domain,
                    precedence = "safe-search rewrite runs before blocklist lookup"
                ))
                wrapResponseV6(packet, hdr, safeResp)?.let { sendToTun(it) }
                allowedCount.incrementAndGet()
                return
            }
        }

        // v6.1: Per-app domain rules (Roadmap #12)
        if (app.first.isNotEmpty()) {
            val ruleAction = appDnsRuleEngine.checkDomain(app.first, domain)
            if (ruleAction == AppDnsRuleEngine.RuleAction.BLOCK) {
                PrivacyLog.d(TAG, "APP-RULE blocked (v6) $domain for ${app.second}")
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "app_rule_block",
                    source = "Per-app DNS rule",
                    matchedValue = app.first,
                    precedence = "per-app block rule runs before shared blocklist"
                ))
                val resp = buildBlockResponse(dns, qtype) ?: return
                val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
                sendToTun(wrapped); blockedCount.incrementAndGet()
                if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
                return
            }
            if (ruleAction == AppDnsRuleEngine.RuleAction.ALLOW) {
                PrivacyLog.d(TAG, "APP-RULE allowed (v6) $domain for ${app.second}")
                logAsync(domain, false, app, qtype, explicitDecision(
                    blocked = false,
                    reason = "app_rule_allow",
                    source = "Per-app DNS rule",
                    matchedValue = app.first,
                    precedence = "per-app allow rule skips shared blocklist"
                ))
                val pCopy = packet.copyOf(length)
                serviceScope.launch { forwardEncrypted(dns, domain, pCopy, 0, app, wrapV6 = true, v6Hdr = hdr) }
                allowedCount.incrementAndGet()
                return
            }
        }

        // v6.1: Content filter categories (Roadmap #40)
        if (contentFilterCategories.isNotEmpty() && contentFilterManager.isBlocked(domain, contentFilterCategories)) {
            val cat = contentFilterManager.lookupCategory(domain)?.displayName ?: "Unknown"
            PrivacyLog.d(TAG, "CONTENT-FILTER blocked (v6) $domain ($qtype) category=$cat")
            logAsyncRich(domain, true, app, qtype,
                trackerCategory = "ContentFilter:$cat",
                decision = explicitDecision(
                    blocked = true,
                    reason = "content_filter",
                    source = cat,
                    matchedValue = domain,
                    precedence = "content category policy runs before shared blocklist"
                ))
            val resp = buildBlockResponse(dns, qtype) ?: return
            val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
            sendToTun(wrapped); blockedCount.incrementAndGet()
            if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
            return
        }

        // v6.1: Parental controls — age-profile category blocking (Roadmap #48)
        if (parentalControlManager.shouldBlock(domain)) {
            val cat = contentFilterManager.lookupCategory(domain)?.displayName ?: "Unknown"
            PrivacyLog.d(TAG, "PARENTAL blocked (v6) $domain ($qtype) category=$cat profile=${parentalControlManager.currentProfile.name}")
            logAsyncRich(domain, true, app, qtype,
                trackerCategory = "Parental:$cat",
                decision = explicitDecision(
                    blocked = true,
                    reason = "parental_control",
                    source = parentalControlManager.currentProfile.name,
                    matchedValue = cat,
                    precedence = "parental profile runs before shared blocklist"
                ))
            val resp = buildBlockResponse(dns, qtype) ?: return
            val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
            sendToTun(wrapped); blockedCount.incrementAndGet()
            if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
            return
        }

        val blockDecision = domainDecision(domain)
        val blocked = blockDecision.blocked

        // v6.0: Threat intelligence domain check
        if (!blocked && threatIntelEnabled) {
            val threat = threatIntelManager.isDomainMalicious(domain)
            if (threat != null) {
                PrivacyLog.i(TAG, "THREAT-INTEL blocked domain (v6): $domain (${threat.feedName})")
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "threat_intel_domain",
                    source = threat.feedName,
                    matchedValue = domain,
                    precedence = "threat intel runs after blocklist miss"
                ))
                val resp = buildBlockResponse(dns, qtype) ?: return
                val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
                sendToTun(wrapped); blockedCount.incrementAndGet()
                if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
                return
            }
        }

        if (blocked) {
            logAsync(domain, true, app, qtype, blockDecision)
            val resp = buildBlockResponse(dns, qtype) ?: return
            val wrapped = wrapResponseV6(packet, hdr, resp) ?: return
            sendToTun(wrapped); blockedCount.incrementAndGet()
            if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
        } else {
            // Cache lookup (v5.0: CacheResult with stale/prefetch)
            val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
            val cacheResult = dnsCache.get(domain, qtypeNum, txId)
            if (cacheResult != null) {
                if (!cacheResult.isStale) {
                    // Fresh cache hit
                    PrivacyLog.d(TAG, "CACHE HIT (v6) $domain ($qtype)")
                    wrapResponseV6(packet, hdr, cacheResult.response)?.let { sendToTun(it) }
                    allowedCount.incrementAndGet()
                    if (cacheResult.needsPrefetch) {
                        val pCopy = packet.copyOf(length)
                        serviceScope.launch {
                            try {
                                forwardEncrypted(dns, domain, pCopy, 0, app, wrapV6 = true, v6Hdr = hdr)
                            } catch (_: Exception) { }
                        }
                    }
                    return
                } else {
                    // v5.0: Serve stale immediately, refresh in background (RFC 8767)
                    PrivacyLog.d(TAG, "SERVE-STALE (v6) $domain ($qtype) — refreshing in background")
                    wrapResponseV6(packet, hdr, cacheResult.response)?.let { sendToTun(it) }
                    allowedCount.incrementAndGet()
                    val pCopy = packet.copyOf(length)
                    serviceScope.launch {
                        try {
                            forwardEncrypted(dns, domain, pCopy, 0, app, wrapV6 = true, v6Hdr = hdr)
                        } catch (_: Exception) { }
                    }
                    return
                }
            }

            // Cache miss
            val pCopy = packet.copyOf(length)
            serviceScope.launch { forwardEncrypted(dns, domain, pCopy, 0, app, wrapV6 = true, v6Hdr = hdr) }
            allowedCount.incrementAndGet()
        }
    }

    /**
     * Send a block response (NXDOMAIN, 0.0.0.0/::, or REFUSED) for an IPv4 packet.
     * The response type is controlled by the blockResponseType preference.
     */
    private suspend fun sendBlockResponse(dns: ByteArray, packet: ByteArray, ihl: Int, isV6: Boolean, qtype: String) {
        val resp = buildBlockResponse(dns, qtype) ?: return
        val wrapped = wrapResponseV4(packet, ihl, resp) ?: return
        sendToTun(wrapped); blockedCount.incrementAndGet()
        if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
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
        val edeCode = if (edeEnabled) DnsPacketBuilder.EDE_BLOCKED else -1
        return DnsPacketBuilder.buildBlockResponse(dns, blockResponseType, edeCode)
    }

    private fun logAsync(
        domain: String,
        blocked: Boolean,
        app: Pair<String, String>,
        qtype: String,
        decision: BlockDecision? = null
    ) {
        logAsyncRich(domain, blocked, app, qtype, decision = decision)
    }

    /** Rich log entry with CNAME chain, resolved IPs, latency, and upstream server. */
    private fun logAsyncRich(
        domain: String, blocked: Boolean, app: Pair<String, String>, qtype: String,
        cnameChain: String = "", resolvedIps: String = "",
        responseTimeMs: Int = 0, upstreamServer: String = "",
        trackerCategory: String = "", trackerOwner: String = "",
        decision: BlockDecision? = null
    ) {
        // Always count stats even if logging disabled
        if (blocked) pendingBlockedStats.incrementAndGet() else pendingAllowedStats.incrementAndGet()

        // v6.0: Network-based tracker detection — enrich log with tracker info
        var tCat = trackerCategory
        var tOwner = trackerOwner
        if (tCat.isEmpty()) {
            val tracker = networkTrackerDb.lookup(domain)
            if (tracker != null) {
                tCat = tracker.category
                tOwner = tracker.owner
            }
        }

        val entry = DnsLogEntry(
            hostname = domain, blocked = blocked,
            appPackage = app.first, appLabel = app.second, queryType = qtype,
            cnameChain = cnameChain, resolvedIps = resolvedIps,
            responseTimeMs = responseTimeMs, upstreamServer = upstreamServer,
            trackerCategory = tCat, trackerOwner = tOwner,
            decisionReason = decision?.reason.orEmpty(),
            decisionSource = decision?.source.orEmpty(),
            matchedValue = decision?.matchedValue.orEmpty(),
            decisionPrecedence = decision?.precedence.orEmpty()
        )

        // v6.2: Track connection in ring buffer for per-app analytics
        connectionTracker.recordConnection(
            packageName = app.first,
            appLabel = app.second,
            domain = domain,
            queryType = qtype,
            resolvedIps = resolvedIps.split(",").filter { it.isNotBlank() },
            blocked = blocked,
            responseTimeMs = responseTimeMs,
            upstreamServer = upstreamServer,
        )

        // Emit to live query stream (non-blocking, drops oldest if full)
        liveQueriesFlow.tryEmit(entry)
        totalQueriesCount.incrementAndGet()

        if (!loggingEnabled) return
        if (!logBuffer.offer(entry)) {
            // Buffer full — drop entry and track it
            droppedQueries.incrementAndGet()
        }
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
            var diskPersistCounter = 0
            while (isActive) {
                delay(2000)
                try {
                    val bufSize = logBuffer.size
                    if (bufSize > 0) {
                        flushLogBuffer()
                        Log.d(TAG, "Flushed $bufSize log entries to DB")
                    }
                    flushStats()
                    // Publish cache stats + dropped count for UI
                    currentCacheStats = dnsCache.getStats()
                    currentDroppedQueries = droppedQueries.get()

                    // v5.0: Persist DNS cache to disk every ~60s (30 cycles * 2s)
                    diskPersistCounter++
                    if (diskPersistCounter >= 30) {
                        diskPersistCounter = 0
                        val entries = dnsCache.exportForDisk()
                        if (entries.isNotEmpty()) {
                            dnsDiskCache.persistBatch(entries)
                        }
                    }
                } catch (e: Exception) {
                    // Catch per-cycle so the flusher never dies permanently
                    Log.e(TAG, "Log flush cycle error: ${e.message}", e)
                }
            }
            Log.w(TAG, "Log flusher stopped (isActive=false)")
        }
    }

    /** Flush VPN stability metrics to Room. */
    private suspend fun flushStability() {
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val uptimeMs = if (vpnStartTime > 0) System.currentTimeMillis() - vpnStartTime else 0L
            val dropped = droppedQueries.getAndSet(0)
            val queries = totalQueriesCount.getAndSet(0)
            val rebuilds = rebuildCount.getAndSet(0)
            val errors = fdErrorCount.getAndSet(0)

            val existing = vpnStabilityDao.getByDate(today)
                ?: com.hostshield.data.model.VpnStabilityEntry(date = today)
            vpnStabilityDao.upsert(existing.copy(
                uptimeMs = existing.uptimeMs + uptimeMs,
                rebuildCount = existing.rebuildCount + rebuilds,
                fdErrors = existing.fdErrors + errors,
                droppedQueries = existing.droppedQueries + dropped,
                totalQueries = existing.totalQueries + queries
            ))

            if (dropped > 0) {
                Log.w(TAG, "VPN stability: $dropped queries dropped (buffer overflow)")
            }

            // Reset start time for next interval
            vpnStartTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Stability flush failed: ${e.message}")
        }
    }

    /** Periodic stability flusher — every 60 seconds. */
    private fun startStabilityFlusher() {
        stabilityFlushJob?.cancel()
        stabilityFlushJob = serviceScope.launch {
            while (isActive) {
                delay(60_000)
                try { flushStability() } catch (_: Exception) { }
            }
        }
    }

    private fun sendToTun(packet: ByteArray) {
        val result = writeChannel.trySend(packet)
        if (result.isFailure) droppedQueries.incrementAndGet()
    }

    private suspend fun writeLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface ?: return@withContext
        FileOutputStream(vpnFd.fileDescriptor).use { output ->
            for (packet in writeChannel) {
                if (!isRunning) break
                try { output.write(packet) } catch (_: Exception) { if (!isRunning) break }
            }
        }
    }

    // ── Domain Blocking ──────────────────────────────────────

    /**
     * Trie-based O(m) domain lookup via BlocklistHolder.
     * Handles exact match, www. prefix, wildcard allow/block.
     * Replaces the old linear Set.contains() + wildcard scan.
     */
    private fun domainDecision(domain: String): BlockDecision {
        if (isPaused) {
            return BlockDecision(
                blocked = false,
                reason = "protection_paused",
                precedence = "pause state bypasses blocklist lookup"
            )
        }
        return blocklist.decide(domain)
    }

    private fun explicitDecision(
        blocked: Boolean,
        reason: String,
        source: String = "",
        matchedValue: String = "",
        precedence: String = ""
    ): BlockDecision = BlockDecision(blocked, reason, source, matchedValue, precedence)

    private fun isDomainBlocked(domain: String): Boolean {
        return domainDecision(domain).blocked
    }

    // ── Packet Parsing (delegated to PacketClassifier & DnsPacketParser) ───

    private fun isIpv4UdpDns(p: ByteArray, len: Int) = PacketClassifier.isIpv4UdpDns(p, len)
    private fun isIpv6UdpDns(p: ByteArray, len: Int) = PacketClassifier.isIpv6UdpDns(p, len)
    private fun isIpv6TcpDns(p: ByteArray, len: Int) = PacketClassifier.isIpv6TcpDns(p, len)

    // ── TCP DNS (RFC 7766) ───────────────────────────────────

    private fun isIpv4TcpDns(p: ByteArray, len: Int) = PacketClassifier.isIpv4TcpDns(p, len)

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
            val dnsLen = ((packet[payloadStart].toInt() and 0xFF) shl 8) or
                (packet[payloadStart + 1].toInt() and 0xFF)
            if (dnsLen in 12..4096 && payloadStart + 2 + dnsLen <= length) {
                val dns = packet.copyOfRange(payloadStart + 2, payloadStart + 2 + dnsLen)
                hostname = parseDnsQueryDomain(dns)
            }
        }

        val blocked = if (hostname != null) isDomainBlocked(hostname) else true // block unknown

        if (blocked) {
            // Send TCP RST — immediate connection rejection
            val rst = buildTcpRst(packet, ihl) ?: return
            sendToTun(rst)
            blockedCount.incrementAndGet()
            if (hostname != null) {
                PrivacyLog.d(TAG, "TCP-DNS BLOCKED (RST) $hostname")
                logAsync(hostname, true, "" to "", "TCP")
            }
        } else {
            // Allowed but we can't fully proxy TCP DNS without state tracking.
            // Drop the packet — app will timeout and retry with UDP per RFC 7766.
            if (hostname != null) {
                PrivacyLog.d(TAG, "TCP-DNS allowed (drop→UDP fallback) $hostname")
            }
        }
    }

    /** Handle IPv6 TCP DNS packets — mirrors processIpv4TcpDns logic. */
    private suspend fun processIpv6TcpDns(packet: ByteArray, length: Int) {
        val hdr = 40 // IPv6 fixed header
        val tcpOff = hdr
        if (length < tcpOff + 20) return

        val dataOff = ((packet[tcpOff + 12].toInt() and 0xF0) shr 4) * 4
        val tcpFlags = packet[tcpOff + 13].toInt() and 0xFF
        val isRst = (tcpFlags and 0x04) != 0
        if (isRst) return

        val payloadStart = tcpOff + dataOff
        val payloadLen = length - payloadStart

        var hostname: String? = null
        if (payloadLen > 14) {
            val dnsLen = ((packet[payloadStart].toInt() and 0xFF) shl 8) or
                (packet[payloadStart + 1].toInt() and 0xFF)
            if (dnsLen in 12..4096 && payloadStart + 2 + dnsLen <= length) {
                val dns = packet.copyOfRange(payloadStart + 2, payloadStart + 2 + dnsLen)
                hostname = parseDnsQueryDomain(dns)
            }
        }

        val blocked = if (hostname != null) isDomainBlocked(hostname) else true

        if (blocked) {
            val rst = buildTcpRstV6(packet) ?: return
            sendToTun(rst)
            blockedCount.incrementAndGet()
            if (hostname != null) {
                PrivacyLog.d(TAG, "TCP6-DNS BLOCKED (RST) $hostname")
                logAsync(hostname, true, "" to "", "TCP")
            }
        } else {
            if (hostname != null) PrivacyLog.d(TAG, "TCP6-DNS allowed (drop) $hostname")
        }
    }

    // ── TLS Fingerprinting (v6.2) ──────────────────────────────

    /**
     * Attempt TLS ClientHello fingerprinting on non-DNS TCP packets.
     * Extracts JA3/JA4 hashes for protocol-level app identification.
     */
    private fun tryTlsFingerprint(packet: ByteArray, length: Int) {
        if (length < 60) return // too small for IP + TCP + TLS
        val ihl = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6) return // not TCP
        val tcpOff = ihl
        if (length < tcpOff + 20) return
        val dataOff = ((packet[tcpOff + 12].toInt() and 0xF0) shr 4) * 4
        val payloadStart = tcpOff + dataOff
        val payloadLen = length - payloadStart
        if (payloadLen < 6) return
        // Quick pre-filter: is this a TLS ClientHello?
        if (!tlsFingerprinter.isClientHello(packet, payloadStart, payloadLen)) return
        val fp = tlsFingerprinter.fingerprint(packet, payloadStart, payloadLen)
        if (fp != null) {
            val app = resolveApp(packet, ihl)
            tlsFingerprinter.record(app.first, app.second, fp)
            PrivacyLog.d(TAG, "TLS-FP ${app.second.ifEmpty { "unknown" }}: JA3=${fp.ja3} JA4=${fp.ja4} SNI=${fp.sni ?: "-"} identity=${fp.knownIdentity ?: "-"}")
        }
    }

    /**
     * TLS fingerprinting for IPv6 non-DNS TCP packets.
     * IPv6 header is 40 bytes fixed, next header at byte 6.
     */
    private fun tryTlsFingerprintV6(packet: ByteArray, length: Int) {
        if (length < 80) return // too small for IPv6(40) + TCP(20) + TLS
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != 6) return // not TCP
        val tcpOff = 40
        if (length < tcpOff + 20) return
        val dataOff = ((packet[tcpOff + 12].toInt() and 0xF0) shr 4) * 4
        val payloadStart = tcpOff + dataOff
        val payloadLen = length - payloadStart
        if (payloadLen < 6) return
        if (!tlsFingerprinter.isClientHello(packet, payloadStart, payloadLen)) return
        val fp = tlsFingerprinter.fingerprint(packet, payloadStart, payloadLen)
        if (fp != null) {
            val app = resolveAppV6(packet, 40)
            tlsFingerprinter.record(app.first, app.second, fp)
            PrivacyLog.d(TAG, "TLS-FP6 ${app.second.ifEmpty { "unknown" }}: JA3=${fp.ja3} JA4=${fp.ja4} SNI=${fp.sni ?: "-"}")
        }
    }

    // ── TCP RST Building (delegated to TcpRstBuilder) ──────

    private fun buildTcpRstV6(orig: ByteArray) = TcpRstBuilder.buildTcpRstV6(orig)
    private fun buildTcpRst(orig: ByteArray, ihl: Int) = TcpRstBuilder.buildTcpRst(orig, ihl)

    /** Check if a context-aware firewall rule should block this app right now. */
    private fun shouldBlockByContext(rule: com.hostshield.data.model.FirewallRule, pkg: String): Boolean {
        if (rule.blockScreenOff && !ContextState.isScreenOn) return true
        if (rule.blockBackground && ContextState.foregroundPackage != pkg) return true
        if (rule.blockMetered && ContextState.isMetered) return true
        return false
    }

    // ── DNS Parsing & Response (delegated to DnsPacketParser) ──

    private fun extractDnsPayload(p: ByteArray, len: Int, ihl: Int) = DnsPacketParser.extractDnsPayload(p, len, ihl)
    private fun extractDnsPayloadV6(p: ByteArray, len: Int, hdr: Int) = DnsPacketParser.extractDnsPayloadV6(p, len, hdr)
    private fun parseDnsQueryDomain(dns: ByteArray) = DnsPacketParser.parseDnsQueryDomain(dns)
    private fun parseDnsQueryType(dns: ByteArray) = DnsPacketParser.parseDnsQueryType(dns)
    private fun wrapResponseV4(orig: ByteArray, ihl: Int, dns: ByteArray) = DnsPacketParser.wrapResponseV4(orig, ihl, dns)
    private fun wrapResponseV6(orig: ByteArray, hdr: Int, dns: ByteArray) = DnsPacketParser.wrapResponseV6(orig, hdr, dns)

    // ── DNS Forwarding ───────────────────────────────────────

    /**
     * Result of [postForwardChecks]: either the response was blocked (CNAME cloak or
     * threat-intel) or it is clean and should be forwarded to the client.
     *
     * @property blocked `true` when the response triggered a block rule.
     * @property blockResponse the DNS block-response bytes to send back, or `null` when
     *           [buildBlockResponse] returned `null` (caller should skip sending).
     */
    private data class PostForwardResult(val blocked: Boolean, val blockResponse: ByteArray? = null)

    /**
     * Shared post-forward checks: CNAME cloaking detection + threat intelligence IP lookup.
     *
     * When the response is blocked, [PostForwardResult.blocked] is `true` and
     * [PostForwardResult.blockResponse] contains the DNS block-response bytes (may be
     * `null` if [buildBlockResponse] failed).  Logging, counter increments, and DNS
     * caching are handled internally.
     */
    private suspend fun postForwardChecks(
        respBytes: ByteArray,
        dns: ByteArray,
        domain: String,
        app: Pair<String, String>,
        latencyMs: Int,
        upstreamServer: String
    ): PostForwardResult {
        val qtype = parseDnsQueryType(dns)

        // 1. CNAME cloaking detection — block if any CNAME target is in blocklist
        val cnameResult = CnameCloakDetector.inspect(respBytes, blocklist)
        if (cnameResult.blocked) {
            PrivacyLog.i(TAG, "CNAME CLOAK blocked: $domain -> ${cnameResult.blockedCname}")
            logAsyncRich(domain, true, app, qtype,
                cnameChain = cnameResult.cnameChain.joinToString(","),
                responseTimeMs = latencyMs, upstreamServer = upstreamServer,
                decision = explicitDecision(
                    blocked = true,
                    reason = "cname_cloak",
                    source = "CNAME cloak detector",
                    matchedValue = cnameResult.blockedCname.orEmpty(),
                    precedence = "post-forward CNAME target check"
                ))
            val blockResp = buildBlockResponse(dns, DnsPacketBuilder.parseQueryType(dns).let {
                when (it) { 1 -> "A"; 28 -> "AAAA"; else -> "A" }
            })
            blockedCount.incrementAndGet()
            return PostForwardResult(blocked = true, blockResponse = blockResp)
        }

        // 2. Threat intelligence IP check
        val resolvedIps = CnameCloakDetector.extractAnswerIps(respBytes)
        if (threatIntelEnabled) {
            for (ip in resolvedIps) {
                val threat = threatIntelManager.isIpMalicious(ip)
                if (threat != null) {
                    PrivacyLog.i(TAG, "THREAT-INTEL blocked IP: $ip for $domain (${threat.feedName})")
                    logAsyncRich(domain, true, app, qtype,
                        resolvedIps = resolvedIps.joinToString(","),
                        responseTimeMs = latencyMs, upstreamServer = upstreamServer,
                        decision = explicitDecision(
                            blocked = true,
                            reason = "threat_intel_ip",
                            source = threat.feedName,
                            matchedValue = ip,
                            precedence = "post-forward resolved IP threat check"
                        ))
                    val blockResp = buildBlockResponse(dns, DnsPacketBuilder.parseQueryType(dns).let {
                        when (it) { 1 -> "A"; 28 -> "AAAA"; else -> "A" }
                    })
                    blockedCount.incrementAndGet()
                    return PostForwardResult(blocked = true, blockResponse = blockResp)
                }
            }
        }

        // 3. Response is clean — log, cache, and let caller forward it
        logAsyncRich(domain, false, app, qtype,
            cnameChain = cnameResult.cnameChain.joinToString(","),
            resolvedIps = resolvedIps.joinToString(","),
            responseTimeMs = latencyMs, upstreamServer = upstreamServer,
            decision = explicitDecision(
                blocked = false,
                reason = "upstream",
                source = upstreamServer,
                matchedValue = domain,
                precedence = "no local block decision matched before upstream response"
            ))

        cacheDnsAnswerIps(domain, respBytes)
        dnsCache.put(domain, DnsPacketBuilder.parseQueryType(dns), respBytes)

        return PostForwardResult(blocked = false)
    }

    private suspend fun forwardUdp(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                    app: Pair<String, String> = Pair("", "")) {
        val sock = DatagramSocket()
        try {
            val startMs = System.currentTimeMillis()
            protect(sock)
            sock.soTimeout = 5000
            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            try {
                sock.receive(rp)
                var respBytes = buf.copyOf(rp.length)

                // RFC 7766 §6.2: when the UDP response has the TC (truncated) bit
                // set, retry the same query over TCP and substitute the response.
                // Many large DNSSEC RRSIG / TXT records can't fit in the 1500-byte
                // UDP buffer.
                respBytes = retryTruncatedUdpOverTcp(dns, respBytes, primary)

                val latencyMs = (System.currentTimeMillis() - startMs).toInt()

                val pfResult = postForwardChecks(respBytes, dns, domain, app, latencyMs, primary)
                if (pfResult.blocked) {
                    if (pfResult.blockResponse != null) wrapResponseV4(orig, ihl, pfResult.blockResponse)?.let { sendToTun(it) }
                    return
                }

                wrapResponseV4(orig, ihl, respBytes)?.let { sendToTun(it) }
            } catch (_: java.net.SocketTimeoutException) {
                forwardUdpFallback(dns, domain, orig, ihl, app)
            }
        } catch (_: Exception) {
            // v5.0: Serve-stale fallback — return expired cache entry if upstream completely fails
            serveStaleV4(dns, domain, orig, ihl)
        } finally {
            try { sock.close() } catch (_: Exception) { }
        }
    }

    private fun retryTruncatedUdpOverTcp(
        dns: ByteArray,
        udpResponse: ByteArray,
        upstream: String
    ): ByteArray {
        val udpReceivedAtMs = DnsTcpFallback.monotonicNowMs()
        val result = DnsTcpFallback.resolveTruncatedUdpResponse(
            udpResponse = udpResponse,
            udpReceivedAtMs = udpReceivedAtMs
        ) {
            forwardOverTcp(dns, upstream)
        }
        if (result.retriedOverTcp && !result.retryStartedWithinDeadline) {
            Log.w(
                TAG,
                "TCP DNS fallback for TC=1 started after ${result.retryStartDelayMs}ms " +
                    "(expected <= ${DnsTcpFallback.MAX_TCP_RETRY_START_DELAY_MS}ms)"
            )
        }
        return result.response
    }

    /**
     * RFC 7766 TCP DNS fallback: 2-byte length prefix + DNS message, both ways.
     * Used when an upstream UDP response has TC=1. Returns null on failure so
     * the caller keeps the original truncated UDP response (which is still a
     * legitimate, parseable answer — just incomplete).
     */
    private fun forwardOverTcp(dns: ByteArray, upstream: String): ByteArray? {
        val sock = java.net.Socket()
        try {
            protect(sock)
            sock.connect(InetSocketAddress(InetAddress.getByName(upstream), DNS_PORT), 3000)
            sock.soTimeout = 4000
            val out = java.io.DataOutputStream(sock.getOutputStream())
            val input = java.io.DataInputStream(sock.getInputStream())
            out.writeShort(dns.size)
            out.write(dns)
            out.flush()
            val respLen = input.readUnsignedShort()
            if (respLen < 12 || respLen > 65535) return null
            val resp = ByteArray(respLen)
            input.readFully(resp)
            return resp
        } catch (_: Exception) {
            return null
        } finally {
            try { sock.close() } catch (_: Exception) { }
        }
    }

    private suspend fun forwardUdpFallback(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                           app: Pair<String, String> = Pair("", "")) {
        val sock = DatagramSocket()
        try {
            val startMs = System.currentTimeMillis()
            val fallback = upstreamDnsServers.getOrElse(1) { UPSTREAM_DNS[1] }
            protect(sock); sock.soTimeout = 5000
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(fallback), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            sock.receive(rp)
            val respBytes = retryTruncatedUdpOverTcp(dns, buf.copyOf(rp.length), fallback)
            val latencyMs = (System.currentTimeMillis() - startMs).toInt()

            val pfResult = postForwardChecks(respBytes, dns, domain, app, latencyMs, "$fallback (fallback)")
            if (pfResult.blocked) {
                if (pfResult.blockResponse != null) wrapResponseV4(orig, ihl, pfResult.blockResponse)?.let { sendToTun(it) }
                return
            }

            wrapResponseV4(orig, ihl, respBytes)?.let { sendToTun(it) }
        } catch (_: Exception) {
            // v5.0: Serve-stale fallback — both upstreams failed, try expired cache
            serveStaleV4(dns, domain, orig, ihl)
        } finally {
            try { sock.close() } catch (_: Exception) { }
        }
    }

    /**
     * v5.0: Serve-stale (RFC 8767) — return an expired-but-still-cached DNS response
     * when all upstream resolvers fail. Critical for WiFi↔cellular transitions where
     * DNS resolution briefly fails. Returns the stale response with a short TTL so
     * the client will re-query soon.
     */
    private suspend fun serveStaleV4(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int) {
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
        val stale = dnsCache.getStale(domain, qtypeNum, txId)
        if (stale != null) {
            PrivacyLog.i(TAG, "SERVE-STALE $domain (upstream failed, returning expired cache)")
            wrapResponseV4(orig, ihl, stale)?.let { sendToTun(it) }
        }
    }

    /**
     * Fail closed when an explicitly-enabled encrypted DNS transport (DoH/DoT/
     * DoQ/WireGuard) cannot complete a query.
     *
     * Critically, this NEVER falls back to plaintext UDP. Doing so would send
     * the query in the clear to a hardcoded public resolver (UPSTREAM_DNS,
     * i.e. 8.8.8.8/1.1.1.1), leaking it and silently overriding the user's
     * choice of encrypted DNS — the defect reported in GitHub issue #1
     * ("enable DoH Quad9, dnsleaktest shows Google DNS"). Instead we serve a
     * stale cached answer when one exists, otherwise return SERVFAIL so the
     * client fails fast.
     */
    private fun failClosedEncrypted(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                    transport: String, wrapV6: Boolean = false, v6Hdr: Int = 0) {
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
        val stale = dnsCache.getStale(domain, qtypeNum, txId)
        if (stale != null) {
            PrivacyLog.i(TAG, "FAIL-CLOSED $transport $domain — serving stale cache (no plaintext fallback)")
            if (wrapV6) wrapResponseV6(orig, v6Hdr, stale)?.let { sendToTun(it) }
            else wrapResponseV4(orig, ihl, stale)?.let { sendToTun(it) }
            return
        }
        PrivacyLog.w(TAG, "FAIL-CLOSED $transport $domain — SERVFAIL (encrypted DNS failed, refusing plaintext fallback)")
        val servfail = DnsPacketBuilder.buildServfail(dns)
        if (wrapV6) wrapResponseV6(orig, v6Hdr, servfail)?.let { sendToTun(it) }
        else wrapResponseV4(orig, ihl, servfail)?.let { sendToTun(it) }
    }

    /**
     * Observe DNS-transport preferences and apply changes live while protection
     * is running. Without this, config is only read once in startVpn(), so
     * changing the DoH provider or upstream servers in Settings appeared to do
     * nothing until the user stopped and restarted protection — the second half
     * of GitHub issue #1 ("unable to change custom dns ... always default to
     * 9.9.9.9"). WireGuard is intentionally excluded (a live key/endpoint change
     * requires a tunnel reconnect via restart).
     */
    private fun startDnsConfigObserver() {
        dnsConfigJob?.cancel()
        dnsConfigJob = serviceScope.launch {
            combine(
                prefs.dohEnabled.map { it.toString() },
                prefs.dohProvider,
                prefs.dotEnabled.map { it.toString() },
                prefs.dotProvider,
                prefs.doqEnabled.map { it.toString() },
                prefs.doqProvider,
                prefs.customUpstreamDns
            ) { values -> values.joinToString("|") }
                .distinctUntilChanged()
                .drop(1) // startVpn() already loaded the initial config
                .collect { applyLiveDnsConfig() }
        }
    }

    private suspend fun applyLiveDnsConfig() {
        useDoH = prefs.dohEnabled.first()
        dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
        useDoT = prefs.dotEnabled.first()
        dotProvider = DotResolver.Provider.fromId(prefs.dotProvider.first())
        useDoQ = prefs.doqEnabled.first()
        doqProvider = DoqResolver.Provider.fromId(prefs.doqProvider.first())
        val customDns = prefs.getUpstreamDnsList()
        upstreamDnsServers = if (customDns.isNotEmpty()) customDns else UPSTREAM_DNS.toList()
        // Flush cache so subsequent queries use the newly selected resolver
        // instead of answers cached from the previous one.
        dnsCache.clear()
        PrivacyLog.i(TAG, "DNS config reloaded live: " +
            "DoH=${if (useDoH) dohProvider.name else "off"}, " +
            "DoT=${if (useDoT) dotProvider.name else "off"}, " +
            "DoQ=${if (useDoQ) doqProvider.name else "off"}, " +
            "upstream=${upstreamDnsServers.joinToString(",")}")
    }

    private suspend fun forwardDoH(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                    app: Pair<String, String> = Pair("", ""),
                                    wrapV6: Boolean = false, v6Hdr: Int = 0) {
        try {
            val startMs = System.currentTimeMillis()
            val dohResult = dohResolver.resolveWithMetadata(dns, dohProvider)
            val resp = dohResult?.response
            if (resp != null) {
                val latencyMs = (System.currentTimeMillis() - startMs).toInt()
                val upstreamLabel = when (dohResult.transport) {
                    DohResolver.Transport.DOH3 -> "DoH3:${dohResult.provider.name}"
                    DohResolver.Transport.DOH -> "DoH:${dohResult.provider.name}"
                }

                val pfResult = postForwardChecks(resp, dns, domain, app, latencyMs, upstreamLabel)
                if (pfResult.blocked) {
                    if (pfResult.blockResponse != null) {
                        if (wrapV6) wrapResponseV6(orig, v6Hdr, pfResult.blockResponse)?.let { sendToTun(it) }
                        else wrapResponseV4(orig, ihl, pfResult.blockResponse)?.let { sendToTun(it) }
                    }
                    return
                }

                if (wrapV6) wrapResponseV6(orig, v6Hdr, resp)?.let { sendToTun(it) }
                else wrapResponseV4(orig, ihl, resp)?.let { sendToTun(it) }
            }
            else {
                // DoH was explicitly enabled — fail closed, never leak to plaintext UDP.
                failClosedEncrypted(dns, domain, orig, ihl, "DoH", wrapV6, v6Hdr)
            }
        } catch (e: Exception) {
            PrivacyLog.w(TAG, "DoH forward failed for $domain (${e.javaClass.simpleName}) — failing closed")
            failClosedEncrypted(dns, domain, orig, ihl, "DoH", wrapV6, v6Hdr)
        }
    }

    /**
     * Forward DNS query via DNS-over-QUIC (RFC 9250).
     * Falls back to DoH (still encrypted) when enabled, otherwise fails closed.
     */
    private suspend fun forwardDoQ(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                    app: Pair<String, String> = Pair("", ""),
                                    wrapV6: Boolean = false, v6Hdr: Int = 0) {
        try {
            val startMs = System.currentTimeMillis()
            val resp = doqResolver.resolve(dns, doqProvider)
            if (resp != null) {
                val latencyMs = (System.currentTimeMillis() - startMs).toInt()
                val upstreamLabel = "DoQ:${doqProvider.name}"

                val pfResult = postForwardChecks(resp, dns, domain, app, latencyMs, upstreamLabel)
                if (pfResult.blocked) {
                    if (pfResult.blockResponse != null) {
                        if (wrapV6) wrapResponseV6(orig, v6Hdr, pfResult.blockResponse)?.let { sendToTun(it) }
                        else wrapResponseV4(orig, ihl, pfResult.blockResponse)?.let { sendToTun(it) }
                    }
                    return
                }

                if (wrapV6) wrapResponseV6(orig, v6Hdr, resp)?.let { sendToTun(it) }
                else wrapResponseV4(orig, ihl, resp)?.let { sendToTun(it) }
            } else {
                // DoQ returned null (server requires full QUIC handshake) — fall back
                // to DoH if enabled (still encrypted), otherwise fail closed.
                if (useDoH) forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
                else failClosedEncrypted(dns, domain, orig, ihl, "DoQ", wrapV6, v6Hdr)
            }
        } catch (_: Exception) {
            // DoQ failed — fall back to DoH (encrypted) or fail closed. Never plaintext.
            if (useDoH) forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            else failClosedEncrypted(dns, domain, orig, ihl, "DoQ", wrapV6, v6Hdr)
        }
    }

    /**
     * Forward DNS query via WireGuard tunnel.
     * Falls back to DoQ or DoH (still encrypted) when enabled, otherwise fails closed.
     */
    private suspend fun forwardWireGuard(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                          app: Pair<String, String> = Pair("", ""),
                                          wrapV6: Boolean = false, v6Hdr: Int = 0) {
        try {
            val startMs = System.currentTimeMillis()
            val resp = wireGuardProxy.resolveDns(dns)
            if (resp != null) {
                val latencyMs = (System.currentTimeMillis() - startMs).toInt()
                val upstreamLabel = "WireGuard"

                val pfResult = postForwardChecks(resp, dns, domain, app, latencyMs, upstreamLabel)
                if (pfResult.blocked) {
                    if (pfResult.blockResponse != null) {
                        if (wrapV6) wrapResponseV6(orig, v6Hdr, pfResult.blockResponse)?.let { sendToTun(it) }
                        else wrapResponseV4(orig, ihl, pfResult.blockResponse)?.let { sendToTun(it) }
                    }
                    return
                }

                if (wrapV6) wrapResponseV6(orig, v6Hdr, resp)?.let { sendToTun(it) }
                else wrapResponseV4(orig, ihl, resp)?.let { sendToTun(it) }
            } else {
                // WireGuard returned null — fall through to another encrypted
                // transport when enabled, otherwise fail closed. Never plaintext.
                if (useDoQ) forwardDoQ(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
                else if (useDoH) forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
                else failClosedEncrypted(dns, domain, orig, ihl, "WireGuard", wrapV6, v6Hdr)
            }
        } catch (_: Exception) {
            if (useDoQ) forwardDoQ(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            else if (useDoH) forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            else failClosedEncrypted(dns, domain, orig, ihl, "WireGuard", wrapV6, v6Hdr)
        }
    }

    /**
     * Dispatch DNS query to the best available encrypted transport.
     * Priority: WireGuard > DoQ > DoH > plaintext UDP.
     */
    /**
     * Forward DNS query via DNS-over-TLS (RFC 7858).
     * Falls back to DoH (still encrypted) when enabled, otherwise fails closed.
     */
    private suspend fun forwardDoT(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                    app: Pair<String, String> = Pair("", ""),
                                    wrapV6: Boolean = false, v6Hdr: Int = 0) {
        try {
            val startMs = System.currentTimeMillis()
            val resp = dotResolver.resolve(dns, dotProvider)
            if (resp != null) {
                val latencyMs = (System.currentTimeMillis() - startMs).toInt()
                val upstreamLabel = "DoT:${dotProvider.name}"

                val pfResult = postForwardChecks(resp, dns, domain, app, latencyMs, upstreamLabel)
                if (pfResult.blocked) {
                    if (pfResult.blockResponse != null) {
                        if (wrapV6) wrapResponseV6(orig, v6Hdr, pfResult.blockResponse)?.let { sendToTun(it) }
                        else wrapResponseV4(orig, ihl, pfResult.blockResponse)?.let { sendToTun(it) }
                    }
                    return
                }

                if (wrapV6) wrapResponseV6(orig, v6Hdr, resp)?.let { sendToTun(it) }
                else wrapResponseV4(orig, ihl, resp)?.let { sendToTun(it) }
            } else {
                // DoT returned null — fall back to DoH if enabled (still encrypted),
                // otherwise fail closed. Never plaintext UDP.
                if (useDoH) forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
                else failClosedEncrypted(dns, domain, orig, ihl, "DoT", wrapV6, v6Hdr)
            }
        } catch (_: Exception) {
            if (useDoH) forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            else failClosedEncrypted(dns, domain, orig, ihl, "DoT", wrapV6, v6Hdr)
        }
    }

    /**
     * Dispatch DNS query to the best available encrypted transport.
     * Priority: WireGuard > DoQ > DoT > DoH. Falls back to plaintext UDP only
     * when no encrypted transport is enabled; enabled transports fail closed.
     */
    private suspend fun forwardEncrypted(dns: ByteArray, domain: String, orig: ByteArray, ihl: Int,
                                          app: Pair<String, String> = Pair("", ""),
                                          wrapV6: Boolean = false, v6Hdr: Int = 0) {
        when {
            useWireGuard -> forwardWireGuard(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            useDoQ -> forwardDoQ(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            useDoT -> forwardDoT(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            useDoH -> forwardDoH(dns, domain, orig, ihl, app, wrapV6, v6Hdr)
            wrapV6 -> forwardUdpV6(dns, domain, orig, v6Hdr, app)
            else -> forwardUdp(dns, domain, orig, ihl, app)
        }
    }

    private suspend fun forwardUdpV6(dns: ByteArray, domain: String, orig: ByteArray, hdr: Int,
                                     app: Pair<String, String> = Pair("", "")) {
        val sock = DatagramSocket()
        try {
            val startMs = System.currentTimeMillis()
            val primary = upstreamDnsServers.firstOrNull() ?: UPSTREAM_DNS[0]
            var responseUpstream = primary
            protect(sock); sock.soTimeout = 5000
            sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(primary), DNS_PORT))
            val buf = ByteArray(1500); val rp = DatagramPacket(buf, buf.size)
            try {
                sock.receive(rp)
            } catch (_: java.net.SocketTimeoutException) {
                // Try fallback upstream
                sock.close()
                val fallback = upstreamDnsServers.getOrElse(1) { UPSTREAM_DNS.getOrElse(1) { UPSTREAM_DNS[0] } }
                val sock2 = DatagramSocket(); protect(sock2); sock2.soTimeout = 5000
                try {
                    sock2.send(DatagramPacket(dns, dns.size, InetAddress.getByName(fallback), DNS_PORT))
                    sock2.receive(rp)
                    responseUpstream = fallback
                } finally { try { sock2.close() } catch (_: Exception) { } }
            }
            val respBytes = retryTruncatedUdpOverTcp(dns, buf.copyOf(rp.length), responseUpstream)
            val latencyMs = (System.currentTimeMillis() - startMs).toInt()

            val pfResult = postForwardChecks(respBytes, dns, domain, app, latencyMs, responseUpstream)
            if (pfResult.blocked) {
                if (pfResult.blockResponse != null) wrapResponseV6(orig, hdr, pfResult.blockResponse)?.let { sendToTun(it) }
                return
            }

            wrapResponseV6(orig, hdr, respBytes)?.let { sendToTun(it) }
        } catch (_: Exception) {
            // v5.0: Serve-stale fallback for IPv6
            val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
            val stale = dnsCache.getStale(domain, qtypeNum, txId)
            if (stale != null) {
                PrivacyLog.i(TAG, "SERVE-STALE (v6) $domain (upstream failed)")
                wrapResponseV6(orig, hdr, stale)?.let { sendToTun(it) }
            }
        } finally {
            try { sock.close() } catch (_: Exception) { }
        }
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
            val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
            if (anCount == 0) return

            // Skip query section to reach answer section
            var off = 12
            val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
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

                val rtype = ((response[off].toInt() and 0xFF) shl 8) or (response[off + 1].toInt() and 0xFF)
                val rdLen = ((response[off + 8].toInt() and 0xFF) shl 8) or (response[off + 9].toInt() and 0xFF)
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

    private fun skipDnsName(buf: ByteArray, start: Int) = DnsPacketParser.skipDnsName(buf, start)

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

    // ── App Resolution (delegated to AppResolver) ──────────

    private val appResolver by lazy { AppResolver(this) }
    private fun resolveApp(p: ByteArray, ihl: Int) = appResolver.resolveApp(p, ihl)
    private fun resolveAppV6(p: ByteArray, hdr: Int) = appResolver.resolveAppV6(p, hdr)
    private fun resolvePkg(uid: Int) = appResolver.resolvePkg(uid)
    private fun findUidFromPort(port: Int) = appResolver.findUidFromPort(port)

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

    private fun startTunnelHeartbeat() {
        tunnelHeartbeatJob?.cancel()
        tunnelHeartbeatJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(HEARTBEAT_INTERVAL_MS)
                assertTunnelHeartbeat()
            }
        }
    }

    private fun cancelTunnelHeartbeat() {
        tunnelHeartbeatJob?.cancel()
        tunnelHeartbeatJob = null
    }

    private fun assertTunnelHeartbeat() {
        if (!isRunning) return
        val fdValid = vpnInterface?.fileDescriptor?.valid() == true
        if (!fdValid) {
            fdErrorCount.incrementAndGet()
            logStructuredVpnEvent("vpn_heartbeat_failed", mapOf(
                "reason" to "tun_fd_invalid",
                "uptime_ms" to (System.currentTimeMillis() - vpnStartTime),
                "action" to "restart"
            ))
            recordEvent(
                DiagnosticEventType.TUN_FD_INVALID,
                "Tunnel heartbeat detected invalid TUN fd",
                mapOf("uptime_ms" to (System.currentTimeMillis() - vpnStartTime))
            )
            serviceScope.launch { restartVpn() }
        }
    }

    private fun recordEvent(
        type: DiagnosticEventType,
        message: String = "",
        fields: Map<String, Any?> = emptyMap()
    ) {
        diagnosticEvents.recordAsync(serviceScope, type, message, fields)
    }

    private fun logStructuredVpnEvent(event: String, fields: Map<String, Any?> = emptyMap()) {
        val obj = org.json.JSONObject()
            .put("event", event)
            .put("timestamp_ms", System.currentTimeMillis())
        fields.forEach { (key, value) -> obj.put(key, value) }
        Log.w(TAG, obj.toString())
    }

    private fun startVpnRecoveryMonitor() {
        vpnRecoveryMonitorJob?.cancel()
        vpnRecoveryMonitorJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(30_000L)
                evaluateVpnRecoveryAdvisory()
            }
        }
    }

    private fun cancelVpnRecoveryMonitor() {
        vpnRecoveryMonitorJob?.cancel()
        vpnRecoveryMonitorJob = null
        vpnRecoveryAdvisoryState.value = null
    }

    private fun evaluateVpnRecoveryAdvisory() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = Build.VERSION.SDK_INT,
            vpnRunning = isRunning,
            alwaysOn = try { isAlwaysOn() } catch (_: Exception) { false },
            lockdownEnabled = try { isLockdownEnabled() } catch (_: Exception) { false },
            tunFdValid = vpnInterface?.fileDescriptor?.valid() == true,
            hasValidatedPhysicalNetwork = hasValidatedPhysicalNetwork(),
            elapsedSinceVpnStartMs = SystemClock.elapsedRealtime() - vpnEstablishedAt,
            inboundPacketCount = tunInboundPacketCount.get()
        )

        if (Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)) {
            if (vpnRecoveryAdvisoryState.value == null) {
                vpnRecoveryAdvisoryState.value = VpnRecoveryAdvisory(
                    title = "Restart device to recover VPN",
                    message = "Android 16 always-on lockdown is active, but HostShield has not received tunnel traffic since startup. This can happen after system updates; a device restart usually restores the VPN stack.",
                    detectedAtMillis = System.currentTimeMillis()
                )
                Log.w(TAG, "Android 16 VPN recovery advisory raised: $snapshot")
            }
        } else if (snapshot.inboundPacketCount > 0L && vpnRecoveryAdvisoryState.value != null) {
            vpnRecoveryAdvisoryState.value = null
        }
    }

    @Suppress("DEPRECATION")
    private fun hasValidatedPhysicalNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    // ── Stats ────────────────────────────────────────────────

    // Stats are now batched via flushStats() called by startLogFlusher()

    // ── Notifications ────────────────────────────────────────

    /** Pause DNS blocking for a specified number of minutes. */
    private fun pauseBlocking(minutes: Int) {
        isPaused = true
        pauseResumeJob?.cancel()
        pauseResumeJob = serviceScope.launch {
            Log.i(TAG, "Blocking paused for ${minutes} minutes")
            updateNotification(blockedCount.get())
            delay(minutes * 60_000L)
            isPaused = false
            pauseResumeJob = null
            Log.i(TAG, "Blocking resumed after ${minutes}-minute pause")
            updateNotification(blockedCount.get())
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        NotificationChannel(CHANNEL_ID, "HostShield VPN", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VPN blocking status"; setShowBadge(false)
        }.let { nm.createNotificationChannel(it) }
        NotificationChannel(ALERT_CHANNEL_ID, "HostShield Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Source health and system alerts"
        }.let { nm.createNotificationChannel(it) }
    }

    private fun makePausePendingIntent(minutes: Int, requestCode: Int): PendingIntent =
        PendingIntent.getService(this, requestCode,
            Intent(this, DnsVpnService::class.java).apply {
                action = ACTION_PAUSE; putExtra("pause_minutes", minutes)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotification(blocked: Int): Notification {
        val ci = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val si = PendingIntent.getService(this, 1,
            Intent(this, DnsVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)

        val title = if (isPaused) "HostShield Paused" else "HostShield Active"
        val sub = buildString {
            if (isPaused) append("Blocking paused")
            else {
                append(if (blocked > 0) "$blocked blocked" else "DNS filtering active")
                if (useWireGuard) append(" | WG")
                else if (useDoQ) append(" | DoQ")
                else if (useDoT) append(" | DoT")
                else if (useDoH) append(" | DoH")
                if (dnsTrapEnabled) append(" | Trap")
            }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(sub)
            .setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true)
            .setContentIntent(ci)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", makePausePendingIntent(0, 5))
        } else {
            // Max 3 actions: Pause 5m, Pause 30m, Stop
            builder.addAction(android.R.drawable.ic_media_pause, "Pause 5m", makePausePendingIntent(5, 2))
            builder.addAction(android.R.drawable.ic_media_pause, "Pause 30m", makePausePendingIntent(30, 3))
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", si)

        return builder.build()
    }

    private fun updateNotification(blocked: Int) {
        currentBlockedCount = blocked
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(blocked))
    }
}
