package com.hostshield.util

import android.util.Log
import com.hostshield.data.source.BoundedResponseReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Domain age checker
// Checks RDAP/whois data to flag newly registered domains.
// Newly registered domains (<30 days) are common in phishing/DGA attacks.

@Singleton
class DomainAgeChecker @Inject constructor() {

    companion object {
        private const val TAG = "DomainAge"
        private const val MAX_RDAP_RESPONSE_BYTES = 64L * 1024L

        // Common multi-part public suffixes where the registered domain is three labels
        // (x.example.co.uk registers example.co.uk, not co.uk)
        private val MULTI_PART_SUFFIXES = setOf(
            "co.uk", "org.uk", "gov.uk", "ac.uk",
            "com.au", "net.au", "org.au",
            "co.jp", "ne.jp", "or.jp",
            "co.nz", "co.za", "com.br", "com.mx", "com.ar", "co.in",
            "com.cn", "com.tw", "com.sg", "com.hk", "co.kr"
        )
    }

    data class DomainAge(
        val domain: String,
        val registrationDate: String? = null,
        val ageDays: Int? = null,
        val isNew: Boolean = false, // < 30 days
        val isSuspicious: Boolean = false, // < 7 days
        val error: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Check domain registration age via RDAP (Registration Data Access Protocol).
     * RDAP is the modern replacement for WHOIS, returns JSON.
     */
    suspend fun check(hostname: String): DomainAge = withContext(Dispatchers.IO) {
        val domain = extractRegisteredDomain(hostname)
        try {
            val request = Request.Builder()
                .url("https://rdap.org/domain/$domain")
                .header("Accept", "application/rdap+json")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DomainAge(domain, error = "RDAP lookup failed (${response.code})")
                }
                BoundedResponseReader.readUtf8(
                    response,
                    MAX_RDAP_RESPONSE_BYTES,
                    "RDAP response"
                ).content.take(10_000)
            }

            if (body.isBlank()) return@withContext DomainAge(domain, error = "Empty response")

            // Parse registration date from RDAP JSON
            val regDate = extractDate(body, "registration")
                ?: extractDate(body, "eventDate")
            if (regDate == null) return@withContext DomainAge(domain, error = "No registration date found")

            val ageDays = calculateAgeDays(regDate)
            DomainAge(
                domain = domain,
                registrationDate = regDate.take(10),
                ageDays = ageDays,
                isNew = ageDays != null && ageDays < 30,
                isSuspicious = ageDays != null && ageDays < 7
            )
        } catch (e: Exception) {
            PrivacyLog.w(TAG, "RDAP check failed for $domain: ${e.message}")
            DomainAge(domain, error = e.message?.take(50))
        }
    }

    internal fun extractRegisteredDomain(hostname: String): String {
        val parts = hostname.lowercase().split('.')
        if (parts.size < 2) return hostname
        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        return if (parts.size >= 3 && lastTwo in MULTI_PART_SUFFIXES) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    private fun extractDate(json: String, keyword: String): String? {
        // Simple extraction — find "registration" event and get its date
        val idx = json.indexOf(keyword, ignoreCase = true)
        if (idx == -1) return null
        // Look for an ISO date pattern near this keyword
        val dateRegex = Regex("""(\d{4}-\d{2}-\d{2}T[\d:.Z+-]+)""")
        val searchWindow = json.substring(idx, minOf(idx + 200, json.length))
        return dateRegex.find(searchWindow)?.value
    }

    private fun calculateAgeDays(isoDate: String): Int? {
        return try {
            val regInstant = java.time.Instant.parse(isoDate)
            val now = java.time.Instant.now()
            java.time.Duration.between(regInstant, now).toDays().toInt()
        } catch (_: Exception) {
            try {
                val date = java.time.LocalDate.parse(isoDate.take(10))
                val now = java.time.LocalDate.now()
                java.time.temporal.ChronoUnit.DAYS.between(date, now).toInt()
            } catch (_: Exception) { null }
        }
    }
}
