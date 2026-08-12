package com.hostshield.service

/** Stable action names shared by notification PendingIntents and the logs route. */
object BlockNotificationActions {
    const val ALLOW_ONCE = "allow_once"
    const val ALLOW_10_MINUTES = "allow_10_minutes"
    const val ALLOW_ALWAYS = "allow_always"
    const val WHY = "why"

    fun isKnown(action: String?): Boolean = action in setOf(
        ALLOW_ONCE,
        ALLOW_10_MINUTES,
        ALLOW_ALWAYS,
        WHY,
    )

    fun isAllow(action: String?): Boolean = action in setOf(
        ALLOW_ONCE,
        ALLOW_10_MINUTES,
        ALLOW_ALWAYS,
    )
}
