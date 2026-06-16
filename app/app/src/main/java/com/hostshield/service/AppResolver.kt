package com.hostshield.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Resolves the requesting app (package name + label) from DNS packet source address/port.
 * Uses ConnectivityManager.getConnectionOwnerUid on API 29+ with /proc/net/udp fallback.
 */
class AppResolver(
    private val context: Context,
    private val protectSocket: () -> Unit = {}
) {
    companion object {
        private const val TAG = "AppResolver"
    }

    private val pm: PackageManager get() = context.packageManager

    /** Resolve app from IPv4 DNS packet. Returns (packageName, appLabel). */
    fun resolveApp(p: ByteArray, ihl: Int): Pair<String, String> {
        try {
            val srcPort = ((p[ihl].toInt() and 0xFF) shl 8) or (p[ihl + 1].toInt() and 0xFF)
            if (srcPort == 0) return "" to ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "" to ""
                val src = InetAddress.getByAddress(p.sliceArray(12 until 16))
                val dst = InetAddress.getByAddress(p.sliceArray(16 until 20))
                val uid = cm.getConnectionOwnerUid(
                    android.system.OsConstants.IPPROTO_UDP,
                    InetSocketAddress(src, srcPort), InetSocketAddress(dst, PacketClassifier.DNS_PORT)
                )
                if (uid > 0) return resolvePkg(uid)
            }
            val uid = findUidFromPort(srcPort)
            if (uid > 0) return resolvePkg(uid)
        } catch (e: Exception) { Log.w(TAG, "IPv4 app resolve failed: ${e.message}") }
        return "" to ""
    }

    /** Resolve app from IPv6 DNS packet. Returns (packageName, appLabel). */
    fun resolveAppV6(p: ByteArray, hdr: Int): Pair<String, String> {
        try {
            val srcPort = ((p[hdr].toInt() and 0xFF) shl 8) or (p[hdr + 1].toInt() and 0xFF)
            if (srcPort == 0) return "" to ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "" to ""
                val src = InetAddress.getByAddress(p.sliceArray(8 until 24))
                val dst = InetAddress.getByAddress(p.sliceArray(24 until 40))
                val uid = cm.getConnectionOwnerUid(
                    android.system.OsConstants.IPPROTO_UDP,
                    InetSocketAddress(src, srcPort), InetSocketAddress(dst, PacketClassifier.DNS_PORT)
                )
                if (uid > 0) return resolvePkg(uid)
            }
            val uid = findUidFromPort(srcPort)
            if (uid > 0) return resolvePkg(uid)
        } catch (e: Exception) { Log.w(TAG, "IPv6 app resolve failed: ${e.message}") }
        return "" to ""
    }

    /** Convert UID to (packageName, appLabel). */
    fun resolvePkg(uid: Int): Pair<String, String> {
        try {
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return "" to ""
            return pkg to pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { Log.w(TAG, "UID $uid package resolve failed: ${e.message}") }
        return "" to ""
    }

    /** Parse /proc/net/udp{,6} to find UID by source port. */
    fun findUidFromPort(port: Int): Int {
        try {
            val hex = String.format("%04X", port)
            for (path in arrayOf("/proc/net/udp", "/proc/net/udp6")) {
                for (line in java.io.File(path).readLines()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 8 && parts[1].endsWith(":$hex"))
                        return parts[7].toIntOrNull() ?: -1
                }
            }
        } catch (e: Exception) { Log.w(TAG, "Port $port UID lookup failed: ${e.message}") }
        return -1
    }
}
