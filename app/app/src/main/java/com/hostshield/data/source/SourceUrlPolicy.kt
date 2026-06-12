package com.hostshield.data.source

import java.net.URI

data class SourceUrlValidation(
    val normalizedUrl: String,
    val isValid: Boolean,
    val errorMessage: String? = null
)

object SourceUrlPolicy {
    private const val HTTPS_REQUIRED_MESSAGE =
        "Source URLs must use HTTPS. Android cleartext policy blocks HTTP sources, including LAN mirrors."

    fun validate(rawUrl: String): SourceUrlValidation {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return SourceUrlValidation(trimmed, false, "Enter a source URL.")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return SourceUrlValidation(trimmed, false, "Use a complete https:// URL.")
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return SourceUrlValidation(trimmed, false, "Use a complete https:// URL.")
        }
        if (scheme == "http") {
            return SourceUrlValidation(trimmed, false, HTTPS_REQUIRED_MESSAGE)
        }
        if (scheme != "https") {
            return SourceUrlValidation(trimmed, false, "Only https:// source URLs are supported.")
        }

        return SourceUrlValidation(trimmed, true)
    }

    fun requireDownloadable(rawUrl: String): String {
        val validation = validate(rawUrl)
        if (!validation.isValid) {
            throw SourceDownloadException(validation.errorMessage ?: "Invalid source URL")
        }
        return validation.normalizedUrl
    }
}
