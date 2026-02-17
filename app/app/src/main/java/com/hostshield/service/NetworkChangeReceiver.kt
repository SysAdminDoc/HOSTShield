package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Re-applies iptables firewall rules when network connectivity changes.
 *
 * Some ROMs and kernels flush iptables chains when:
 * - WiFi reconnects
 * - Mobile data toggles
 * - Airplane mode toggled
 * - VPN connects/disconnects
 *
 * This receiver catches those events and re-applies our hs-* chains.
 *
 * AndroidManifest:
 *   <receiver android:name=".service.NetworkChangeReceiver" android:exported="false">
 *       <intent-filter>
 *           <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
 *       </intent-filter>
 *   </receiver>
 *
 * Note: On Android 7+ CONNECTIVITY_CHANGE is only delivered to
 * registered receivers, not manifest-declared. For newer versions
 * we use ConnectivityManager.NetworkCallback in IptablesManager.
 */
@AndroidEntryPoint
class NetworkChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var iptablesManager: IptablesManager

    companion object {
        private const val TAG = "NetChangeRcvr"
        // Debounce: don't re-apply more than once per 10s
        @Volatile private var lastApply = 0L
        private const val DEBOUNCE_MS = 10_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        val now = System.currentTimeMillis()
        if (now - lastApply < DEBOUNCE_MS) return
        lastApply = now

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val networkFwEnabled = prefs.networkFirewallEnabled.first()
                val autoApply = prefs.autoApplyFirewall.first()

                if (networkFwEnabled && autoApply && iptablesManager.isActive.value) {
                    Log.i(TAG, "Network changed, re-applying iptables rules")
                    iptablesManager.applyRules()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Re-apply failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
