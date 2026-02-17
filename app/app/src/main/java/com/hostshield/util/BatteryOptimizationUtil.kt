package com.hostshield.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.6.0 - Battery Optimization Utility
//
// VPN services are frequently killed by Android battery optimization,
// especially on OEM skins (MIUI, EMUI, OneUI, ColorOS). This utility:
// 1. Checks if HostShield is exempt from battery optimization
// 2. Provides intent to request exemption
// 3. Detects OEM-specific battery killer apps
//
// Per research: battery/OEM killing is the #1 user complaint across
// DNS66, NetGuard, RethinkDNS, and all VPN-based adblockers.

@Singleton
class BatteryOptimizationUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class BatteryStatus(
        val isOptimized: Boolean,
        val oemBatteryKiller: String? = null,
        val needsUserAction: Boolean = false,
        val message: String = ""
    )

    /** Check if HostShield is subject to battery optimization. */
    fun check(): BatteryStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)

        val oemKiller = detectOemBatteryKiller()

        return when {
            // Battery optimization ON + OEM killer present: worst case
            !isIgnoring && oemKiller != null -> BatteryStatus(
                isOptimized = true,
                oemBatteryKiller = oemKiller,
                needsUserAction = true,
                message = "Battery optimization is on and $oemKiller may kill HostShield. " +
                    "Disable battery optimization and add HostShield to $oemKiller's whitelist."
            )
            // Battery optimization ON, no OEM killer
            !isIgnoring -> BatteryStatus(
                isOptimized = true,
                needsUserAction = true,
                message = "Battery optimization may stop HostShield from running in the background. " +
                    "Tap to disable battery optimization."
            )
            // Battery exemption IS granted. OEM killer may exist but the
            // user already did what we asked. Don't nag -- just log it.
            // The SettingsScreen still shows the OEM info for reference.
            else -> BatteryStatus(
                isOptimized = false,
                oemBatteryKiller = oemKiller,
                needsUserAction = false,
                message = "Battery optimization is properly configured."
            )
        }
    }

    /** Get intent to request battery optimization exemption. */
    fun getExemptionIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Get intent to open battery optimization settings (fallback). */
    fun getBatterySettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Try to launch the exemption dialog. If the direct intent fails
     * (some OEMs block it), fall back to the general battery settings list.
     * Returns true if an intent was launched successfully.
     */
    fun requestExemption(activityContext: Context): Boolean {
        // First try the direct one-tap dialog
        try {
            activityContext.startActivity(getExemptionIntent())
            return true
        } catch (_: Exception) { }
        // Fallback: open the full battery optimization list
        try {
            activityContext.startActivity(getBatterySettingsIntent())
            return true
        } catch (_: Exception) { }
        // Last resort: open general device settings
        try {
            activityContext.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        } catch (_: Exception) { }
        return false
    }

    /**
     * Detect OEM-specific battery management apps.
     * Returns the OEM battery killer name, or null on stock Android.
     */
    private fun detectOemBatteryKiller(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val pm = context.packageManager

        return when {
            // Xiaomi MIUI
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                if (isPackageInstalled(pm, "com.miui.securitycenter")) "MIUI Security"
                else null
            }
            // Huawei EMUI
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                if (isPackageInstalled(pm, "com.huawei.systemmanager")) "EMUI Power Manager"
                else null
            }
            // Samsung OneUI
            manufacturer.contains("samsung") -> {
                if (isPackageInstalled(pm, "com.samsung.android.lool") ||
                    isPackageInstalled(pm, "com.samsung.android.sm")) "Samsung Device Care"
                else null
            }
            // OnePlus OxygenOS
            manufacturer.contains("oneplus") -> {
                if (isPackageInstalled(pm, "com.oneplus.security")) "OnePlus Battery Optimization"
                else null
            }
            // Oppo ColorOS / Realme
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                if (isPackageInstalled(pm, "com.coloros.oppoguardelf")) "ColorOS Battery Management"
                else null
            }
            // Vivo FuntouchOS
            manufacturer.contains("vivo") -> {
                if (isPackageInstalled(pm, "com.vivo.abe")) "Vivo Battery Management"
                else null
            }
            else -> null
        }
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0); true
        } catch (_: Exception) { false }
    }
}
