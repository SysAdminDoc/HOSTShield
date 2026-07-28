package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import com.hostshield.util.RootUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Re-blocks a domain after the DNS-log temporary allow window expires.
 * WorkManager owns the timer so process death, activity recreation, and Doze do
 * not turn a temporary exception into a permanent one.
 */
@HiltWorker
class TemporaryAllowWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPreferences,
    private val blocklist: BlocklistHolder,
    private val rootUtil: RootUtil,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME_PREFIX = "hostshield_temporary_allow"
        private const val KEY_HOSTNAME = "hostname"

        fun schedule(context: Context, hostname: String, delayMinutes: Int) {
            val host = hostname.trim().lowercase()
            if (host.isBlank()) return

            val request = OneTimeWorkRequestBuilder<TemporaryAllowWorker>()
                .setInputData(workDataOf(KEY_HOSTNAME to host))
                .setInitialDelay(delayMinutes.coerceAtLeast(1).toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(host),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun workName(hostname: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(hostname.toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { "%02x".format(it) }
            return "$WORK_NAME_PREFIX:$digest"
        }
    }

    override suspend fun doWork(): Result {
        val host = inputData.getString(KEY_HOSTNAME)?.trim()?.lowercase().orEmpty()
        if (host.isBlank()) return Result.failure()

        return try {
            // End the temporary allow: drop it from the user-allow set so the
            // domain reverts to whatever the sources/rules dictate, rather than
            // stamping a permanent "User block rule" via addDomain.
            blocklist.clearTemporaryAllow(host)
            if (prefs.blockMethod.first() == BlockMethod.ROOT_HOSTS) {
                // Root mode blocks via the hosts file; re-add the sinkhole entry
                // removed when the allow started. A later full rebuild reconciles
                // it back to source-managed state.
                rootUtil.appendHostEntry(host)
            }
            Log.i("TemporaryAllowWorker", "Temporary allow expired for $host")
            Result.success()
        } catch (e: Exception) {
            Log.e("TemporaryAllowWorker", "Failed to restore temporary allow for $host", e)
            Result.retry()
        }
    }
}
