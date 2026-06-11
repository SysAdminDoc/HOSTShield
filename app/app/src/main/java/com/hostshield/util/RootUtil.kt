package com.hostshield.util

import android.content.Context
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Root utility helpers

@Singleton
class RootUtil @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnosticEvents: DiagnosticEventStore
) {

    companion object {
        const val HOSTS_PATH = "/system/etc/hosts"

        // RFC 1123 hostname + IPv4 dotted-quad. Anything else is rejected before
        // it reaches the root shell, preventing newline/backtick/`$()` injection.
        private val HOSTNAME_RE = Regex("^[a-zA-Z0-9][a-zA-Z0-9._\\-]{0,253}$")
        private val IPV4_RE = Regex("^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$")
        // IPv6 + ::1 + 0.0.0.0 + 127.0.0.1 all match either of the above when canonical.
        private val IPV6_RE = Regex("^[0-9a-fA-F:]+$")

        internal fun isValidHostname(name: String): Boolean =
            name.length in 1..253 && HOSTNAME_RE.matches(name)

        internal fun isValidRedirectIp(ip: String): Boolean =
            IPV4_RE.matches(ip) || IPV6_RE.matches(ip)
    }

    /** Temp file inside app-private cache (no root or SELinux issues). */
    private val tempFile: File get() = File(context.cacheDir, "hostshield_hosts_tmp")

    @Volatile private var cachedActivePath: String? = null

    /** Check if root access is available. Requests a shell if needed. */
    fun isRootAvailable(): Boolean {
        val granted = Shell.isAppGrantedRoot()
        if (granted != null) return granted
        return try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    /** Check if the device appears to use Magisk systemless hosts. */
    suspend fun isMagiskSystemless(): Boolean = withContext(Dispatchers.IO) {
        val result = Shell.cmd("[ -f /data/adb/modules/hosts/system/etc/hosts ] && echo yes || echo no").exec()
        result.out.firstOrNull()?.trim() == "yes"
    }

    /** Read current hosts file content. */
    suspend fun readHostsFile(): String = withContext(Dispatchers.IO) {
        val path = getActiveHostsPath()
        val result = Shell.cmd("cat \"$path\"").exec()
        if (result.isSuccess) result.out.joinToString("\n") else ""
    }

    /** Write new hosts file content atomically. */
    suspend fun writeHostsFile(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = getActiveHostsPath()
            val tmp = tempFile

            // Write to app-private cache dir (always writable, no root needed)
            tmp.writeText(content)
            val tmpPath = tmp.absolutePath

            // Backup current hosts
            Shell.cmd("cp \"$path\" \"${path}.bak\" 2>/dev/null || true").exec()

            if (path.startsWith("/system")) {
                val r = Shell.cmd(
                    "mount -o rw,remount /system",
                    "cp '$tmpPath' '$path'",
                    "chmod 644 '$path'",
                    "chown root:root '$path'",
                    "mount -o ro,remount /system"
                ).exec()
                if (!r.isSuccess) {
                    tmp.delete()
                    diagnosticEvents.recordBlocking(
                        DiagnosticEventType.ROOT_COMMAND_FAILED,
                        "Root hosts write failed",
                        mapOf("path" to path, "stderr" to r.err.joinToString().take(500))
                    )
                    return@withContext Result.failure(
                        Exception("Failed to write hosts: ${r.err.joinToString()}")
                    )
                }
            } else {
                val r = Shell.cmd(
                    "cp '$tmpPath' '$path'",
                    "chmod 644 '$path'",
                    "chown root:root '$path'"
                ).exec()
                if (!r.isSuccess) {
                    tmp.delete()
                    diagnosticEvents.recordBlocking(
                        DiagnosticEventType.ROOT_COMMAND_FAILED,
                        "Root hosts write failed",
                        mapOf("path" to path, "stderr" to r.err.joinToString().take(500))
                    )
                    return@withContext Result.failure(
                        Exception("Failed to write hosts: ${r.err.joinToString()}")
                    )
                }
            }

            tmp.delete()
            flushDnsCache()
            Result.success(Unit)
        } catch (e: Exception) {
            tempFile.delete()
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.ROOT_COMMAND_FAILED,
                "Root hosts write threw exception",
                mapOf("error" to (e.message ?: e.javaClass.simpleName))
            )
            Result.failure(e)
        }
    }

    /** Restore original hosts file from backup. */
    suspend fun restoreHostsFile(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = getActiveHostsPath()
            val backup = "${path}.bak"
            val hasBackup = Shell.cmd("[ -f \"$backup\" ] && echo yes || echo no").exec()
                .out.firstOrNull()?.trim() == "yes"

            if (hasBackup) {
                if (path.startsWith("/system")) {
                    Shell.cmd(
                        "mount -o rw,remount /system",
                        "cp \"$backup\" \"$path\"",
                        "mount -o ro,remount /system"
                    ).exec()
                } else {
                    Shell.cmd("cp \"$backup\" \"$path\"").exec()
                }
            } else {
                val tmp = tempFile
                tmp.writeText("127.0.0.1 localhost\n::1 localhost\n")
                val tmpPath = tmp.absolutePath
                if (path.startsWith("/system")) {
                    Shell.cmd(
                        "mount -o rw,remount /system",
                        "cp '$tmpPath' '$path'",
                        "chmod 644 '$path'",
                        "mount -o ro,remount /system"
                    ).exec()
                } else {
                    Shell.cmd("cp '$tmpPath' '$path'", "chmod 644 '$path'").exec()
                }
                tmp.delete()
            }

            flushDnsCache()
            Result.success(Unit)
        } catch (e: Exception) {
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.ROOT_COMMAND_FAILED,
                "Root hosts restore failed",
                mapOf("error" to (e.message ?: e.javaClass.simpleName))
            )
            Result.failure(e)
        }
    }

    /** Get line count of current hosts file. */
    suspend fun getHostsLineCount(): Int = withContext(Dispatchers.IO) {
        val path = getActiveHostsPath()
        val result = Shell.cmd("wc -l < \"$path\"").exec()
        result.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    /**
     * Hot-patch: append a single block entry to the hosts file without full rewrite.
     * Equivalent to AdAway's "block from log" behavior.
     */
    suspend fun appendHostEntry(hostname: String, redirectIp: String = "0.0.0.0"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Reject anything that isn't a strict hostname / IP — protects against
            // newline / `;` / `$(...)` / backslash injection through the root shell.
            if (!isValidHostname(hostname)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid hostname: $hostname")
                )
            }
            if (!isValidRedirectIp(redirectIp)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid redirect IP: $redirectIp")
                )
            }
            val path = getActiveHostsPath()
            val line = "$redirectIp $hostname"
            // Only append if not already present. grep -wF matches whole-word.
            val check = Shell.cmd("grep -qwF '$hostname' \"$path\"").exec()
            if (!check.isSuccess) {
                // Use printf to avoid 'echo' interpreting backslashes on some shells.
                Shell.cmd("printf '%s\\n' '$line' >> \"$path\"").exec()
                flushDnsCache()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hot-patch: remove all lines matching a hostname from the hosts file.
     * Uses Kotlin-side filtering instead of sed to avoid delimiter injection issues.
     */
    suspend fun removeHostEntry(hostname: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isValidHostname(hostname)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid hostname: $hostname")
                )
            }
            val path = getActiveHostsPath()
            val result = Shell.cmd("cat \"$path\"").exec()
            if (!result.isSuccess) return@withContext Result.failure(Exception("Cannot read hosts file"))

            // Token-aware match: strip trailing comments at '#', split on whitespace,
            // case-insensitive equality on token[1]. Catches `0.0.0.0 host # comment`,
            // `0.0.0.0\thost`, multi-host lines (`0.0.0.0 a b c`).
            val targetLc = hostname.lowercase()
            val filtered = result.out.filter { rawLine ->
                val noComment = rawLine.substringBefore('#').trim()
                if (noComment.isEmpty()) return@filter true
                val tokens = noComment.split(Regex("\\s+"))
                if (tokens.size < 2) return@filter true
                // Drop the line only when one of the hostname tokens matches.
                tokens.drop(1).none { it.lowercase() == targetLc }
            }
            val tmp = tempFile
            tmp.writeText(filtered.joinToString("\n") + "\n")
            Shell.cmd("cp '${tmp.absolutePath}' '$path'", "chmod 644 '$path'").exec()
            tmp.delete()
            flushDnsCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun flushDnsCache() = withContext(Dispatchers.IO) {
        Shell.cmd(
            "ndc resolver clearnetdns || true",
            "settings put global captive_portal_mode 0 || true"
        ).exec()
    }

    private suspend fun getActiveHostsPath(): String {
        cachedActivePath?.let { return it }
        val resolved = if (isMagiskSystemless()) {
            "/data/adb/modules/hosts/system/etc/hosts"
        } else {
            HOSTS_PATH
        }
        cachedActivePath = resolved
        return resolved
    }

    suspend fun getSystemInfo(): Map<String, String> = withContext(Dispatchers.IO) {
        val info = mutableMapOf<String, String>()
        val rootResult = Shell.cmd("id").exec()
        info["root_uid"] = rootResult.out.firstOrNull() ?: "unknown"

        val suImpl = Shell.cmd("magisk -v 2>/dev/null || su --version 2>/dev/null || echo unknown").exec()
        info["su_impl"] = suImpl.out.firstOrNull() ?: "unknown"

        val selinux = Shell.cmd("getenforce 2>/dev/null || echo unknown").exec()
        info["selinux"] = selinux.out.firstOrNull() ?: "unknown"

        val sdk = Shell.cmd("getprop ro.build.version.sdk").exec()
        info["sdk"] = sdk.out.firstOrNull() ?: "unknown"

        info
    }

    suspend fun rebootDeviceForVpnRecovery(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isRootAvailable()) {
                return@withContext Result.failure(IllegalStateException("Root access is not available"))
            }
            val result = Shell.cmd("svc power reboot || reboot").exec()
            if (result.isSuccess) Result.success(Unit)
            else Result.failure(Exception(result.err.joinToString().ifBlank { "Reboot command failed" }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
