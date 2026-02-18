package com.hostshield.service

import android.content.Context
import android.util.Log
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root-mode DNS proxy with per-app attribution.
 *
 * Architecture (2 coroutines):
 *
 *  1. DNS PROXY on 127.0.0.1:5454 + [::1]:5454 -- receives all DNS queries
 *     via iptables NAT redirect. Parses hostname from raw DNS packets, checks
 *     blocklist, returns NXDOMAIN for blocked domains or forwards upstream.
 *     Logs every query to Room database. UID attribution uses /proc/net/udp
 *     port-to-UID mapping as a fast-path before falling back to the
 *     hostname->UID cache populated by the dumpsys poller.
 *
 *  2. DUMPSYS POLLER -- polls `dumpsys dnsresolver` every 3s for the
 *     DnsQueryLog ring buffer. Extracts hostname->UID mappings and enriches
 *     DB entries that were inserted without app info.
 *
 * UID attribution note: The previous logcat-based approach (`service call
 * dnsresolver 10 i32 0` to enable verbose logging, then tail logcat) was
 * removed in v1.7.0. The binder transaction code is an AOSP implementation
 * detail that varies across Android versions and OEM ROMs, and AOSP warns
 * it logs PII. The /proc/net + dumpsys approach is stable and portable.
 *
 * Security: route_localnet=1 is required for DNAT to loopback. Compensating
 * iptables INPUT rules block external access to 127.0.0.0/8 to mitigate
 * the CVE-2020-8558 attack vector (LAN hosts reaching loopback services).
 *
 * On start: disables Private DNS (forces netd to use port 53), installs
 * iptables NAT redirect with route_localnet hardening.
 * On stop: restores Private DNS, removes iptables, resets route_localnet.
 */
