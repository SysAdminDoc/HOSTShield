package com.hostshield.service

import android.util.Log
import com.hostshield.util.PrivacyLog
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * v6.0: DNS-over-TLS (DoT) resolver (roadmap #44).
 *
 * Sends DNS queries over TLS (port 853) per RFC 7858.
 * Framing: 2-byte length prefix + DNS wire format.
 *
 * Supports Cloudflare, Google, Quad9, AdGuard as DoT providers.
 * Each call creates a fresh TLS connection (no pooling, since DoT
 * queries are infrequent — only used as fallback or user choice).
 */
@Singleton
class DotResolver @Inject constructor() {

    companion object {
        private const val TAG = "DotResolver"
        private const val DOT_PORT = 853
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
        // RFC 7858 caps the length-prefixed message at 65535. Large DNSSEC /
        // RRSIG / TXT responses routinely exceed 4096 bytes.
        private const val MAX_DOT_RESPONSE = 65535
        private const val MIN_DNS_MESSAGE = 12
    }

    enum class Provider(val hostname: String, val ip: String) {
        CLOUDFLARE("one.one.one.one", "1.1.1.1"),
        GOOGLE("dns.google", "8.8.8.8"),
        QUAD9("dns.quad9.net", "9.9.9.9"),
        ADGUARD("dns.adguard-dns.com", "94.140.14.14");

        companion object {
            fun fromId(id: String): Provider = try {
                valueOf(id.uppercase())
            } catch (_: Exception) {
                CLOUDFLARE
            }
        }
    }

    /**
     * Resolve a DNS query via DNS-over-TLS.
     *
     * @param dns The raw DNS query bytes (wire format, no length prefix).
     * @param provider The DoT provider to use.
     * @return The raw DNS response bytes, or null on failure.
     */
    fun resolve(dns: ByteArray, provider: Provider = Provider.CLOUDFLARE): ByteArray? {
        var socket: SSLSocket? = null
        return try {
            val sslContext = SSLContext.getInstance("TLSv1.3")
            sslContext.init(null, null, null)
            val factory = sslContext.socketFactory

            socket = factory.createSocket() as SSLSocket
            socket.soTimeout = READ_TIMEOUT_MS

            // SNI hostname verification
            val params = socket.sslParameters
            params.serverNames = listOf(javax.net.ssl.SNIHostName(provider.hostname))
            socket.sslParameters = params

            // Connect to provider IP on port 853
            socket.connect(InetSocketAddress(provider.ip, DOT_PORT), CONNECT_TIMEOUT_MS)
            socket.startHandshake()

            // Verify hostname matches certificate
            val session = socket.session
            val peerHost = session.peerHost ?: provider.ip
            val verifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(provider.hostname, session)) {
                PrivacyLog.w(TAG, "Hostname verification failed for ${provider.hostname}")
                return null
            }

            val output = DataOutputStream(socket.outputStream)
            val input = DataInputStream(socket.inputStream)

            // RFC 7858: 2-byte length prefix + DNS message
            output.writeShort(dns.size)
            output.write(dns)
            output.flush()

            // Read response: 2-byte length prefix + DNS message
            val respLen = input.readUnsignedShort()
            if (respLen < MIN_DNS_MESSAGE || respLen > MAX_DOT_RESPONSE) {
                Log.w(TAG, "DoT response length out of range ($MIN_DNS_MESSAGE..$MAX_DOT_RESPONSE): $respLen")
                return null
            }

            val resp = ByteArray(respLen)
            input.readFully(resp)

            Log.d(TAG, "DoT resolved via ${provider.name}: ${dns.size}B -> ${resp.size}B")
            resp
        } catch (e: Exception) {
            Log.w(TAG, "DoT resolve failed (${provider.name}): ${e.message}")
            null
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }
}
