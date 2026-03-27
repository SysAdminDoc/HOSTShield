package com.hostshield.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v3.5.0 -- Domain Age Checker
// Checks RDAP/whois data to flag newly registered domains.
// Newly registered domains (<30 days) are common in phishing/DGA attacks.

@Singleton
class DomainAgeChecker @Inject constructor() {

    companion object {
        private const val TAG = "DomainAge"
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

            val response = client.newCall(request).execute()
            val body: String?
            try {
                if (!response.isSuccessful) {
                    return@withContext DomainAge(domain, error = "RDAP lookup failed (${response.code})")
                }
                body = response.body?.string()?.take(10_000)
            } finally {
                response.close()
            }

            if (body.isNullOrBlank()) return@withContext DomainAge(domain, error = "Empty response")

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
            Log.w(TAG, "RDAP check failed for $domain: ${e.message}")
            DomainAge(domain, error = e.message?.take(50))
        }
    }

    private fun extractRegisteredDomain(hostname: String): String {
        val parts = hostname.lowercase().split('.')
        return if (parts.size >= 2) "${parts[parts.size - 2]}.${parts.last()}" else hostname
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
