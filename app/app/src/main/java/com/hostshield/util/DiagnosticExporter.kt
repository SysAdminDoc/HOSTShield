package com.hostshield.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.hostshield.BuildConfig
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import com.hostshield.service.DnsCache
import com.hostshield.service.DohPinManifest
import com.hostshield.service.IptablesManager
import com.hostshield.util.PrivateDnsDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic Report Generator
 *
 * Generates a comprehensive text report for debugging user-reported issues.
 * The report includes device info, app state, blocklist stats, recent DNS
 * logs, iptables state, and VPN configuration — everything needed to
 * diagnose problems without back-and-forth.
 *
 * Privacy: DNS logs are truncated to last 50 entries. No user credentials
 * or browsing history beyond recent DNS queries.
 */
@Singleton
class DiagnosticExporter @Inject constructor(
    private val prefs: AppPreferences,
    private val blocklist: BlocklistHolder,
    private val iptablesManager: IptablesManager,
    private val dnsLogDao: DnsLogDao,
    private val connectionLogDao: ConnectionLogDao,
    private val privateDnsDetector: PrivateDnsDetector,
    private val diagnosticEventStore: DiagnosticEventStore
) {
    companion object {
        private const val TAG = "DiagExport"
    }

    /**
     * Generate a diagnostic report and return the file path.
     */
    suspend fun generate(context: Context): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder(8192)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        sb.appendLine("╔══════════════════════════════════════════════════╗")
        sb.appendLine("║       HostShield Diagnostic Report               ║")
        sb.appendLine("╚══════════════════════════════════════════════════╝")
        sb.appendLine("Generated: $ts")
        sb.appendLine()

        // ── Device Info ──
        sb.appendLine("── Device ──────────────────────────────────────────")
        sb.appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Build: ${Build.DISPLAY}")
        sb.appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Kernel: ${System.getProperty("os.version") ?: "unknown"}")
        sb.appendLine()

        // ── App Info ──
        sb.appendLine("── App ─────────────────────────────────────────────")
        sb.appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
        sb.appendLine("Flavor: ${BuildConfig.FLAVOR}")
        sb.appendLine()

        // ── Configuration ──
        sb.appendLine("── Configuration ───────────────────────────────────")
        try {
            sb.appendLine("Enabled: ${prefs.isEnabled.first()}")
            sb.appendLine("Block method: ${prefs.blockMethod.first()}")
            sb.appendLine("DoH enabled: ${prefs.dohEnabled.first()}")
            sb.appendLine("DoH provider: ${prefs.dohProvider.first()}")
            sb.appendLine("DoT enabled: ${prefs.dotEnabled.first()}")
            sb.appendLine("DoT provider: ${prefs.dotProvider.first()}")
            sb.appendLine("DoQ enabled: ${prefs.doqEnabled.first()}")
            sb.appendLine("DoQ provider: ${prefs.doqProvider.first()}")
            sb.appendLine("DoQ maturity: ${ExperimentalEngineDisclosure.DOQ_DIAGNOSTIC}")
            sb.appendLine("WireGuard DNS enabled: ${prefs.wireGuardEnabled.first()}")
            sb.appendLine("WireGuard DNS configured: ${prefs.wireGuardEndpoint.first().isNotBlank() && prefs.wireGuardDnsIp.first().isNotBlank()}")
            sb.appendLine("WireGuard DNS maturity: ${ExperimentalEngineDisclosure.WIREGUARD_DIAGNOSTIC}")
            sb.appendLine("Resolver default policy: ${ExperimentalEngineDisclosure.PRODUCTION_DEFAULT}")
            sb.appendLine("DNS trap: ${prefs.dnsTrapEnabled.first()}")
            sb.appendLine("Block response: ${prefs.blockResponseType.first()}")
            sb.appendLine("DNS logging: ${prefs.dnsLogging.first()}")
            sb.appendLine("Custom upstream: ${prefs.customUpstreamDns.first().ifEmpty { "(default)" }}")
            sb.appendLine("Firewall enabled: ${prefs.networkFirewallEnabled.first()}")
            sb.appendLine("Firewall mode: ${prefs.firewallMode.first()}")
            val remoteDohVersionLabel =
                prefs.getRemoteDohVersion().takeIf { it > 0 }?.toString() ?: "(none)"
            sb.appendLine("Remote DoH bypass version: $remoteDohVersionLabel")
            sb.appendLine("Remote DoH bypass domains: ${countCsvValues(prefs.getRemoteDohDomains())}")
            sb.appendLine("Remote DoH bypass wildcards: ${countCsvValues(prefs.getRemoteDohWildcards())}")
        } catch (e: Exception) {
            sb.appendLine("Error reading prefs: ${e.message}")
        }
        sb.appendLine()

        // -- DoH Pin Manifest --
        sb.appendLine("-- DoH Pin Manifest -------------------------------")
        DohPinManifest.diagnosticSummaryLines().forEach { sb.appendLine(it) }
        sb.appendLine()

        // ── Blocklist ──
        sb.appendLine("── Blocklist ───────────────────────────────────────")
        sb.appendLine("Domains loaded: ${blocklist.domainCount}")
        sb.appendLine("Blocked count: ${blocklist.getBlockedCount()}")
        sb.appendLine()

        // ── DNS Cache ──
        sb.appendLine("── DNS Cache ───────────────────────────────────────")
        sb.appendLine("(Cache stats available when VPN is running)")
        sb.appendLine()

        // ── Firewall State ──
        sb.appendLine("── Firewall (iptables) ─────────────────────────────")
        sb.appendLine("Active: ${iptablesManager.isActive.value}")
        sb.appendLine("Rule count: ${iptablesManager.lastApplyCount.value}")
        try {
            val dump = iptablesManager.getDiagnosticDump()
            sb.appendLine(dump)
        } catch (e: Exception) {
            sb.appendLine("Diagnostic dump error: ${e.message}")
        }
        sb.appendLine()

        // ── Recent DNS Logs ──
        sb.appendLine("── Recent DNS Queries (last 50) ────────────────────")
        try {
            val logs = dnsLogDao.getRecentLogs(50).first()
            if (logs.isEmpty()) {
                sb.appendLine("(no DNS logs)")
            } else {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                for (log in logs) {
                    val time = sdf.format(Date(log.timestamp))
                    val status = if (log.blocked) "BLK" else "OK "
                    val app = log.appLabel.ifEmpty { log.appPackage.ifEmpty { "?" } }
                    sb.appendLine("  $time [$status] ${log.hostname} (${log.queryType}) [$app]")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Error reading logs: ${e.message}")
        }
        sb.appendLine()

        // ── Network State ──
        sb.appendLine("── Network State ───────────────────────────────────")
        try {
            val privateDns = privateDnsDetector.detect()
            sb.appendLine("Private DNS mode: ${privateDns.mode}")
            sb.appendLine("Private DNS host: ${privateDns.hostname}")
        } catch (e: Exception) {
            sb.appendLine("Private DNS check error: ${e.message}")
        }
        sb.appendLine()

        // ── VPN Interface ──
        sb.appendLine("── VPN Interface ───────────────────────────────────")
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("cat", "/proc/net/if_inet6"))
            val output = proc.inputStream.bufferedReader().readText()
            if (output.contains("tun")) {
                sb.appendLine("TUN interface found:")
                output.lines().filter { it.contains("tun") }.forEach { sb.appendLine("  $it") }
            } else {
                sb.appendLine("No TUN interface detected")
            }
        } catch (e: Exception) {
            sb.appendLine("VPN interface check error: ${e.message}")
        }
        sb.appendLine()

        // ── System DNS ──
        sb.appendLine("── System DNS Servers ──────────────────────────────")
        try {
            val prop = Runtime.getRuntime().exec(arrayOf("getprop", "net.dns1"))
            sb.appendLine("net.dns1: ${prop.inputStream.bufferedReader().readText().trim()}")
            val prop2 = Runtime.getRuntime().exec(arrayOf("getprop", "net.dns2"))
            sb.appendLine("net.dns2: ${prop2.inputStream.bufferedReader().readText().trim()}")
        } catch (e: Exception) {
            sb.appendLine("DNS property check error: ${e.message}")
        }
        sb.appendLine()

        // -- Local Diagnostic Events --
        sb.appendLine("-- Diagnostic Events (last 50) -----------------------")
        try {
            val events = diagnosticEventStore.readRecent(50)
            if (events.isEmpty()) {
                sb.appendLine("(no diagnostic events)")
            } else {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                for (event in events) {
                    val time = sdf.format(Date(event.timestampMs))
                    val fields = event.fields.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    val suffix = when {
                        event.message.isNotBlank() && fields.isNotBlank() -> " -- ${event.message} ($fields)"
                        event.message.isNotBlank() -> " -- ${event.message}"
                        fields.isNotBlank() -> " -- $fields"
                        else -> ""
                    }
                    sb.appendLine("  $time [${event.type}]$suffix")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Diagnostic event read error: ${e.message}")
        }
        sb.appendLine()

        sb.appendLine("── End of Report ───────────────────────────────────")

        // Write to file
        val dir = File(context.cacheDir, "diagnostics")
        dir.mkdirs()
        val file = File(dir, "hostshield-diag-${System.currentTimeMillis()}.txt")
        file.writeText(sb.toString())
        Log.i(TAG, "Diagnostic report: ${file.absolutePath} (${file.length()} bytes)")
        file
    }

    /**
     * Generate a ZIP package containing the text report and raw event JSONL.
     */
    suspend fun generateZip(context: Context): File = withContext(Dispatchers.IO) {
        val report = generate(context)
        val eventsJsonl = diagnosticEventStore.readJsonlSnapshot()
        val remoteDohVersion = prefs.getRemoteDohVersion()
        val remoteDohDomainCount = countCsvValues(prefs.getRemoteDohDomains())
        val remoteDohWildcardCount = countCsvValues(prefs.getRemoteDohWildcards())
        val dir = File(context.cacheDir, "diagnostics")
        dir.mkdirs()

        val zip = File(dir, "hostshield-diag-${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.addFileEntry("hostshield-diagnostic.txt", report)
            zos.addTextEntry("diagnostic-events.jsonl", eventsJsonl)
            zos.addTextEntry(
                "manifest.json",
                JSONObject()
                    .put("generated_at_ms", System.currentTimeMillis())
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
                    .put("event_count", eventsJsonl.lineSequence().filter { it.isNotBlank() }.count())
                    .put("doh_pin_manifest_version", DohPinManifest.VERSION)
                    .put("doh_pin_manifest_issued_on", DohPinManifest.ISSUED_ON)
                    .put("remote_doh_bypass_version", remoteDohVersion)
                    .put("remote_doh_bypass_domain_count", remoteDohDomainCount)
                    .put("remote_doh_bypass_wildcard_count", remoteDohWildcardCount)
                    .toString(2)
            )
        }

        Log.i(TAG, "Diagnostic package: ${zip.absolutePath} (${zip.length()} bytes)")
        zip
    }

    /**
     * Generate and share via Android share sheet.
     */
    suspend fun generateAndShare(context: Context) {
        try {
            val file = generateZip(context)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "HostShield Diagnostic Package")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Diagnostic Package")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}", e)
        }
    }

    private fun ZipOutputStream.addFileEntry(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipOutputStream.addTextEntry(entryName: String, content: String) {
        putNextEntry(ZipEntry(entryName))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun countCsvValues(value: String): Int =
        value.split(",").count { it.trim().isNotBlank() }
}
