package com.hostshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hostshield.MainActivity
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// HostShield v1.0.0 - VPN DNS Blocking Service
//
// Architecture: DNS-only interception
// - Routes only the fake DNS subnet (10.120.0.0/24) through the TUN
// - System DNS queries arrive on the TUN because addDnsServer points there
// - All non-DNS traffic bypasses the VPN entirely (no packet drops)
// - Blocked queries get an NXDOMAIN response written back to TUN
// - Allowed queries are forwarded to the real upstream DNS via a protected socket

@AndroidEntryPoint
class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.hostshield.VPN_START"
        const val ACTION_STOP = "com.hostshield.VPN_STOP"
        const val CHANNEL_ID = "hostshield_vpn"
        const val NOTIFICATION_ID = 1
        private const val VPN_ADDRESS = "10.120.0.1"
        private const val VPN_ROUTE = "10.120.0.0"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_ALT = "1.1.1.1"
    }

    @Inject lateinit var dnsLogDao: DnsLogDao
    @Inject lateinit var blockStatsDao: BlockStatsDao
    @Inject lateinit var blocklist: BlocklistHolder
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var repository: HostShieldRepository
    @Inject lateinit var downloader: SourceDownloader

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var excludedApps = setOf<String>()

    // Write channel to serialize TUN writes from multiple coroutines
    private val writeChannel = Channel<ByteArray>(Channel.BUFFERED)

    // Stats
    private var blockedCount = 0
    private var allowedCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(0),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else 0
                )
                serviceScope.launch { startVpn() }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun setExcludedApps(apps: Set<String>) {
        excludedApps = apps
    }

    fun getBlockedCount(): Int = blockedCount

    private suspend fun startVpn() {
        if (isRunning) return

        try {
            // Load excluded apps from preferences
            excludedApps = prefs.excludedApps.first()

            // If blocklist is empty (e.g. after boot), rebuild from sources
            if (blocklist.domains.isEmpty()) {
                rebuildBlocklist()
            }

            val builder = Builder()
                .setSession("HostShield")
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VPN_ADDRESS)
                // Only route the VPN subnet — DNS queries to 10.120.0.1 arrive on TUN.
                // All other traffic (HTTP, HTTPS, etc.) bypasses the VPN entirely.
                .addRoute(VPN_ROUTE, 24)
                .setBlocking(true)

            // Exclude apps from VPN
            excludedApps.forEach { pkg ->
                try { builder.addDisallowedApplication(pkg) }
                catch (_: PackageManager.NameNotFoundException) { }
            }
            try { builder.addDisallowedApplication(packageName) }
            catch (_: Exception) { }

            vpnInterface = builder.establish() ?: run {
                stopSelf()
                return
            }

            isRunning = true

            // Launch the write serializer
            serviceScope.launch { writeLoop() }
            // Launch the packet reader/processor
            serviceScope.launch { readLoop() }

        } catch (e: Exception) {
            stopVpn()
        }
    }

    /** Rebuild the in-memory blocklist from sources and user rules. */
    private suspend fun rebuildBlocklist() {
        try {
            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()

            for (source in sources) {
                val result = downloader.download(source)
                result.onSuccess { dl ->
                    if (!dl.notModified) {
                        val parsed = HostsParser.parse(dl.content)
                        parsed.forEach { allDomains.add(it.hostname) }
                    }
                }
            }

            // Add exact user block rules
            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }

            // Remove exact allow rules
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }

            // Load wildcard rules
            val wildcards = repository.getEnabledWildcards()

            blocklist.update(allDomains, wildcards)
        } catch (_: Exception) { }
    }

    /** Reads packets from TUN and processes DNS queries. */
    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface ?: return@withContext
        val input = FileInputStream(vpnFd.fileDescriptor)
        val packet = ByteArray(VPN_MTU)

        while (isRunning) {
            try {
                val length = input.read(packet)
                if (length <= 0) {
                    delay(1)
                    continue
                }

                // Only process IPv4 UDP packets to port 53
                if (!isIpv4UdpDns(packet, length)) continue

                val ihl = (packet[0].toInt() and 0x0F) * 4
                val dnsPayload = extractDnsPayload(packet, length, ihl) ?: continue
                val queryDomain = parseDnsQueryDomain(dnsPayload) ?: continue
                val queryType = parseDnsQueryType(dnsPayload)

                val isBlocked = isDomainBlocked(queryDomain)

                // Log asynchronously
                serviceScope.launch {
                    try {
                        val appInfo = resolveApp(packet, ihl)
                        dnsLogDao.insert(
                            DnsLogEntry(
                                hostname = queryDomain,
                                blocked = isBlocked,
                                appPackage = appInfo.first,
                                appLabel = appInfo.second,
                                queryType = queryType
                            )
                        )
                        // Aggregate daily stats
                        aggregateStats(isBlocked)
                    } catch (_: Exception) { }
                }

                if (isBlocked) {
                    val nxResponse = buildNxdomainResponse(dnsPayload) ?: continue
                    val wrapped = wrapDnsResponseInIp(packet, ihl, nxResponse) ?: continue
                    writeChannel.send(wrapped)
                    blockedCount++
                    if (blockedCount % 100 == 0) updateNotification(blockedCount)
                } else {
                    // Forward via protected socket in a child coroutine
                    val packetCopy = packet.copyOf(length)
                    val ihlCopy = ihl
                    serviceScope.launch {
                        forwardDnsQuery(dnsPayload, packetCopy, ihlCopy)
                    }
                    allowedCount++
                }
            } catch (e: Exception) {
                if (!isRunning) break
                delay(10)
            }
        }
    }

    /** Serializes all writes to the TUN file descriptor. */
    private suspend fun writeLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface ?: return@withContext
        val output = FileOutputStream(vpnFd.fileDescriptor)

        for (packet in writeChannel) {
            if (!isRunning) break
            try {
                output.write(packet)
            } catch (_: Exception) {
                if (!isRunning) break
            }
        }
    }

    private fun isDomainBlocked(domain: String): Boolean {
        val d = domain.lowercase()
        val domains = blocklist.domains
        val wildcards = blocklist.wildcardRules
        // Exact match
        if (d in domains) return true
        if (d.startsWith("www.") && d.removePrefix("www.") in domains) return true
        // Wildcard rules
        if (wildcards.isNotEmpty()) {
            // Check allow wildcards first (override)
            for (rule in wildcards) {
                if (rule.type == RuleType.ALLOW && HostsParser.matchesWildcard(d, rule.hostname)) return false
            }
            // Check block wildcards
            for (rule in wildcards) {
                if (rule.type == RuleType.BLOCK && HostsParser.matchesWildcard(d, rule.hostname)) return true
            }
        }
        return false
    }

    // ── Packet parsing ──────────────────────────────────────

    private fun isIpv4UdpDns(packet: ByteArray, length: Int): Boolean {
        if (length < 28) return false
        val versionIhl = packet[0].toInt() and 0xFF
        if (versionIhl shr 4 != 4) return false // Not IPv4
        if (packet[9].toInt() and 0xFF != 17) return false // Not UDP
        val ihl = (versionIhl and 0x0F) * 4
        if (length < ihl + 8) return false
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        return dstPort == DNS_PORT
    }

    private fun extractDnsPayload(packet: ByteArray, length: Int, ihl: Int): ByteArray? {
        val udpStart = ihl
        if (length < udpStart + 8) return null
        val udpLength = ((packet[udpStart + 4].toInt() and 0xFF) shl 8) or
                (packet[udpStart + 5].toInt() and 0xFF)
        val dnsStart = udpStart + 8
        val dnsLength = udpLength - 8
        if (dnsLength < 12 || dnsStart + dnsLength > length) return null
        return packet.copyOfRange(dnsStart, dnsStart + dnsLength)
    }

    private fun parseDnsQueryDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        var offset = 12
        val parts = mutableListOf<String>()
        while (offset < dns.size) {
            val labelLen = dns[offset].toInt() and 0xFF
            if (labelLen == 0) break
            if (labelLen > 63 || offset + 1 + labelLen > dns.size) return null
            parts.add(String(dns, offset + 1, labelLen, Charsets.US_ASCII))
            offset += 1 + labelLen
        }
        return if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else null
    }

    private fun parseDnsQueryType(dns: ByteArray): String {
        if (dns.size < 14) return "?"
        var offset = 12
        // Skip QNAME
        while (offset < dns.size) {
            val labelLen = dns[offset].toInt() and 0xFF
            if (labelLen == 0) { offset++; break }
            offset += 1 + labelLen
        }
        if (offset + 2 > dns.size) return "?"
        val qtype = ((dns[offset].toInt() and 0xFF) shl 8) or (dns[offset + 1].toInt() and 0xFF)
        return when (qtype) {
            1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 15 -> "MX"
            16 -> "TXT"; 2 -> "NS"; 6 -> "SOA"; 33 -> "SRV"
            65 -> "HTTPS"; else -> "TYPE$qtype"
        }
    }

    // ── DNS response construction ───────────────────────────

    private fun buildNxdomainResponse(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val response = query.copyOf()
        // Byte 2: QR=1 (response), OPCODE=0 (standard), AA=1 (authoritative), TC=0, RD=copy
        val rdBit = query[2].toInt() and 0x01 // preserve RD
        response[2] = (0x84 or rdBit).toByte() // QR=1, AA=1, RD=preserved
        // Byte 3: RA=1 (recursion available), RCODE=3 (NXDOMAIN)
        response[3] = 0x83.toByte() // RA=1, RCODE=3
        // Zero answer, authority, additional counts
        response[6] = 0; response[7] = 0 // ANCOUNT
        response[8] = 0; response[9] = 0 // NSCOUNT
        response[10] = 0; response[11] = 0 // ARCOUNT
        return response
    }

    private fun wrapDnsResponseInIp(
        originalPacket: ByteArray,
        ihl: Int,
        dnsResponse: ByteArray
    ): ByteArray? {
        try {
            val totalLength = ihl + 8 + dnsResponse.size
            val response = ByteArray(totalLength)

            // Copy and modify IP header
            System.arraycopy(originalPacket, 0, response, 0, ihl)
            // Swap src <-> dst IP addresses
            System.arraycopy(originalPacket, 12, response, 16, 4)
            System.arraycopy(originalPacket, 16, response, 12, 4)
            // Update total length
            response[2] = ((totalLength shr 8) and 0xFF).toByte()
            response[3] = (totalLength and 0xFF).toByte()
            // TTL
            response[8] = 64

            // UDP header: swap ports
            response[ihl + 0] = originalPacket[ihl + 2]
            response[ihl + 1] = originalPacket[ihl + 3]
            response[ihl + 2] = originalPacket[ihl + 0]
            response[ihl + 3] = originalPacket[ihl + 1]
            // UDP length
            val udpLen = 8 + dnsResponse.size
            response[ihl + 4] = ((udpLen shr 8) and 0xFF).toByte()
            response[ihl + 5] = (udpLen and 0xFF).toByte()
            response[ihl + 6] = 0 // Zero UDP checksum
            response[ihl + 7] = 0

            // DNS payload
            System.arraycopy(dnsResponse, 0, response, ihl + 8, dnsResponse.size)

            // Recalculate IP checksum
            response[10] = 0; response[11] = 0
            var sum = 0L
            for (i in 0 until ihl step 2) {
                sum += ((response[i].toInt() and 0xFF) shl 8) or (response[i + 1].toInt() and 0xFF)
            }
            while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
            val checksum = sum.inv().toInt() and 0xFFFF
            response[10] = ((checksum shr 8) and 0xFF).toByte()
            response[11] = (checksum and 0xFF).toByte()

            return response
        } catch (_: Exception) {
            return null
        }
    }

    // ── DNS forwarding ──────────────────────────────────────

    private suspend fun forwardDnsQuery(dnsPayload: ByteArray, originalPacket: ByteArray, ihl: Int) {
        try {
            val socket = DatagramSocket()
            protect(socket) // Critical: prevent VPN loop

            val dnsServer = InetAddress.getByName(UPSTREAM_DNS)
            val request = DatagramPacket(dnsPayload, dnsPayload.size, dnsServer, DNS_PORT)
            socket.soTimeout = 5000
            socket.send(request)

            val buf = ByteArray(1500)
            val responsePacket = DatagramPacket(buf, buf.size)
            socket.receive(responsePacket)
            socket.close()

            val dnsResponse = buf.copyOf(responsePacket.length)
            val wrapped = wrapDnsResponseInIp(originalPacket, ihl, dnsResponse) ?: return
            writeChannel.send(wrapped)
        } catch (_: Exception) { }
    }

    // ── App resolution ──────────────────────────────────────

    private fun resolveApp(packet: ByteArray, ihl: Int): Pair<String, String> {
        // Source IP from the packet could be used with ConnectivityManager
        // For now, return empty — proper UID resolution requires TrafficStats
        return "" to ""
    }

    // ── Stats aggregation ───────────────────────────────────

    private suspend fun aggregateStats(wasBlocked: Boolean) {
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val existing = blockStatsDao.getStatsByDate(today) ?: BlockStats(date = today)
            blockStatsDao.upsert(
                existing.copy(
                    blockedCount = existing.blockedCount + if (wasBlocked) 1 else 0,
                    allowedCount = existing.allowedCount + if (!wasBlocked) 1 else 0,
                    totalQueries = existing.totalQueries + 1
                )
            )
        } catch (_: Exception) { }
    }

    // ── VPN lifecycle ───────────────────────────────────────

    private fun stopVpn() {
        isRunning = false
        writeChannel.close()
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notifications ───────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "HostShield VPN", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows VPN blocking status"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(blocked: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DnsVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HostShield Active")
            .setContentText(if (blocked > 0) "$blocked queries blocked" else "DNS filtering active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(blocked: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(blocked))
    }
}
