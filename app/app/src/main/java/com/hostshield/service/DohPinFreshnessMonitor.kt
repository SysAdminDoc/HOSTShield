package com.hostshield.service

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Evaluates the built-in encrypted-DNS pin lifecycle using an injectable clock. */
@Singleton
class DohPinFreshnessMonitor @Inject constructor(
    private val clock: Clock
) {
    data class Warning(
        val freshness: DohPinManifest.Freshness,
        val providerHostnames: List<String>,
        val date: String
    ) {
        val providerLabel: String
            get() = providerHostnames.joinToString(", ")
    }

    fun currentWarning(): Warning? = warningFor(LocalDate.now(clock))

    internal fun warningFor(today: LocalDate): Warning? {
        val expired = DohPinManifest.providers.filter {
            it.freshness(today) == DohPinManifest.Freshness.EXPIRED
        }
        if (expired.isNotEmpty()) {
            return Warning(
                freshness = DohPinManifest.Freshness.EXPIRED,
                providerHostnames = expired.map { it.hostname },
                date = expired.minOf { it.expiresAfter }
            )
        }

        val reviewDue = DohPinManifest.providers.filter {
            it.freshness(today) == DohPinManifest.Freshness.REVIEW_DUE
        }
        if (reviewDue.isNotEmpty()) {
            return Warning(
                freshness = DohPinManifest.Freshness.REVIEW_DUE,
                providerHostnames = reviewDue.map { it.hostname },
                date = reviewDue.minOf { it.reviewAfter }
            )
        }
        return null
    }
}
