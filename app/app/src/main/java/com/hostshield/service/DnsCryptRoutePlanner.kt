package com.hostshield.service

import com.hostshield.util.DnsStampParser
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

/**
 * Validates DNSCrypt resolver/relay stamps and builds the anonymized relay
 * envelope described by the DNSCrypt v2 relay extension.
 */
object DnsCryptRoutePlanner {

    private val ANON_MAGIC = byteArrayOf(
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0x00, 0x00
    )

    data class Endpoint(
        val original: String,
        val host: String,
        val port: Int,
        val address: InetAddress
    ) {
        val canonicalHost: String = address.hostAddress ?: host
    }

    data class Route(
        val resolver: DnsStampParser.DnsStamp,
        val resolverEndpoint: Endpoint,
        val relay: DnsStampParser.DnsStamp?,
        val relayEndpoint: Endpoint?,
        val networkDestination: Endpoint
    ) {
        val anonymized: Boolean = relayEndpoint != null
        val resolverSeesClientIp: Boolean = !anonymized
    }

    fun plan(
        resolver: DnsStampParser.DnsStamp,
        relay: DnsStampParser.DnsStamp? = null
    ): Result<Route> = runCatching {
        require(resolver.protocol == DnsStampParser.DnsStamp.Protocol.DNSCRYPT) {
            "Resolver stamp must use DNSCrypt protocol"
        }
        require(resolver.providerPublicKey.size == DNSCRYPT_PROVIDER_PUBLIC_KEY_BYTES) {
            "DNSCrypt resolver stamp must include a 32-byte provider public key"
        }
        require(resolver.providerName.isNotBlank()) {
            "DNSCrypt resolver stamp must include a provider name"
        }

        val resolverEndpoint = parseEndpoint(resolver.address)
        val relayEndpoint = relay?.let {
            require(it.protocol == DnsStampParser.DnsStamp.Protocol.DNSCRYPT_RELAY) {
                "Relay stamp must use Anonymized DNSCrypt relay protocol"
            }
            parseEndpoint(it.address)
        }

        if (relayEndpoint != null) {
            require(!sameEndpoint(resolverEndpoint, relayEndpoint)) {
                "Anonymized DNSCrypt relay must be distinct from the resolver"
            }
        }

        Route(
            resolver = resolver,
            resolverEndpoint = resolverEndpoint,
            relay = relay,
            relayEndpoint = relayEndpoint,
            networkDestination = relayEndpoint ?: resolverEndpoint
        )
    }

    fun relayPrefix(route: Route): ByteArray {
        require(route.anonymized) { "Relay prefix requires an anonymized route" }
        val resolverAddress = route.resolverEndpoint.address
        val targetIp = resolverAddress.toRelayTargetBytes()
        val out = ByteArrayOutputStream(ANON_MAGIC.size + 16 + 2)
        out.write(ANON_MAGIC)
        out.write(targetIp)
        out.write((route.resolverEndpoint.port ushr 8) and 0xFF)
        out.write(route.resolverEndpoint.port and 0xFF)
        return out.toByteArray()
    }

    fun wrapForRelay(route: Route, dnsCryptQuery: ByteArray): ByteArray {
        if (!route.anonymized) return dnsCryptQuery
        val prefix = relayPrefix(route)
        return ByteArray(prefix.size + dnsCryptQuery.size).also {
            System.arraycopy(prefix, 0, it, 0, prefix.size)
            System.arraycopy(dnsCryptQuery, 0, it, prefix.size, dnsCryptQuery.size)
        }
    }

    fun parseEndpoint(raw: String, defaultPort: Int = DNSCRYPT_DEFAULT_PORT): Endpoint {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Endpoint address is blank" }

        val hostAndPort = splitHostPort(trimmed, defaultPort)
        val host = hostAndPort.first
        val port = hostAndPort.second
        require(port in 1..65535) { "Endpoint port is out of range" }
        require(isIpLiteral(host)) { "DNSCrypt relay routing requires an IP literal address" }

        val address = InetAddress.getByName(host)
        require(address is Inet4Address || address is Inet6Address) {
            "Endpoint address is not an IP literal"
        }
        return Endpoint(
            original = trimmed,
            host = host,
            port = port,
            address = address
        )
    }

    private fun splitHostPort(raw: String, defaultPort: Int): Pair<String, Int> {
        if (raw.startsWith("[")) {
            val end = raw.indexOf(']')
            require(end > 1) { "Bracketed IPv6 endpoint is malformed" }
            val host = raw.substring(1, end)
            val tail = raw.substring(end + 1)
            val port = if (tail.isBlank()) {
                defaultPort
            } else {
                require(tail.startsWith(":")) { "Bracketed IPv6 endpoint has invalid port suffix" }
                tail.substring(1).toIntOrNull() ?: error("Endpoint port is not numeric")
            }
            return host to port
        }

        val colonCount = raw.count { it == ':' }
        if (colonCount == 0) return raw to defaultPort
        if (colonCount == 1) {
            val host = raw.substringBefore(':')
            val port = raw.substringAfter(':').toIntOrNull() ?: error("Endpoint port is not numeric")
            return host to port
        }

        return raw to defaultPort
    }

    private fun isIpLiteral(host: String): Boolean {
        val lower = host.lowercase(Locale.US)
        if (IPV4_REGEX.matches(lower)) return lower.split('.').all {
            it.toIntOrNull()?.let { part -> part in 0..255 } == true
        }
        return ':' in lower && lower.all { ch ->
            ch in '0'..'9' || ch in 'a'..'f' || ch == ':' || ch == '.' || ch == '%'
        }
    }

    private fun sameEndpoint(a: Endpoint, b: Endpoint): Boolean =
        a.port == b.port && a.address.address.contentEquals(b.address.address)

    private fun InetAddress.toRelayTargetBytes(): ByteArray {
        return when (this) {
            is Inet4Address -> ByteArray(16).also { mapped ->
                mapped[10] = 0xff.toByte()
                mapped[11] = 0xff.toByte()
                val v4 = address
                System.arraycopy(v4, 0, mapped, 12, v4.size)
            }
            is Inet6Address -> address
            else -> error("Unsupported relay target address")
        }
    }

    private const val DNSCRYPT_PROVIDER_PUBLIC_KEY_BYTES = 32
    private const val DNSCRYPT_DEFAULT_PORT = 443
    private val IPV4_REGEX = Regex("""\d{1,3}(\.\d{1,3}){3}""")
}
