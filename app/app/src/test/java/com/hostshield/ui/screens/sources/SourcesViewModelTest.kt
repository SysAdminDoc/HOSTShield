package com.hostshield.ui.screens.sources

import app.cash.turbine.test
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.preferences.UiPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import io.mockk.*
import kotlinx.coroutines.cancel
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
    private lateinit var uiPreferences: UiPreferences
    private val sourcesFlow = MutableStateFlow<List<HostSource>>(emptyList())
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true, relaxUnitFun = true)
        downloader = mockk(relaxed = true)
        blocklistHolder = mockk(relaxed = true)
        uiPreferences = mockk(relaxed = true, relaxUnitFun = true)
        every { repository.getAllSources() } returns sourcesFlow
        every { uiPreferences.savedDenseListFilters(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
    }

    @After
    fun teardown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SourcesViewModel(repository, downloader, blocklistHolder, uiPreferences)
        .also { createdViewModels += it }

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
    fun `saved source filters persist apply and clear`() = runTest {
        val vm = createViewModel()

        vm.setSearchQuery("hagezi")
        vm.setFilter(SourceListFilter.UNHEALTHY)
        vm.saveCurrentFilter()
        advanceUntilIdle()

        coVerify {
            uiPreferences.saveDenseListFilter(
                "sources",
                match { it.contains("hagezi") && it.contains("Needs review") },
                match { it.contains("\"query\":\"hagezi\"") && it.contains("\"filter\":\"UNHEALTHY\"") }
            )
        }

        vm.clearFilters()
        assertEquals("", vm.searchQuery.value)
        assertEquals(SourceListFilter.ALL, vm.filter.value)

        vm.applySavedFilter(
            SavedDenseListFilter(
                screen = "sources",
                label = "Allowlists",
                payload = """{"query":"allow","filter":"ALLOWLIST"}""",
                updatedAt = 1L
            )
        )

        assertEquals("allow", vm.searchQuery.value)
        assertEquals(SourceListFilter.ALLOWLIST, vm.filter.value)

        vm.clearSavedFilters()
        advanceUntilIdle()

        coVerify { uiPreferences.clearDenseListFilters("sources") }
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
            // The init block sets isLoading=false after the upstream flow's
            // first emission. With UnconfinedTestDispatcher the repository
            // flow already emitted, so it should be false.
            assertFalse(items.last())
        }
    }

    @Test
    fun `isLoading stays true until the repository flow actually emits`() = runTest {
        // Cold repository flow that has NOT emitted yet — the stateIn'd
        // `sources` initial emptyList must not flip isLoading to false.
        val upstream = kotlinx.coroutines.flow.MutableSharedFlow<List<HostSource>>()
        every { repository.getAllSources() } returns upstream

        val vm = createViewModel()
        assertTrue(vm.isLoading.value)

        upstream.emit(listOf(HostSource(id = 1, url = "https://example.com/hosts", label = "Real", category = SourceCategory.ADS)))
        advanceUntilIdle()

        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `error flow starts null`() = runTest {
        val vm = createViewModel()
        vm.error.test {
            assertNull(awaitItem())
        }
    }
}
