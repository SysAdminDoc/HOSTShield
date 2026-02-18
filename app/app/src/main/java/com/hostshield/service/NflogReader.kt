package com.hostshield.service

import android.content.Context
import android.util.Log
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.model.ConnectionLogEntry
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads iptables log events to populate the connection log.
 *
 * Supports two logging backends (auto-detected):
 * 1. NFLOG target -- netlink-based, no dmesg spam. Preferred.
 *    Read via `nflog` netlink or by checking iptables counters.
 * 2. LOG target -- writes to kernel ring buffer (dmesg).
 *    Fallback for Samsung and other kernels missing nfnetlink module.
 *
 * IptablesManager's reject chain installs whichever target is available
 * using `NFLOG ... || LOG ...` shell fallback.
 *
 * LOG output format (iptables LOG with --log-uid):
 *   [timestamp] HSBlock IN= OUT=wlan0 SRC=10.0.0.5 DST=142.250.80.46
 *   LEN=60 TOS=0x00 PREC=0x00 TTL=64 PROTO=TCP SPT=43210 DPT=443
 *   UID=10234
 *
 * We always tail dmesg regardless of which target was used, since both
 * NFLOG and LOG output to the kernel ring buffer on most kernels, and
 * dmesg is the most portable collection method without native code.
 */
