package com.hostshield.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local, single-use domain exceptions granted by a blocked-domain notification.
 *
 * This is intentionally not persisted: "Allow once" must expire with the one DNS
 * decision it authorizes and must not survive a process restart as a permanent rule.
 */
object OneShotAllowStore {
    private val domains = ConcurrentHashMap.newKeySet<String>()

    fun grant(hostname: String) {
        normalize(hostname).takeIf { it.isNotBlank() }?.let(domains::add)
    }

    fun consume(hostname: String): Boolean {
        val normalized = normalize(hostname)
        return normalized.isNotBlank() && domains.remove(normalized)
    }

    private fun normalize(hostname: String): String = hostname.trim().lowercase().removeSuffix(".")
}
