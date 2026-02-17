package com.hostshield.util

import android.content.Context
import android.util.Log
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.database.DnsLogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports blocked DNS queries and firewall connection logs as PCAP files.
 *
 * Generates synthetic DNS query packets for each blocked domain so the
 * PCAP can be opened in Wireshark for analysis. Connection log entries
 * are represented as TCP SYN packets to show blocked destinations.
 *
 * PCAP format: https://wiki.wireshark.org/Development/LibpcapFileFormat
 *
 * Usage:
 *   val file = pcapExporter.exportDnsLogs(context, days = 7)
 *   // Share file via Intent
 */
@Singleton
class PcapExporter @Inject constructor(
    private val dnsLogDao: DnsLogDao,
    private val connectionLogDao: ConnectionLogDao
) {
    companion object {
        private const val TAG = "PcapExport"
        // PCAP magic numbers
        private const val PCAP_MAGIC = 0xA1B2C3D4.toInt()
        private const val PCAP_VERSION_MAJOR = 2
        private const val PCAP_VERSION_MINOR = 4
        private const val PCAP_SNAPLEN = 65535
        private const val LINKTYPE_RAW = 101  // Raw IPv4/IPv6
    }

    /**
     * Export blocked DNS queries from the last N days as a PCAP file.
     * Returns the file path, or null on failure.
     */
    suspend fun exportDnsLogs(context: Context, days: Int = 7): File? = withContext(Dispatchers.IO) {
        try {
            val since = System.currentTimeMillis() - (days.toLong() * 86_400_000)
            val logs = dnsLogDao.getBlockedLogs(limit = 10000).first()
                .filter { it.timestamp >= since }

            if (logs.isEmpty()) {
                Log.w(TAG, "No blocked DNS logs to export")
                return@withContext null
            }

            val file = File(context.cacheDir, "hostshield_dns_${System.currentTimeMillis()}.pcap")
            FileOutputStream(file).use { fos ->
                writePcapHeader(fos)

                for (entry in logs) {
                    val packet = buildDnsQueryPacket(
                        hostname = entry.hostname,
                        timestamp = entry.timestamp,
                        srcPort = (1024 + (entry.id % 64000)).toInt()
                    )
                    writePcapRecord(fos, packet, entry.timestamp)
                }
            }

            Log.i(TAG, "Exported ${logs.size} DNS entries to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "DNS PCAP export failed: ${e.message}", e)
            null
        }
    }

    /**
     * Export connection (firewall) logs as PCAP.
     */
    suspend fun exportConnectionLogs(context: Context, days: Int = 7): File? = withContext(Dispatchers.IO) {
        try {
            val since = System.currentTimeMillis() - (days.toLong() * 86_400_000)
            val logs = connectionLogDao.getBlockedLogs(limit = 10000).first()
                .filter { it.timestamp >= since }

            if (logs.isEmpty()) return@withContext null

            val file = File(context.cacheDir, "hostshield_fw_${System.currentTimeMillis()}.pcap")
            FileOutputStream(file).use { fos ->
                writePcapHeader(fos)

                for (entry in logs) {
                    val packet = buildTcpSynPacket(
                        dstIp = entry.destination,
                        dstPort = entry.port,
                        srcPort = (entry.uid % 64000) + 1024,
                        isTcp = entry.protocol.equals("TCP", true)
                    )
                    if (packet != null) {
                        writePcapRecord(fos, packet, entry.timestamp)
                    }
                }
            }

            Log.i(TAG, "Exported ${logs.size} connection entries")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Connection PCAP export failed: ${e.message}", e)
            null
        }
    }

    /**
     * Export both DNS and connection logs into a single PCAP.
     */
    suspend fun exportAll(context: Context, days: Int = 7): File? = withContext(Dispatchers.IO) {
        try {
            val since = System.currentTimeMillis() - (days.toLong() * 86_400_000)

            val dnsLogs = dnsLogDao.getBlockedLogs(limit = 10000).first()
                .filter { it.timestamp >= since }
            val connLogs = connectionLogDao.getBlockedLogs(limit = 10000).first()
                .filter { it.timestamp >= since }

            if (dnsLogs.isEmpty() && connLogs.isEmpty()) return@withContext null

            val file = File(context.cacheDir, "hostshield_all_${System.currentTimeMillis()}.pcap")
            FileOutputStream(file).use { fos ->
                writePcapHeader(fos)

                // Merge both log types sorted by timestamp
                data class TimedPacket(val timestamp: Long, val data: ByteArray)
                val packets = mutableListOf<TimedPacket>()

                for (dns in dnsLogs) {
                    val pkt = buildDnsQueryPacket(dns.hostname, dns.timestamp, (1024 + (dns.id % 64000)).toInt())
                    packets.add(TimedPacket(dns.timestamp, pkt))
                }
                for (conn in connLogs) {
                    val pkt = buildTcpSynPacket(conn.destination, conn.port, (conn.uid % 64000) + 1024, conn.protocol.equals("TCP", true))
                    if (pkt != null) packets.add(TimedPacket(conn.timestamp, pkt))
                }

                packets.sortBy { it.timestamp }
                for (p in packets) {
                    writePcapRecord(fos, p.data, p.timestamp)
                }
            }

            Log.i(TAG, "Exported ${dnsLogs.size} DNS + ${connLogs.size} connection entries")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Combined PCAP export failed: ${e.message}", e)
            null
        }
    }

    // ---- PCAP file format helpers ----

    private fun writePcapHeader(out: OutputStream) {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(PCAP_MAGIC)
        buf.putShort(PCAP_VERSION_MAJOR.toShort())
        buf.putShort(PCAP_VERSION_MINOR.toShort())
        buf.putInt(0)  // thiszone
        buf.putInt(0)  // sigfigs
        buf.putInt(PCAP_SNAPLEN)
        buf.putInt(LINKTYPE_RAW)
        out.write(buf.array())
    }

    private fun writePcapRecord(out: OutputStream, data: ByteArray, timestampMs: Long) {
        val sec = (timestampMs / 1000).toInt()
        val usec = ((timestampMs % 1000) * 1000).toInt()

        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(sec)
        header.putInt(usec)
        header.putInt(data.size)  // incl_len
        header.putInt(data.size)  // orig_len
        out.write(header.array())
        out.write(data)
    }

    // ---- Synthetic packet builders ----

    /**
     * Build a synthetic DNS query packet (UDP) for a hostname.
     * IPv4 header + UDP header + DNS query payload.
     */
    private fun buildDnsQueryPacket(hostname: String, timestamp: Long, srcPort: Int): ByteArray {
        val dnsPayload = buildDnsQuery(hostname, (timestamp and 0xFFFF).toInt())
        val udpLen = 8 + dnsPayload.size
        val ipLen = 20 + udpLen

        val buf = ByteBuffer.allocate(ipLen)

        // IPv4 header (20 bytes)
        buf.put(0x45.toByte())           // version + IHL
        buf.put(0x00.toByte())           // DSCP
        buf.putShort(ipLen.toShort())    // total length
        buf.putShort(0x0000.toShort())   // identification
        buf.putShort(0x4000.toShort())   // flags + fragment offset (DF)
        buf.put(64.toByte())             // TTL
        buf.put(17.toByte())             // protocol: UDP
        buf.putShort(0.toShort())        // checksum (0 = not computed)
        buf.put(byteArrayOf(10, 0, 0, 1))    // src IP
        buf.put(byteArrayOf(8, 8, 8, 8))     // dst IP (Google DNS placeholder)

        // UDP header (8 bytes)
        buf.putShort(srcPort.toShort())  // src port
        buf.putShort(53.toShort())       // dst port
        buf.putShort(udpLen.toShort())   // length
        buf.putShort(0.toShort())        // checksum

        // DNS payload
        buf.put(dnsPayload)

        return buf.array()
    }

    /**
     * Build minimal DNS query payload for a hostname.
     */
    private fun buildDnsQuery(hostname: String, txId: Int): ByteArray {
        val labels = hostname.split('.')
        // Calculate size: 12 (header) + labels + 1 (null) + 4 (QTYPE + QCLASS)
        val nameLen = labels.sumOf { it.length + 1 } + 1
        val buf = ByteBuffer.allocate(12 + nameLen + 4)

        // DNS header
        buf.putShort(txId.toShort())     // Transaction ID
        buf.putShort(0x0100.toShort())   // Flags: standard query, recursion desired
        buf.putShort(1.toShort())        // Questions: 1
        buf.putShort(0.toShort())        // Answers: 0
        buf.putShort(0.toShort())        // Authority: 0
        buf.putShort(0.toShort())        // Additional: 0

        // QNAME
        for (label in labels) {
            buf.put(label.length.toByte())
            buf.put(label.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0.toByte()) // null terminator

        // QTYPE (A = 1) + QCLASS (IN = 1)
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        return buf.array()
    }

    /**
     * Build synthetic TCP SYN or UDP packet for a connection log entry.
     */
    private fun buildTcpSynPacket(dstIp: String, dstPort: Int, srcPort: Int, isTcp: Boolean): ByteArray? {
        val dstBytes = try {
            val parts = dstIp.split('.')
            if (parts.size != 4) return null
            parts.map { it.toInt().toByte() }.toByteArray()
        } catch (_: Exception) { return null }

        val transportLen = if (isTcp) 20 else 8
        val ipLen = 20 + transportLen
        val buf = ByteBuffer.allocate(ipLen)

        // IPv4 header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(ipLen.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put((if (isTcp) 6 else 17).toByte()) // TCP=6, UDP=17
        buf.putShort(0.toShort())
        buf.put(byteArrayOf(10, 0, 0, 1))
        buf.put(dstBytes)

        if (isTcp) {
            // TCP header (20 bytes, SYN)
            buf.putShort(srcPort.toShort())
            buf.putShort(dstPort.toShort())
            buf.putInt(0)          // sequence
            buf.putInt(0)          // ack
            buf.putShort(0x5002.toShort()) // data offset=5, SYN flag
            buf.putShort(65535.toShort())  // window
            buf.putShort(0.toShort())      // checksum
            buf.putShort(0.toShort())      // urgent
        } else {
            // UDP header (8 bytes)
            buf.putShort(srcPort.toShort())
            buf.putShort(dstPort.toShort())
            buf.putShort(transportLen.toShort())
            buf.putShort(0.toShort())
        }

        return buf.array()
    }
}
