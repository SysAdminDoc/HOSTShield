package com.hostshield.service

/**
 * What to serve when an explicitly-enabled encrypted DNS transport fails.
 *
 * Extracted from [DnsVpnService.failClosedEncrypted] so the invariant is pinned
 * by a test rather than only by review and a CLAUDE.md note.
 *
 * The invariant: an encrypted transport failure NEVER falls back to plaintext
 * UDP. Doing so sends the query in the clear to a hardcoded public resolver,
 * leaking it and silently overriding the user's choice of encrypted DNS — the
 * defect reported in GitHub issue #1 ("enable DoH Quad9, dnsleaktest shows
 * Google DNS"). Serve a stale cached answer when one exists, otherwise SERVFAIL
 * so the client fails fast.
 */
object EncryptedFailurePolicy {

    enum class Action {
        /** Serve the stale cached answer. */
        SERVE_STALE,

        /** Return SERVFAIL (RCODE=2). */
        SERVFAIL,

        /** Plaintext transport: retrying upstream is allowed. */
        RETRY_UPSTREAM,
    }

    /**
     * @param encryptedTransport true when the query was routed over an enabled
     *   encrypted transport (DoH/DoT/DoQ/WireGuard)
     * @param staleAvailable true when the cache holds a usable stale answer
     */
    fun decide(encryptedTransport: Boolean, staleAvailable: Boolean): Action = when {
        !encryptedTransport -> Action.RETRY_UPSTREAM
        staleAvailable -> Action.SERVE_STALE
        else -> Action.SERVFAIL
    }

    /**
     * Guard for the encrypted paths: plaintext UDP forwarding is never a legal
     * outcome of an encrypted-transport failure.
     */
    fun allowsPlaintextFallback(encryptedTransport: Boolean): Boolean = !encryptedTransport
}
