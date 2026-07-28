package com.hostshield.ui.screens.settings

import com.hostshield.util.RootUtil
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostsEditorViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var rootUtil: RootUtil

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        rootUtil = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `read failure surfaces error state instead of an empty editor`() = runTest {
        coEvery { rootUtil.readHostsFile() } returns Result.failure(Exception("root denied"))

        val vm = HostsEditorViewModel(rootUtil)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("read failure must mark loadFailed", state.loadFailed)
        assertTrue("read failure message is an error", state.messageIsError)
        assertEquals("", state.content)
    }

    @Test
    fun `save reports failure and keeps isEdited when the write fails`() = runTest {
        coEvery { rootUtil.readHostsFile() } returns Result.success("0.0.0.0 ads.example.com")
        coEvery { rootUtil.writeHostsFile(any()) } returns Result.failure(Exception("remount failed"))

        val vm = HostsEditorViewModel(rootUtil)
        advanceUntilIdle()
        vm.setContent("0.0.0.0 ads.example.com\n0.0.0.0 new.example.com")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("failed save must surface an error", state.messageIsError)
        assertTrue("failed save keeps unsaved edits", state.isEdited)
    }

    @Test
    fun `save reports success when the write succeeds`() = runTest {
        coEvery { rootUtil.readHostsFile() } returns Result.success("0.0.0.0 ads.example.com")
        coEvery { rootUtil.writeHostsFile(any()) } returns Result.success(Unit)

        val vm = HostsEditorViewModel(rootUtil)
        advanceUntilIdle()
        vm.setContent("0.0.0.0 ads.example.com\n0.0.0.0 new.example.com")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse("successful save is not an error", state.messageIsError)
        assertFalse("successful save clears the edited flag", state.isEdited)
        assertEquals("Hosts file saved", state.message)
    }

    @Test
    fun `save is refused after a read failure`() = runTest {
        coEvery { rootUtil.readHostsFile() } returns Result.failure(Exception("root denied"))

        val vm = HostsEditorViewModel(rootUtil)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.messageIsError)
        io.mockk.coVerify(exactly = 0) { rootUtil.writeHostsFile(any()) }
    }
}
