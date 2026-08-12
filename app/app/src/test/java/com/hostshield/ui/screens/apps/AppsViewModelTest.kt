package com.hostshield.ui.screens.apps

import app.cash.turbine.test
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.AppDnsRule
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.preferences.UiPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.util.RootUtil
import com.hostshield.service.AppDnsRuleEngine
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
    private lateinit var appDnsRuleDao: AppDnsRuleDao
    private lateinit var appDnsRuleEngine: AppDnsRuleEngine
    private lateinit var repository: HostShieldRepository
    private lateinit var blocklist: BlocklistHolder
    private lateinit var prefs: AppPreferences
    private lateinit var uiPreferences: UiPreferences
    private lateinit var rootUtil: RootUtil
    private val appsFlow = MutableStateFlow<List<AppQueryStat>>(emptyList())
    private val appLogsFlow = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    private val appRulesFlow = MutableStateFlow<List<AppDnsRule>>(emptyList())
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dnsLogDao = mockk(relaxed = true)
        appDnsRuleDao = mockk(relaxed = true, relaxUnitFun = true)
        appDnsRuleEngine = mockk(relaxed = true, relaxUnitFun = true)
        repository = mockk(relaxed = true, relaxUnitFun = true)
        blocklist = mockk(relaxed = true, relaxUnitFun = true)
        prefs = mockk(relaxed = true)
        uiPreferences = mockk(relaxed = true, relaxUnitFun = true)
        rootUtil = mockk(relaxed = true, relaxUnitFun = true)

        every { dnsLogDao.getAllAppsWithCounts() } returns appsFlow
        every { dnsLogDao.getDomainsForApp(any(), any()) } returns flowOf(emptyList())
        every { dnsLogDao.getLogsForApp(any(), any()) } returns appLogsFlow
        every { appDnsRuleDao.getRulesForApp(any()) } returns appRulesFlow
        every { prefs.ui } returns uiPreferences
        every { uiPreferences.savedDenseListFilters(any()) } returns flowOf(emptyList())
    }

    @After
    fun teardown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        savedStateHandle: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()
    ) = AppsViewModel(
        dnsLogDao,
        appDnsRuleDao,
        appDnsRuleEngine,
        repository,
        blocklist,
        prefs,
        rootUtil,
        savedStateHandle,
    )
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
    fun `search query seeds from nav arg in saved state handle`() = runTest {
        val vm = createViewModel(
            androidx.lifecycle.SavedStateHandle(mapOf("query" to "tracker"))
        )
        assertEquals("tracker", vm.searchQuery.value)
    }

    @Test
    fun `search query defaults to empty without nav arg`() = runTest {
        val vm = createViewModel()
        assertEquals("", vm.searchQuery.value)
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

    @Test
    fun `breakage projection groups blocked domains and preserves source attribution`() {
        val domains = buildAppBreakageDomains(
            listOf(
                DnsLogEntry(
                    hostname = "ads.example.com",
                    blocked = true,
                    decisionSource = "HaGeZi",
                    matchedValue = "example.com",
                    decisionReason = "source_list",
                ),
                DnsLogEntry(
                    hostname = "ads.example.com",
                    blocked = true,
                    decisionSource = "AdGuard",
                    decisionReason = "source_list",
                ),
                DnsLogEntry(hostname = "ok.example.com", blocked = false),
            )
        )

        assertEquals(1, domains.size)
        assertEquals("ads.example.com", domains.single().hostname)
        assertEquals(2, domains.single().hitCount)
        assertEquals(listOf("AdGuard", "HaGeZi"), domains.single().sources)
        assertEquals(listOf("example.com"), domains.single().matchedValues)
    }

    @Test
    fun `app diagnosis allow creates revocable app-scoped rule`() = runTest {
        val vm = createViewModel()
        vm.selectApp("com.example.app")
        vm.allowDomainForApp("Ads.Example.COM")
        advanceUntilIdle()

        coVerify {
            appDnsRuleDao.insert(
                match {
                    it.packageName == "com.example.app" &&
                        it.domain == "ads.example.com" &&
                        it.action == "allow"
                }
            )
        }
        coVerify { appDnsRuleEngine.reloadForApp("com.example.app") }
    }
}
