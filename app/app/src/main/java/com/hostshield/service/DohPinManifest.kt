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
    const val VERSION = 2
    const val ISSUED_ON = "2026-06-13"

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

    // Pins target the INTERMEDIATE CA (primary) and ROOT CA (backup) of each
    // provider's served chain, NOT the leaf certificate. Leaf certs rotate every
    // ~90 days; pinning them silently broke DoH for every provider (the manifest
    // v1 pins also named the wrong CAs entirely). Intermediates are stable for
    // years and roots for ~decades, and OkHttp's CertificatePinner passes if ANY
    // pinned SPKI appears in the verified chain — so leaf rotation no longer
    // breaks pinning. Values verified against the live chains on 2026-06-13
    // (openssl s_client + on-device OkHttp verified-chain logging).
    val providers: List<ProviderPins> = listOf(
        ProviderPins(
            providerId = "CLOUDFLARE",
            hostname = "cloudflare-dns.com",
            reviewAfter = "2026-12-13",
            expiresAfter = "2027-06-13",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/zGgA4OU4DjJdvpRYUqbi5Vh2g9W5Oc/PgKihy9mkLsE=", "SSL.com SSL Intermediate CA ECC R2"),
                Pin(PinRole.BACKUP, "sha256/oyD01TTXvpfBro3QSZc1vIlcMjrdLTiL/M9mLCPX+Zo=", "SSL.com Root Certification Authority ECC")
            )
        ),
        ProviderPins(
            providerId = "GOOGLE",
            hostname = "dns.google",
            reviewAfter = "2026-12-13",
            expiresAfter = "2027-06-13",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/YPtHaftLw6/0vnc2BnNKGF54xiCA28WFcccjkA4ypCM=", "Google Trust Services WR2"),
                Pin(PinRole.BACKUP, "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", "GTS Root R1")
            )
        ),
        ProviderPins(
            providerId = "QUAD9",
            hostname = "dns.quad9.net",
            reviewAfter = "2026-12-13",
            expiresAfter = "2027-06-13",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/qBRjZmOmkSNJL0p70zek7odSIzqs/muR4Jk9xYyCP+E=", "DigiCert Global G3 TLS ECC SHA384 2020 CA1"),
                Pin(PinRole.BACKUP, "sha256/uUwZgwDOxcBXrQcntwu+kYFpkiVkOaezL0WYEZ3anJc=", "DigiCert Global Root G3")
            )
        ),
        ProviderPins(
            providerId = "NEXTDNS",
            hostname = "dns.nextdns.io",
            reviewAfter = "2026-12-13",
            expiresAfter = "2027-06-13",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/xwyjb5aN7tSRWj02XSZa2cKGLxXLdKHUBfLT/7twjhQ=", "ZeroSSL ECC DV SSL CA 2"),
                Pin(PinRole.BACKUP, "sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE=", "Sectigo Public Server Authentication Root E46")
            )
        ),
        ProviderPins(
            providerId = "ADGUARD",
            hostname = "dns.adguard-dns.com",
            reviewAfter = "2026-12-13",
            expiresAfter = "2027-06-13",
            pins = listOf(
                Pin(PinRole.PRIMARY, "sha256/3fLLVjRIWnCqDqIETU2OcnMP7EzmN/Z3Q/jQ8cIaAoc=", "ZeroSSL ECC Domain Secure Site CA"),
                Pin(PinRole.BACKUP, "sha256/ICGRfpgmOUXIWcQ/HXPLQTkFPEFPoDyjvH7ohhQpjzs=", "USERTrust ECC Certification Authority")
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
