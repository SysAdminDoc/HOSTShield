package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleTestResult(
    val domain: String,
    val isBlocked: Boolean,
    val matchedBy: String // "exact", "wildcard: *.foo.com", "regex: .*ad.*", "source list", "not matched"
)

data class RuleTestState(
    val testDomain: String = "",
    val results: List<RuleTestResult> = emptyList(),
    val isTesting: Boolean = false,
    val batchInput: String = "",
    val batchResults: List<RuleTestResult> = emptyList()
)

@HiltViewModel
class RuleTestViewModel @Inject constructor(
    private val blocklist: BlocklistHolder,
    private val repository: HostShieldRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RuleTestState())
    val state = _state.asStateFlow()

    fun setTestDomain(d: String) { _state.update { it.copy(testDomain = d) } }
    fun setBatchInput(s: String) { _state.update { it.copy(batchInput = s) } }

    fun testDomain() {
        val domain = _state.value.testDomain.trim().lowercase()
        if (domain.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true) }
            val result = testSingleDomain(domain)
            _state.update {
                it.copy(
                    isTesting = false,
                    results = listOf(result) + it.results.take(29)
                )
            }
        }
    }

    fun testBatch() {
        val domains = _state.value.batchInput.lines()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it.contains('.') }
            .distinct().take(100)
        if (domains.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true) }
            val results = domains.map { testSingleDomain(it) }
            _state.update { it.copy(isTesting = false, batchResults = results) }
        }
    }

    private suspend fun testSingleDomain(domain: String): RuleTestResult {
        val isBlocked = blocklist.isBlocked(domain)

        // Determine what matched
        val matchedBy = if (isBlocked) {
            // Check user rules first
            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            val exactMatch = blockRules.find { !it.isWildcard && !it.isRegex && it.hostname.lowercase() == domain }
            if (exactMatch != null) return RuleTestResult(domain, true, "exact rule: ${exactMatch.hostname}")

            val wildcardMatch = blockRules.filter { it.isWildcard }.find {
                HostsParser.matchesWildcard(domain, it.hostname)
            }
            if (wildcardMatch != null) return RuleTestResult(domain, true, "wildcard: ${wildcardMatch.hostname}")

            val regexMatch = blockRules.filter { it.isRegex }.find {
                try { Regex(it.hostname, RegexOption.IGNORE_CASE).containsMatchIn(domain) }
                catch (_: Exception) { false }
            }
            if (regexMatch != null) return RuleTestResult(domain, true, "regex: ${regexMatch.hostname}")

            "source blocklist"
        } else {
            // Check if allow rule matches
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            val allowMatch = allowRules.find { it.hostname.lowercase() == domain }
            if (allowMatch != null) "allowed by rule: ${allowMatch.hostname}"
            else "not blocked"
        }

        return RuleTestResult(domain, isBlocked, matchedBy)
    }
}

@Composable
fun RuleTestScreen(
    viewModel: RuleTestViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Rule Tester", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("Test if domains match your rules", color = TextDim, fontSize = 11.sp)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Single domain test
            item {
                OutlinedTextField(
                    value = state.testDomain,
                    onValueChange = { viewModel.setTestDomain(it) },
                    placeholder = { Text("ads.example.com", color = TextDim) },
                    leadingIcon = { Icon(Icons.Filled.Dns, null, tint = TextDim) },
                    trailingIcon = {
                        if (state.isTesting) CircularProgressIndicator(Modifier.size(20.dp), color = Teal, strokeWidth = 2.dp)
                        else IconButton(onClick = { viewModel.testDomain() }) {
                            Icon(Icons.Filled.PlayArrow, "Test", tint = Teal)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
            }

            // Single results
            items(state.results) { result ->
                ResultRow(result)
            }

            // Batch test
            item {
                Spacer(Modifier.height(8.dp))
                Text("BATCH TEST", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.batchInput,
                    onValueChange = { viewModel.setBatchInput(it) },
                    placeholder = { Text("One domain per line...", color = TextDim) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                    maxLines = 10, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.testBatch() },
                    enabled = !state.isTesting && state.batchInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Test All", fontSize = 12.sp) }
            }

            // Batch results
            if (state.batchResults.isNotEmpty()) {
                item {
                    val blocked = state.batchResults.count { it.isBlocked }
                    Text("$blocked/${state.batchResults.size} blocked", color = TextDim, fontSize = 11.sp)
                }
                items(state.batchResults) { result -> ResultRow(result) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ResultRow(result: RuleTestResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (result.isBlocked) Red.copy(alpha = 0.06f) else Surface2
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                .background(if (result.isBlocked) Red else Green))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.domain, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(result.matchedBy, color = TextDim, fontSize = 10.sp)
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = (if (result.isBlocked) Red else Green).copy(alpha = 0.12f)
            ) {
                Text(
                    if (result.isBlocked) "BLOCKED" else "ALLOWED",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (result.isBlocked) Red else Green,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
