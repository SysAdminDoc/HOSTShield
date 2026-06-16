package com.hostshield.util

import java.security.MessageDigest

object ParentalPinHashPolicy {
    private val sha256HexPattern = Regex("^[0-9a-fA-F]{64}$")

    fun isLegacySha256Record(stored: String): Boolean =
        sha256HexPattern.matches(stored)

    fun verifyLegacySha256Pin(rawPin: String, stored: String): Boolean {
        if (!isLegacySha256Record(stored)) return false
        return MessageDigest.isEqual(
            sha256Hex(rawPin).lowercase().toByteArray(Charsets.UTF_8),
            stored.lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    fun sha256Hex(rawPin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(rawPin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
