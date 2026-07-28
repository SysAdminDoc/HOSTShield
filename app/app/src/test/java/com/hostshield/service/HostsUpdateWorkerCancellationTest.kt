package com.hostshield.service

import android.content.Context
import androidx.work.WorkerParameters
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.DiagnosticEventStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class HostsUpdateWorkerCancellationTest {

    @Test
    fun `WorkManager cancellation is propagated without failure diagnostics`() = runTest {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        val cancellation = CancellationException("constraints changed")
        val dohBypassUpdater = mockk<DohBypassUpdater>()
        coEvery { dohBypassUpdater.fetchAndStore() } throws cancellation
        val diagnosticEvents = mockk<DiagnosticEventStore>(relaxed = true)
        val worker = HostsUpdateWorker(
            context = context,
            workerParams = mockk<WorkerParameters>(relaxed = true),
            prefs = mockk<AppPreferences>(relaxed = true),
            sourceCoordinator = mockk<BlocklistSourceCoordinator>(relaxed = true),
            dohBypassUpdater = dohBypassUpdater,
            cnameCloakUpdater = mockk<CnameCloakUpdater>(relaxed = true),
            httpClient = OkHttpClient(),
            diagnosticEvents = diagnosticEvents,
            sourceFailureNotifier = mockk(relaxed = true),
        )

        val thrown = try {
            worker.doWork()
            fail("Expected CancellationException")
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
        coVerify(exactly = 0) { diagnosticEvents.record(any(), any(), any()) }
    }
}
