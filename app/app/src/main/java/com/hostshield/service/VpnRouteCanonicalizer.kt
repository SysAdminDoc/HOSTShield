package com.hostshield.service

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object VpnRouteCanonicalizer {
    data class Route(val address: String, val prefixLength: Int)

    fun canonicalize(address: String, prefixLength: Int): Route {
        val parsed = parseNumericAddress(address)
        val bytes = parsed.address.copyOf()
        val maxPrefix = bytes.size * 8
        require(prefixLength in 0..maxPrefix) {
            "Prefix $prefixLength is invalid for ${if (bytes.size == 4) "IPv4" else "IPv6"} route $address"
        }

        clearHostBits(bytes, prefixLength)
        val canonicalAddress = requireNotNull(InetAddress.getByAddress(bytes).hostAddress) {
            "Canonical route address was unavailable for $address"
        }
        return Route(
            address = canonicalAddress,
            prefixLength = prefixLength
        )
    }

    private fun parseNumericAddress(address: String): InetAddress {
        val trimmed = address.trim()
        require(trimmed.isNotEmpty()) { "Route address is blank" }
        require('/' !in trimmed) { "Route address must not include CIDR suffix: $address" }
        require('%' !in trimmed) { "Scoped IPv6 routes are not supported: $address" }

        if ('.' in trimmed && ':' !in trimmed) {
            return InetAddress.getByAddress(parseIpv4Bytes(trimmed)).also {
                require(it is Inet4Address) { "Route address must be IPv4: $address" }
            }
        }
        require(':' in trimmed) { "Route address must be numeric: $address" }
        return try {
            InetAddress.getByName(trimmed).also {
                require(it is Inet6Address) { "Route address must be IPv6: $address" }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid IPv6 route address: $address", e)
        }
    }

    private fun parseIpv4Bytes(address: String): ByteArray {
        val parts = address.split('.')
        require(parts.size == 4) { "Invalid IPv4 route address: $address" }
        return ByteArray(4) { index ->
            val part = parts[index]
            require(part.isNotEmpty() && part.all { it.isDigit() }) {
                "Invalid IPv4 route address: $address"
            }
            val value = part.toInt()
            require(value in 0..255) { "Invalid IPv4 route address: $address" }
            value.toByte()
        }
    }

    private fun clearHostBits(bytes: ByteArray, prefixLength: Int) {
        val fullBytes = prefixLength / 8
        val remainingBits = prefixLength % 8
        val firstHostByte = if (remainingBits == 0) fullBytes else fullBytes + 1

        if (remainingBits > 0 && fullBytes < bytes.size) {
            val mask = (0xFF shl (8 - remainingBits)) and 0xFF
            bytes[fullBytes] = (bytes[fullBytes].toInt() and mask).toByte()
        }
        for (index in firstHostByte until bytes.size) {
            bytes[index] = 0
        }
    }
}
