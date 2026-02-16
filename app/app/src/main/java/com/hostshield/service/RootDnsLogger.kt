package com.hostshield.service

import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.database.BlockStatsDao
import com.hostshield.data.model.BlockStats
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.domain.BlocklistHolder
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root-mode DNS logger. Captures all DNS queries system-wide via tcpdump
 * running as root, parses hostnames, checks against blocklist, and logs
 * to the database — providing the same DNS log experience as VPN mode.
 */
@Singleton
class RootDnsLogger @Inject constructor(
    private val dnsLogDao: DnsLogDao,
    private val blockStatsDao: BlockStatsDao,
    private val blocklist: BlocklistHolder
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logJob: Job? = null
    private var tcpdumpProcess: Process? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Regex for tcpdump DNS query lines:
    // "12:34:56.789 IP 10.0.0.1.54321 > 8.8.8.8.53: 12345+ A? example.com. (32)"
    // Also handles: "... AAAA? example.com. ..."
    private val dnsQueryPattern = Regex(
        """>\s+\S+\.53:\s+\d+\+?\s+(A{1,4})\?\s+(\S+?)\.\s"""
    )

    fun start() {
        if (logJob?.isActive == true) return

        logJob = scope.launch {
            _isRunning.value = true
            try {
                // Use root shell to run tcpdump capturing only DNS queries
                // -n = no reverse DNS, -l = line-buffered, -i any = all interfaces
                // -Q out = outgoing only (queries, not responses)
                val result = Shell.cmd("which tcpdump 2>/dev/null || echo missing").exec()
                val hasTcpdump = result.out.firstOrNull()?.contains("missing") != true

                if (hasTcpdump) {
                    runTcpdumpLogger()
                } else {
                    // Fallback: poll dnsmasq/resolved logs via logcat
                    runLogcatLogger()
                }
            } catch (_: CancellationException) {
                // Normal shutdown
            } catch (_: Exception) {
                // Logger failed, silently degrade
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        logJob?.cancel()
        logJob = null
        tcpdumpProcess?.destroy()
        tcpdumpProcess = null
        scope.launch {
            Shell.cmd("killall tcpdump 2>/dev/null || true").exec()
        }
    }

    private suspend fun runTcpdumpLogger() {
        // Start tcpdump as a root process via su and read stdout line by line
        val su = Runtime.getRuntime().exec(arrayOf("su", "-c", "tcpdump -n -l -i any port 53 2>/dev/null"))
        tcpdumpProcess = su

        val reader = su.inputStream.bufferedReader()
        val seen = mutableMapOf<String, Long>() // debounce: hostname -> last_seen_ms

        try {
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) {
                    reader.readLine()
                } ?: break

                val match = dnsQueryPattern.find(line) ?: continue
                val queryType = match.groupValues[1]   // A or AAAA
                val hostname = match.groupValues[2].lowercase().trimEnd('.')

                // Skip noise
                if (hostname.isEmpty() || hostname.length < 4) continue
                if (hostname.endsWith(".local") || hostname.endsWith(".arpa")) continue
                if (hostname == "localhost") continue

                // Debounce: skip if we logged this hostname within the last 2 seconds
                val now = System.currentTimeMillis()
                val lastSeen = seen[hostname]
                if (lastSeen != null && now - lastSeen < 2000) continue
                seen[hostname] = now

                // Trim debounce map periodically
                if (seen.size > 500) {
                    val cutoff = now - 5000
                    seen.entries.removeAll { it.value < cutoff }
                }

                // Check blocklist
                val isBlocked = blocklist.isBlocked(hostname)

                // Log to database
                try {
                    dnsLogDao.insert(DnsLogEntry(
                        hostname = hostname,
                        blocked = isBlocked,
                        queryType = queryType,
                        timestamp = now
                    ))

                    // Aggregate stats
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val existing = blockStatsDao.getStatsByDate(today) ?: BlockStats(date = today)
                    blockStatsDao.upsert(existing.copy(
                        blockedCount = existing.blockedCount + if (isBlocked) 1 else 0,
                        allowedCount = existing.allowedCount + if (!isBlocked) 1 else 0,
                        totalQueries = existing.totalQueries + 1
                    ))
                } catch (_: Exception) { }
            }
        } finally {
            su.destroy()
            tcpdumpProcess = null
        }
    }

    /**
     * Fallback: use logcat to capture DNS resolution events from the system resolver.
     * Less comprehensive than tcpdump but works without tcpdump binary.
     */
    private suspend fun runLogcatLogger() {
        // Clear old logcat buffer first
        Shell.cmd("logcat -c").exec()

        val su = Runtime.getRuntime().exec(arrayOf(
            "su", "-c", "logcat -v time -s resolv:V DnsProxyListener:V NetworkMonitor:V 2>/dev/null"
        ))
        tcpdumpProcess = su

        val reader = su.inputStream.bufferedReader()
        // Pattern: "resolv  : DNS query for example.com"
        // Or: "DnsProxyListener: DNS query ... example.com ..."
        val dnsPattern = Regex("""(?:query|getaddrinfo|resolve)[^a-z]*([a-z0-9][-a-z0-9.]+\.[a-z]{2,})""", RegexOption.IGNORE_CASE)
        val seen = mutableMapOf<String, Long>()

        try {
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) {
                    reader.readLine()
                } ?: break

                val match = dnsPattern.find(line) ?: continue
                val hostname = match.groupValues[1].lowercase().trimEnd('.')
                if (hostname.isEmpty() || hostname.length < 4) continue
                if (hostname.endsWith(".local") || hostname.endsWith(".arpa")) continue

                val now = System.currentTimeMillis()
                val lastSeen = seen[hostname]
                if (lastSeen != null && now - lastSeen < 2000) continue
                seen[hostname] = now
                if (seen.size > 500) {
                    val cutoff = now - 5000
                    seen.entries.removeAll { it.value < cutoff }
                }

                val isBlocked = blocklist.isBlocked(hostname)
                try {
                    dnsLogDao.insert(DnsLogEntry(
                        hostname = hostname, blocked = isBlocked, timestamp = now
                    ))
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val existing = blockStatsDao.getStatsByDate(today) ?: BlockStats(date = today)
                    blockStatsDao.upsert(existing.copy(
                        blockedCount = existing.blockedCount + if (isBlocked) 1 else 0,
                        allowedCount = existing.allowedCount + if (!isBlocked) 1 else 0,
                        totalQueries = existing.totalQueries + 1
                    ))
                } catch (_: Exception) { }
            }
        } finally {
            su.destroy()
            tcpdumpProcess = null
        }
    }
}
