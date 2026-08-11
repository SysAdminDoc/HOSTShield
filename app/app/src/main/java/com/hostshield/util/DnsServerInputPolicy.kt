package com.hostshield.util

import java.net.Inet6Address
import java.net.InetAddress

object DnsServerInputPolicy {
    private const val MAX_SERVERS = 4
    private val SERVER_SPLIT_RE = Regex("""[,;\s]+""")
    private val IPV4_RE = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")
    private val IPV6_RE = Regex("""^[0-9a-fA-F:]{2,45}$""")

    /** Must mirror DnsVpnService.VPN_ADDRESS / VPN_ADDRESS6 / DNS_PREFIXES. */
    private const val VPN_ADDRESS4 = "10.120.0.1"
    private const val VPN_ADDRESS6 = "fd00:0:0:0:0:0:0:1"
    private val VIRTUAL_DNS_PREFIXES = setOf("192.0.2", "198.51.100", "203.0.113")

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
        return normalizeIpv4(value) ?: normalizeIpv6(value)
    }

    fun normalizeIpv4(rawValue: String): String? {
        val value = rawValue.trim().trim('[', ']')
        if (!IPV4_RE.matches(value)) return null
        val octets = value.split(".").map { it.toIntOrNull() ?: return null }
        return if (octets.size == 4 && octets.all { it in 0..255 }) {
            octets.joinToString(".")
        } else {
            null
        }
    }

    /**
     * Validate an IPv4 blocked-domain redirect target.
     *
     * Rejects addresses that would break resolution rather than redirect it: the
     * tunnel's own address, the RFC 5737 TEST-NET prefixes HostShield hands out as
     * virtual DNS servers (redirecting there loops queries back into the tunnel),
     * multicast, and the limited broadcast address. Loopback is intentionally
     * allowed — 127.0.0.1 is the classic hosts-file sinkhole.
     */
    fun normalizeRedirectIpv4(rawValue: String): String? {
        val value = normalizeIpv4(rawValue) ?: return null
        val octets = value.split(".").map { it.toInt() }
        if (value == "255.255.255.255") return null
        if (octets[0] in 224..239) return null // multicast
        if (value == VPN_ADDRESS4) return null
        val prefix = "${octets[0]}.${octets[1]}.${octets[2]}"
        if (prefix in VIRTUAL_DNS_PREFIXES) return null
        return value
    }

    /** IPv6 counterpart of [normalizeRedirectIpv4]. Rejects multicast and the tunnel address. */
    fun normalizeRedirectIpv6(rawValue: String): String? {
        val value = normalizeIpv6(rawValue) ?: return null
        if (value.startsWith("ff")) return null // multicast ff00::/8
        if (value == VPN_ADDRESS6) return null
        return value
    }

    fun normalizeIpv6(rawValue: String): String? {
        val value = rawValue.trim().trim('[', ']')
        if (!value.contains(":") || !IPV6_RE.matches(value)) return null
        return runCatching {
            val address = InetAddress.getByName(value)
            if (address is Inet6Address) address.hostAddress?.lowercase() else null
        }.getOrNull()
    }
}
