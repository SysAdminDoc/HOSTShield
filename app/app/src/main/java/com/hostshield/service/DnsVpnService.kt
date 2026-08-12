package com.hostshield.service

import android.app.AlarmManager
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
import androidx.core.app.ServiceCompat
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlockDecision
import com.hostshield.domain.BlocklistHolder
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
import java.util.concurrent.atomic.AtomicInteger
import java.net.InetAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong

// Only USER-originated allow decisions bypass threat-intel re-blocking. Source
// (downloaded) allowlists share the same reason codes ("allowlist",
// "allowlist_wildcard", "dns_type_allow") but must NOT be able to whitelist a
// malware domain past URLhaus/Spamhaus — otherwise a compromised or careless
// remote allowlist silently defeats threat-intel protection. The distinguishing
// signal is the decision's `source`, not its `reason`.
internal val THREAT_INTEL_BYPASS_SOURCES = setOf(
    "User allow rule",
    "User wildcard allow rule",
    "User regex allow rule",
)

internal fun BlockDecision.skipsThreatIntelChecks(): Boolean =
    !blocked && (reason == "protection_paused" || source in THREAT_INTEL_BYPASS_SOURCES)

// VPN DNS blocking service
//
// Architecture: DNS-only interception (DNS66-style TEST-NET routing)
//
// - VPN interface at 10.120.0.1/24 + fd00::1/120 (dual-stack)
// - Virtual DNS servers use RFC 5737 TEST-NET addresses (192.0.2.x,
//   198.51.100.x, 203.0.113.x) with automatic fallback if a prefix
//   conflicts with an active network route.
// - Only host routes (/32 IPv4, /128 IPv6) for virtual DNS/trap addresses, so ONLY DNS packets
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
        /** Foreground-app sampling cadence for context-aware `blockBackground` rules. */
        private const val FOREGROUND_POLL_INTERVAL_MS = 5_000L
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

        val vpnRecoveryAdvisory: kotlinx.coroutines.flow.StateFlow<VpnRecoveryAdvisory?> =
            VpnRecoveryMonitor.advisory

        fun dismissVpnRecoveryAdvisory() {
            VpnRecoveryMonitor.dismiss()
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

        // DoT (DNS-over-TLS) trap: these IPs also run on port 853.
        // We route them through VPN and drop non-port-53 traffic,
        // forcing apps to fall back to port 53 where we can filter.
        // Note: The signed DNS-trap set already routes port 53 traffic. This
        // list is for hostname-based routing of additional DoT endpoints.
        private val DOT_TRAP_IPS = arrayOf(
            "dns.google",          // 8.8.8.8, 8.8.4.4
            "1dot1dot1dot1.cloudflare-dns.com", // 1.1.1.1
            "dns.quad9.net",       // 9.9.9.9
        )

    }

    @Inject lateinit var dnsLogDao: DnsLogDao
    @Inject lateinit var blockStatsDao: BlockStatsDao
    @Inject lateinit var blocklist: BlocklistHolder
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var repository: HostShieldRepository
    @Inject lateinit var sourceCoordinator: BlocklistSourceCoordinator
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
    @Inject lateinit var blockNotificationService: BlockNotificationService
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
    // Independent of serviceScope so the final log flush survives serviceScope.cancel()
    // and onDestroy(). Used only for fire-and-forget teardown work off the main thread.
    private val teardownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
    @Volatile private var useWireGuard = false
    @Volatile private var dnsTrapEnabled = true
    @Volatile private var threatIntelEnabled = false
    @Volatile private var dnsOnlyMode = false
    @Volatile private var safeSearchEnabled = false
    @Volatile private var contentFilterCategories: Set<ContentCategory> = emptySet()
    @Volatile private var blockResponseType = "nxdomain"
    @Volatile private var ipv4Redirect = ""
    @Volatile private var ipv6Redirect = ""
    private var edeEnabled = false
    // Custom upstream DNS — updated live by startDnsConfigObserver()
    @Volatile private var upstreamDnsServers = UPSTREAM_DNS.toList()
    private var dnsConfigJob: Job? = null
    private var filterConfigJob: Job? = null

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
    private val DNS_ANSWER_CACHE_MAX = 2000  // hard cap; bounds high-cardinality bursts

    // Network change debounce — prevents infinite VPN restart loop.
    // When VPN establishes, Android fires onAvailable() for the VPN's own
    // network interface. Without this guard, that triggers restartVpn() which
    // re-establishes the VPN, firing onAvailable() again → infinite cycle.
    @Volatile private var vpnEstablishedAt = 0L          // SystemClock.elapsedRealtime()
    @Volatile private var networkLost = false             // true after onLost() fires
    private val NETWORK_RESTART_COOLDOWN_MS = 5000L      // ignore events within 5s of start

    private var loggingEnabled = true  // read from prefs at startVpn()

    // DNS Response Cache — LRU with TTL-aware expiration
    private val dnsCache = DnsCache(maxEntries = 2000, maxNegativeEntries = 500)
    private val dnsForwarder by lazy {
        DnsForwarder(
            dohResolver = dohResolver,
            dotResolver = dotResolver,
            doqResolver = doqResolver,
            wireGuardProxy = wireGuardProxy,
            config = {
                DnsForwardingConfig(
                    useDoH = useDoH,
                    dohProvider = dohProvider,
                    useDoT = useDoT,
                    dotProvider = dotProvider,
                    useDoQ = useDoQ,
                    doqProvider = doqProvider,
                    useWireGuard = useWireGuard,
                    upstreamDnsServers = upstreamDnsServers,
                )
            },
            protectDatagram = { socket -> protect(socket) },
            protectSocket = { socket -> protect(socket) },
            defaultUpstreamDnsServers = UPSTREAM_DNS.toList(),
        )
    }

    private val dnsLogManager by lazy {
        DnsLogManager(
            dnsLogDao = dnsLogDao,
            blockStatsDao = blockStatsDao,
            dnsDiskCache = dnsDiskCache,
            networkTrackerDb = networkTrackerDb,
            connectionTracker = connectionTracker,
            dnsCache = dnsCache,
            loggingEnabled = { loggingEnabled },
            emitLiveQuery = { liveQueriesFlow.tryEmit(it) },
            publishCacheStats = { currentCacheStats = it },
            publishDroppedQueries = { currentDroppedQueries = it },
        )
    }

    private val blocklistManager by lazy {
        BlocklistManager(sourceCoordinator, sourceFailureNotifier, ::recordEvent)
    }

    private val vpnNotificationController by lazy {
        VpnNotificationController(
            service = this,
            isPaused = { isPaused },
            transportLabel = {
                when {
                    useWireGuard -> "WG"
                    useDoQ -> "DoQ"
                    useDoT -> "DoT"
                    useDoH -> "DoH"
                    else -> ""
                }
            },
            dnsTrapEnabled = { dnsTrapEnabled },
            publishBlockedCount = { currentBlockedCount = it },
        )
    }

    private val vpnRecoveryMonitor by lazy {
        VpnRecoveryMonitor(
            service = this,
            vpnRunning = { isRunning },
            tunFdValid = { vpnInterface?.fileDescriptor?.valid() == true },
            vpnEstablishedAt = { vpnEstablishedAt },
            inboundPacketCount = { tunInboundPacketCount.get() },
        )
    }

    // VPN Stability tracking
    private val fdErrorCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val rebuildCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val tunInboundPacketCount = AtomicLong(0L)
    private var vpnStartTime = 0L
    @Volatile private var stabilityFlushJob: Job? = null
    @Volatile private var tunnelHeartbeatJob: Job? = null
    @Volatile private var contextStateJob: Job? = null

    // Pause state: when paused, all queries are allowed (no blocking)
    @Volatile private var isPaused = false
    @Volatile private var pauseResumeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        vpnNotificationController.createChannels()
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
                    this, NOTIFICATION_ID, vpnNotificationController.build(0),
                    ProtectionForegroundServiceTypes.runtimeType()
                )
                serviceScope.launch { startVpn() }
            }
            else -> {
                // Null intent = system restarted us after process death (START_STICKY).
                // Re-promote to foreground and restart the VPN if prefs say we should be on.
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, vpnNotificationController.build(0),
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

    override fun onRevoke() {
        // System-initiated revoke (another VPN took the slot). Unlike a
        // user-initiated stop, nothing else updates the enabled pref or the
        // widget here — without this they keep showing "Protected".
        teardownScope.launch {
            try { prefs.setEnabled(false) } catch (_: Exception) { }
            HostShieldWidgetProvider.updateWidget(
                applicationContext, false,
                try { prefs.lastApplyCount.first() } catch (_: Exception) { 0 }
            )
        }
        stopVpn(); super.onRevoke()
    }

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
        blockNotificationService.stop()
        cancelWatchdog()
        cancelTunnelHeartbeat()
        vpnRecoveryMonitor.cancel()
        dnsLogManager.cancel()
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
            vpnRecoveryMonitor.cancel()

            excludedApps = prefs.excludedApps.first()
            blockedApps = prefs.blockedApps.first()
            // Load context-aware firewall rules
            val ctxRules = firewallRuleDao.getContextAwareRules().first()
            contextRules = ctxRules.associateBy { it.packageName }
            ContextState.register(this)
            startContextStateMonitor()
            useDoH = prefs.dohEnabled.first()
            dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
            useDoT = prefs.dotEnabled.first()
            dotProvider = DotResolver.Provider.fromId(prefs.dotProvider.first())
            useDoQ = if (com.hostshield.BuildConfig.DEBUG) prefs.doqEnabled.first() else false
            doqProvider = DoqResolver.Provider.fromId(prefs.doqProvider.first())
            useWireGuard = if (com.hostshield.BuildConfig.DEBUG) prefs.wireGuardEnabled.first() else false
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
            val activeTrapIpSets = dohBypassUpdater.getCached().ipSets
                ?: DnsTrapIpSets.FALLBACK
            safeSearchEnabled = prefs.safeSearchEnabled.first()
            // Pre-warm and keep safe-search endpoints resolved off the packet
            // loop — a cold cache there blocks the single TUN thread on a
            // system-resolver lookup.
            if (safeSearchEnabled) safeSearchEnforcer.startBackgroundRefresh()
            contentFilterCategories = prefs.contentFilterCategories.first()
                .mapNotNull { name ->
                    try { ContentCategory.valueOf(name) } catch (_: Exception) { null }
                }.toSet()
            loggingEnabled = prefs.dnsLogging.first()
            blockResponseType = prefs.blockResponseType.first()
            ipv4Redirect = prefs.ipv4Redirect.first()
            ipv6Redirect = prefs.ipv6Redirect.first()
            edeEnabled = prefs.edeEnabled.first()

            // Resolve custom upstream DNS
            val customDns = prefs.getUpstreamDnsList()
            upstreamDnsServers = if (customDns.isNotEmpty()) customDns else UPSTREAM_DNS.toList()

            if (blocklist.domainCount == 0) blocklistManager.rebuild()

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
            var ipv6VpnAvailable = false
            try {
                builder.addAddress(VPN_ADDRESS6, 120)
                ipv6VpnAvailable = true
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

            // IPv6 virtual DNS. Do not add an IPv6 route when the OEM rejected
            // the IPv6 tunnel address; doing so can make establish() fail.
            if (ipv6VpnAvailable) {
                builder.addDnsServer(VDNS6_PRIMARY)
                builder.addCanonicalRoute(VDNS6_PRIMARY, 128)
            }

            // DNS Trap: route well-known public DNS through TUN
            // v6.0: Skip trap routes in DNS-only mode for lower battery (~0.5%)
            if (dnsTrapEnabled && !dnsOnlyMode) {
                // Signed manifest v2 IPs replace the fallback as a complete
                // set; older manifests and corrupt caches use the APK set.
                for (route in DnsTrapRoutePlanner.routeTargets(
                    activeTrapIpSets,
                    ipv6VpnAvailable
                )) {
                    try { builder.addRoute(route.address, route.prefixLength) } catch (_: Exception) { }
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
                // Reflect reality: without this, the enabled pref (set by the
                // caller), the widget, and the QS tile keep claiming "Protected"
                // while the service silently exits.
                try { prefs.setEnabled(false) } catch (_: Exception) { }
                HostShieldWidgetProvider.updateWidget(
                    applicationContext, false,
                    try { prefs.lastApplyCount.first() } catch (_: Exception) { 0 }
                )
                stopSelf(); return
            }

            vpnEstablishedAt = SystemClock.elapsedRealtime()
            vpnStartTime = System.currentTimeMillis()
            networkLost = false
            isRunning = true
            blockNotificationService.start()
            dnsAnswerCache.clear()
            dnsLogManager.droppedQueries.set(0)
            dnsLogManager.totalQueriesCount.set(0)
            clearCacheCallback = {
                dnsCache.clear()
                // v5.0: Also clear persistent disk cache
                serviceScope.launch { dnsDiskCache.clear() }
            }
            serviceScope.launch { writeLoop() }
            serviceScope.launch { packetLoop() }
            dnsLogManager.start(serviceScope)
            startStabilityFlusher()
            registerNetworkCallback()
            scheduleWatchdog()
            startTunnelHeartbeat()
            vpnRecoveryMonitor.start(serviceScope)
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
                    "trap=${dnsTrapEnabled && !dnsOnlyMode} (" +
                        "${activeTrapIpSets.dnsTrapIpv4.size + activeTrapIpSets.dnsTrapIpv6.size}+" +
                        "${activeTrapIpSets.dohBypassIpv4.size + activeTrapIpSets.dohBypassIpv6.size} IPs), " +
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
        safeSearchEnforcer.stopBackgroundRefresh()
        val wasRunning = isRunning
        if (wasRunning) {
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.VPN_STOP,
                "VPN stopped",
                mapOf(
                    "uptime_ms" to if (vpnStartTime > 0) System.currentTimeMillis() - vpnStartTime else 0L,
                    "blocked" to blockedCount.get(),
                    "allowed" to allowedCount.get(),
                    "dropped" to dnsLogManager.droppedQueries.get(),
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
        vpnRecoveryMonitor.cancel()
        unregisterNetworkCallback()
        dnsConfigJob?.cancel(); dnsConfigJob = null
        filterConfigJob?.cancel(); filterConfigJob = null
        dnsLogManager.cancel()
        stabilityFlushJob?.cancel(); stabilityFlushJob = null
        // Disconnect WireGuard proxy if active
        try { if (useWireGuard) wireGuardProxy.disconnect() } catch (_: Exception) { }
        contextStateJob?.cancel(); contextStateJob = null
        ContextState.foregroundPackage = ""
        ContextState.unregister(this)
        dnsAnswerCache.clear()
        dnsCache.clear()
        clearCacheCallback = null
        try { writeChannel.close() } catch (_: Exception) { }
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        // Flush remaining logs OFF the main thread to avoid ANR — stopVpn() runs on
        // the main thread from ACTION_STOP/onRevoke. teardownScope outlives
        // serviceScope, so the flush is not cancelled before it completes. The final
        // stopForeground/stopSelf happens after the flush (or its 3s timeout).
        val pending = dnsLogManager.pendingEntries
        teardownScope.launch {
            try {
                withTimeoutOrNull(3000L) {
                    if (pending > 0) Log.i(TAG, "Flushing $pending remaining log entries on stop")
                    dnsLogManager.flushForShutdown()
                    flushStability()
                } ?: Log.w(TAG, "Final log flush timed out after 3s — some entries may be lost")
            } catch (e: Exception) {
                Log.e(TAG, "Final log flush failed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                    try { stopSelf() } catch (_: Exception) { }
                }
            }
        }
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
            vpnRecoveryMonitor.cancel()
            dnsConfigJob?.cancel(); dnsConfigJob = null
            filterConfigJob?.cancel(); filterConfigJob = null
            unregisterNetworkCallback()
            // Flush buffered logs before restart — don't lose entries
            dnsLogManager.cancel()
            try { dnsLogManager.flushForShutdown() } catch (_: Exception) { }
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
                    // Metered state was previously sampled only at ContextState.register(),
                    // so `blockMetered` rules enforced whatever was true at VPN start
                    // across every Wi-Fi/cellular handover that did not fire onLost.
                    ContextState.isMetered =
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
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
                    vpnRecoveryMonitor.onInboundPacket()
                    count++

                    val ipVer = (packet[0].toInt() and 0xF0) shr 4
                    when (ipVer) {
                        4 -> {
                            if (isIpv4UdpDns(packet, length)) processDnsPacket(packet, length, isV6 = false)
                            else if (isIpv4TcpDns(packet, length)) processTcpDns(packet, length, isV6 = false)
                            else tryTlsFingerprintPacket(packet, length, isV6 = false)
                        }
                        6 -> {
                            if (isIpv6UdpDns(packet, length)) processDnsPacket(packet, length, isV6 = true)
                            else if (isIpv6TcpDns(packet, length)) processTcpDns(packet, length, isV6 = true)
                            else tryTlsFingerprintPacket(packet, length, isV6 = true)
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

    private suspend fun processDnsPacket(packet: ByteArray, length: Int, isV6: Boolean) {
        val headerOffset = if (isV6) 40 else (packet[0].toInt() and 0x0F) * 4
        val dns = if (isV6) extractDnsPayloadV6(packet, length, headerOffset)
                  else extractDnsPayload(packet, length, headerOffset)
        dns ?: return
        val domain = parseDnsQueryDomain(dns) ?: return
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val qtype = DnsPacketBuilder.queryTypeLabel(qtypeNum)
        var app = if (isV6) resolveAppV6(packet, headerOffset) else resolveApp(packet, headerOffset)

        if (app.first.isEmpty()) {
            val heuristicUid = findUidByDnsCorrelation(domain)
            if (heuristicUid > 0) app = resolvePkg(heuristicUid)
        }

        if (!dnsOnlyMode && app.first.isNotEmpty() && app.first in blockedApps) {
            logAsync(domain, true, app, qtype, explicitDecision(
                blocked = true,
                reason = "app_firewall",
                source = "Per-app DNS firewall",
                matchedValue = app.first,
                precedence = "per-app firewall runs before DNS policy"
            ))
            sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "app_firewall")
            return
        }

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
                sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "context_firewall")
                return
            }
        }

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
                wrapAndSend(packet, headerOffset, isV6, safeResp)
                allowedCount.incrementAndGet()
                return
            }
        }

        if (app.first.isNotEmpty()) {
            val ruleAction = appDnsRuleEngine.checkDomain(app.first, domain, qtypeNum)
            if (ruleAction == AppDnsRuleEngine.RuleAction.BLOCK) {
                PrivacyLog.d(TAG, "APP-RULE blocked $domain for ${app.second}")
                logAsync(domain, true, app, qtype, explicitDecision(
                    blocked = true,
                    reason = "app_rule_block",
                    source = "Per-app DNS rule",
                    matchedValue = app.first,
                    precedence = "per-app block rule runs before shared blocklist"
                ))
                sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "app_rule")
                return
            }
            if (ruleAction == AppDnsRuleEngine.RuleAction.ALLOW) {
                PrivacyLog.d(TAG, "APP-RULE allowed $domain for ${app.second}")
                logAsync(domain, false, app, qtype, explicitDecision(
                    blocked = false,
                    reason = "app_rule_allow",
                    source = "Per-app DNS rule",
                    matchedValue = app.first,
                    precedence = "per-app allow rule skips shared blocklist and threat intel for this app"
                ))
                val pCopy = packet.copyOf(length)
                serviceScope.launch {
                    forwardEncrypted(
                        dns,
                        domain,
                        pCopy,
                        if (isV6) 0 else headerOffset,
                        app,
                        isV6,
                        headerOffset,
                        skipThreatIntelChecks = true
                    )
                }
                allowedCount.incrementAndGet()
                return
            }
        }

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
            sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "content_filter")
            return
        }

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
            sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "parental_control")
            return
        }

        val blockDecision = domainDecision(domain, qtypeNum)
        val blocked = blockDecision.blocked
        val skipThreatIntelChecks = blockDecision.skipsThreatIntelChecks()

        if (!blocked && threatIntelEnabled && !skipThreatIntelChecks) {
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
                sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "threat_intel")
                return
            }
        }

        if (blocked) {
            logAsync(domain, true, app, qtype, blockDecision)
            PrivacyLog.d(TAG, "BLOCKED $domain ($qtype) [${app.second.ifEmpty { "system" }}]")
            sendBlockResponse(dns, packet, headerOffset, isV6, qtype, "blocklist")
        } else {
            val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
            val cacheResult = dnsCache.get(domain, qtypeNum, txId)
            if (cacheResult != null) {
                if (!cacheResult.isStale) {
                    PrivacyLog.d(TAG, "CACHE HIT $domain ($qtype)")
                    val pfResult = postForwardChecks(
                        cacheResult.response,
                        dns,
                        domain,
                        app,
                        latencyMs = 0,
                        upstreamServer = "DNS cache",
                        skipThreatIntelChecks = skipThreatIntelChecks,
                        isFromCache = true
                    )
                    if (pfResult.blocked) {
                        if (pfResult.blockResponse != null) wrapAndSend(packet, headerOffset, isV6, pfResult.blockResponse)
                        return
                    }
                    wrapAndSend(packet, headerOffset, isV6, cacheResult.response)
                    allowedCount.incrementAndGet()
                    if (cacheResult.needsPrefetch) {
                        serviceScope.launch {
                            try {
                                refreshDnsCacheOnly(dns, domain, app, skipThreatIntelChecks)
                            } catch (e: Exception) { PrivacyLog.d(TAG, "Prefetch failed for $domain: ${e.message}") }
                        }
                    }
                    return
                } else {
                    PrivacyLog.d(TAG, "SERVE-STALE $domain ($qtype) — refreshing in background")
                    val pfResult = postForwardChecks(
                        cacheResult.response,
                        dns,
                        domain,
                        app,
                        latencyMs = 0,
                        upstreamServer = "DNS stale cache",
                        skipThreatIntelChecks = skipThreatIntelChecks,
                        isFromCache = true
                    )
                    if (pfResult.blocked) {
                        if (pfResult.blockResponse != null) wrapAndSend(packet, headerOffset, isV6, pfResult.blockResponse)
                        return
                    }
                    wrapAndSend(packet, headerOffset, isV6, cacheResult.response)
                    allowedCount.incrementAndGet()
                    serviceScope.launch {
                        try {
                            refreshDnsCacheOnly(dns, domain, app, skipThreatIntelChecks)
                        } catch (e: Exception) { PrivacyLog.d(TAG, "Stale refresh failed for $domain: ${e.message}") }
                    }
                    return
                }
            }

            PrivacyLog.d(TAG, "ALLOWED $domain ($qtype)")
            val pCopy = packet.copyOf(length)
            serviceScope.launch { forwardEncrypted(dns, domain, pCopy, if (isV6) 0 else headerOffset, app, isV6, headerOffset, skipThreatIntelChecks) }
            allowedCount.incrementAndGet()
        }
    }

    @Suppress("UNUSED") // removed — unified into processDnsPacket

    /**
     * Send a block response (NXDOMAIN, 0.0.0.0/::, or REFUSED) for a DNS packet.
     * The response type is controlled by the blockResponseType preference.
     */
    private suspend fun sendBlockResponse(dns: ByteArray, packet: ByteArray, headerOffset: Int, isV6: Boolean, qtype: String, reason: String? = null) {
        val resp = buildBlockResponse(dns, qtype, reason) ?: return
        val wrapped = if (isV6) wrapResponseV6(packet, headerOffset, resp)
                      else wrapResponseV4(packet, headerOffset, resp)
        wrapped?.let { sendToTun(it) } ?: return
        blockedCount.incrementAndGet()
        if (blockedCount.get() % 100 == 0) updateNotification(blockedCount.get())
    }

    private fun wrapAndSend(packet: ByteArray, headerOffset: Int, isV6: Boolean, dns: ByteArray) {
        val wrapped = if (isV6) wrapResponseV6(packet, headerOffset, dns)
                      else wrapResponseV4(packet, headerOffset, dns)
        wrapped?.let { sendToTun(it) }
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
    private fun buildBlockResponse(dns: ByteArray, qtype: String, reason: String? = null): ByteArray? {
        val edeCode = if (edeEnabled) DnsPacketBuilder.EDE_BLOCKED else -1
        return DnsPacketBuilder.buildBlockResponse(
            dns, blockResponseType, edeCode, if (edeEnabled) reason else null,
            ipv4Redirect, ipv6Redirect,
        )
    }

    private fun logAsync(
        domain: String,
        blocked: Boolean,
        app: Pair<String, String>,
        qtype: String,
        decision: BlockDecision? = null
    ) {
        dnsLogManager.record(domain, blocked, app, qtype, decision = decision)
    }

    /** Rich log entry with CNAME chain, resolved IPs, latency, and upstream server. */
    private fun logAsyncRich(
        domain: String, blocked: Boolean, app: Pair<String, String>, qtype: String,
        cnameChain: String = "", resolvedIps: String = "",
        responseTimeMs: Int = 0, upstreamServer: String = "",
        trackerCategory: String = "", trackerOwner: String = "",
        decision: BlockDecision? = null
    ) {
        dnsLogManager.record(
            domain = domain,
            blocked = blocked,
            app = app,
            qtype = qtype,
            cnameChain = cnameChain,
            resolvedIps = resolvedIps,
            responseTimeMs = responseTimeMs,
            upstreamServer = upstreamServer,
            trackerCategory = trackerCategory,
            trackerOwner = trackerOwner,
            decision = decision,
        )
    }

    /**
     * Keeps [ContextState.foregroundPackage] fresh for `blockBackground` rules.
     *
     * Without this the field stays at its "" initial value forever, and every
     * background rule blocks its app unconditionally (the policy fails open on the
     * empty value, so an ungranted usage-stats permission degrades to "never block
     * by background" rather than "always block").
     *
     * Only runs when at least one active rule actually needs it — polling usage
     * stats has a battery cost and requires PACKAGE_USAGE_STATS.
     */
    private fun startContextStateMonitor() {
        contextStateJob?.cancel()
        if (contextRules.values.none { it.blockBackground }) {
            ContextState.foregroundPackage = ""
            contextStateJob = null
            return
        }
        contextStateJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    ContextState.updateForegroundApp(this@DnsVpnService)
                } catch (e: Exception) {
                    Log.d(TAG, "Foreground sample failed: ${e.message}")
                }
                delay(FOREGROUND_POLL_INTERVAL_MS)
            }
        }
    }

    /** Flush VPN stability metrics to Room. */
    private suspend fun flushStability() {
        val dropped = dnsLogManager.droppedQueries.getAndSet(0)
        val queries = dnsLogManager.totalQueriesCount.getAndSet(0)
        val rebuilds = rebuildCount.getAndSet(0)
        val errors = fdErrorCount.getAndSet(0)
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val uptimeMs = if (vpnStartTime > 0) System.currentTimeMillis() - vpnStartTime else 0L

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
            dnsLogManager.droppedQueries.addAndGet(dropped)
            dnsLogManager.totalQueriesCount.addAndGet(queries)
            rebuildCount.addAndGet(rebuilds)
            fdErrorCount.addAndGet(errors)
            Log.e(TAG, "Stability flush failed: ${e.message}")
        }
    }

    /** Periodic stability flusher — every 60 seconds. */
    private fun startStabilityFlusher() {
        stabilityFlushJob?.cancel()
        stabilityFlushJob = serviceScope.launch {
            while (isActive) {
                delay(60_000)
                try { flushStability() } catch (e: Exception) { Log.w(TAG, "Stability flush failed: ${e.message}") }
            }
        }
    }

    private fun sendToTun(packet: ByteArray) {
        val result = writeChannel.trySend(packet)
        if (result.isFailure) dnsLogManager.droppedQueries.incrementAndGet()
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
    private fun domainDecision(domain: String, queryType: Int? = null): BlockDecision {
        if (isPaused) {
            return BlockDecision(
                blocked = false,
                reason = "protection_paused",
                precedence = "pause state bypasses blocklist lookup"
            )
        }
        return blocklist.decide(domain, queryType)
    }

    private fun explicitDecision(
        blocked: Boolean,
        reason: String,
        source: String = "",
        matchedValue: String = "",
        precedence: String = ""
    ): BlockDecision = BlockDecision(blocked, reason, source, matchedValue, precedence)

    private fun isDomainBlocked(domain: String, queryType: Int? = null): Boolean {
        return domainDecision(domain, queryType).blocked
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
     * - SYN packets: Send RST to reject the connection immediately. If the SYN
     *   carries a DNS payload, check it against the blocklist first.
     * - Data packets with parseable DNS: Check against blocklist. If blocked,
     *   send RST. If allowed, drop — app times out and retries via UDP.
     * - Data packets with unparseable DNS (EDNS, zone transfers, fragmented):
     *   Drop silently. Sending RST here would break legitimate TCP DNS for
     *   allowed domains.
     *
     * This prevents TCP DNS bypass of blocking without implementing a full
     * TCP state machine. Allowed TCP DNS queries fall back to UDP on timeout
     * (standard DNS client behavior per RFC 7766 §6.2.2).
     */
    private suspend fun processTcpDns(packet: ByteArray, length: Int, isV6: Boolean) {
        val headerOffset = if (isV6) 40 else (packet[0].toInt() and 0x0F) * 4
        val tcpOff = headerOffset
        if (length < tcpOff + 20) return

        val dataOff = ((packet[tcpOff + 12].toInt() and 0xF0) shr 4) * 4
        val tcpFlags = packet[tcpOff + 13].toInt() and 0xFF
        if ((tcpFlags and 0x04) != 0) return // RST — don't respond

        val payloadStart = tcpOff + dataOff
        val payloadLen = length - payloadStart

        val isSyn = (tcpFlags and 0x02) != 0
        var hostname: String? = null
        var qtypeNum: Int? = null
        if (payloadLen > 14) {
            val dnsLen = ((packet[payloadStart].toInt() and 0xFF) shl 8) or
                (packet[payloadStart + 1].toInt() and 0xFF)
            if (dnsLen in 12..4096 && payloadStart + 2 + dnsLen <= length) {
                val dns = packet.copyOfRange(payloadStart + 2, payloadStart + 2 + dnsLen)
                hostname = parseDnsQueryDomain(dns)
                qtypeNum = DnsPacketBuilder.parseQueryType(dns)
            }
        }

        if (hostname == null && !isSyn) return

        val blocked = if (hostname != null) isDomainBlocked(hostname, qtypeNum) else true

        if (blocked) {
            // Trim to the captured length: the shared MTU-sized read buffer would
            // otherwise inflate the payload term in the RST ACK computation,
            // producing an out-of-window RST the client TCP stack ignores.
            val trimmed = packet.copyOf(length)
            val rst = if (isV6) buildTcpRstV6(trimmed) else buildTcpRst(trimmed, headerOffset)
            rst ?: return
            sendToTun(rst)
            blockedCount.incrementAndGet()
            if (hostname != null) {
                PrivacyLog.d(TAG, "TCP-DNS BLOCKED (RST) $hostname")
                logAsync(hostname, true, "" to "", "TCP")
            }
        } else {
            if (hostname != null) PrivacyLog.d(TAG, "TCP-DNS allowed (drop→UDP fallback) $hostname")
        }
    }

    // ── TLS Fingerprinting (v6.2) ──────────────────────────────

    private fun tryTlsFingerprintPacket(packet: ByteArray, length: Int, isV6: Boolean) {
        val minSize = if (isV6) 80 else 60
        if (length < minSize) return
        val headerOffset = if (isV6) 40 else (packet[0].toInt() and 0x0F) * 4
        val protocol = if (isV6) packet[6].toInt() and 0xFF else packet[9].toInt() and 0xFF
        if (protocol != 6) return
        val tcpOff = headerOffset
        if (length < tcpOff + 20) return
        val dataOff = ((packet[tcpOff + 12].toInt() and 0xF0) shr 4) * 4
        val payloadStart = tcpOff + dataOff
        val payloadLen = length - payloadStart
        if (payloadLen < 6) return
        if (!tlsFingerprinter.isClientHello(packet, payloadStart, payloadLen)) return
        val fp = tlsFingerprinter.fingerprint(packet, payloadStart, payloadLen) ?: return
        val app = if (isV6) resolveAppV6(packet, headerOffset) else resolveApp(packet, headerOffset)
        tlsFingerprinter.record(app.first, app.second, fp)
        PrivacyLog.d(TAG, "TLS-FP ${app.second.ifEmpty { "unknown" }}: JA3=${fp.ja3} JA4=${fp.ja4} SNI=${fp.sni ?: "-"}")
    }

    // ── TCP RST Building (delegated to TcpRstBuilder) ──────

    private fun buildTcpRstV6(orig: ByteArray) = TcpRstBuilder.buildTcpRstV6(orig)
    private fun buildTcpRst(orig: ByteArray, ihl: Int) = TcpRstBuilder.buildTcpRst(orig, ihl)

    /** Check if a context-aware firewall rule should block this app right now. */
    private fun shouldBlockByContext(rule: com.hostshield.data.model.FirewallRule, pkg: String): Boolean =
        ContextFirewallPolicy.shouldBlock(
            blockScreenOff = rule.blockScreenOff,
            blockBackground = rule.blockBackground,
            blockMetered = rule.blockMetered,
            packageName = pkg,
            isScreenOn = ContextState.isScreenOn,
            foregroundPackage = ContextState.foregroundPackage,
            isMetered = ContextState.isMetered,
        )

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
        upstreamServer: String,
        skipThreatIntelChecks: Boolean = false,
        /**
         * True when [respBytes] was served from the DNS cache (fresh hit,
         * serve-stale, or fail-closed stale). Cache-origin responses must NOT be
         * re-inserted: `dnsCache.put` recomputes the TTL from the stored (never
         * decremented) TTL bytes, so re-putting on every read resets expiry,
         * defeats prefetch (`needsPrefetch` never trips), and promotes an expired
         * stale answer back to a full-length fresh TTL. Only genuine live-upstream
         * answers should (re)populate the cache.
         */
        isFromCache: Boolean = false
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
        if (threatIntelEnabled && !skipThreatIntelChecks) {
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
        if (!isFromCache) {
            dnsCache.put(domain, DnsPacketBuilder.parseQueryType(dns), respBytes)
        }

        return PostForwardResult(blocked = false)
    }

    private suspend fun serveStale(
        dns: ByteArray,
        domain: String,
        orig: ByteArray,
        headerOffset: Int,
        isV6: Boolean,
        app: Pair<String, String> = Pair("", ""),
        skipThreatIntelChecks: Boolean = false
    ) {
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
        val stale = dnsCache.getStale(domain, qtypeNum, txId)
        // Decision lives in EncryptedFailurePolicy so the "never plaintext" rule is
        // covered by a test instead of only by review.
        val action = EncryptedFailurePolicy.decide(
            encryptedTransport = true,
            staleAvailable = stale != null,
        )
        if (action == EncryptedFailurePolicy.Action.SERVE_STALE && stale != null) {
            PrivacyLog.i(TAG, "SERVE-STALE $domain (upstream failed, returning expired cache)")
            val pfResult = postForwardChecks(
                stale,
                dns,
                domain,
                app,
                latencyMs = 0,
                upstreamServer = "DNS stale cache",
                skipThreatIntelChecks = skipThreatIntelChecks,
                isFromCache = true
            )
            if (pfResult.blocked) {
                if (pfResult.blockResponse != null) wrapAndSend(orig, headerOffset, isV6, pfResult.blockResponse)
                return
            }
            wrapAndSend(orig, headerOffset, isV6, stale)
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
    private suspend fun failClosedEncrypted(
        dns: ByteArray,
        domain: String,
        orig: ByteArray,
        ihl: Int,
        transport: String,
        wrapV6: Boolean = false,
        v6Hdr: Int = 0,
        app: Pair<String, String> = Pair("", ""),
        skipThreatIntelChecks: Boolean = false
    ) {
        val headerOffset = if (wrapV6) v6Hdr else ihl
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val txId = if (dns.size >= 2) byteArrayOf(dns[0], dns[1]) else byteArrayOf(0, 0)
        val stale = dnsCache.getStale(domain, qtypeNum, txId)
        if (stale != null) {
            PrivacyLog.i(TAG, "FAIL-CLOSED $transport $domain — serving stale cache (no plaintext fallback)")
            val pfResult = postForwardChecks(
                stale,
                dns,
                domain,
                app,
                latencyMs = 0,
                upstreamServer = "$transport stale cache",
                skipThreatIntelChecks = skipThreatIntelChecks,
                isFromCache = true
            )
            if (pfResult.blocked) {
                if (pfResult.blockResponse != null) wrapAndSend(orig, headerOffset, wrapV6, pfResult.blockResponse)
                return
            }
            wrapAndSend(orig, headerOffset, wrapV6, stale)
            return
        }
        PrivacyLog.w(TAG, "FAIL-CLOSED $transport $domain — SERVFAIL (encrypted DNS failed, refusing plaintext fallback)")
        wrapAndSend(orig, headerOffset, wrapV6, DnsPacketBuilder.buildServfail(dns))
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
        // Content-filter / safe-search / threat-intel / parental toggles were
        // previously read only at startVpn(), so changing them while protection
        // ran had no effect until a full restart — contradicting the "changes
        // take effect immediately" copy on those screens. Reload them live too.
        filterConfigJob?.cancel()
        filterConfigJob = serviceScope.launch {
            combine(
                prefs.threatIntelEnabled.map { it.toString() },
                prefs.safeSearchEnabled.map { it.toString() },
                prefs.contentFilterCategories.map { it.sorted().joinToString(",") },
                prefs.parentalEnabled.map { it.toString() },
                prefs.parentalAgeProfile,
                prefs.blockResponseType,
                prefs.ipv4Redirect,
                prefs.ipv6Redirect,
            ) { values -> values.joinToString("|") }
                .distinctUntilChanged()
                .drop(1)
                .collect { applyLiveFilterConfig() }
        }
    }

    private suspend fun applyLiveFilterConfig() {
        threatIntelEnabled = prefs.threatIntelEnabled.first()
        safeSearchEnabled = prefs.safeSearchEnabled.first()
        if (safeSearchEnabled) safeSearchEnforcer.startBackgroundRefresh()
        else safeSearchEnforcer.stopBackgroundRefresh()
        contentFilterCategories = prefs.contentFilterCategories.first()
            .mapNotNull { name -> try { ContentCategory.valueOf(name) } catch (_: Exception) { null } }
            .toSet()
        try {
            parentalControlManager.loadState()
        } catch (e: Exception) {
            Log.w(TAG, "Parental control live reload failed: ${e.message}")
        }
        // Block-response shape and redirect targets were also read only at
        // startVpn(), so editing them in Settings did nothing until a restart.
        blockResponseType = prefs.blockResponseType.first()
        ipv4Redirect = prefs.ipv4Redirect.first()
        ipv6Redirect = prefs.ipv6Redirect.first()
        PrivacyLog.i(TAG, "Filter config reloaded live: " +
            "threatIntel=$threatIntelEnabled, safeSearch=$safeSearchEnabled, " +
            "contentCategories=${contentFilterCategories.size}, " +
            "blockResponse=$blockResponseType")
    }

    private suspend fun applyLiveDnsConfig() {
        useDoH = prefs.dohEnabled.first()
        dohProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
        useDoT = prefs.dotEnabled.first()
        dotProvider = DotResolver.Provider.fromId(prefs.dotProvider.first())
        useDoQ = if (com.hostshield.BuildConfig.DEBUG) prefs.doqEnabled.first() else false
        doqProvider = DoqResolver.Provider.fromId(prefs.doqProvider.first())
        val customDns = prefs.getUpstreamDnsList()
        upstreamDnsServers = if (customDns.isNotEmpty()) customDns else UPSTREAM_DNS.toList()
        // Flush cache so subsequent queries use the newly selected resolver
        // instead of answers cached from the previous one.
        dnsCache.clear()
        dnsForwarder.clear()
        PrivacyLog.i(TAG, "DNS config reloaded live: " +
            "DoH=${if (useDoH) dohProvider.name else "off"}, " +
            "DoT=${if (useDoT) dotProvider.name else "off"}, " +
            "DoQ=${if (useDoQ) doqProvider.name else "off"}, " +
            "upstream=${upstreamDnsServers.joinToString(",")}")
    }

    private suspend fun forwardEncrypted(
        dns: ByteArray,
        domain: String,
        orig: ByteArray,
        ihl: Int,
        app: Pair<String, String> = Pair("", ""),
        wrapV6: Boolean = false,
        v6Hdr: Int = 0,
        skipThreatIntelChecks: Boolean = false,
    ) {
        val qtypeNum = DnsPacketBuilder.parseQueryType(dns)
        val resolved = dnsForwarder.resolve(dns, domain)
        if (resolved.shared) {
            PrivacyLog.d(TAG, "DEDUP HIT $domain (${DnsPacketBuilder.queryTypeLabel(qtypeNum)})")
        }
        sendResolvedResponse(
            resolved.value,
            dns,
            domain,
            orig,
            if (wrapV6) v6Hdr else ihl,
            wrapV6,
            app,
            skipThreatIntelChecks,
        )
    }

    // ── DNS Answer Cache (Heuristic UID Attribution) ────────

    private suspend fun refreshDnsCacheOnly(
        dns: ByteArray,
        domain: String,
        app: Pair<String, String> = Pair("", ""),
        skipThreatIntelChecks: Boolean = false,
    ) {
        val resolved = dnsForwarder.resolve(dns, domain).value
        if (resolved is UpstreamResolveResult.Success) {
            val response = patchDnsTransactionId(resolved.response, dns)
            postForwardChecks(
                response,
                dns,
                domain,
                app,
                resolved.latencyMs,
                resolved.upstreamServer,
                skipThreatIntelChecks,
            )
        }
    }

    private suspend fun sendResolvedResponse(
        result: UpstreamResolveResult,
        dns: ByteArray,
        domain: String,
        orig: ByteArray,
        headerOffset: Int,
        isV6: Boolean,
        app: Pair<String, String>,
        skipThreatIntelChecks: Boolean
    ) {
        when (result) {
            is UpstreamResolveResult.Success -> {
                val response = patchDnsTransactionId(result.response, dns)
                val pfResult = postForwardChecks(
                    response,
                    dns,
                    domain,
                    app,
                    result.latencyMs,
                    result.upstreamServer,
                    skipThreatIntelChecks
                )
                if (pfResult.blocked) {
                    if (pfResult.blockResponse != null) wrapAndSend(orig, headerOffset, isV6, pfResult.blockResponse)
                    return
                }
                wrapAndSend(orig, headerOffset, isV6, response)
            }
            is UpstreamResolveResult.EncryptedFailure -> {
                failClosedEncrypted(
                    dns,
                    domain,
                    orig,
                    headerOffset,
                    result.transport,
                    isV6,
                    headerOffset,
                    app,
                    skipThreatIntelChecks
                )
            }
            UpstreamResolveResult.PlaintextFailure -> {
                serveStale(dns, domain, orig, headerOffset, isV6, app, skipThreatIntelChecks)
            }
        }
    }

    private fun patchDnsTransactionId(response: ByteArray, query: ByteArray): ByteArray {
        val copy = response.copyOf()
        if (copy.size >= 2 && query.size >= 2) {
            copy[0] = query[0]
            copy[1] = query[1]
        }
        return copy
    }

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

            // Periodic eviction: first drop stale entries, then enforce a hard
            // size cap by oldest timestamp so a high-cardinality burst (many
            // distinct fresh IPs within the TTL window) can't grow the map
            // without bound when nothing is stale enough to purge.
            if (cached > 0 && dnsAnswerCache.size > 500) {
                dnsAnswerCache.entries.removeAll { now - it.value.second > DNS_ANSWER_CACHE_TTL_MS }
                if (dnsAnswerCache.size > DNS_ANSWER_CACHE_MAX) {
                    val excess = dnsAnswerCache.size - DNS_ANSWER_CACHE_MAX
                    dnsAnswerCache.entries
                        .sortedBy { it.value.second }
                        .take(excess)
                        .forEach { dnsAnswerCache.remove(it.key) }
                }
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

    // ── Stats ────────────────────────────────────────────────

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

    private fun updateNotification(blocked: Int) = vpnNotificationController.update(blocked)
}
