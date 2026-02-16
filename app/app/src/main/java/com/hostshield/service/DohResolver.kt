package com.hostshield.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v0.2.0 — DNS-over-HTTPS Resolver
// ══════════════════════════════════════════════════════════════

@Singleton
class DohResolver @Inject constructor() {

    enum class Provider(val url: String) {
        CLOUDFLARE("https://cloudflare-dns.com/dns-query"),
        GOOGLE("https://dns.google/dns-query"),
        QUAD9("https://dns.quad9.net/dns-query"),
        NEXTDNS("https://dns.nextdns.io/dns-query"),
        ADGUARD("https://dns.adguard-dns.com/dns-query");

        companion object {
            fun fromId(id: String): Provider = when (id.lowercase()) {
                "cloudflare" -> CLOUDFLARE
                "google" -> GOOGLE
                "quad9" -> QUAD9
                "nextdns" -> NEXTDNS
                "adguard" -> ADGUARD
                else -> CLOUDFLARE
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val DNS_MESSAGE_TYPE = "application/dns-message".toMediaType()

    /**
     * Resolve a DNS query via DoH using the RFC 8484 wire format (POST).
     *
     * @param dnsQuery Raw DNS query bytes (standard wire format)
     * @param provider DoH provider to use
     * @return Raw DNS response bytes, or null on failure
     */
    suspend fun resolve(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(provider.url)
                .post(dnsQuery.toRequestBody(DNS_MESSAGE_TYPE))
                .addHeader("Accept", "application/dns-message")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve via GET with base64url encoding (alternative method).
     */
    suspend fun resolveGet(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val encoded = android.util.Base64.encodeToString(
                dnsQuery,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            val url = "${provider.url}?dns=$encoded"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/dns-message")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
