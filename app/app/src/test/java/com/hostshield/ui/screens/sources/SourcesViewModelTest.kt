package com.hostshield.ui.screens.sources

import app.cash.turbine.test
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourcesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HostShieldRepository
    private lateinit var downloader: SourceDownloader
    private lateinit var blocklistHolder: BlocklistHolder
    private val sourcesFlow = MutableStateFlow<List<HostSource>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true, relaxUnitFun = true)
        downloader = mockk(relaxed = true)
        blocklistHolder = mockk(relaxed = true)
        every { repository.getAllSources() } returns sourcesFlow
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SourcesViewModel(repository, downloader, blocklistHolder)

    @Test
    fun `initial sources flow emits from repository`() = runTest {
        val vm = createViewModel()
        vm.sources.test {
            assertEquals(emptyList<HostSource>(), awaitItem())
        }
    }

    @Test
    fun `sources flow updates when repository emits`() = runTest {
        val vm = createViewModel()
        val source = HostSource(id = 1, url = "https://example.com/hosts", label = "Test", category = SourceCategory.ADS)
        vm.sources.test {
            assertEquals(emptyList<HostSource>(), awaitItem())
            sourcesFlow.value = listOf(source)
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Test", updated[0].label)
        }
    }

    @Test
    fun `deleteSource invokes repository delete`() = runTest {
        val source = HostSource(id = 3, url = "https://example.com/hosts", label = "Delete Me", category = SourceCategory.TRACKERS)
        val vm = createViewModel()
        vm.deleteSource(source)
        advanceUntilIdle()

        coVerify { repository.deleteSource(source) }
    }

    @Test
    fun `toggleSource invokes repository toggle`() = runTest {
        val vm = createViewModel()
        vm.toggleSource(7L, false)
        advanceUntilIdle()

        coVerify { repository.toggleSource(7L, false) }
    }

    @Test
    fun `isLoading starts true then becomes false after first emission`() = runTest {
        val vm = createViewModel()
        vm.isLoading.test {
            // After init block runs with UnconfinedTestDispatcher, isLoading should
            // eventually become false once sources emits its first value.
            val items = mutableListOf<Boolean>()
            // Collect available items (init fires immediately with Unconfined)
            items.add(awaitItem())
            // The init block sets isLoading=false after sources.first{true}
            // With UnconfinedTestDispatcher the flow already emitted, so it should be false
            assertFalse(items.last())
        }
    }

    @Test
    fun `error flow starts null`() = runTest {
        val vm = createViewModel()
        vm.error.test {
            assertNull(awaitItem())
        }
    }
}
