package com.hostshield.util

import java.net.Inet6Address
import java.net.InetAddress

object DnsServerInputPolicy {
    private const val MAX_SERVERS = 4
    private val SERVER_SPLIT_RE = Regex("""[,;\s]+""")
    private val IPV4_RE = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")
    private val IPV6_RE = Regex("""^[0-9a-fA-F:]{2,45}$""")

    fun normalizeServerList(rawValue: String): String =
        parseServerList(rawValue).joinToString(",")

    fun parseServerList(rawValue: String): List<String> {
        val seen = linkedSetOf<String>()
        rawValue.split(SERVER_SPLIT_RE)
            .asSequence()
            .mapNotNull { normalizeServerIp(it) }
            .forEach { server ->
                if (seen.size < MAX_SERVERS) seen.add(server)
            }
        return seen.toList()
    }

    fun normalizeServerIp(rawValue: String): String? {
        val value = rawValue.trim().trim('[', ']')
        if (value.isBlank()) return null
        if (IPV4_RE.matches(value)) {
            val octets = value.split(".").map { it.toIntOrNull() ?: return null }
            return if (octets.size == 4 && octets.all { it in 0..255 }) {
                octets.joinToString(".")
            } else {
                null
            }
        }
        if (!value.contains(":") || !IPV6_RE.matches(value)) return null
        return runCatching {
            val address = InetAddress.getByName(value)
            if (address is Inet6Address) address.hostAddress?.lowercase() else null
        }.getOrNull()
    }
}
