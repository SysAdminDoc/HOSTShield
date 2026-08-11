package com.hostshield.service

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Protection-state truth for the pause auto-resume path (v6.9.65).
 *
 * The rule is that the enabled pref flips only after a foreground-service start
 * actually succeeds — otherwise the UI, tile, and widget all claim "Protected"
 * with nothing running, and the schedule worker then skips the whole window.
 * That fix had no coverage.
 */
class PauseResumeWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        mockkObject(ProtectionServiceStarter)
    }

    @After
    fun tearDown() {
        unmockkObject(ProtectionServiceStarter)
    }

    private fun workerWith(prefs: AppPreferences) = PauseResumeWorker(
        appContext = context,
        params = mockk<WorkerParameters>(relaxed = true),
        prefs = prefs,
    )

    private fun prefsFor(method: BlockMethod): AppPreferences =
        mockk<AppPreferences>(relaxed = true).also {
            every { it.blockMethod } returns flowOf(method)
        }

    @Test
    fun `a denied foreground-service start retries and never claims protection`() = runTest {
        every {
            ProtectionServiceStarter.startForegroundService(any(), any(), any())
        } returns false
        val prefs = prefsFor(BlockMethod.VPN)

        val result = workerWith(prefs).doWork()

        assertTrue("denied start must retry", result is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { prefs.setEnabled(true) }
        coVerify(exactly = 0) { prefs.setPauseEndTime(0L) }
    }

    @Test
    fun `a successful start marks protection enabled and clears the pause`() = runTest {
        every {
            ProtectionServiceStarter.startForegroundService(any(), any(), any())
        } returns true
        val prefs = prefsFor(BlockMethod.VPN)

        val result = workerWith(prefs).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { prefs.setEnabled(true) }
        coVerify(exactly = 1) { prefs.setPauseEndTime(0L) }
    }

    @Test
    fun `a user disable during the pause is respected`() = runTest {
        val prefs = prefsFor(BlockMethod.DISABLED)

        val result = workerWith(prefs).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // The pause is cleared, but protection must not be re-enabled behind the user.
        coVerify(exactly = 1) { prefs.setPauseEndTime(0L) }
        coVerify(exactly = 0) { prefs.setEnabled(true) }
    }
}
