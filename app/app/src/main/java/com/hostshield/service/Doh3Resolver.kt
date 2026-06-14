package com.hostshield.service

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Doh3Resolver @Inject constructor() {

    companion object {
        const val EMBEDDED_CRONET_ENABLED = false
        const val DISABLED_REASON =
            "Embedded Cronet DoH3 transport is disabled until a maintained, non-vulnerable Cronet artifact is available."

        internal fun isHttp3Protocol(protocol: String?): Boolean {
            val normalized = protocol?.trim()?.lowercase().orEmpty()
            return normalized.startsWith("h3") ||
                normalized == "http/3" ||
                normalized.contains("quic")
        }
    }

    enum class Provider(
        val dohProvider: DohResolver.Provider,
        val url: String,
        val hostname: String
    ) {
        CLOUDFLARE(
            DohResolver.Provider.CLOUDFLARE,
            "https://cloudflare-dns.com/dns-query",
            "cloudflare-dns.com"
        ),
        GOOGLE(
            DohResolver.Provider.GOOGLE,
            "https://dns.google/dns-query",
            "dns.google"
        ),
        QUAD9(
            DohResolver.Provider.QUAD9,
            "https://dns.quad9.net/dns-query",
            "dns.quad9.net"
        ),
        NEXTDNS(
            DohResolver.Provider.NEXTDNS,
            "https://dns.nextdns.io/dns-query",
            "dns.nextdns.io"
        ),
        ADGUARD(
            DohResolver.Provider.ADGUARD,
            "https://dns.adguard-dns.com/dns-query",
            "dns.adguard-dns.com"
        );

        companion object {
            fun fromDohProvider(provider: DohResolver.Provider): Provider =
                entries.firstOrNull { it.dohProvider == provider } ?: CLOUDFLARE
        }
    }

    data class Doh3Response(
        val response: ByteArray,
        val provider: Provider,
        val negotiatedProtocol: String,
        val latencyMs: Long
    )

    val isAvailable: Boolean = EMBEDDED_CRONET_ENABLED

    suspend fun resolve(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): Doh3Response? = null

    fun getFastestProvider(): Provider? = null

    fun getLatencyStats(): Map<String, Long> = emptyMap()
}
