package com.hostshield.util

object ExperimentalEngineDisclosure {
    const val PRODUCTION_DEFAULT =
        "Release builds force DoQ, DoH3, and WireGuard DNS off. Debug builds may expose DoQ and WireGuard DNS for local testing. Production encrypted DNS remains pinned DoH/DoT."

    const val DOQ_LABEL = "Experimental simplified DoQ engine"
    const val DOQ_UI =
        "Debug-only simplified QUIC path; release builds force DoQ off."
    const val DOQ_DIAGNOSTIC =
        "EXPERIMENTAL debug-only simplified DoQ engine; not a full QUIC/TLS 1.3 stack; release builds force DoQ off and production encrypted DNS remains pinned DoH/DoT."

    const val WIREGUARD_LABEL = "Experimental simplified WireGuard DNS engine"
    const val WIREGUARD_UI =
        "Debug-only DNS tunnel path; release builds force WireGuard DNS off."
    const val WIREGUARD_DIAGNOSTIC =
        "EXPERIMENTAL debug-only simplified WireGuard DNS engine; DNS-only, not a full WireGuard tunnel; release builds force WireGuard DNS off and production encrypted DNS remains pinned DoH/DoT."
}
