package com.hostshield.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hostshield.R
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Quick Settings tile for toggling HostShield protection.
 *
 * Tile states:
 *   ACTIVE   = protection running (green shield)
 *   INACTIVE = protection off (grey shield)
 *
 * Tap behavior depends on current block method:
 *   ROOT_HOSTS -> starts/stops root DNS logger
 *   VPN -> starts/stops VPN service
 *
 * AndroidManifest entry:
 *   <service
 *       android:name=".service.HostShieldTileService"
 *       android:icon="@drawable/ic_shield"
 *       android:label="HostShield"
 *       android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
 *       android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.service.quicksettings.action.QS_TILE" />
 *       </intent-filter>
 *   </service>
 */
@AndroidEntryPoint
class HostShieldTileService : TileService() {

    @Inject lateinit var prefs: AppPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val isEnabled = prefs.isEnabled.first()
            val method = prefs.blockMethod.first()
            updateTile(isEnabled, method)
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val isEnabled = prefs.isEnabled.first()
            val method = prefs.blockMethod.first()

            if (isEnabled) {
                // Stop
                when (method) {
                    BlockMethod.VPN -> {
                        val intent = Intent(this@HostShieldTileService, DnsVpnService::class.java)
                            .apply { action = DnsVpnService.ACTION_STOP }
                        startService(intent)
                    }
                    BlockMethod.ROOT_HOSTS -> {
                        RootDnsService.stop(this@HostShieldTileService)
                    }
                    BlockMethod.DNS_PROXY -> {
                        stopService(Intent(this@HostShieldTileService, DnsProxyService::class.java))
                    }
                    BlockMethod.DISABLED -> { }
                }
                prefs.setEnabled(false)
                HostShieldWidgetProvider.updateWidget(
                    applicationContext, false, prefs.lastApplyCount.first()
                )
                updateTile(false, method)
            } else {
                // No protection method chosen — nothing to start. Keep the tile
                // inactive instead of flipping it ACTIVE with no running service.
                if (method == BlockMethod.DISABLED) {
                    updateTile(false, method)
                    return@launch
                }
                // VPN consent missing (tile added before first in-app enable, or
                // another VPN app took the slot): establish() would return null
                // and the service would stopSelf() — while the tile/widget/pref
                // all claimed "Protected". Route through the app to request it.
                if (method == BlockMethod.VPN &&
                    android.net.VpnService.prepare(this@HostShieldTileService) != null
                ) {
                    launchAppForConsent()
                    updateTile(false, method)
                    return@launch
                }
                // Start — only mark enabled if the start was actually accepted.
                val started = when (method) {
                    BlockMethod.VPN -> {
                        val intent = Intent(this@HostShieldTileService, DnsVpnService::class.java)
                            .apply { action = DnsVpnService.ACTION_START }
                        ProtectionServiceStarter.startForegroundService(
                            this@HostShieldTileService,
                            intent,
                            "HostShieldTileService"
                        )
                    }
                    BlockMethod.ROOT_HOSTS -> {
                        RootDnsService.start(this@HostShieldTileService, "HostShieldTileService")
                    }
                    BlockMethod.DNS_PROXY -> {
                        DnsProxyService.start(this@HostShieldTileService, "HostShieldTileService")
                    }
                    BlockMethod.DISABLED -> false
                }
                if (!started) {
                    updateTile(false, method)
                    return@launch
                }
                prefs.setEnabled(true)
                val count = prefs.lastApplyCount.first()
                HostShieldWidgetProvider.updateWidget(
                    applicationContext, true, count, mode = method.name,
                )
                updateTile(true, method)
            }
        }
    }

    private fun launchAppForConsent() {
        val intent = Intent(this, com.hostshield.MainActivity::class.java).apply {
            action = "com.hostshield.SHORTCUT_TOGGLE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this, 0, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                            android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            android.util.Log.w("HostShieldTile", "Could not open app for VPN consent: ${e.message}")
        }
    }

    private fun updateTile(isEnabled: Boolean, method: BlockMethod) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "HostShield"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !isEnabled -> "Off"
                method == BlockMethod.VPN -> {
                    // Counter resets on VPN (re)start, not at midnight — label
                    // it as a plain count, not "today".
                    val count = DnsVpnService.currentBlockedCount
                    if (count > 0) "$count blocked" else "VPN"
                }
                method == BlockMethod.ROOT_HOSTS -> "Root"
                method == BlockMethod.DNS_PROXY -> "Proxy"
                else -> "Off"
            }
        }
        try {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_shield)
        } catch (_: Exception) { }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
