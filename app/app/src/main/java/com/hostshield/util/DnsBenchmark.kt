package com.hostshield.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

@Singleton
class DnsBenchmark @Inject constructor() {

    data class BenchmarkResult(
        val serverIp: String,
        val serverName: String,
        val avgLatencyMs: Int,
        val minLatencyMs: Int,
        val maxLatencyMs: Int,
        val successRate: Float,
        val isReachable: Boolean
    )

    private val servers = listOf(
        "8.8.8.8" to "Google",
        "8.8.4.4" to "Google Secondary",
        "1.1.1.1" to "Cloudflare",
        "1.0.0.1" to "Cloudflare Secondary",
        "9.9.9.9" to "Quad9",
        "149.112.112.112" to "Quad9 Secondary",
        "208.67.222.222" to "OpenDNS",
        "208.67.220.220" to "OpenDNS Secondary",
        "94.140.14.14" to "AdGuard",
        "94.140.15.15" to "AdGuard Secondary",
    )

    private val testDomains = listOf("google.com", "example.com", "cloudflare.com")

    private companion object {
        const val DNS_PORT = 53
        const val TIMEOUT_MS = 3_000
        const val RECEIVE_BUFFER_SIZE = 512
    }

    /**
     * Run a DNS latency benchmark against all known servers plus any custom ones.
     *
     * @param includeCustom additional server IPs to test (labelled "Custom")
     * @param queriesPerServer number of queries sent per server (one per test domain, repeated)
     * @param onProgress callback invoked as (completed, total) after each server finishes
     * @return results sorted by average latency ascending
     */
    suspend fun runBenchmark(
        includeCustom: List<String> = emptyList(),
        queriesPerServer: Int = 3,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<BenchmarkResult> {
        val allServers = servers + includeCustom.map { it to "Custom" }
        val total = allServers.size
        var completed = 0
        val lock = Any()

        return withContext(Dispatchers.IO) {
            allServers.map { (ip, name) ->
                async {
                    val result = benchmarkServer(ip, name, queriesPerServer)
                    synchronized(lock) {
                        completed++
                        onProgress(completed, total)
                    }
                    result
                }
            }.awaitAll().sortedBy { it.avgLatencyMs }
        }
    }

    private fun benchmarkServer(
        ip: String,
        name: String,
        queriesPerServer: Int
    ): BenchmarkResult {
        val latencies = mutableListOf<Long>()
        var successes = 0
        val totalQueries = queriesPerServer * testDomains.size

        for (i in 0 until queriesPerServer) {
            for (domain in testDomains) {
                val latency = measureDnsQuery(ip, domain)
                if (latency >= 0) {
                    latencies.add(latency)
                    successes++
                }
            }
        }

        val avg = if (latencies.isNotEmpty()) latencies.average().toInt() else Int.MAX_VALUE
        val min = latencies.minOrNull()?.toInt() ?: Int.MAX_VALUE
        val max = latencies.maxOrNull()?.toInt() ?: Int.MAX_VALUE
        val rate = successes.toFloat() / totalQueries

        return BenchmarkResult(
            serverIp = ip,
            serverName = name,
            avgLatencyMs = avg,
            minLatencyMs = min,
            maxLatencyMs = max,
            successRate = rate,
            isReachable = successes > 0
        )
    }

    /**
     * Send a single DNS A-record query to [serverIp] for [domain] over UDP.
     *
     * @return round-trip time in milliseconds, or -1 on failure/timeout
     */
    private fun measureDnsQuery(serverIp: String, domain: String): Long {
        return try {
            val packet = buildDnsQuery(domain)
            val address = InetAddress.getByName(serverIp)
            val sendPacket = DatagramPacket(packet, packet.size, address, DNS_PORT)
            val recvBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

            DatagramSocket().use { socket ->
                // Connect so the OS drops datagrams from any other source, then
                // validate the reply: it must echo the query's transaction ID and
                // carry RCODE 0. Otherwise a SERVFAIL/REFUSED or stray datagram
                // would score as a fast "success" and skew the ranking.
                socket.connect(address, DNS_PORT)
                socket.soTimeout = TIMEOUT_MS
                val start = System.nanoTime()
                socket.send(sendPacket)
                socket.receive(recvPacket)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                val len = recvPacket.length
                if (len < 12) return -1L
                val idMatches = recvBuffer[0] == packet[0] && recvBuffer[1] == packet[1]
                val rcode = recvBuffer[3].toInt() and 0x0F
                if (!idMatches || rcode != 0) return -1L
                elapsed
            }
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Build a minimal DNS query packet for an A record lookup.
     *
     * Layout (RFC 1035):
     *   Header  – 12 bytes
     *   Question – variable (encoded domain + QTYPE + QCLASS)
     */
    private fun buildDnsQuery(domain: String): ByteArray {
        val encodedDomain = encodeDomainName(domain)
        val buffer = ByteBuffer.allocate(12 + encodedDomain.size + 4)

        // ── Header (12 bytes) ──
        val id = Random.nextInt(0xFFFF).toShort()
        buffer.putShort(id)             // ID
        buffer.putShort(0x0100)         // Flags: standard query, recursion desired
        buffer.putShort(1)              // QDCOUNT
        buffer.putShort(0)              // ANCOUNT
        buffer.putShort(0)              // NSCOUNT
        buffer.putShort(0)              // ARCOUNT

        // ── Question ──
        buffer.put(encodedDomain)
        buffer.putShort(1)              // QTYPE  = A
        buffer.putShort(1)              // QCLASS = IN

        return buffer.array()
    }

    /**
     * Encode a domain name into DNS wire format.
     * e.g. "google.com" -> [6]google[3]com[0]
     */
    private fun encodeDomainName(domain: String): ByteArray {
        val parts = domain.split('.')
        val out = mutableListOf<Byte>()
        for (part in parts) {
            out.add(part.length.toByte())
            for (c in part) {
                out.add(c.code.toByte())
            }
        }
        out.add(0) // terminating zero-length label
        return out.toByteArray()
    }
}
