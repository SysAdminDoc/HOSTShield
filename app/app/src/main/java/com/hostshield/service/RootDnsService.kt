package com.hostshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hostshield.MainActivity
import com.hostshield.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service wrapper for RootDnsLogger.
 *
 * Android 14+ aggressively kills background coroutines. The RootDnsLogger
 * needs to run continuously (DNS proxy + logcat reader + dumpsys poller),
 * so it must live inside a foreground service with a persistent notification.
 *
 * This service delegates all DNS work to RootDnsLogger (Hilt singleton)
 * and just provides the foreground lifecycle.
 */
@AndroidEntryPoint
class RootDnsService : Service() {

    companion object {
        const val ACTION_START = "com.hostshield.ROOT_DNS_START"
        const val ACTION_STOP = "com.hostshield.ROOT_DNS_STOP"
        private const val CHANNEL_ID = "hostshield_root"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "RootDnsService"

        fun start(context: Context) {
            val intent = Intent(context, RootDnsService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RootDnsService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var rootDnsLogger: RootDnsLogger
    @Inject lateinit var blockNotificationService: BlockNotificationService

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting root DNS service")
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification("Initializing..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                rootDnsLogger.start()
                blockNotificationService.start()
                updateNotification("Root DNS proxy active")
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping root DNS service")
                rootDnsLogger.stop()
                blockNotificationService.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Null intent = system restarted us after process death (START_STICKY).
                // Re-promote to foreground and restart the DNS proxy.
                Log.i(TAG, "System restarted root DNS service -- resuming")
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification("Resuming..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                rootDnsLogger.start()
                blockNotificationService.start()
                updateNotification("Root DNS proxy active")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        rootDnsLogger.stop()
        blockNotificationService.stop()
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RootDnsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("HostShield Root Mode")
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
            CHANNEL_ID, "Root DNS Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while root DNS blocking is active"
        }.let {
            getSystemService(NotificationManager::class.java).createNotificationChannel(it)
        }
    }
}
