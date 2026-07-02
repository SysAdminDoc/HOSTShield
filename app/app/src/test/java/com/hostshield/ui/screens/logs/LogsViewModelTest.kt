package com.hostshield.ui.screens.logs

import android.content.Context
import app.cash.turbine.test
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.service.AppDnsRuleEngine
import com.hostshield.util.GeoIpLookup
import com.hostshield.util.RootUtil
import io.mockk.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: HostShieldRepository
    private lateinit var blocklist: BlocklistHolder
    private lateinit var rootUtil: RootUtil
    private lateinit var prefs: AppPreferences
    private lateinit var geoIpLookup: GeoIpLookup
    private lateinit var appDnsRuleDao: AppDnsRuleDao
    private lateinit var appDnsRuleEngine: AppDnsRuleEngine
    private val logsFlow = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true, relaxUnitFun = true)
        blocklist = mockk(relaxed = true, relaxUnitFun = true)
        rootUtil = mockk(relaxed = true, relaxUnitFun = true)
        prefs = mockk(relaxed = true)
        geoIpLookup = mockk(relaxed = true)
        appDnsRuleDao = mockk(relaxed = true, relaxUnitFun = true)
        appDnsRuleEngine = mockk(relaxed = true, relaxUnitFun = true)

        every { repository.getRecentLogs(any()) } returns logsFlow
        coEvery { repository.getEnabledRulesByType(RuleType.BLOCK) } returns emptyList()
        coEvery { repository.getEnabledRulesByType(RuleType.ALLOW) } returns emptyList()
        every { prefs.pinnedDomains } returns flowOf(emptySet())
    }

    @After
    fun teardown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = LogsViewModel(
        appContext = context,
        repository = repository,
        blocklist = blocklist,
        rootUtil = rootUtil,
        prefs = prefs,
        geoIpLookup = geoIpLookup,
        appDnsRuleDao = appDnsRuleDao,
        appDnsRuleEngine = appDnsRuleEngine
    ).also { createdViewModels += it }

    @Test
    fun `initial state has empty search query`() = runTest {
        val vm = createViewModel()
        vm.searchQuery.test {
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `initial state has null blocked filter`() = runTest {
        val vm = createViewModel()
        vm.showBlocked.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `setSearch updates searchQuery flow`() = runTest {
        val vm = createViewModel()
        vm.searchQuery.test {
            assertEquals("", awaitItem())
            vm.setSearch("google")
            assertEquals("google", awaitItem())
        }
    }

    @Test
    fun `setFilter updates showBlocked flow`() = runTest {
        val vm = createViewModel()
        vm.showBlocked.test {
            assertNull(awaitItem())
            vm.setFilter(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `setQueryTypeFilter updates queryTypeFilter flow`() = runTest {
        val vm = createViewModel()
        vm.queryTypeFilter.test {
            assertNull(awaitItem())
            vm.setQueryTypeFilter("AAAA")
            assertEquals("AAAA", awaitItem())
        }
    }

    @Test
    fun `clearLogs invokes repository clearAllLogs`() = runTest {
        val vm = createViewModel()
        vm.clearLogs()
        advanceUntilIdle()

        coVerify { repository.clearAllLogs() }
    }

    @Test
    fun `threat review count tracks distinct threat intel blocked domains`() = runTest {
        val vm = createViewModel()

        vm.threatReviewCount.test {
            assertEquals(0, awaitItem())
            logsFlow.value = listOf(
                DnsLogEntry(
                    hostname = "bad.example",
                    blocked = true,
                    decisionReason = "threat_intel_domain",
                    decisionSource = "URLhaus",
                    matchedValue = "bad.example"
                ),
                DnsLogEntry(
                    hostname = "bad.example",
                    blocked = true,
                    decisionReason = "threat_intel_domain",
                    decisionSource = "URLhaus",
                    matchedValue = "bad.example"
                ),
                DnsLogEntry(
                    hostname = "ip-hit.example",
                    blocked = true,
                    decisionReason = "threat_intel_ip",
                    decisionSource = "Spamhaus DROP",
                    matchedValue = "203.0.113.44"
                ),
                DnsLogEntry(
                    hostname = "ordinary-block.example",
                    blocked = true,
                    decisionReason = "source_list"
                ),
                DnsLogEntry(
                    hostname = "allowed-threat-context.example",
                    blocked = false,
                    decisionReason = "threat_intel_domain"
                )
            )

            assertEquals(2, awaitItem())
        }
    }
}
