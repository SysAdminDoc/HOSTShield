package com.hostshield.service

import okhttp3.CertificatePinner
import java.time.LocalDate

/**
 * Versioned local pin manifest for built-in DoH providers.
 *
 * Pins remain fail-closed through OkHttp's CertificatePinner. The metadata here
 * makes rotations auditable: every host has primary and backup SPKI pins plus
 * explicit review and expiry dates for release diagnostics.
 */
object DohPinManifest {
    const val VERSION = 1
    const val ISSUED_ON = "2026-05-17"

    enum class PinRole {
        PRIMARY,
        BACKUP
    }

    enum class Freshness {
        CURRENT,
        REVIEW_DUE,
        EXPIRED
    }

    data class Pin(
        val role: PinRole,
        val value: String,
        val label: String
    )

    data class ProviderPins(
        val providerId: String,
        val hostname: String,
        val reviewAfter: String,
        val expiresAfter: String,
        val pins: List<Pin>
    ) {
        val primaryPins: List<Pin>
            get() = pins.filter { it.role == PinRole.PRIMARY }

        val backupPins: List<Pin>
            get() = pins.filter { it.role == PinRole.BACKUP }

        fun freshness(today: LocalDate = LocalDate.now()): Freshness {
            val reviewDate = LocalDate.parse(reviewAfter)
            val expiryDate = LocalDate.parse(expiresAfter)
            return when {
                today.isAfter(expiryDate) -> Freshness.EXPIRED
                today.isAfter(reviewDate) -> Freshness.REVIEW_DUE
                else -> Freshness.CURRENT
            }
        }

        fun diagnosticFields(today: LocalDate = LocalDate.now()): Map<String, Any> = mapOf(
            "pin_manifest_version" to VERSION,
            "pin_manifest_issued_on" to ISSUED_ON,
            "hostname" to hostname,
            "pin_review_after" to reviewAfter,
            "pin_expires_after" to expiresAfter,
            "pin_freshness" to freshness(today).name,
            "pin_count" to pins.size,
            "primary_pin_labels" to primaryPins.joinToString(",") { it.label },
            "backup_pin_labels" to backupPins.joinToString(",") { it.label }
        )
    }

    val providers: List<ProviderPins> = listOf(
        ProviderPins(
            providerId = "CLOUDFLARE",
            hostname = "cloudflare-dns.com",
            reviewAfter = "2026-08-17",
            expiresAfter = "2026-11-17",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=", "DigiCert Global G2"),
                Pin(PinRole.BACKUP, "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=", "Baltimore CyberTrust")
            )
        ),
        ProviderPins(
            providerId = "GOOGLE",
            hostname = "dns.google",
            reviewAfter = "2026-08-17",
            expiresAfter = "2026-11-17",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", "GTS Root R1"),
                Pin(PinRole.BACKUP, "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=", "GlobalSign/Baltimore")
            )
        ),
        ProviderPins(
            providerId = "QUAD9",
            hostname = "dns.quad9.net",
            reviewAfter = "2026-08-17",
            expiresAfter = "2026-11-17",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=", "DigiCert Global G2"),
                Pin(PinRole.BACKUP, "sha256/RRM1dGqnDFsCJXBTHky16vi1obOlCgFFn/yOhI/y+ho=", "DigiCert ECC")
            )
        ),
        ProviderPins(
            providerId = "NEXTDNS",
            hostname = "dns.nextdns.io",
            reviewAfter = "2026-08-17",
            expiresAfter = "2026-11-17",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", "ISRG Root X1"),
                Pin(PinRole.BACKUP, "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=", "Baltimore CyberTrust")
            )
        ),
        ProviderPins(
            providerId = "ADGUARD",
            hostname = "dns.adguard-dns.com",
            reviewAfter = "2026-08-17",
            expiresAfter = "2026-11-17",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=", "DigiCert Global G2"),
                Pin(PinRole.BACKUP, "sha256/RRM1dGqnDFsCJXBTHky16vi1obOlCgFFn/yOhI/y+ho=", "DigiCert ECC")
            )
        )
    )

    fun certificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        providers.forEach { provider ->
            builder.add(provider.hostname, *provider.pins.map { it.value }.toTypedArray())
        }
        return builder.build()
    }

    fun forProvider(provider: DohResolver.Provider): ProviderPins? =
        providers.firstOrNull { it.providerId == provider.name }

    fun forHostname(hostname: String): ProviderPins? =
        providers.firstOrNull { it.hostname.equals(hostname, ignoreCase = true) }

    fun diagnosticSummaryLines(today: LocalDate = LocalDate.now()): List<String> {
        val lines = mutableListOf<String>()
        lines += "Manifest version: $VERSION"
        lines += "Issued on: $ISSUED_ON"
        providers.forEach { provider ->
            lines += "${provider.providerId}: host=${provider.hostname}, pins=${provider.primaryPins.size} primary/${provider.backupPins.size} backup, review_after=${provider.reviewAfter}, expires_after=${provider.expiresAfter}, freshness=${provider.freshness(today)}"
        }
        return lines
    }
}
