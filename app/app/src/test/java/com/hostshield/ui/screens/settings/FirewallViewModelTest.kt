package com.hostshield.ui.screens.settings

import app.cash.turbine.test
import com.hostshield.data.database.FirewallRuleDao
import com.hostshield.data.model.FirewallRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.IptablesManager
import com.hostshield.service.NflogReader
import io.mockk.*
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
class FirewallViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: AppPreferences
    private lateinit var firewallRuleDao: FirewallRuleDao
    private lateinit var iptablesManager: IptablesManager
    private lateinit var nflogReader: NflogReader

    private val rulesFlow = MutableStateFlow<List<FirewallRule>>(emptyList())
    private val blockedCountFlow = MutableStateFlow(0)
    private val iptablesActiveFlow = MutableStateFlow(false)
    private val iptablesErrorFlow = MutableStateFlow("")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true)
        firewallRuleDao = mockk(relaxed = true, relaxUnitFun = true)
        iptablesManager = mockk(relaxed = true, relaxUnitFun = true)
        nflogReader = mockk(relaxed = true, relaxUnitFun = true)

        every { prefs.blockedApps } returns flowOf(emptySet())
        every { prefs.excludedApps } returns flowOf(emptySet())
        every { firewallRuleDao.getAllRules() } returns rulesFlow
        every { firewallRuleDao.getBlockedCount() } returns blockedCountFlow
        every { iptablesManager.isActive } returns iptablesActiveFlow
        every { iptablesManager.lastError } returns iptablesErrorFlow
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = FirewallViewModel(prefs, firewallRuleDao, iptablesManager, nflogReader)

    @Test
    fun `initial firewallRules flow starts empty`() = runTest {
        val vm = createViewModel()
        vm.firewallRules.test {
            assertEquals(emptyList<FirewallRule>(), awaitItem())
        }
    }

    @Test
    fun `firewallRules flow updates when dao emits`() = runTest {
        val vm = createViewModel()
        val rule = FirewallRule(id = 1, uid = 1000, packageName = "com.example.app", appLabel = "Example")
        vm.firewallRules.test {
            assertEquals(emptyList<FirewallRule>(), awaitItem())
            rulesFlow.value = listOf(rule)
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("com.example.app", updated[0].packageName)
        }
    }

    @Test
    fun `toggleWifi invokes dao setWifi`() = runTest {
        val vm = createViewModel()
        vm.toggleWifi(1000, false)
        advanceUntilIdle()

        coVerify { firewallRuleDao.setWifi(1000, false, any()) }
    }

    @Test
    fun `toggleMobile invokes dao setMobile`() = runTest {
        val vm = createViewModel()
        vm.toggleMobile(1000, true)
        advanceUntilIdle()

        coVerify { firewallRuleDao.setMobile(1000, true, any()) }
    }

    @Test
    fun `setSearchQuery updates searchQuery flow`() = runTest {
        val vm = createViewModel()
        vm.searchQuery.test {
            assertEquals("", awaitItem())
            vm.setSearchQuery("chrome")
            assertEquals("chrome", awaitItem())
        }
    }

    @Test
    fun `setFilter updates filter flow`() = runTest {
        val vm = createViewModel()
        vm.filter.test {
            assertEquals(FirewallFilter.ALL, awaitItem())
            vm.setFilter(FirewallFilter.BLOCKED)
            assertEquals(FirewallFilter.BLOCKED, awaitItem())
        }
    }
}
