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
 * Architecture (3 coroutines):
 *
 *  1. DNS PROXY on 127.0.0.1:5454 -- receives all DNS queries via iptables
 *     NAT redirect. Parses hostname from raw DNS packets, checks blocklist,
 *     returns NXDOMAIN for blocked domains or forwards upstream. Logs every
 *     query to Room database.
 *
 *  2. UID RESOLVER -- enables Android DnsResolver verbose logging via
 *     `service call dnsresolver 10 i32 0`, then tails logcat for resolv/
 *     DnsProxyListener tags. These log lines contain both the hostname AND
 *     the requesting app's UID (extracted via SO_PEERCRED inside netd).
 *     Builds a hostname->UID cache for the proxy to use.
 *
 *  3. DUMPSYS POLLER (fallback) -- polls `dumpsys dnsresolver` every 3s
 *     for the DnsQueryLog ring buffer. Catches queries the logcat reader
 *     may have missed. Also enriches entries already in the database.
 *
 * On start: disables Private DNS (forces netd to use port 53), installs
 * iptables NAT redirect, enables verbose resolver logging.
 * On stop: restores Private DNS, removes iptables, resets log level.
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
    private var logcatJob: Job? = null
    private var dumpsysJob: Job? = null
    private var proxySocket: DatagramSocket? = null
    private var logcatProcess: Process? = null

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
    private val pendingBlockedStats = java.util.concurrent.atomic.AtomicInteger(0)
    private val pendingAllowedStats = java.util.concurrent.atomic.AtomicInteger(0)

    fun start() {
        if (proxyJob?.isActive == true) return

        scope.launch {
            // Read logging preference
            loggingEnabled = try { prefs.dnsLogging.first() } catch (_: Exception) { true }
            Log.i(TAG, "Root DNS starting (logging=$loggingEnabled)")

            // Step 1: Disable Private DNS so netd uses plain port 53
            disablePrivateDns()

            // Step 2: Resolve upstream DNS
            upstreamDns = resolveUpstreamDns()
            Log.i(TAG, "Upstream DNS: ${upstreamDns.hostAddress}")

            // Step 3: Enable verbose DnsResolver logging
            enableVerboseLogging()

            // Step 4: Install iptables NAT redirect
            removeIptablesRules()
            installIptablesRules()
        }

        // Start periodic stats flusher (every 2s)
        startStatsFlusher()

        // Coroutine 1: Tail logcat for UID attribution
        logcatJob = scope.launch {
            delay(800)
            readLogcatDns()
        }

        // Coroutine 2: Fallback dumpsys poller
        dumpsysJob = scope.launch {
            delay(2000)
            pollDumpsys()
        }

        // Coroutine 3: DNS proxy
        proxyJob = scope.launch {
            delay(1000) // let setup complete
            _isRunning.value = true
            try {
                runDnsProxy()
            } catch (_: CancellationException) { }
            catch (e: Exception) { Log.e(TAG, "Proxy failed: ${e.message}", e) }
            finally { _isRunning.value = false }
        }
    }

    fun stop() {
        logcatJob?.cancel(); logcatJob = null
        dumpsysJob?.cancel(); dumpsysJob = null
        proxyJob?.cancel(); proxyJob = null
        logFlushJob?.cancel(); logFlushJob = null
        try { proxySocket?.close() } catch (_: Exception) { }
        proxySocket = null
        logcatProcess?.destroy(); logcatProcess = null
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
            restorePrivateDns()
            resetLogLevel()
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

    // ---- Verbose DnsResolver Logging ----------------------------

    private suspend fun enableVerboseLogging() {
        // Transaction 10 = setLogSeverity, i32 0 = VERBOSE
        val r = Shell.cmd("service call dnsresolver 10 i32 0").exec()
        if (r.isSuccess) Log.i(TAG, "DnsResolver verbose logging enabled")
        else Log.w(TAG, "Failed to enable verbose logging: ${r.err}")
    }

    private suspend fun resetLogLevel() {
        // Reset to WARNING (default)
        Shell.cmd("service call dnsresolver 10 i32 3").exec()
    }

    // ---- iptables Management ------------------------------------

    private fun natRule(): String {
        val myUid = android.os.Process.myUid()
        return "-p udp --dport 53 -m owner ! --uid-owner $myUid " +
            "-j DNAT --to-destination 127.0.0.1:$PROXY_PORT"
    }

    private suspend fun installIptablesRules() {
        val myUid = android.os.Process.myUid()
        // 1. Redirect all DNS (UDP port 53) to local proxy
        Shell.cmd("iptables -t nat -I OUTPUT ${natRule()}").exec()
        Shell.cmd("ip6tables -t nat -I OUTPUT ${natRule()}").exec()

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
            val a = Shell.cmd("iptables -t nat -D OUTPUT ${natRule()} 2>/dev/null").exec()
            val b = Shell.cmd("ip6tables -t nat -D OUTPUT ${natRule()} 2>/dev/null").exec()
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
    }

    // ---- Logcat UID Reader --------------------------------------

    /**
     * With verbose logging enabled, DnsResolver logs queries with UIDs:
     *   resolv  : res_nsend: ... uid=10234 host=example.com
     *   DnsProxyListener: ... uid 10234 ... example.com
     *   resolv  : getaddrinfo: ... host=example.com ... uid=10234
     * Format varies by Android version; we use flexible regex.
     */
    private suspend fun readLogcatDns() {
        // Clear old logcat
        Shell.cmd("logcat -c -b main 2>/dev/null").exec()

        val su = Runtime.getRuntime().exec(arrayOf(
            "su", "-c",
            "logcat -v brief -b main -s resolv:V DnsProxyListener:V DnsResolver:V netd:V 2>/dev/null"
        ))
        logcatProcess = su
        val reader = su.inputStream.bufferedReader()

        // Multiple regex patterns for different Android versions
        val patterns = arrayOf(
            // Android 12+: "resolv  : ... uid=10234 ... example.com"
            Regex("""uid[=: ]+(\d+).*?([a-z0-9][-a-z0-9.]+\.[a-z]{2,})""", RegexOption.IGNORE_CASE),
            // Reverse: "example.com ... uid=10234"
            Regex("""([a-z0-9][-a-z0-9.]+\.[a-z]{2,}).*?uid[=: ]+(\d+)""", RegexOption.IGNORE_CASE),
            // Android 10-11: "getaddrinfo ... host=example.com ... uid 10234"
            Regex("""host[=: ]+([a-z0-9][-a-z0-9.]+\.[a-z]{2,}).*?uid[=: ]+(\d+)""", RegexOption.IGNORE_CASE)
        )

        try {
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break

                for (pattern in patterns) {
                    val match = pattern.find(line) ?: continue

                    var uid: Int? = null
                    var host: String? = null

                    // First pattern: uid first, host second
                    // Second pattern: host first, uid second
                    // Third pattern: host first, uid second
                    if (pattern == patterns[0]) {
                        uid = match.groupValues[1].toIntOrNull()
                        host = match.groupValues[2].lowercase()
                    } else {
                        host = match.groupValues[1].lowercase()
                        uid = match.groupValues[2].toIntOrNull()
                    }

                    if (uid != null && uid > 0 && host != null && host.length >= 4
                        && !host.endsWith(".arpa") && !host.endsWith(".local")
                    ) {
                        hostnameUidMap[host] = uid to System.currentTimeMillis()

                        // Enrich any pending DB entries
                        enrichPendingEntry(host, uid)
                    }
                    break
                }

                // Evict stale cache entries
                if (hostnameUidMap.size > 1000) {
                    val cutoff = System.currentTimeMillis() - 30_000L
                    hostnameUidMap.entries.removeAll { it.value.second < cutoff }
                }
            }
        } catch (_: CancellationException) { }
        catch (e: Exception) { Log.w(TAG, "Logcat reader stopped: ${e.message}") }
        finally { su.destroy(); logcatProcess = null }
    }

    /**
     * When the logcat reader finds a UID for a hostname, update any
     * recently-inserted DB entries that were missing the app info.
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
        val socket = DatagramSocket(PROXY_PORT, InetAddress.getByName("127.0.0.1"))
        proxySocket = socket
        socket.soTimeout = 0

        Log.i(TAG, "DNS proxy on 127.0.0.1:$PROXY_PORT")

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
        socket.close()
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
                val nx = buildNxdomainResponse(data)
                withContext(Dispatchers.IO) {
                    synchronized(socket) {
                        socket.send(DatagramPacket(nx, nx.size, clientAddr, clientPort))
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

            // Resolve app from logcat/dumpsys UID cache
            val (appPkg, appLabel) = resolveAppByHostname(hostname)

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

    private fun buildNxdomainResponse(query: ByteArray): ByteArray {
        if (query.size < 12) return query
        val r = query.copyOf()
        r[2] = ((0x80 or 0x04) or (query[2].toInt() and 0x01)).toByte()
        r[3] = (0x80 or 0x03).toByte()
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
