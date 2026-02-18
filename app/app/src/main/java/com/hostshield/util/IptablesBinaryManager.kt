package com.hostshield.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages iptables/ip6tables binary resolution.
 *
 * System iptables varies wildly across OEMs:
 * - Some ship iptables-nft shims that don't support all match/target modules
 * - Some have iptables in /system/bin, others in /system/xbin
 * - Some Samsung devices require iptables from /sbin or Magisk overlay
 *
 * Resolution order:
 * 1. Bundled binary in app's private files (if extracted from assets)
 * 2. Magisk-provided iptables (/data/adb/magisk/busybox iptables)
 * 3. System iptables (via $PATH)
 *
 * AFWall+ and InviZible Pro bundle their own iptables v1.8.x for reliability.
 * We follow the same pattern but fall back gracefully if no bundled binary.
 *
 * To bundle: place static arm64/arm iptables binary in assets/bin/iptables-<abi>.
 * The extract() method copies it to app filesDir/bin/ and makes it executable.
 */
@Singleton
class IptablesBinaryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "IptablesBin"
        private const val BIN_DIR = "bin"
        private const val IPTABLES_NAME = "iptables"
        private const val IP6TABLES_NAME = "ip6tables"
    }

    // Cached resolved paths
    @Volatile private var iptablesPath: String = "iptables"
    @Volatile private var ip6tablesPath: String = "ip6tables"
    @Volatile private var resolved = false

    /**
     * Resolve the best available iptables binary.
     * Call once during startup (e.g., in Application.onCreate or first root access).
     */
    fun resolve() {
        if (resolved) return

        // 1. Check for bundled binary
        val bundledDir = File(context.filesDir, BIN_DIR)
        val bundledIpt = File(bundledDir, IPTABLES_NAME)
        val bundledIp6 = File(bundledDir, IP6TABLES_NAME)

        if (bundledIpt.exists() && bundledIpt.canExecute()) {
            iptablesPath = bundledIpt.absolutePath
            ip6tablesPath = if (bundledIp6.exists() && bundledIp6.canExecute()) {
                bundledIp6.absolutePath
            } else {
                // Many iptables builds include ip6tables as a symlink or the same binary
                bundledIpt.absolutePath
            }
            resolved = true
            Log.i(TAG, "Using bundled: $iptablesPath")
            return
        }

        // 2. Probe system locations
        val systemPaths = arrayOf(
            "/system/bin/iptables",
            "/system/xbin/iptables",
            "/sbin/iptables",
            "/vendor/bin/iptables"
        )
        for (path in systemPaths) {
            if (File(path).exists()) {
                iptablesPath = path
                val ip6path = path.replace("iptables", "ip6tables")
                ip6tablesPath = if (File(ip6path).exists()) ip6path else "ip6tables"
                resolved = true
                Log.i(TAG, "Using system: $iptablesPath")
                return
            }
        }

        // 3. Fallback to PATH
        iptablesPath = "iptables"
        ip6tablesPath = "ip6tables"
        resolved = true
        Log.i(TAG, "Using PATH fallback: iptables")
    }

    /** Get resolved iptables path. */
    fun iptables(): String {
        if (!resolved) resolve()
        return iptablesPath
    }

    /** Get resolved ip6tables path. */
    fun ip6tables(): String {
        if (!resolved) resolve()
        return ip6tablesPath
    }

    /**
     * Extract bundled iptables from assets to app private storage.
     * Call this if you've placed binaries in assets/bin/.
     *
     * @return true if extraction succeeded, false if no bundled binary available
     */
    fun extractFromAssets(): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        val abiShort = when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("arm") -> "arm"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> return false
        }

        val assetName = "bin/iptables-$abiShort"
        return try {
            val outDir = File(context.filesDir, BIN_DIR)
            outDir.mkdirs()
            val outFile = File(outDir, IPTABLES_NAME)

            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile.setExecutable(true, false)

            // Create ip6tables symlink (same binary responds to argv[0])
            val ip6File = File(outDir, IP6TABLES_NAME)
            if (!ip6File.exists()) {
                Shell.cmd("ln -sf ${outFile.absolutePath} ${ip6File.absolutePath}").exec()
            }

            resolved = false // force re-resolve
            resolve()
            Log.i(TAG, "Extracted bundled iptables for $abiShort")
            true
        } catch (e: Exception) {
            Log.w(TAG, "No bundled iptables for $abiShort: ${e.message}")
            false
        }
    }

    /**
     * Get version info for diagnostic display.
     */
    fun getVersionInfo(): String {
        return try {
            val result = Shell.cmd("${iptables()} --version 2>&1").exec()
            val ver = result.out.firstOrNull() ?: "unknown"
            "iptables: ${iptables()} ($ver)"
        } catch (e: Exception) {
            "iptables: ${iptables()} (version check failed)"
        }
    }
}
