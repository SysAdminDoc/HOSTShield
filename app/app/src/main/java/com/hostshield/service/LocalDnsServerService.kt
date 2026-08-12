package com.hostshield.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hostshield.MainActivity
import com.hostshield.R
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LocalDnsServerService : Service() {
    companion object {
        const val ACTION_START = "com.hostshield.LAN_DNS_START"
        const val ACTION_STOP = "com.hostshield.LAN_DNS_STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_ALLOW_EXTERNAL_CLIENTS = "allow_external_clients"
        const val EXTRA_PERSIST_DISABLED = "persist_disabled"
        private const val CHANNEL_ID = "hostshield_lan_dns"
        private const val NOTIFICATION_ID = 4
        private const val TAG = "LocalDnsServerService"

        fun start(
            context: Context,
            port: Int,
            allowExternalClients: Boolean,
            caller: String = "LocalDnsServerService.start"
        ): Boolean {
            if (!hasLocalNetworkPermission(context, port)) {
                Log.w(TAG, "LAN DNS start blocked: ACCESS_LOCAL_NETWORK is not granted for UDP $port")
                return false
            }
            val intent = Intent(context, LocalDnsServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_ALLOW_EXTERNAL_CLIENTS, allowExternalClients)
            }
            return ProtectionServiceStarter.startForegroundService(context, intent, caller)
        }

        @Suppress("InlinedApi")
        internal fun hasLocalNetworkPermission(context: Context, listenPort: Int): Boolean {
            if (!localDnsRequiresLocalNetworkPermission(
                    platformSdk = Build.VERSION.SDK_INT,
                    targetSdk = context.applicationInfo.targetSdkVersion,
                    listenPort = listenPort
                )
            ) {
                return true
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun stop(context: Context, persistDisabled: Boolean = true) {
            context.startService(
                Intent(context, LocalDnsServerService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_PERSIST_DISABLED, persistDisabled)
                }
            )
        }
    }

    @Inject lateinit var localDnsServer: LocalDnsServer
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var diagnosticEvents: DiagnosticEventStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopLanDns(
                persistDisabled = intent.getBooleanExtra(EXTRA_PERSIST_DISABLED, true)
            )
            ACTION_START, null -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Starting LAN DNS..."),
                    runtimeForegroundServiceType()
                )
                serviceScope.launch { startLanDns(intent) }
            }
            else -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Resuming LAN DNS..."),
                    runtimeForegroundServiceType()
                )
                serviceScope.launch { startLanDns(null) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        localDnsServer.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout(startId, 0)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout(startId, fgsType)
    }

    private suspend fun startLanDns(intent: Intent?) {
        val requestedPort = intent
            ?.getIntExtra(EXTRA_PORT, -1)
            ?.takeIf(::isSupportedLocalDnsPort)
            ?: prefs.lanDnsPort.first()
        val allowExternalClients = intent
            ?.getBooleanExtra(EXTRA_ALLOW_EXTERNAL_CLIENTS, false)
            ?: prefs.lanDnsAllowExternalClients.first()

        if (!hasLocalNetworkPermission(this, requestedPort)) {
            prefs.setLanDnsEnabled(false)
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.FOREGROUND_SERVICE_START_FAILED,
                "LAN DNS local-network permission was not granted",
                mapOf(
                    "port" to requestedPort,
                    "platform_sdk" to Build.VERSION.SDK_INT,
                    "target_sdk" to applicationInfo.targetSdkVersion
                )
            )
            Log.w(TAG, "LAN DNS service stopped because ACCESS_LOCAL_NETWORK is not granted")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val startedPort = localDnsServer.start(
            listenPort = requestedPort,
            allowExternalClients = allowExternalClients
        )

        if (startedPort > 0) {
            prefs.setLanDnsEnabled(true)
            prefs.setLanDnsPort(startedPort)
            updateNotification(
                "Serving UDP $startedPort; " +
                    if (allowExternalClients) "public-source clients allowed" else "private LAN clients only"
            )
            Log.i(TAG, "LAN DNS service started on UDP $startedPort")
        } else {
            prefs.setLanDnsEnabled(false)
            diagnosticEvents.recordBlocking(
                DiagnosticEventType.FOREGROUND_SERVICE_START_FAILED,
                "LAN DNS server failed to start",
                mapOf("port" to requestedPort)
            )
            Log.w(TAG, localDnsServer.status().message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopLanDns(persistDisabled: Boolean) {
        if (persistDisabled) {
            serviceScope.launch { prefs.setLanDnsEnabled(false) }
        }
        localDnsServer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleForegroundServiceTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout received (startId=$startId, type=$fgsType)")
        diagnosticEvents.recordBlocking(
            DiagnosticEventType.FOREGROUND_SERVICE_TIMEOUT,
            "LAN DNS foreground service timeout",
            mapOf(
                "service" to "LocalDnsServerService",
                "start_id" to startId,
                "fgs_type" to fgsType
            )
        )
        localDnsServer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocalDnsServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("HostShield LAN DNS")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_shield, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationChannel(
            CHANNEL_ID,
            "LAN DNS Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while LAN DNS serving is active"
        }.let {
            getSystemService(NotificationManager::class.java).createNotificationChannel(it)
        }
    }

    private fun runtimeForegroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
}