@Singleton
class NflogReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionLogDao: ConnectionLogDao
) {
    companion object {
        private const val TAG = "NflogReader"
        private const val NFLOG_PREFIX = "HSBlock"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dmesgJob: Job? = null
    private var dmesgProcess: Process? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _liveCount = MutableStateFlow(0)
    val liveBlockCount: StateFlow<Int> = _liveCount.asStateFlow()

    // uid -> (packageName, appLabel) cache
    private val uidAppCache = ConcurrentHashMap<Int, Pair<String, String>>()

    // Dedup: hash of recent entries to avoid duplicate logging
    private val recentHashes = LinkedHashSet<Long>()
    private val maxRecentHashes = 500

    private var logTarget = "UNKNOWN" // "NFLOG", "LOG", or "NONE"

    /**
     * Detect which logging target the kernel supports and ensure it's installed.
     * IptablesManager's reject chain already tries `NFLOG ... || LOG ...`
     * but we verify here and install a supplementary LOG rule if needed
     * (NFLOG doesn't always write to dmesg, which is what we tail).
     */
    suspend fun installLogRules() {
        // Probe: is NFLOG available?
        val nflogProbe = Shell.cmd(
            "iptables -A OUTPUT -j NFLOG --nflog-prefix HSProbe --nflog-group 41 2>&1 && " +
            "iptables -D OUTPUT -j NFLOG --nflog-prefix HSProbe --nflog-group 41 2>/dev/null"
        ).exec()

        if (nflogProbe.isSuccess) {
            logTarget = "NFLOG"
            // NFLOG may not write to dmesg on all kernels. Add a supplementary
            // LOG rule so we can tail dmesg reliably as well.
            Shell.cmd(
                "iptables -I hs-reject -j LOG --log-prefix \"$NFLOG_PREFIX \" --log-uid 2>/dev/null || true",
                "ip6tables -I hs-reject -j LOG --log-prefix \"$NFLOG_PREFIX \" --log-uid 2>/dev/null || true"
            ).exec()
            Log.i(TAG, "Log target: NFLOG (with LOG supplement for dmesg tailing)")
        } else {
            // Probe: is LOG available?
            val logProbe = Shell.cmd(
                "iptables -A OUTPUT -j LOG --log-prefix HSProbe 2>&1 && " +
                "iptables -D OUTPUT -j LOG --log-prefix HSProbe 2>/dev/null"
            ).exec()

            if (logProbe.isSuccess) {
                logTarget = "LOG"
                // Ensure LOG rule is in hs-reject (IptablesManager may have already added it)
                Shell.cmd(
                    "iptables -I hs-reject -j LOG --log-prefix \"$NFLOG_PREFIX \" --log-uid 2>/dev/null || true",
                    "ip6tables -I hs-reject -j LOG --log-prefix \"$NFLOG_PREFIX \" --log-uid 2>/dev/null || true"
                ).exec()
                Log.i(TAG, "Log target: LOG (NFLOG unavailable — Samsung kernel?)")
            } else {
                logTarget = "NONE"
                Log.w(TAG, "No log target available (neither NFLOG nor LOG). Connection logging disabled.")
            }
        }
    }

    fun start() {
        if (dmesgJob?.isActive == true) return

        dmesgJob = scope.launch {
            _isRunning.value = true
            installLogRules()
            if (logTarget == "NONE") {
                Log.w(TAG, "No log target — connection logging unavailable")
                _isRunning.value = false
                return@launch
            }
            try {
                tailDmesg()
            } catch (_: CancellationException) { }
            catch (e: Exception) { Log.e(TAG, "NFLOG reader failed: ${e.message}") }
            finally { _isRunning.value = false }
        }
    }

    fun stop() {
        dmesgJob?.cancel(); dmesgJob = null
        dmesgProcess?.destroy(); dmesgProcess = null
        recentHashes.clear()
    }

    /**
     * Tail dmesg for iptables LOG output lines containing our prefix.
     * `dmesg -w` streams new kernel messages in real-time.
     */
    private suspend fun tailDmesg() {
        // Clear old dmesg to avoid replaying stale entries
        Shell.cmd("dmesg -C 2>/dev/null || true").exec()

        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "dmesg -w 2>/dev/null"))
        dmesgProcess = proc
        val reader = proc.inputStream.bufferedReader()

        // Regex patterns for iptables LOG output
        val logPattern = Regex("""$NFLOG_PREFIX\s+""")
        val srcPattern = Regex("""SRC=([0-9a-f:.]+)""", RegexOption.IGNORE_CASE)
        val dstPattern = Regex("""DST=([0-9a-f:.]+)""", RegexOption.IGNORE_CASE)
        val dptPattern = Regex("""DPT=(\d+)""")
        val sptPattern = Regex("""SPT=(\d+)""")
        val protoPattern = Regex("""PROTO=(\w+)""")
        val uidPattern = Regex("""UID=(\d+)""")
        val outPattern = Regex("""OUT=(\w*)""")

        try {
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (!logPattern.containsMatchIn(line)) continue

                try {
                    val dst = dstPattern.find(line)?.groupValues?.get(1) ?: ""
                    val dpt = dptPattern.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val proto = protoPattern.find(line)?.groupValues?.get(1) ?: "TCP"
                    val uid = uidPattern.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    val iface = outPattern.find(line)?.groupValues?.get(1) ?: ""

                    // Dedup check
                    val hash = (dst.hashCode().toLong() * 31 + dpt) * 31 + uid
                    val now = System.currentTimeMillis()
                    val timeHash = hash * 31 + (now / 2000) // 2-second window
                    val isDuplicate = synchronized(recentHashes) {
                        if (timeHash in recentHashes) {
                            true
                        } else {
                            recentHashes.add(timeHash)
                            if (recentHashes.size > maxRecentHashes)
                                recentHashes.iterator().let { it.next(); it.remove() }
                            false
                        }
                    }
                    if (isDuplicate) continue

                    val (pkg, label) = if (uid > 0) resolvePackage(uid) else ("" to "")

                    connectionLogDao.insert(ConnectionLogEntry(
                        uid = uid,
                        packageName = pkg,
                        appLabel = label,
                        destination = dst,
                        port = dpt,
                        protocol = proto,
                        action = "REJECT",
                        interfaceName = iface,
                        timestamp = now
                    ))

                    _liveCount.value++

                } catch (e: Exception) { Log.w(TAG, "Parse error: ${e.message}") }
            }
        } catch (_: CancellationException) { }
        catch (e: Exception) { Log.w(TAG, "dmesg reader stopped: ${e.message}") }
        finally { proc.destroy(); dmesgProcess = null }
    }

    private fun resolvePackage(uid: Int): Pair<String, String> {
        uidAppCache[uid]?.let { return it }
        try {
            val pm = context.packageManager
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return ("uid:$uid" to "UID $uid")
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
            val result = pkg to label
            if (uidAppCache.size > 200) {
                uidAppCache.keys().toList().take(100).forEach { uidAppCache.remove(it) }
            }
            uidAppCache[uid] = result
            return result
        } catch (_: Exception) { return "" to "" }
    }
}
