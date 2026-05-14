package com.hostshield.service

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Doh3Resolver @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val TAG = "Doh3Resolver"
        private const val MAX_DOH_RESPONSE = 65535
        private const val MIN_DNS_MESSAGE = 12
        private const val READ_CHUNK_BYTES = 16 * 1024
        private const val REQUEST_TIMEOUT_MS = 2500L
        private const val EMA_ALPHA = 0.3
        private val PIN_EXPIRATION = Date(1893456000000L) // 2030-01-01T00:00:00Z

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
        val hostname: String,
        val pins: List<String>
    ) {
        CLOUDFLARE(
            DohResolver.Provider.CLOUDFLARE,
            "https://cloudflare-dns.com/dns-query",
            "cloudflare-dns.com",
            listOf(
                "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=",
                "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="
            )
        ),
        GOOGLE(
            DohResolver.Provider.GOOGLE,
            "https://dns.google/dns-query",
            "dns.google",
            listOf(
                "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
                "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="
            )
        ),
        QUAD9(
            DohResolver.Provider.QUAD9,
            "https://dns.quad9.net/dns-query",
            "dns.quad9.net",
            listOf(
                "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=",
                "sha256/RRM1dGqnDFsCJXBTHky16vi1obOlCgFFn/yOhI/y+ho="
            )
        ),
        NEXTDNS(
            DohResolver.Provider.NEXTDNS,
            "https://dns.nextdns.io/dns-query",
            "dns.nextdns.io",
            listOf(
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
                "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="
            )
        ),
        ADGUARD(
            DohResolver.Provider.ADGUARD,
            "https://dns.adguard-dns.com/dns-query",
            "dns.adguard-dns.com",
            listOf(
                "sha256/eLbhBSJjPiGMb5eySMPmFpibkWIGxabkr3kda0ALqjw=",
                "sha256/RRM1dGqnDFsCJXBTHky16vi1obOlCgFFn/yOhI/y+ho="
            )
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

    private val appContext = context.applicationContext
    private val callbackExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "HostShield-DoH3").apply { isDaemon = true }
    }
    private val latencyEma = ConcurrentHashMap<Provider, Long>()

    private val engine: CronetEngine by lazy {
        val builder = CronetEngine.Builder(appContext)
            .setUserAgent("HostShield DoH3")
            .enableHttp2(true)
            .enableQuic(true)

        Provider.entries.forEach { provider ->
            builder.addQuicHint(provider.hostname, 443, 443)
            builder.addPublicKeyPins(
                provider.hostname,
                provider.decodedPins(),
                false,
                PIN_EXPIRATION
            )
        }

        builder.build()
    }

    suspend fun resolve(
        dnsQuery: ByteArray,
        provider: Provider = Provider.CLOUDFLARE
    ): Doh3Response? = withContext(Dispatchers.IO) {
        val selectedProvider = getFastestProvider() ?: provider
        val response = execute(selectedProvider, dnsQuery) ?: return@withContext null
        updateLatency(response.provider, response.latencyMs)
        response
    }

    fun getFastestProvider(): Provider? =
        latencyEma.minByOrNull { it.value }?.key

    fun getLatencyStats(): Map<String, Long> =
        latencyEma.map { (provider, ema) -> provider.name to ema }.toMap()

    private suspend fun execute(provider: Provider, dnsQuery: ByteArray): Doh3Response? {
        val startedAtNs = System.nanoTime()
        val result = CompletableDeferred<Doh3Response?>()
        val sink = ByteArrayOutputStream()
        val readBuffer = ByteBuffer.allocateDirect(READ_CHUNK_BYTES)

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String
            ) {
                Log.w(TAG, "Unexpected DoH3 redirect from ${provider.name} to $newLocationUrl")
                result.complete(null)
                request.cancel()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                if (info.httpStatusCode !in 200..299) {
                    Log.d(TAG, "DoH3 ${provider.name} HTTP ${info.httpStatusCode}")
                    result.complete(null)
                    request.cancel()
                    return
                }
                request.read(readBuffer)
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer
            ) {
                byteBuffer.flip()
                if (byteBuffer.hasRemaining()) {
                    val chunk = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(chunk)
                    if (sink.size() + chunk.size > MAX_DOH_RESPONSE) {
                        Log.w(TAG, "DoH3 response exceeded $MAX_DOH_RESPONSE-byte cap")
                        result.complete(null)
                        request.cancel()
                        return
                    }
                    sink.write(chunk)
                }
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                val protocol = info.negotiatedProtocol.orEmpty()
                if (!isHttp3Protocol(protocol)) {
                    Log.d(TAG, "DoH3 ${provider.name} negotiated $protocol; falling back to pinned DoH")
                    result.complete(null)
                    return
                }

                val bytes = sink.toByteArray()
                if (bytes.size < MIN_DNS_MESSAGE) {
                    result.complete(null)
                    return
                }

                val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000
                result.complete(
                    Doh3Response(
                        response = bytes,
                        provider = provider,
                        negotiatedProtocol = protocol,
                        latencyMs = elapsedMs
                    )
                )
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: CronetException
            ) {
                Log.d(TAG, "DoH3 ${provider.name} failed: ${error.javaClass.simpleName}: ${error.message}")
                result.complete(null)
            }
        }

        val request = engine.newUrlRequestBuilder(provider.url, callback, callbackExecutor)
            .setHttpMethod("POST")
            .addHeader("Accept", "application/dns-message")
            .addHeader("Content-Type", "application/dns-message")
            .setUploadDataProvider(ByteArrayUploadProvider(dnsQuery), callbackExecutor)
            .build()

        request.start()
        val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { result.await() }
        if (response == null && !result.isCompleted) {
            request.cancel()
        }
        return response
    }

    private fun Provider.decodedPins(): Set<ByteArray> =
        pins.mapTo(LinkedHashSet()) { pin ->
            Base64.decode(pin.removePrefix("sha256/"), Base64.DEFAULT)
        }

    private fun updateLatency(provider: Provider, ms: Long) {
        val previous = latencyEma[provider]
        val ema = if (previous == null) {
            ms
        } else {
            ((1 - EMA_ALPHA) * previous + EMA_ALPHA * ms).toLong()
        }
        latencyEma[provider] = ema
    }

    private class ByteArrayUploadProvider(private val data: ByteArray) : UploadDataProvider() {
        private var offset = 0

        override fun getLength(): Long = data.size.toLong()

        override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
            val remaining = data.size - offset
            val toWrite = minOf(byteBuffer.remaining(), remaining)
            if (toWrite > 0) {
                byteBuffer.put(data, offset, toWrite)
                offset += toWrite
            }
            uploadDataSink.onReadSucceeded(offset >= data.size)
        }

        override fun rewind(uploadDataSink: UploadDataSink) {
            offset = 0
            uploadDataSink.onRewindSucceeded()
        }
    }
}
