package com.hostshield.util

import java.util.Base64

/** Validation for the standard base64-encoded 32-byte keys used by WireGuard. */
object WireGuardKeyPolicy {
    private const val KEY_BYTES = 32

    fun normalize(rawValue: String): String? {
        val value = rawValue.trim()
        if (value.isBlank()) return null
        return runCatching { Base64.getDecoder().decode(value) }
            .getOrNull()
            ?.takeIf { it.size == KEY_BYTES }
            ?.let { value }
    }
}