@Singleton
class RootDnsLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dnsLogDao: DnsLogDao,
    private val blockStatsDao: BlockStatsDao,
    private val blocklist: BlocklistHolder,
    private val prefs: AppPreferences
) {
    companion object {
        private const val TAG = "RootDnsLogger"
        private const val PROXY_PORT = 5454
        private const val DNS_PORT = 53
        private const val UPSTREAM_TIMEOUT_MS = 5000
        private const val DUMPSYS_POLL_MS = 3000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var proxyJob: Job? = null
    private var dumpsysJob: Job? = null
    private var proxySocket: DatagramSocket? = null
    private var proxySocket6: DatagramSocket? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // hostname -> (uid, timestampMs)
    private val hostnameUidMap = ConcurrentHashMap<String, Pair<Int, Long>>()
    // uid -> (packageName, appLabel)
    private val uidAppCache = ConcurrentHashMap<Int, Pair<String, String>>()
    // Recent queries from proxy that need UID enrichment
    private val pendingUidLookup = ConcurrentHashMap<String, Long>() // hostname -> dbId

    private var originalPrivateDnsMode: String? = null
    private var originalPrivateDnsSpecifier: String? = null
    private var upstreamDns: InetAddress = InetAddress.getByName("8.8.8.8")

    // Stats flusher job
    private var logFlushJob: Job? = null
    private var loggingEnabled = true
    private var blockResponseType = "nxdomain"
    private val pendingBlockedStats = java.util.concurrent.atomic.AtomicInteger(0)
    private val pendingAllowedStats = java.util.concurrent.atomic.AtomicInteger(0)

    fun start() {
        if (proxyJob?.isActive == true) return

        // Gate so the proxy waits until iptables is fully installed.
        val setupDone = CompletableDeferred<Unit>()

        scope.launch {
            try {
                // Read logging preference
                loggingEnabled = try { prefs.dnsLogging.first() } catch (_: Exception) { true }
                blockResponseType = try { prefs.blockResponseType.first() } catch (_: Exception) { "nxdomain" }
                Log.i(TAG, "Root DNS starting (logging=$loggingEnabled, blockResponse=$blockResponseType)")

                // Step 1: Disable Private DNS so netd uses plain port 53
                disablePrivateDns()

                // Step 2: Resolve upstream DNS
                upstreamDns = resolveUpstreamDns()
                Log.i(TAG, "Upstream DNS: ${upstreamDns.hostAddress}")

                // Step 3: Install iptables NAT redirect (with route_localnet hardening)
                removeIptablesRules()
                installIptablesRules()

                Log.i(TAG, "Setup complete — signalling proxy to start")
            } finally {
                setupDone.complete(Unit)
            }
        }

        // Start periodic stats flusher (every 2s)
        startStatsFlusher()

        // Coroutine 1: Dumpsys poller for UID attribution
        dumpsysJob = scope.launch {
            delay(2000)
            pollDumpsys()
        }

        // Coroutine 2: DNS proxy — waits for setup to finish before binding
        proxyJob = scope.launch {
            setupDone.await()  // block until iptables + Private DNS are ready
            _isRunning.value = true
            try {
                runDnsProxy()
            } catch (_: CancellationException) { }
            catch (e: Exception) { Log.e(TAG, "Proxy failed: ${e.message}", e) }
            finally { _isRunning.value = false }
        }
    }

    fun stop() {
        dumpsysJob?.cancel(); dumpsysJob = null
        proxyJob?.cancel(); proxyJob = null
        logFlushJob?.cancel(); logFlushJob = null
        try { proxySocket?.close() } catch (_: Exception) { }
        try { proxySocket6?.close() } catch (_: Exception) { }
        proxySocket = null
        proxySocket6 = null
        // Flush remaining stats synchronously before teardown
        try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                flushStats()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Final stats flush failed: ${e.message}")
        }
        hostnameUidMap.clear()
        pendingUidLookup.clear()
        scope.launch {
            removeIptablesRules()
            // Restore route_localnet to default (0) and remove compensating INPUT rules
            Shell.cmd(
                "sysctl -w net.ipv4.conf.all.route_localnet=0 2>/dev/null",
                "sysctl -w net.ipv4.conf.default.route_localnet=0 2>/dev/null",
                "iptables -D INPUT -d 127.0.0.0/8 ! -i lo -j DROP 2>/dev/null",
                "ip6tables -D INPUT -d ::1/128 ! -i lo -j DROP 2>/dev/null"
            ).exec()
            restorePrivateDns()
        }
    }

    // ---- Private DNS Management ---------------------------------

    private suspend fun disablePrivateDns() {
        try {
            originalPrivateDnsMode = Shell.cmd("settings get global private_dns_mode").exec()
                .out.firstOrNull()?.trim()
            originalPrivateDnsSpecifier = Shell.cmd("settings get global private_dns_specifier").exec()
                .out.firstOrNull()?.trim()
            if (originalPrivateDnsMode != "off") {
                Shell.cmd("settings put global private_dns_mode off").exec()
                Log.i(TAG, "Private DNS disabled (was: $originalPrivateDnsMode)")
                delay(1500) // allow system to switch DNS mode
            }
        } catch (e: Exception) { Log.w(TAG, "Failed to disable Private DNS: ${e.message}") }
    }

    private suspend fun restorePrivateDns() {
        try {
            val mode = originalPrivateDnsMode
            if (mode != null && mode != "off" && mode != "null") {
                Shell.cmd("settings put global private_dns_mode $mode").exec()
                val spec = originalPrivateDnsSpecifier
                if (!spec.isNullOrBlank() && spec != "null")
                    Shell.cmd("settings put global private_dns_specifier $spec").exec()
                Log.i(TAG, "Private DNS restored: $mode")
            }
            originalPrivateDnsMode = null
        } catch (_: Exception) { }
    }

    // ---- iptables Management ------------------------------------

    private fun natRule4(): String {
        val myUid = android.os.Process.myUid()
        return "-p udp --dport 53 -m owner ! --uid-owner $myUid " +
            "-j DNAT --to-destination 127.0.0.1:$PROXY_PORT"
    }

    private fun natRule6(): String {
        val myUid = android.os.Process.myUid()
        return "-p udp --dport 53 -m owner ! --uid-owner $myUid " +
            "-j DNAT --to-destination [::1]:$PROXY_PORT"
    }

    private suspend fun installIptablesRules() {
        val myUid = android.os.Process.myUid()

        // SECURITY: Compensating INPUT rules MUST be installed BEFORE enabling
        // route_localnet. Without these, setting route_localnet=1 allows hosts
        // on the same LAN to reach loopback-bound services on this device
        // (CVE-2020-8558 attack vector). These rules ensure only the loopback
        // interface can deliver packets to 127.0.0.0/8 and ::1.
        Shell.cmd(
            "iptables -I INPUT -d 127.0.0.0/8 ! -i lo -j DROP",
            "ip6tables -I INPUT -d ::1/128 ! -i lo -j DROP"
        ).exec()
        Log.i(TAG, "Compensating INPUT rules installed (loopback protection)")

        // Now enable route_localnet so the kernel allows DNAT to 127.0.0.1.
        // Without this sysctl, packets redirected to loopback are silently dropped.
        Shell.cmd(
            "sysctl -w net.ipv4.conf.all.route_localnet=1",
            "sysctl -w net.ipv4.conf.default.route_localnet=1"
        ).exec()
        Log.i(TAG, "route_localnet enabled (hardened with INPUT DROP rules)")

        // 1. Redirect all DNS (UDP port 53) to local proxy
        Shell.cmd("iptables -t nat -I OUTPUT ${natRule4()}").exec()
        Shell.cmd("ip6tables -t nat -I OUTPUT ${natRule6()}").exec()

        // 2. Block DNS-over-TLS (port 853) to prevent Private DNS bypass.
        // Apps/system using DoT will fail and fall back to port 53 (which we redirect).
        Shell.cmd("iptables -I OUTPUT -p tcp --dport 853 -m owner ! --uid-owner $myUid -j REJECT --reject-with tcp-reset 2>/dev/null || true").exec()
        Shell.cmd("ip6tables -I OUTPUT -p tcp --dport 853 -m owner ! --uid-owner $myUid -j REJECT --reject-with tcp-reset 2>/dev/null || true").exec()

        // 3. Block DNS-over-HTTPS to known DoH provider IPs (port 443).
        // This prevents apps from hardcoding DoH endpoints to bypass DNS filtering.
        val dohIps = arrayOf(
            "8.8.8.8", "8.8.4.4",                    // Google DoH
            "1.1.1.1", "1.0.0.1",                    // Cloudflare DoH
            "9.9.9.9", "149.112.112.112",             // Quad9 DoH
            "104.16.248.249", "104.16.249.249",       // Cloudflare CDN DoH
            "9.9.9.11", "149.112.112.11",             // Quad9 DoH alt
        )
        for (ip in dohIps) {
            Shell.cmd("iptables -I OUTPUT -p tcp -d $ip --dport 443 -m owner ! --uid-owner $myUid -j REJECT --reject-with tcp-reset 2>/dev/null || true").exec()
            // Also block QUIC (UDP 443) — prevents DNS-over-HTTP/3 and DNS-over-QUIC bypass
            Shell.cmd("iptables -I OUTPUT -p udp -d $ip --dport 443 -m owner ! --uid-owner $myUid -j REJECT --reject-with icmp-port-unreachable 2>/dev/null || true").exec()
        }

        // 4. Also redirect TCP DNS (port 53) — some apps use TCP for large responses
        Shell.cmd("iptables -t nat -I OUTPUT -p tcp --dport 53 -m owner ! --uid-owner $myUid -j DNAT --to-destination 127.0.0.1:$PROXY_PORT 2>/dev/null || true").exec()

        Log.i(TAG, "iptables rules installed (NAT redirect + DoT block + DoH block)")
    }

    private suspend fun removeIptablesRules() {
        val myUid = android.os.Process.myUid()
        for (i in 0..4) {
            val a = Shell.cmd("iptables -t nat -D OUTPUT ${natRule4()} 2>/dev/null").exec()
            val b = Shell.cmd("ip6tables -t nat -D OUTPUT ${natRule6()} 2>/dev/null").exec()
            if (!a.isSuccess && !b.isSuccess) break
        }
        // Clean up DoT blocking rules
        for (i in 0..2) {
            Shell.cmd("iptables -D OUTPUT -p tcp --dport 853 -m owner ! --uid-owner $myUid -j REJECT --reject-with tcp-reset 2>/dev/null").exec()
            Shell.cmd("ip6tables -D OUTPUT -p tcp --dport 853 -m owner ! --uid-owner $myUid -j REJECT --reject-with tcp-reset 2>/dev/null").exec()
        }
        // Clean up DoH blocking rules
        val dohIps = arrayOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112",
            "104.16.248.249", "104.16.249.249", "9.9.9.11", "149.112.112.11")
        for (ip in dohIps) {
            Shell.cmd("iptables -D OUTPUT -p tcp -d $ip --dport 443 -m owner ! --uid-owner $myUid -j REJECT --reject-with tcp-reset 2>/dev/null").exec()
            Shell.cmd("iptables -D OUTPUT -p udp -d $ip --dport 443 -m owner ! --uid-owner $myUid -j REJECT --reject-with icmp-port-unreachable 2>/dev/null").exec()
        }
        // Clean up TCP DNS redirect
        Shell.cmd("iptables -t nat -D OUTPUT -p tcp --dport 53 -m owner ! --uid-owner $myUid -j DNAT --to-destination 127.0.0.1:$PROXY_PORT 2>/dev/null").exec()
        // Clean up compensating loopback protection INPUT rules
        Shell.cmd(
            "iptables -D INPUT -d 127.0.0.0/8 ! -i lo -j DROP 2>/dev/null",
            "ip6tables -D INPUT -d ::1/128 ! -i lo -j DROP 2>/dev/null"
        ).exec()
    }

    // ---- /proc/net UID Fast-Path ---------------------------------

    /**
     * Fast-path UID lookup via /proc/net/udp and /proc/net/udp6.
     * When the proxy receives a DNAT'd packet, the original source port
     * may still be visible in the kernel's UDP socket table. This avoids
     * waiting for the 3s dumpsys poll cycle.
     *
     * /proc/net/udp format:
     *   sl  local_address:port rem_address:port st ... uid ...
     *   0:  0100007F:1234      00000000:0000   07 ... 10234 ...
     *
     * Returns UID > 0 on success, -1 on failure.
     */
    private fun findUidFromSourcePort(srcPort: Int): Int {
        if (srcPort <= 0) return -1
        val hex = String.format("%04X", srcPort).uppercase()
        for (path in arrayOf("/proc/net/udp", "/proc/net/udp6")) {
            try {
                for (line in java.io.File(path).readLines()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 8 && parts[1].uppercase().endsWith(":$hex")) {
                        val uid = parts[7].toIntOrNull() ?: -1
                        if (uid > 0) return uid
                    }
                }
            } catch (_: Exception) { }
        }
        return -1
    }

    /**
     * Resolve app for a query using all available attribution methods.
     * Priority: 1) /proc/net source port lookup, 2) hostname->UID cache
     * from dumpsys poller, 3) return empty (will be enriched later).
     */
    private fun resolveAppForQuery(hostname: String, clientPort: Int): Pair<String, String> {
        // Fast-path: check /proc/net for the client's source port
        val procUid = findUidFromSourcePort(clientPort)
        if (procUid > 0) {
            val result = resolvePackage(procUid)
            if (result.first.isNotEmpty()) {
                // Cache for future lookups of this hostname
                hostnameUidMap[hostname] = procUid to System.currentTimeMillis()
                return result
            }
        }

        // Fallback: dumpsys-populated hostname->UID cache
        return resolveAppByHostname(hostname)
    }

    /**
     * When the dumpsys poller (or /proc/net lookup) finds a UID for a hostname,
     * update any recently-inserted DB entries that were missing the app info.
     */
    private suspend fun enrichPendingEntry(hostname: String, uid: Int) {
        val id = pendingUidLookup.remove(hostname) ?: return
        val (pkg, label) = resolvePackage(uid)
        if (pkg.isNotEmpty()) {
            try {
                dnsLogDao.updateAppInfo(id, pkg, label)
            } catch (e: Exception) {
                Log.w(TAG, "UID enrichment failed for $hostname: ${e.message}")
            }
        }
    }

    // ---- Dumpsys Poller (fallback) ------------------------------

    private suspend fun pollDumpsys() {
        while (currentCoroutineContext().isActive) {
            try {
                val result = Shell.cmd("dumpsys dnsresolver 2>/dev/null").exec()
                if (result.isSuccess) parseDumpsysOutput(result.out)
            } catch (_: CancellationException) { break }
            catch (_: Exception) { }
            delay(DUMPSYS_POLL_MS)
        }
    }

    // DnsQueryLog formats vary:
    // "  10234 example.com A 0ms success"
    // "uid=10234 ... example.com"
    private val dumpTabPattern = Regex("""^\s*(\d{4,6})\s+([-a-z0-9.]+\.[a-z]{2,})""")
    private val dumpUidHostPattern = Regex("""uid[=: ]?(\d+).*?([a-z0-9][-a-z0-9.]+\.[a-z]{2,})""", RegexOption.IGNORE_CASE)

    private fun parseDumpsysOutput(lines: List<String>) {
        val now = System.currentTimeMillis()
        for (line in lines) {
            val lc = line.lowercase()
            if (lc.contains("config") || lc.contains("nameserver") || lc.startsWith("--")) continue

            val tabMatch = dumpTabPattern.find(line.trim())
            if (tabMatch != null) {
                val uid = tabMatch.groupValues[1].toIntOrNull()
                val host = tabMatch.groupValues[2].lowercase()
                if (uid != null && uid > 1000 && host.length >= 4) {
                    hostnameUidMap[host] = uid to now
                    scope.launch { enrichPendingEntry(host, uid) }
                    continue
                }
            }

            val uidHostMatch = dumpUidHostPattern.find(lc)
            if (uidHostMatch != null) {
                val uid = uidHostMatch.groupValues[1].toIntOrNull()
                val host = uidHostMatch.groupValues[2]
                if (uid != null && uid > 1000 && host.length >= 4
                    && !host.endsWith(".arpa") && !host.endsWith(".local")) {
                    hostnameUidMap[host] = uid to now
                    scope.launch { enrichPendingEntry(host, uid) }
                }
            }
        }
    }

    // ---- DNS Proxy ----------------------------------------------

    private suspend fun runDnsProxy() {
        // Bind IPv4 proxy socket with retry for quick stop/start cycles.
        var socket4: DatagramSocket? = null
        for (attempt in 1..5) {
            try {
                socket4 = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), PROXY_PORT))
                }
                break
            } catch (e: java.net.BindException) {
                Log.w(TAG, "Proxy IPv4 bind attempt $attempt failed: ${e.message}")
                if (attempt == 5) throw e
                delay(500L * attempt)
            }
        }
        proxySocket = socket4!!
        socket4.soTimeout = 0

        // Bind IPv6 proxy socket — receives DNS redirected via ip6tables.
        try {
            val s6 = DatagramSocket(null).apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(InetAddress.getByName("::1"), PROXY_PORT))
            }
            s6.soTimeout = 0
            proxySocket6 = s6
            Log.i(TAG, "DNS proxy on 127.0.0.1:$PROXY_PORT + [::1]:$PROXY_PORT")

            // Launch IPv6 receive loop as a child coroutine
            scope.launch {
                try { proxyReceiveLoop(s6) }
                catch (_: java.net.SocketException) { }
                catch (e: Exception) { Log.w(TAG, "IPv6 proxy ended: ${e.message}") }
            }
        } catch (e: Exception) {
            // IPv6 proxy is non-critical; log and continue with IPv4 only
            Log.w(TAG, "IPv6 proxy bind failed (IPv4 only): ${e.message}")
        }

        // IPv4 receive loop (main)
        proxyReceiveLoop(socket4)
        socket4.close()
    }

    /** Shared receive loop for both IPv4 and IPv6 proxy sockets. */
    private suspend fun proxyReceiveLoop(socket: DatagramSocket) {
        while (currentCoroutineContext().isActive) {
            try {
                val buf = ByteArray(1500)
                val pkt = DatagramPacket(buf, buf.size)
                withContext(Dispatchers.IO) { socket.receive(pkt) }
                val data = pkt.data.copyOf(pkt.length)
                val addr = pkt.address
                val port = pkt.port
                scope.launch { handleQuery(socket, data, addr, port) }
            } catch (_: java.net.SocketException) { break }
            catch (e: Exception) { Log.w(TAG, "Proxy recv: ${e.message}") }
        }
    }

    private suspend fun handleQuery(
        socket: DatagramSocket, data: ByteArray,
        clientAddr: InetAddress, clientPort: Int
    ) {
        try {
            val hostname = parseDnsHostname(data)
            if (hostname.isNullOrBlank()) {
                forwardAndRelay(socket, data, clientAddr, clientPort)
                return
            }

            val queryType = parseDnsQueryType(data)
            val isBlocked = blocklist.isBlocked(hostname)

            if (isBlocked) {
                val resp = buildBlockResponse(data, queryType)
                withContext(Dispatchers.IO) {
                    synchronized(socket) {
                        socket.send(DatagramPacket(resp, resp.size, clientAddr, clientPort))
                    }
                }
            } else {
                forwardAndRelay(socket, data, clientAddr, clientPort)
            }

            // Always count stats (even if logging disabled)
            if (isBlocked) pendingBlockedStats.incrementAndGet()
            else pendingAllowedStats.incrementAndGet()

            // Only write to DB if logging is enabled
            if (!loggingEnabled) return

            // Resolve app: /proc/net source-port fast-path, then dumpsys cache
            val (appPkg, appLabel) = resolveAppForQuery(hostname, clientPort)

            try {
                val entryId = dnsLogDao.insertAndGetId(DnsLogEntry(
                    hostname = hostname,
                    blocked = isBlocked,
                    queryType = queryType,
                    timestamp = System.currentTimeMillis(),
                    appPackage = appPkg,
                    appLabel = appLabel
                ))

                // If we don't have app info yet, queue for enrichment
                if (appPkg.isEmpty()) {
                    pendingUidLookup[hostname] = entryId
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB insert failed for $hostname: ${e.message}", e)
            }

        } catch (e: Exception) { Log.w(TAG, "Query handler: ${e.message}") }
    }

    private suspend fun forwardAndRelay(
        proxy: DatagramSocket, query: ByteArray,
        clientAddr: InetAddress, clientPort: Int
    ) {
        withContext(Dispatchers.IO) {
            var upstream: DatagramSocket? = null
            try {
                upstream = DatagramSocket()
                upstream.soTimeout = UPSTREAM_TIMEOUT_MS
                upstream.send(DatagramPacket(query, query.size, upstreamDns, DNS_PORT))
                val buf = ByteArray(1500)
                val resp = DatagramPacket(buf, buf.size)
                upstream.receive(resp)
                synchronized(proxy) {
                    proxy.send(DatagramPacket(buf, resp.length, clientAddr, clientPort))
                }
            } catch (e: Exception) { Log.w(TAG, "Forward: ${e.message}") }
            finally { try { upstream?.close() } catch (_: Exception) { } }
        }
    }

    // ---- App Resolution -----------------------------------------

    private fun resolveAppByHostname(hostname: String): Pair<String, String> {
        val (uid, _) = hostnameUidMap[hostname] ?: return "" to ""
        if (uid == 0) return "android" to "System"
        return resolvePackage(uid)
    }

    private fun resolvePackage(uid: Int): Pair<String, String> {
        uidAppCache[uid]?.let { return it }
        try {
            val pm = context.packageManager
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull()
                ?: return ("uid:$uid" to "UID $uid").also { uidAppCache[uid] = it }
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
            val result = pkg to label
            if (uidAppCache.size > 300) {
                uidAppCache.keys().toList().take(150).forEach { uidAppCache.remove(it) }
            }
            uidAppCache[uid] = result
            return result
        } catch (_: Exception) { }
        return "" to ""
    }

    // ---- DNS Parsing --------------------------------------------

    private fun parseDnsHostname(data: ByteArray): String? {
        if (data.size < 13) return null
        try {
            val sb = StringBuilder()
            var pos = 12
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) break
                if (len > 63) return null
                pos++
                if (pos + len > data.size) return null
                if (sb.isNotEmpty()) sb.append('.')
                for (i in 0 until len) sb.append(data[pos + i].toInt().toChar())
                pos += len
            }
            val h = sb.toString().lowercase()
            return if (h.length >= 4) h else null
        } catch (_: Exception) { return null }
    }

    private fun parseDnsQueryType(data: ByteArray): String {
        try {
            var pos = 12
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) { pos++; break }
                if (len > 63) { pos += 2; break }
                pos += 1 + len
            }
            if (pos + 2 > data.size) return "A"
            val qt = (data[pos].toInt() and 0xFF shl 8) or (data[pos + 1].toInt() and 0xFF)
            return when (qt) { 1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 65 -> "HTTPS"; else -> "TYPE$qt" }
        } catch (_: Exception) { return "A" }
    }

    /**
     * Build a block response based on the configured response type.
     * Dispatches to NXDOMAIN, zero-IP, or REFUSED builder.
     */
    private fun buildBlockResponse(query: ByteArray, queryType: String): ByteArray {
        return when (blockResponseType) {
            "zero_ip" -> buildZeroIpResponse(query, queryType) ?: buildNxdomainResponse(query)
            "refused" -> buildRefusedResponse(query)
            else -> buildNxdomainResponse(query) // "nxdomain" (default)
        }
    }

    private fun buildNxdomainResponse(query: ByteArray): ByteArray {
        if (query.size < 12) return query
        val r = query.copyOf()
        r[2] = ((0x80 or 0x04) or (query[2].toInt() and 0x01)).toByte()
        r[3] = (0x80 or 0x03).toByte()
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
        return r
    }

    /**
     * Zero-IP response: RCODE=0 (NOERROR) with A=0.0.0.0 or AAAA=::.
     * Only for A/AAAA queries; other types return null (caller falls back).
     */
    private fun buildZeroIpResponse(query: ByteArray, queryType: String): ByteArray? {
        if (query.size < 12) return null
        val rdata = when (queryType) {
            "A" -> byteArrayOf(0, 0, 0, 0)
            "AAAA" -> ByteArray(16)
            else -> return null
        }
        val rdataType = when (queryType) {
            "A" -> byteArrayOf(0, 1)
            "AAAA" -> byteArrayOf(0, 28)
            else -> return null
        }

        // Skip query name to find where answer starts
        var off = 12
        while (off < query.size) {
            val len = query[off].toInt() and 0xFF
            if (len == 0) { off++; break }
            if (len and 0xC0 == 0xC0) { off += 2; break }
            off += 1 + len
        }
        off += 4 // QTYPE + QCLASS

        // Build answer: pointer to query name + TYPE + CLASS + TTL + RDLENGTH + RDATA
        val answer = byteArrayOf(0xC0.toByte(), 0x0C) + rdataType + byteArrayOf(
            0, 1,                       // CLASS IN
            0, 0, 1, 0x2C.toByte(),     // TTL 300s
            (rdata.size shr 8 and 0xFF).toByte(), (rdata.size and 0xFF).toByte()
        ) + rdata

        val resp = ByteArray(off + answer.size)
        System.arraycopy(query, 0, resp, 0, off.coerceAtMost(query.size))
        if (resp.size >= 12) {
            resp[2] = (0x84 or (query[2].toInt() and 0x01)).toByte() // QR=1, AA=1, RD preserved
            resp[3] = 0x80.toByte()  // RA=1, RCODE=0 (NOERROR)
            resp[4] = 0; resp[5] = 1    // QDCOUNT=1
            resp[6] = 0; resp[7] = 1    // ANCOUNT=1
            resp[8] = 0; resp[9] = 0    // NSCOUNT=0
            resp[10] = 0; resp[11] = 0  // ARCOUNT=0
        }
        System.arraycopy(answer, 0, resp, off, answer.size)
        return resp
    }

    /** REFUSED response: RCODE=5. */
    private fun buildRefusedResponse(query: ByteArray): ByteArray {
        if (query.size < 12) return query
        val r = query.copyOf()
        r[2] = (0x84 or (query[2].toInt() and 0x01)).toByte()
        r[3] = 0x85.toByte()  // RA=1, RCODE=5
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
        return r
    }

    // ---- Upstream DNS -------------------------------------------

    private suspend fun resolveUpstreamDns(): InetAddress {
        // 1. Check user-configured custom DNS
        try {
            val custom = prefs.customUpstreamDns.first().trim()
            if (custom.isNotBlank()) {
                try { return InetAddress.getByName(custom) } catch (_: Exception) {
                    Log.w(TAG, "Invalid custom DNS: $custom, falling back")
                }
            }
        } catch (_: Exception) { }

        // 2. System DNS from getprop
        try {
            for (prop in arrayOf("net.dns1", "net.dns2")) {
                val ip = Shell.cmd("getprop $prop").exec().out.firstOrNull()?.trim()
                if (!ip.isNullOrBlank() && ip != "0.0.0.0" && ip != "127.0.0.1")
                    try { return InetAddress.getByName(ip) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }

        // 3. Fallback
        return InetAddress.getByName("8.8.8.8")
    }

    // ---- Stats --------------------------------------------------

    /** Periodic stats flusher — every 2 seconds. Crash-resistant. */
    private fun startStatsFlusher() {
        logFlushJob?.cancel()
        logFlushJob = scope.launch {
            Log.i(TAG, "Stats flusher started")
            while (isActive) {
                delay(2000)
                try {
                    flushStats()
                } catch (e: Exception) {
                    Log.e(TAG, "Stats flush cycle error: ${e.message}", e)
                }
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
}
