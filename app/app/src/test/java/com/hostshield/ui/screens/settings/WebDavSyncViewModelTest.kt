package com.hostshield.ui.screens.settings

import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.BackupRestoreUtil
import com.hostshield.util.WebDavSync
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebDavSyncViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: AppPreferences
    private lateinit var webDavSync: WebDavSync
    private lateinit var backupRestoreUtil: BackupRestoreUtil

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true)
        webDavSync = mockk(relaxed = true)
        backupRestoreUtil = mockk(relaxed = true)
        every { prefs.webdavUrl } returns flowOf("")
        every { prefs.webdavUsername } returns flowOf("")
        every { prefs.webdavPassword } returns flowOf("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid url validation is flagged as an error not a success banner`() = runTest {
        val vm = WebDavSyncViewModel(prefs, webDavSync, backupRestoreUtil)
        vm.saveCredentials("not-a-url", "user", "pass")
        advanceUntilIdle()

        assertTrue("validation message must be an error", vm.messageIsError)
    }

    @Test
    fun `saving valid credentials is not an error`() = runTest {
        val vm = WebDavSyncViewModel(prefs, webDavSync, backupRestoreUtil)
        vm.saveCredentials("https://cloud.example.com/remote.php/dav/files/user", "user", "pass")
        advanceUntilIdle()

        assertFalse("a successful save must not be flagged as error", vm.messageIsError)
    }
}
