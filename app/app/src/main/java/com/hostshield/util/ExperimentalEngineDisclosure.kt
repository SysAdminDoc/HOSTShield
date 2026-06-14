package com.hostshield.util

object ExperimentalEngineDisclosure {
    const val PRODUCTION_DEFAULT =
        "Production default remains pinned DoH/DoT. DoQ, DoH3, and WireGuard DNS are disabled unless explicitly enabled."

    const val DOQ_LABEL = "Experimental simplified DoQ engine"
    const val DOQ_UI =
        "Experimental simplified engine. Not a full QUIC/TLS 1.3 stack; falls back to DoT."
    const val DOQ_DIAGNOSTIC =
        "EXPERIMENTAL simplified DoQ engine; not a full QUIC/TLS 1.3 stack; production default remains pinned DoH/DoT."

    const val WIREGUARD_LABEL = "Experimental simplified WireGuard DNS engine"
    const val WIREGUARD_UI =
        "Experimental DNS-only engine. Not a full WireGuard tunnel; keep production DNS on DoH/DoT until audited."
    const val WIREGUARD_DIAGNOSTIC =
        "EXPERIMENTAL simplified WireGuard DNS engine; DNS-only, not a full WireGuard tunnel; production default remains pinned DoH/DoT."
}
