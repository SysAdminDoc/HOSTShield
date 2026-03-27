package com.hostshield.util

/**
 * v5.1: Detect whether an IP address is on the local network (RFC1918/RFC4193).
 *
 * Used by the VPN firewall to implement per-app LAN toggle:
 * apps can be allowed on LAN while blocked from internet, or vice versa.
 * Inspired by AFWall+'s dedicated LAN toggle per app.
 *
 * Private address ranges:
 * - IPv4: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16
 * - IPv6: fc00::/7 (ULA), fe80::/10 (link-local), ::1 (loopback)
 */
object LanDetector {

    /**
     * Check if an IP address is a private/local network address.
     *
     * @param ip IPv4 or IPv6 address string
     * @return true if the IP is in a private range (RFC1918/RFC4193/link-local)
     */
    fun isPrivateIp(ip: String): Boolean {
        if (ip.isBlank()) return false

        // IPv4 checks
        if (ip.contains('.') && !ip.contains(':')) {
            return isPrivateIpv4(ip)
        }

        // IPv6 checks
        if (ip.contains(':')) {
            return isPrivateIpv6(ip)
        }

        return false
    }

    /**
     * Check if a destination is local (LAN, localhost, or link-local).
     * Same as isPrivateIp but also handles hostnames like "localhost".
     */
    fun isLocalDestination(destination: String): Boolean {
        if (destination.isBlank()) return false
        if (destination == "localhost" || destination.endsWith(".local")) return true
        return isPrivateIp(destination)
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false

        val octets = try {
            parts.map { it.toInt() }
        } catch (_: NumberFormatException) {
            return false
        }

        if (octets.any { it < 0 || it > 255 }) return false

        val a = octets[0]
        val b = octets[1]

        return when {
            a == 10 -> true                           // 10.0.0.0/8
            a == 172 && b in 16..31 -> true           // 172.16.0.0/12
            a == 192 && b == 168 -> true              // 192.168.0.0/16
            a == 127 -> true                          // 127.0.0.0/8 (loopback)
            a == 169 && b == 254 -> true              // 169.254.0.0/16 (link-local)
            a == 0 -> true                            // 0.0.0.0/8
            a == 255 && b == 255 -> true              // broadcast
            else -> false
        }
    }

    private fun isPrivateIpv6(ip: String): Boolean {
        val lower = ip.lowercase()
        return when {
            lower == "::1" -> true                    // loopback
            lower == "::" -> true                     // unspecified
            lower.startsWith("fc") -> true            // fc00::/7 ULA (fc00-fdff)
            lower.startsWith("fd") -> true            // fc00::/7 ULA
            lower.startsWith("fe80") -> true          // fe80::/10 link-local
            lower.startsWith("::ffff:") -> {          // IPv4-mapped IPv6
                val v4 = lower.removePrefix("::ffff:")
                isPrivateIpv4(v4)
            }
            else -> false
        }
    }
}
