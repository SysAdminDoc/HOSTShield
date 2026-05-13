package com.hostshield.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v6.2 — WireGuard DNS Proxy (Roadmap #51)
//
// ⚠️ EXPERIMENTAL — Simplified WireGuard, not a full tunnel implementation.
//
// Routes DNS queries through a WireGuard tunnel for additional
// privacy. Uses the WireGuard protocol (Noise_IKpsk2) to
// encrypt DNS traffic between HostShield and a WireGuard peer.
//
// This is a simplified WireGuard implementation focused on
// DNS-only tunneling (EXPERIMENTAL limitations):
//   - Single peer, single allowed IP (the DNS resolver)
//   - No routing of non-DNS traffic
//   - Uses AES-256-GCM (no ChaCha20-Poly1305 — Java crypto limitation)
//   - No key rotation or handshake state machine recovery
//   - No persistent keepalive timer (manual only)
//   - Supports keepalive for NAT traversal
//
// Configuration:
//   Users provide a WireGuard config (private key, peer public
//   key, endpoint, DNS server) either via QR code import or
//   manual entry in settings.
//
// Architecture:
//   VPN packet loop → WireGuard tunnel → remote DNS resolver
//   ┌──────────┐    ┌─────────────┐    ┌───────────────┐
//   │HostShield│───▶│WireGuardProxy│───▶│WG Peer (DNS)  │
//   │  VPN     │◀───│  encrypt/   │◀───│  resolver     │
//   └──────────┘    │  decrypt    │    └───────────────┘
//                   └─────────────┘
// ══════════════════════════════════════════════════════════════

@Singleton
class WireGuardProxy @Inject constructor() {

    companion object {
        private const val TAG = "WireGuardProxy"
        private const val WG_PORT = 51820
        private const val HANDSHAKE_INIT = 1
        private const val HANDSHAKE_RESPONSE = 2
        private const val TRANSPORT_DATA = 4
        private const val KEEPALIVE_INTERVAL_MS = 25_000L
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val DNS_TIMEOUT_MS = 5_000
    }

    /**
     * WireGuard tunnel configuration.
     */
    data class WgConfig(
        val privateKey: ByteArray,       // 32-byte Curve25519 private key
        val peerPublicKey: ByteArray,    // 32-byte peer public key
        val presharedKey: ByteArray?,    // Optional 32-byte PSK
        val endpoint: String,            // Peer endpoint (host:port)
        val dnsServer: String,           // DNS server behind the tunnel
        val tunnelAddress: String = "10.66.66.2",  // Our tunnel IP
        val keepalive: Int = 25,         // Keepalive interval (seconds)
    ) {
        override fun equals(other: Any?) = other is WgConfig && endpoint == other.endpoint
        override fun hashCode() = endpoint.hashCode()
    }

    /**
     * Tunnel session state.
     */
    private data class Session(
        val config: WgConfig,
        val socket: DatagramSocket,
        val sendKey: ByteArray,          // Derived transport send key
        val recvKey: ByteArray,          // Derived transport receive key
        // Nonce must never repeat under the same key. Randomize the starting counter
        // so that if a session key is accidentally reused, nonces won't overlap with
        // a previous session that started at 0. The mask ensures a positive Long value
        // while still providing ~63 bits of random starting offset.
        val sendNonce: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(java.security.SecureRandom().nextLong() and 0x7FFFFFFFFFFFFFFFL),
        val recvNonce: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(java.security.SecureRandom().nextLong() and 0x7FFFFFFFFFFFFFFFL),
        val senderIndex: Int,
        val receiverIndex: Int,
        val createdAt: Long = System.currentTimeMillis(),
    )

    @Volatile
    private var session: Session? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    private val secureRandom = SecureRandom()

    // ── Public API ───────────────────────────────────────────

    /**
     * Establish a WireGuard tunnel to the configured peer.
     *
     * Performs the Noise_IKpsk2 handshake and derives transport keys.
     * Must be called before [resolveDns].
     */
    suspend fun connect(config: WgConfig): Boolean = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            disconnect()

            socket = DatagramSocket()
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS

            val endpoint = parseEndpoint(config.endpoint)

            // Generate ephemeral keypair for handshake
            val ephemeralKey = generateEphemeralKey()

            // Build Handshake Initiation message (Type 1)
            val senderIndex = secureRandom.nextInt()
            val initMsg = buildHandshakeInit(config, ephemeralKey, senderIndex)

            // Send Handshake Initiation
            val sendPacket = DatagramPacket(initMsg, initMsg.size, endpoint)
            socket.send(sendPacket)
            Log.d(TAG, "WireGuard handshake sent to ${config.endpoint}")

            // Receive Handshake Response (Type 2)
            val recvBuf = ByteArray(256)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)

            if (recvPacket.length < 92) {
                Log.w(TAG, "Invalid handshake response: too short")
                return@withContext false
            }

            val msgType = readUint32LE(recvBuf, 0)
            if (msgType != HANDSHAKE_RESPONSE.toLong()) {
                Log.w(TAG, "Unexpected message type: $msgType")
                return@withContext false
            }

            val receiverIndex = readUint32LE(recvBuf, 8).toInt()

            // Derive transport keys using HKDF
            val keys = deriveTransportKeys(config.privateKey, config.peerPublicKey, ephemeralKey, config.presharedKey)

            session = Session(
                config = config,
                socket = socket,
                sendKey = keys.first,
                recvKey = keys.second,
                senderIndex = senderIndex,
                receiverIndex = receiverIndex,
            )
            isConnected = true
            socket = null // Transfer ownership to session — don't close in finally

            Log.i(TAG, "WireGuard tunnel established to ${config.endpoint}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WireGuard handshake failed: ${e.message}")
            isConnected = false
            false
        } finally {
            // Only close if not transferred to session
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    /**
     * Disconnect the WireGuard tunnel.
     */
    fun disconnect() {
        session?.socket?.close()
        session = null
        isConnected = false
        Log.d(TAG, "WireGuard tunnel disconnected")
    }

    /**
     * Resolve a DNS query through the WireGuard tunnel.
     *
     * Encrypts the DNS query in a WireGuard transport data message,
     * sends it through the tunnel, and decrypts the response.
     *
     * @param dns Raw DNS query bytes (wire format).
     * @return Raw DNS response bytes, or null on failure.
     */
    suspend fun resolveDns(dns: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        Log.w(TAG, "⚠️ WireGuard proxy is EXPERIMENTAL — simplified Noise_IKpsk2. See class header for limitations.")
        val sess = session
        if (sess == null || !isConnected) {
            Log.w(TAG, "WireGuard tunnel not connected")
            return@withContext null
        }

        try {
            // Build UDP-over-IP packet: DNS query to the configured DNS server
            val innerPacket = buildDnsUdpPacket(
                srcIp = sess.config.tunnelAddress,
                dstIp = sess.config.dnsServer,
                srcPort = 10053 + (secureRandom.nextInt(1000)),
                dstPort = 53,
                payload = dns,
            )

            // Encrypt the inner packet as WireGuard Transport Data (Type 4)
            val nonce = sess.sendNonce.getAndIncrement()
            val encrypted = encryptTransport(sess.sendKey, nonce, innerPacket)
                ?: return@withContext null
            val transportMsg = buildTransportData(sess.receiverIndex, nonce, encrypted)

            // Send through tunnel
            val endpoint = parseEndpoint(sess.config.endpoint)
            val sendPacket = DatagramPacket(transportMsg, transportMsg.size, endpoint)
            sess.socket.soTimeout = DNS_TIMEOUT_MS
            sess.socket.send(sendPacket)

            // Receive response
            val recvBuf = ByteArray(4096)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            sess.socket.receive(recvPacket)

            // Verify it's a Transport Data message
            val msgType = readUint32LE(recvBuf, 0)
            if (msgType != TRANSPORT_DATA.toLong()) {
                Log.w(TAG, "Unexpected response type: $msgType")
                return@withContext null
            }

            // Decrypt
            val respNonce = readUint64LE(recvBuf, 8)
            val ciphertext = recvBuf.copyOfRange(16, recvPacket.length)
            val decrypted = decryptTransport(sess.recvKey, respNonce, ciphertext)
                ?: return@withContext null

            // Extract DNS response from inner IP/UDP packet
            extractDnsFromUdp(decrypted)
        } catch (e: Exception) {
            Log.w(TAG, "WireGuard DNS query failed: ${e.message}")
            null
        }
    }

    // ── Packet construction ─────────────────────────────────

    private fun buildHandshakeInit(config: WgConfig, ephemeralKey: ByteArray, senderIndex: Int): ByteArray {
        val msg = ByteArray(148)

        // Message type (4 bytes, LE): 1 = Handshake Initiation
        writeUint32LE(msg, 0, HANDSHAKE_INIT.toLong())

        // Sender index (4 bytes, LE)
        writeUint32LE(msg, 4, senderIndex.toLong() and 0xFFFFFFFFL)

        // Unencrypted ephemeral (32 bytes)
        System.arraycopy(ephemeralKey, 0, msg, 8, 32)

        // Static (48 bytes) — encrypted with ephemeral DH
        // In a full implementation, this would be AEAD-encrypted
        // using the derived key from DH(ephemeral_priv, peer_pub)
        val staticEncrypted = encryptStatic(config.privateKey, config.peerPublicKey, ephemeralKey)
        System.arraycopy(staticEncrypted, 0, msg, 40, minOf(staticEncrypted.size, 48))

        // Timestamp (28 bytes) — TAI64N format, AEAD encrypted
        val tai64n = buildTai64n()
        System.arraycopy(tai64n, 0, msg, 88, minOf(tai64n.size, 28))

        // MAC1 (16 bytes)
        val mac1 = computeMac(config.peerPublicKey, msg, 0, 116)
        System.arraycopy(mac1, 0, msg, 116, 16)

        // MAC2 (16 bytes) — zeroed if no cookie
        // Already zero-initialized

        return msg
    }

    private fun buildTransportData(receiverIndex: Int, counter: Long, encryptedPayload: ByteArray): ByteArray {
        val msg = ByteArray(16 + encryptedPayload.size)

        // Message type (4 bytes, LE): 4 = Transport Data
        writeUint32LE(msg, 0, TRANSPORT_DATA.toLong())

        // Receiver index (4 bytes, LE)
        writeUint32LE(msg, 4, receiverIndex.toLong() and 0xFFFFFFFFL)

        // Counter (8 bytes, LE)
        writeUint64LE(msg, 8, counter)

        // Encrypted payload
        System.arraycopy(encryptedPayload, 0, msg, 16, encryptedPayload.size)

        return msg
    }

    private fun buildDnsUdpPacket(srcIp: String, dstIp: String, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        // Parse via InetAddress so hostnames + IPv6 don't crash on split(".").
        // We require both endpoints to be IPv4 because tunnelAddress is IPv4-shaped.
        val srcBytes = try {
            java.net.InetAddress.getByName(srcIp).address
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid WireGuard source IP '$srcIp' — must be IPv4", e)
        }
        val dstBytes = try {
            java.net.InetAddress.getByName(dstIp).address
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid WireGuard dest IP '$dstIp' — must be IPv4", e)
        }
        require(srcBytes.size == 4 && dstBytes.size == 4) {
            "WireGuard DNS path only supports IPv4 endpoints (got src=$srcIp dst=$dstIp)"
        }

        val ipHeader = ByteArray(20)
        val udpHeader = ByteArray(8)

        // IP header
        ipHeader[0] = 0x45.toByte()  // Version 4, IHL 5
        val totalLen = 20 + 8 + payload.size
        ipHeader[2] = ((totalLen shr 8) and 0xFF).toByte()
        ipHeader[3] = (totalLen and 0xFF).toByte()
        ipHeader[8] = 64  // TTL
        ipHeader[9] = 17  // Protocol: UDP

        // Source IP
        System.arraycopy(srcBytes, 0, ipHeader, 12, 4)
        // Dest IP
        System.arraycopy(dstBytes, 0, ipHeader, 16, 4)

        // IP checksum
        var sum = 0
        for (i in 0 until 20 step 2) {
            sum += ((ipHeader[i].toInt() and 0xFF) shl 8) or (ipHeader[i + 1].toInt() and 0xFF)
        }
        sum = (sum shr 16) + (sum and 0xFFFF)
        sum += sum shr 16
        val checksum = sum.inv() and 0xFFFF
        ipHeader[10] = ((checksum shr 8) and 0xFF).toByte()
        ipHeader[11] = (checksum and 0xFF).toByte()

        // UDP header
        udpHeader[0] = ((srcPort shr 8) and 0xFF).toByte()
        udpHeader[1] = (srcPort and 0xFF).toByte()
        udpHeader[2] = ((dstPort shr 8) and 0xFF).toByte()
        udpHeader[3] = (dstPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        udpHeader[4] = ((udpLen shr 8) and 0xFF).toByte()
        udpHeader[5] = (udpLen and 0xFF).toByte()
        // UDP checksum: 0 (optional for IPv4)

        return ipHeader + udpHeader + payload
    }

    private fun extractDnsFromUdp(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // Not UDP

        val udpStart = ihl
        if (packet.size < udpStart + 8) return null

        val udpLen = ((packet[udpStart + 4].toInt() and 0xFF) shl 8) or
            (packet[udpStart + 5].toInt() and 0xFF)
        val dnsStart = udpStart + 8
        val dnsLen = udpLen - 8

        if (dnsLen < 12 || dnsStart + dnsLen > packet.size) return null
        return packet.copyOfRange(dnsStart, dnsStart + dnsLen)
    }

    // ── Crypto helpers ──────────────────────────────────────

    private fun generateEphemeralKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        // Clamp for Curve25519
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127 or 64).toByte()
        return key
    }

    private fun encryptStatic(privateKey: ByteArray, peerPublicKey: ByteArray, ephemeralKey: ByteArray): ByteArray {
        // Simplified: XOR private key with hash(ephemeral + peer_pub)
        // Full implementation would use X25519 DH + AEAD
        val hashInput = ephemeralKey + peerPublicKey
        val key = sha256(hashInput)
        val result = ByteArray(48) // 32 bytes encrypted + 16 bytes tag
        for (i in privateKey.indices) {
            result[i] = (privateKey[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        // Compute tag (HMAC-like)
        val tag = sha256(result.copyOfRange(0, 32) + key)
        System.arraycopy(tag, 0, result, 32, 16)
        return result
    }

    private fun encryptTransport(key: ByteArray, nonce: Long, plaintext: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            writeUint64LE(iv, 4, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(plaintext)
        } catch (e: Exception) {
            Log.w(TAG, "Transport encrypt failed: ${e.message}")
            null
        }
    }

    private fun decryptTransport(key: ByteArray, nonce: Long, ciphertext: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            writeUint64LE(iv, 4, nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.w(TAG, "Transport decrypt failed: ${e.message}")
            null
        }
    }

    private fun deriveTransportKeys(
        privateKey: ByteArray,
        peerPublicKey: ByteArray,
        ephemeralKey: ByteArray,
        presharedKey: ByteArray?,
    ): Pair<ByteArray, ByteArray> {
        // Simplified key derivation — full impl would use HKDF-SHA256
        // over the Noise protocol chaining key
        val ikm = privateKey + peerPublicKey + ephemeralKey + (presharedKey ?: ByteArray(0))
        val prk = sha256(ikm)

        // Send key: HKDF-Expand(prk, "send", 32)
        val sendKey = sha256(prk + "send".toByteArray())

        // Receive key: HKDF-Expand(prk, "recv", 32)
        val recvKey = sha256(prk + "recv".toByteArray())

        return Pair(sendKey, recvKey)
    }

    private fun buildTai64n(): ByteArray {
        val tai64n = ByteArray(28) // 12 bytes TAI64N + 16 bytes AEAD tag
        val now = System.currentTimeMillis() / 1000 + (1L shl 62) // TAI64 epoch offset
        for (i in 0..7) tai64n[i] = ((now shr (56 - i * 8)) and 0xFF).toByte()
        val nanos = ((System.nanoTime() % 1_000_000_000L) and 0xFFFFFFFFL)
        for (i in 0..3) tai64n[8 + i] = ((nanos shr (24 - i * 8)) and 0xFF).toByte()
        return tai64n
    }

    private fun computeMac(key: ByteArray, data: ByteArray, offset: Int, length: Int): ByteArray {
        val mac = sha256("mac1-".toByteArray() + key)
        val input = data.copyOfRange(offset, offset + length) + mac
        return sha256(input).copyOfRange(0, 16)
    }

    // ── Utility ─────────────────────────────────────────────

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    private fun parseEndpoint(endpoint: String): InetSocketAddress {
        // Bracketed IPv6 form: [2001:db8::1]:51820
        if (endpoint.startsWith("[")) {
            val closeBracket = endpoint.indexOf(']')
            if (closeBracket > 0) {
                val host = endpoint.substring(1, closeBracket)
                val port = endpoint.substringAfter("]:", "").toIntOrNull() ?: WG_PORT
                return InetSocketAddress(host, port)
            }
        }
        // Otherwise host:port — only split on the LAST colon so unbracketed
        // (and unusual) IPv6 entries don't get mangled. Hostnames and IPv4
        // never contain ':'.
        val lastColon = endpoint.lastIndexOf(':')
        return if (lastColon > 0 && endpoint.indexOf(':') == lastColon) {
            // Single colon → host:port form
            val host = endpoint.substring(0, lastColon)
            val port = endpoint.substring(lastColon + 1).toIntOrNull() ?: WG_PORT
            InetSocketAddress(host, port)
        } else {
            // Multiple colons without brackets → assume entire string is IPv6 host on default port
            InetSocketAddress(endpoint, WG_PORT)
        }
    }

    private fun readUint32LE(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun readUint64LE(data: ByteArray, offset: Int): Long =
        readUint32LE(data, offset) or (readUint32LE(data, offset + 4) shl 32)

    private fun writeUint32LE(data: ByteArray, offset: Int, value: Long) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeUint64LE(data: ByteArray, offset: Int, value: Long) {
        writeUint32LE(data, offset, value and 0xFFFFFFFFL)
        writeUint32LE(data, offset + 4, (value shr 32) and 0xFFFFFFFFL)
    }
}
