package com.hostshield.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import javax.inject.Inject
import javax.inject.Singleton

enum class RootShellFramework {
    MAGISK,
    KERNEL_SU,
    APATCH,
    UNKNOWN
}

@Singleton
class RootShellRunner @Inject constructor(
    private val diagnosticEvents: DiagnosticEventStore
) {
    companion object {
        private const val TAG = "RootShellRunner"

        internal fun parseMagiskMajor(version: String?): Int? {
            if (version.isNullOrBlank()) return null
            return Regex("""\d+""").find(version)?.value?.toIntOrNull()
        }

        internal fun supportsMountMaster(version: String): Boolean =
            (parseMagiskMajor(version) ?: 0) >= 26

        internal fun detectFrameworkFromProbe(
            magiskVersion: String?,
            hasKernelSu: Boolean,
            hasAPatch: Boolean
        ): RootShellFramework = when {
            !magiskVersion.isNullOrBlank() -> RootShellFramework.MAGISK
            hasAPatch -> RootShellFramework.APATCH
            hasKernelSu -> RootShellFramework.KERNEL_SU
            else -> RootShellFramework.UNKNOWN
        }

        internal fun frameworkLabel(
            framework: RootShellFramework,
            magiskVersion: String? = null
        ): String = when (framework) {
            RootShellFramework.MAGISK -> "Magisk ${magiskVersion.orEmpty().ifBlank { "unknown" }}"
            RootShellFramework.KERNEL_SU -> "KernelSU"
            RootShellFramework.APATCH -> "APatch"
            RootShellFramework.UNKNOWN -> "unknown"
        }
    }

    @Volatile private var cachedMagiskVersion: String? = null
    @Volatile private var cachedUseMountMaster: Boolean? = null
    @Volatile private var cachedRootFramework: RootShellFramework? = null
    @Volatile private var mountMasterShell: Shell? = null

    fun run(commands: List<String>): Shell.Result =
        Shell.cmd(*commands.toTypedArray()).exec().also {
            recordFailureIfNeeded(it, "root_shell", commands.size)
        }

    fun runIptables(commands: List<String>): Shell.Result {
        if (!shouldUseMountMaster()) return run(commands)

        val shell = getMountMasterShell()
        if (shell == null || !shell.isAlive || !shell.isRoot) {
            Log.w(TAG, "Magisk mount-master shell unavailable; falling back to default root shell")
            return run(commands)
        }
        return shell.newJob().add(*commands.toTypedArray()).exec().also {
            recordFailureIfNeeded(it, "iptables_mount_master", commands.size)
        }
    }

    fun getIptablesShellLabel(): String {
        val framework = cachedRootFramework ?: detectRootFramework()
        val version = cachedMagiskVersion ?: if (framework == RootShellFramework.MAGISK) {
            detectMagiskVersion()
        } else {
            ""
        }
        return if (shouldUseMountMaster()) {
            "su --mount-master (Magisk ${version.ifBlank { "26+" }})"
        } else {
            "default su (${frameworkLabel(framework, version)})"
        }
    }

    private fun shouldUseMountMaster(): Boolean {
        cachedUseMountMaster?.let { return it }
        if (detectRootFramework() != RootShellFramework.MAGISK) {
            cachedUseMountMaster = false
            return false
        }
        val version = detectMagiskVersion()
        val useMountMaster = supportsMountMaster(version)
        cachedUseMountMaster = useMountMaster
        if (useMountMaster) {
            Log.i(TAG, "Using Magisk mount-master shell for iptables commands (Magisk $version)")
        }
        return useMountMaster
    }

    private fun detectMagiskVersion(): String {
        cachedMagiskVersion?.let { return it }
        val version = try {
            Shell.cmd("magisk -v 2>/dev/null || true").exec().out
                .firstOrNull()
                ?.trim()
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
        cachedMagiskVersion = version
        return version
    }

    private fun detectRootFramework(): RootShellFramework {
        cachedRootFramework?.let { return it }
        val magiskVersion = detectMagiskVersion()
        val probeOutput = try {
            Shell.cmd(
                "[ -d /data/adb/ksu ] && echo kernelsu || true",
                "[ -d /data/adb/ap ] && echo apatch || true"
            ).exec().out
        } catch (_: Exception) {
            emptyList()
        }
        val framework = detectFrameworkFromProbe(
            magiskVersion = magiskVersion,
            hasKernelSu = probeOutput.any { it.trim() == "kernelsu" },
            hasAPatch = probeOutput.any { it.trim() == "apatch" }
        )
        cachedRootFramework = framework
        return framework
    }

    private fun getMountMasterShell(): Shell? {
        mountMasterShell?.takeIf { it.isAlive && it.isRoot }?.let { return it }
        return try {
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .build()
                .also { mountMasterShell = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Magisk mount-master shell: ${e.message}")
            null
        }
    }

    private fun recordFailureIfNeeded(result: Shell.Result, context: String, commandCount: Int) {
        if (result.isSuccess) return
        diagnosticEvents.recordBlocking(
            DiagnosticEventType.ROOT_COMMAND_FAILED,
            "Root shell command failed",
            mapOf(
                "context" to context,
                "framework" to frameworkLabel(cachedRootFramework ?: detectRootFramework(), cachedMagiskVersion),
                "command_count" to commandCount,
                "stderr" to result.err.joinToString().take(500)
            )
        )
    }
}
