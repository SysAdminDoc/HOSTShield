package com.hostshield.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptivePortalHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CaptivePortal"
        private const val NOTIFICATION_ID = 42
        private const val COOLDOWN_MS = 5000L
        // Intentionally HTTP, not HTTPS. Android's captive portal detection works by
        // issuing a plain HTTP request and checking for an HTTP 204 response. If the
        // network's captive portal intercepts the request (302 redirect to a login page),
        // the OS knows the user must authenticate. HTTPS would break this mechanism
        // because the TLS handshake would fail before reaching the portal's redirect.
        private const val CAPTIVE_PORTAL_URL = "http://connectivitycheck.gstatic.com/generate_204"
    }

    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastDetectionAt = 0L
    @Volatile private var isHandlingPortal = false

    fun register() {
        if (callback != null) return
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastDetectionAt < COOLDOWN_MS) return
                    lastDetectionAt = now
                    Log.i(TAG, "Captive portal detected — pausing VPN for login")
                    isHandlingPortal = true
                    pauseVpn()
                    showLoginNotification(network)
                }
            }
            callback = cb
            cm.registerNetworkCallback(request, cb)
            // Also register for validation to detect when portal is cleared
            registerValidationCallback()
            Log.i(TAG, "Captive portal handler registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register captive portal handler: ${e.message}")
        }
    }

    private var validationCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerValidationCallback() {
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (!isHandlingPortal) return
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                        Log.i(TAG, "Captive portal cleared — resuming VPN")
                        isHandlingPortal = false
                        resumeVpn()
                        dismissNotification()
                    }
                }
            }
            validationCallback = cb
            cm.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register validation callback: ${e.message}")
        }
    }

    fun unregister() {
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
            callback?.let { cm.unregisterNetworkCallback(it) }
            validationCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) { }
        callback = null
        validationCallback = null
        isHandlingPortal = false
        Log.i(TAG, "Captive portal handler unregistered")
    }

    private fun pauseVpn() {
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_PAUSE
            putExtra("pause_minutes", 3)  // 3-minute window for portal login
        }
        context.startService(intent)
    }

    private fun resumeVpn() {
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_PAUSE
            putExtra("pause_minutes", 0)  // 0 = resume immediately
        }
        context.startService(intent)
    }

    private fun showLoginNotification(network: Network) {
        // Try to get the captive portal URL from the network
        val portalUrl = getCaptivePortalUrl(network)

        val loginIntent = Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, NOTIFICATION_ID, loginIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, DnsVpnService.ALERT_CHANNEL_ID)
            .setContentTitle("Captive Portal Detected")
            .setContentText("VPN paused — tap to log in to Wi-Fi network")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_directions, "Login", pi)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    private fun getCaptivePortalUrl(network: Network): String {
        // On API 30+, ConnectivityManager can provide the portal URL via LinkProperties
        // Fall back to the standard connectivity check URL
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return CAPTIVE_PORTAL_URL
            val lp = cm.getLinkProperties(network)
            // CaptivePortalData is API 30+ but its userPortalUrl may not always
            // be populated. Fall through to default connectivity check URL.
        } catch (_: Exception) { }
        return CAPTIVE_PORTAL_URL
    }

    private fun dismissNotification() {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }
}
