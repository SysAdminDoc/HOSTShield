package com.hostshield.ui.screens.apps

import app.cash.turbine.test
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.preferences.UiPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.util.RootUtil
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dnsLogDao: DnsLogDao
    private lateinit var repository: HostShieldRepository
    private lateinit var blocklist: BlocklistHolder
    private lateinit var prefs: AppPreferences
    private lateinit var uiPreferences: UiPreferences
    private lateinit var rootUtil: RootUtil
    private val appsFlow = MutableStateFlow<List<AppQueryStat>>(emptyList())
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dnsLogDao = mockk(relaxed = true)
        repository = mockk(relaxed = true, relaxUnitFun = true)
        blocklist = mockk(relaxed = true, relaxUnitFun = true)
        prefs = mockk(relaxed = true)
        uiPreferences = mockk(relaxed = true, relaxUnitFun = true)
        rootUtil = mockk(relaxed = true, relaxUnitFun = true)

        every { dnsLogDao.getAllAppsWithCounts() } returns appsFlow
        every { dnsLogDao.getDomainsForApp(any(), any()) } returns flowOf(emptyList())
        every { prefs.ui } returns uiPreferences
        every { uiPreferences.savedDenseListFilters(any()) } returns flowOf(emptyList())
    }

    @After
    fun teardown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AppsViewModel(dnsLogDao, repository, blocklist, prefs, rootUtil)
        .also { createdViewModels += it }

    @Test
    fun `apps flow emits from dao`() = runTest {
        val vm = createViewModel()
        val app = AppQueryStat(
            appPackage = "com.example.browser",
            appLabel = "Browser",
            totalQueries = 12,
            blockedQueries = 4
        )

        vm.apps.test {
            assertEquals(emptyList<AppQueryStat>(), awaitItem())
            appsFlow.value = listOf(app)
            assertEquals(listOf(app), awaitItem())
        }
    }

    @Test
    fun `saved app filters persist apply and clear`() = runTest {
        val vm = createViewModel()

        vm.setSearch("browser")
        vm.setFilter(AppsActivityFilter.BLOCKED)
        vm.saveCurrentFilter()
        advanceUntilIdle()

        coVerify {
            uiPreferences.saveDenseListFilter(
                "apps",
                match { it.contains("browser") && it.contains("Blocked") },
                match { it.contains("\"query\":\"browser\"") && it.contains("\"filter\":\"BLOCKED\"") }
            )
        }

        vm.clearFilters()
        assertEquals("", vm.searchQuery.value)
        assertEquals(AppsActivityFilter.ALL, vm.filter.value)

        vm.applySavedFilter(
            SavedDenseListFilter(
                screen = "apps",
                label = "No blocks",
                payload = """{"query":"maps","filter":"UNBLOCKED"}""",
                updatedAt = 1L
            )
        )

        assertEquals("maps", vm.searchQuery.value)
        assertEquals(AppsActivityFilter.UNBLOCKED, vm.filter.value)

        vm.clearSavedFilters()
        advanceUntilIdle()

        coVerify { uiPreferences.clearDenseListFilters("apps") }
    }
}
