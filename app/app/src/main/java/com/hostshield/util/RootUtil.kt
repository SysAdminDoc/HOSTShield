package com.hostshield.util

import android.content.Context
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.0.0 — Root Utilities

@Singleton
class RootUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val HOSTS_PATH = "/system/etc/hosts"
    }

    /** Temp file inside app-private cache (no root or SELinux issues). */
    private val tempFile: File get() = File(context.cacheDir, "hostshield_hosts_tmp")

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
        val result = Shell.cmd("cat $path").exec()
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
            Shell.cmd("cp $path ${path}.bak 2>/dev/null || true").exec()

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
            Result.failure(e)
        }
    }

    /** Restore original hosts file from backup. */
    suspend fun restoreHostsFile(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = getActiveHostsPath()
            val backup = "${path}.bak"
            val hasBackup = Shell.cmd("[ -f $backup ] && echo yes || echo no").exec()
                .out.firstOrNull()?.trim() == "yes"

            if (hasBackup) {
                if (path.startsWith("/system")) {
                    Shell.cmd(
                        "mount -o rw,remount /system",
                        "cp $backup $path",
                        "mount -o ro,remount /system"
                    ).exec()
                } else {
                    Shell.cmd("cp $backup $path").exec()
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
            Result.failure(e)
        }
    }

    /** Get line count of current hosts file. */
    suspend fun getHostsLineCount(): Int = withContext(Dispatchers.IO) {
        val path = getActiveHostsPath()
        val result = Shell.cmd("wc -l < $path").exec()
        result.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    /**
     * Hot-patch: append a single block entry to the hosts file without full rewrite.
     * Equivalent to AdAway's "block from log" behavior.
     */
    suspend fun appendHostEntry(hostname: String, redirectIp: String = "0.0.0.0"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = getActiveHostsPath()
            val line = "$redirectIp $hostname"
            // Only append if not already present
            val check = Shell.cmd("grep -qF '$hostname' $path").exec()
            if (!check.isSuccess) {
                Shell.cmd("echo '$line' >> $path").exec()
                flushDnsCache()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hot-patch: remove all lines matching a hostname from the hosts file.
     * Used when allowing a previously blocked domain from the DNS log.
     */
    suspend fun removeHostEntry(hostname: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = getActiveHostsPath()
            // sed -i: delete any line containing the exact hostname as a word
            Shell.cmd("sed -i '/ $hostname\$/d' $path").exec()
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
        return if (isMagiskSystemless()) {
            "/data/adb/modules/hosts/system/etc/hosts"
        } else {
            HOSTS_PATH
        }
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
}
