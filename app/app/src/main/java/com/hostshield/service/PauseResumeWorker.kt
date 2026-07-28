package com.hostshield.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Deferred VPN-resume worker. Replaces the in-receiver `delay(...)` previously
 * used by `AutomationReceiver.ACTION_PAUSE`, which Android's broadcast lifetime
 * limit (~10 s for `goAsync()`) killed before pauses longer than that could
 * resume. WorkManager survives process death and Doze, so pauses up to 24 h
 * resume reliably.
 *
 * The worker only re-enables protection. The receiver still performs the
 * synchronous disable so the user sees an immediate effect.
 */
@HiltWorker
class PauseResumeWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPreferences,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "hostshield_pause_resume"

        /** Schedule a one-shot resume after [delayMinutes] minutes. */
        fun schedule(context: Context, delayMinutes: Int) {
            val req = OneTimeWorkRequestBuilder<PauseResumeWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        /** Cancel any pending resume — e.g., if user manually re-enables earlier. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val method = prefs.blockMethod.first()
            val started = when (method) {
                BlockMethod.VPN -> {
                    ProtectionServiceStarter.startForegroundService(
                        appContext,
                        Intent(appContext, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_START
                        },
                        "PauseResumeWorker"
                    )
                }
                BlockMethod.ROOT_HOSTS -> RootDnsService.start(appContext, "PauseResumeWorker")
                BlockMethod.DNS_PROXY -> {
                    DnsProxyService.start(appContext, "PauseResumeWorker")
                }
                BlockMethod.DISABLED -> {
                    // User disabled while paused — respect that. Do NOT fall through
                    // to setEnabled(true) below, which would silently re-enable the pref.
                    prefs.setPauseEndTime(0L)
                    Log.i("PauseResume", "Pause expired but method is DISABLED — leaving protection off")
                    return Result.success()
                }
            }
            if (!started) {
                // Foreground-service start denied (e.g., Android 12+ background
                // restriction without a battery exemption). Do NOT mark protection
                // enabled — the UI would claim "Protected" with nothing running.
                // Retry with backoff; the start succeeds once the app is foregrounded.
                Log.w("PauseResume", "Resume start denied for $method — retrying")
                return Result.retry()
            }
            prefs.setEnabled(true)
            prefs.setPauseEndTime(0L)
            Log.i("PauseResume", "VPN auto-resumed after pause (method=$method)")
            Result.success()
        } catch (e: Exception) {
            Log.e("PauseResume", "Resume failed: ${e.message}", e)
            Result.retry()
        }
    }
}
