package com.hostshield.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

internal object ProtectionServiceStarter {
    private const val TAG = "ProtectionSvcStarter"

    fun startForegroundService(context: Context, intent: Intent, caller: String): Boolean {
        return try {
            context.startForegroundService(intent)
            true
        } catch (e: RuntimeException) {
            val service = serviceLabel(intent)
            Log.e(TAG, "Foreground service start failed (caller=$caller, service=$service)", e)
            recordStartFailure(context, caller, service, intent.action, e)
            false
        }
    }

    private fun recordStartFailure(
        context: Context,
        caller: String,
        service: String,
        action: String?,
        error: RuntimeException
    ) {
        diagnosticStore(context)?.recordBlocking(
            DiagnosticEventType.FOREGROUND_SERVICE_START_FAILED,
            "Foreground service start failed",
            mapOf(
                "caller" to caller,
                "service" to service,
                "action" to (action ?: ""),
                "exception" to error.javaClass.name,
                "message" to (error.message ?: "")
            )
        )
    }

    private fun diagnosticStore(context: Context): DiagnosticEventStore? {
        return runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DiagnosticsEntryPoint::class.java
            ).diagnosticEventStore()
        }.getOrNull()
    }

    private fun serviceLabel(intent: Intent): String {
        return intent.component?.className?.substringAfterLast('.')
            ?: intent.`package`
            ?: intent.action
            ?: "unknown"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DiagnosticsEntryPoint {
        fun diagnosticEventStore(): DiagnosticEventStore
    }
}
