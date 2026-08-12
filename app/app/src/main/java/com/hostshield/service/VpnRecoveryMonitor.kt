package com.hostshield.service

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.hostshield.util.Android16VpnRecoveryDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches the Android 16 always-on/lockdown failure mode where the VPN is up
 * but the system never delivers packets to its TUN interface.
 */
internal class VpnRecoveryMonitor(
    private val service: VpnService,
    private val vpnRunning: () -> Boolean,
    private val tunFdValid: () -> Boolean,
    private val vpnEstablishedAt: () -> Long,
    private val inboundPacketCount: () -> Long,
) {
    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L
        private const val TAG = "HostShield"
        private val advisoryState = MutableStateFlow<VpnRecoveryAdvisory?>(null)

        val advisory: StateFlow<VpnRecoveryAdvisory?> = advisoryState

        fun dismiss() {
            advisoryState.value = null
        }
    }

    private var monitorJob: Job? = null

    fun start(scope: CoroutineScope) {
        cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive && vpnRunning()) {
                delay(CHECK_INTERVAL_MS)
                evaluate()
            }
        }
    }

    fun cancel() {
        monitorJob?.cancel()
        monitorJob = null
        advisoryState.value = null
    }

    fun onInboundPacket() {
        if (advisoryState.value != null) advisoryState.value = null
    }

    private fun evaluate() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = Build.VERSION.SDK_INT,
            vpnRunning = vpnRunning(),
            alwaysOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { service.isAlwaysOn() } catch (_: Exception) { false }
            } else false,
            lockdownEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { service.isLockdownEnabled() } catch (_: Exception) { false }
            } else false,
            tunFdValid = tunFdValid(),
            hasValidatedPhysicalNetwork = hasValidatedPhysicalNetwork(),
            elapsedSinceVpnStartMs = SystemClock.elapsedRealtime() - vpnEstablishedAt(),
            inboundPacketCount = inboundPacketCount(),
        )

        if (Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)) {
            if (advisoryState.value == null) {
                advisoryState.value = VpnRecoveryAdvisory(
                    title = "Restart device to recover VPN",
                    message = "Android 16 always-on lockdown is active, but HostShield has not received tunnel traffic since startup. This can happen after system updates; a device restart usually restores the VPN stack.",
                    detectedAtMillis = System.currentTimeMillis(),
                )
                Log.w(TAG, "Android 16 VPN recovery advisory raised: $snapshot")
            }
        } else if (snapshot.inboundPacketCount > 0L && advisoryState.value != null) {
            advisoryState.value = null
        }
    }

    @Suppress("DEPRECATION")
    private fun hasValidatedPhysicalNetwork(): Boolean {
        val connectivityManager = service.getSystemService(ConnectivityManager::class.java)
            ?: return false
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@any false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }
}
