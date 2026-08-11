package com.hostshield.service

/**
 * Decides whether an incoming `SHORTCUT_TOGGLE` may flip protection unattended.
 *
 * MainActivity is exported for the launcher, so any installed app can send this
 * action. On API 34+ the launched-from identity is supplied by the system and
 * cannot be forged. Below that the only signal is `Activity.getReferrer()`,
 * which returns caller-supplied `EXTRA_REFERRER` verbatim — an attacker can set
 * it to anything, including the real launcher's package or a host-less URI. No
 * referrer-based rule can distinguish a launcher from malware there, so the
 * pre-34 answer is never "trusted": the app opens on Home and the user taps the
 * shield themselves.
 */
object ShortcutTrustPolicy {

    enum class Decision {
        /** Caller identity verified — perform the toggle. */
        TRUSTED,

        /** Caller identity cannot be established on this OS version — require a tap. */
        UNVERIFIABLE,

        /** Caller identity verified and is not permitted to toggle. */
        UNTRUSTED,
    }

    const val MIN_VERIFIABLE_SDK = 34

    /**
     * @param sdkInt running platform level
     * @param callerUid `Activity.launchedFromUid` (API 34+), or null if unavailable
     * @param myUid this process's uid
     * @param systemUid the platform uid (`Process.SYSTEM_UID`)
     * @param callerPackage `Activity.launchedFromPackage` (API 34+), or null
     * @param homePackage the resolved default launcher package, or null
     */
    fun decide(
        sdkInt: Int,
        callerUid: Int?,
        myUid: Int,
        systemUid: Int,
        callerPackage: String?,
        homePackage: String?,
    ): Decision {
        if (sdkInt < MIN_VERIFIABLE_SDK) return Decision.UNVERIFIABLE
        if (callerUid == null) return Decision.UNTRUSTED
        if (callerUid == myUid) return Decision.TRUSTED
        if (callerUid == systemUid) return Decision.TRUSTED
        if (callerPackage != null && homePackage != null && callerPackage == homePackage) {
            return Decision.TRUSTED
        }
        return Decision.UNTRUSTED
    }
}
