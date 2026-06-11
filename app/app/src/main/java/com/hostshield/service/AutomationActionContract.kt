package com.hostshield.service

/**
 * Public automation broadcast contract used by Tasker, MacroDroid, shell, and
 * same-signature companion apps.
 */
object AutomationActionContract {
    const val ACTION_ENABLE = "com.hostshield.ACTION_ENABLE"
    const val ACTION_DISABLE = "com.hostshield.ACTION_DISABLE"
    const val ACTION_TOGGLE = "com.hostshield.ACTION_TOGGLE"
    const val ACTION_APPLY_FIREWALL = "com.hostshield.ACTION_APPLY_FIREWALL"
    const val ACTION_CLEAR_FIREWALL = "com.hostshield.ACTION_CLEAR_FIREWALL"
    const val ACTION_STATUS = "com.hostshield.ACTION_STATUS"
    const val ACTION_REFRESH_BLOCKLIST = "com.hostshield.ACTION_REFRESH_BLOCKLIST"
    const val ACTION_SET_PROFILE = "com.hostshield.ACTION_SET_PROFILE"
    const val ACTION_SET_DNS = "com.hostshield.ACTION_SET_DNS"
    const val ACTION_PAUSE = "com.hostshield.ACTION_PAUSE"
    const val STATUS_RESULT = "com.hostshield.STATUS_RESULT"

    const val EXTRA_PROFILE_NAME = "profile_name"
    const val EXTRA_DNS_SERVERS = "dns_servers"
    const val EXTRA_DURATION_MINUTES = "duration_minutes"

    private const val LEGACY_PREFIX = "com.hostshield.action."
    private const val CANONICAL_PREFIX = "com.hostshield.ACTION_"
    const val LEGACY_EXTRA_PAUSE_MINUTES = "pause_minutes"
    const val DEFAULT_PAUSE_MINUTES = 5
    const val MAX_PAUSE_MINUTES = 1_440

    val canonicalActions: Set<String> = setOf(
        ACTION_ENABLE,
        ACTION_DISABLE,
        ACTION_TOGGLE,
        ACTION_APPLY_FIREWALL,
        ACTION_CLEAR_FIREWALL,
        ACTION_STATUS,
        ACTION_REFRESH_BLOCKLIST,
        ACTION_SET_PROFILE,
        ACTION_SET_DNS,
        ACTION_PAUSE
    )

    val legacyActionAliases: Map<String, String> =
        canonicalActions.associateBy { action -> action.replace(CANONICAL_PREFIX, LEGACY_PREFIX) }

    val intentFilterActions: Set<String> = canonicalActions + legacyActionAliases.keys

    fun normalizeAction(action: String?): String? {
        if (action == null) return null
        return if (action in canonicalActions) action else legacyActionAliases[action]
    }

    fun pauseDurationMinutes(durationMinutes: Int?, legacyPauseMinutes: Int?): Int {
        val requested = durationMinutes ?: legacyPauseMinutes ?: DEFAULT_PAUSE_MINUTES
        if (requested == 0) return 0
        return requested.coerceIn(1, MAX_PAUSE_MINUTES)
    }
}
