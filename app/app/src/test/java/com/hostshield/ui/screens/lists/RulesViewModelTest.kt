package com.hostshield.ui.screens.lists

import app.cash.turbine.test
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.repository.HostShieldRepository
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
class RulesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HostShieldRepository
    private val rulesFlow = MutableStateFlow<List<UserRule>>(emptyList())
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true, relaxUnitFun = true)
        every { repository.getAllRules() } returns rulesFlow
    }

    @After
    fun teardown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RulesViewModel(repository)
        .also { createdViewModels += it }

    @Test
    fun `initial state emits empty list`() = runTest {
        val vm = createViewModel()
        vm.rules.test {
            assertEquals(emptyList<UserRule>(), awaitItem())
        }
    }

    @Test
    fun `rules flow reflects repository emissions`() = runTest {
        val vm = createViewModel()
        val rule = UserRule(id = 1, hostname = "ads.example.com", type = RuleType.BLOCK)
        vm.rules.test {
            assertEquals(emptyList<UserRule>(), awaitItem())
            rulesFlow.value = listOf(rule)
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("ads.example.com", updated[0].hostname)
        }
    }

    @Test
    fun `addRule invokes repository with correct UserRule`() = runTest {
        val vm = createViewModel()
        vm.addRule("Ads.Example.COM", RuleType.BLOCK, comment = "test rule")
        advanceUntilIdle()

        coVerify {
            repository.addRule(match {
                it.hostname == "ads.example.com" &&
                    it.type == RuleType.BLOCK &&
                    it.comment == "test rule" &&
                    !it.isWildcard &&
                    !it.isRegex
            })
        }
    }

    @Test
    fun `addRule with wildcard hostname sets isWildcard true`() = runTest {
        val vm = createViewModel()
        vm.addRule("*.doubleclick.net", RuleType.BLOCK)
        advanceUntilIdle()

        coVerify {
            repository.addRule(match {
                it.hostname == "*.doubleclick.net" &&
                    it.isWildcard &&
                    !it.isRegex
            })
        }
    }

    @Test
    fun `addRule classifies a wildcard even with surrounding whitespace`() = runTest {
        // Regression: the wildcard check ran on the UNtrimmed input, so a
        // pasted " *.doubleclick.net" was stored trimmed but as an exact rule
        // for the literal "*." string — silently matching nothing.
        val vm = createViewModel()
        vm.addRule(" *.doubleclick.net ", RuleType.BLOCK)
        advanceUntilIdle()

        coVerify {
            repository.addRule(match {
                it.hostname == "*.doubleclick.net" &&
                    it.isWildcard &&
                    !it.isRegex
            })
        }
    }

    @Test
    fun `deleteRule invokes repository delete`() = runTest {
        val rule = UserRule(id = 5, hostname = "tracker.evil.com", type = RuleType.BLOCK)
        val vm = createViewModel()
        vm.deleteRule(rule)
        advanceUntilIdle()

        coVerify { repository.deleteRule(rule) }
    }

    @Test
    fun `toggleRule invokes repository toggle`() = runTest {
        val vm = createViewModel()
        vm.toggleRule(42L, false)
        advanceUntilIdle()

        coVerify { repository.toggleRule(42L, false) }
    }
}
