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
                    BlockMethod.DISABLED -> { }
                }
                prefs.setEnabled(false)
                HostShieldWidgetProvider.updateWidget(applicationContext, false, 0)
                updateTile(false, method)
            } else {
                // Start
                when (method) {
                    BlockMethod.VPN -> {
                        val intent = Intent(this@HostShieldTileService, DnsVpnService::class.java)
                            .apply { action = DnsVpnService.ACTION_START }
                        startForegroundService(intent)
                    }
                    BlockMethod.ROOT_HOSTS -> {
                        RootDnsService.start(this@HostShieldTileService)
                    }
                    BlockMethod.DISABLED -> { }
                }
                prefs.setEnabled(true)
                val count = prefs.lastApplyCount.first()
                HostShieldWidgetProvider.updateWidget(applicationContext, true, count)
                updateTile(true, method)
            }
        }
    }

    private fun updateTile(isEnabled: Boolean, method: BlockMethod) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "HostShield"
        tile.subtitle = when {
            !isEnabled -> "Off"
            method == BlockMethod.VPN -> "VPN"
            method == BlockMethod.ROOT_HOSTS -> "Root"
            else -> "Off"
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
