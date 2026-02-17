package com.hostshield.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v1.6.0 - Private DNS Detector
//
// Android 9+ Private DNS (DNS-over-TLS on port 853) bypasses local VPN
// DNS interception entirely. When enabled, DNS queries go directly to
// the configured DoT provider, skipping the VPN's TUN interface.
//
// This detector reads the system settings to determine the current mode
// so we can warn users and guide them to disable it.

@Singleton
class PrivateDnsDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class PrivateDnsMode {
        /** Private DNS is off -- VPN DNS filtering works correctly. */
        OFF,
        /** Automatic (opportunistic DoT) -- may partially bypass VPN. */
        AUTOMATIC,
        /** Strict mode with a custom hostname -- fully bypasses VPN DNS. */
        STRICT,
        /** Could not determine (pre-Android 9 or settings not accessible). */
        UNKNOWN
    }

    data class PrivateDnsStatus(
        val mode: PrivateDnsMode,
        val hostname: String = "",
        val bypassesVpn: Boolean = mode != PrivateDnsMode.OFF && mode != PrivateDnsMode.UNKNOWN
    )

    /**
     * Check current Private DNS configuration.
     * Returns UNKNOWN on Android < 9 (Private DNS didn't exist).
     */
    fun detect(): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PrivateDnsStatus(PrivateDnsMode.UNKNOWN)
        }

        return try {
            val resolver = context.contentResolver
            val mode = Settings.Global.getString(resolver, "private_dns_mode") ?: ""
            val hostname = Settings.Global.getString(resolver, "private_dns_specifier") ?: ""

            when (mode.lowercase()) {
                "off" -> PrivateDnsStatus(PrivateDnsMode.OFF)
                "opportunistic" -> PrivateDnsStatus(PrivateDnsMode.AUTOMATIC)
                "hostname" -> PrivateDnsStatus(PrivateDnsMode.STRICT, hostname)
                else -> {
                    // Default on most devices is "opportunistic" (automatic)
                    if (mode.isBlank()) PrivateDnsStatus(PrivateDnsMode.AUTOMATIC)
                    else PrivateDnsStatus(PrivateDnsMode.UNKNOWN)
                }
            }
        } catch (_: Exception) {
            PrivateDnsStatus(PrivateDnsMode.UNKNOWN)
        }
    }
}
