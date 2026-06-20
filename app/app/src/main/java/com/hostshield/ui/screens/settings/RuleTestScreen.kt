package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldCompactState
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RULE_TEST_SCREEN_TAG = "RuleTestScreen"
private val RULE_TEST_DOMAIN_REGEX = Regex(
    """^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$"""
)

internal fun normalizeRuleTestDomain(rawDomain: String): String? {
    val domain = rawDomain.trim().trimEnd('.').lowercase()
    if (domain.isBlank() || domain.length > 253) return null
    if (domain.any { it.isWhitespace() }) return null
    if (domain.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '.' }) return null
    return if (RULE_TEST_DOMAIN_REGEX.matches(domain)) domain else null
}

internal fun normalizeRuleTestDomains(rawDomains: String, limit: Int = 100): List<String> =
    rawDomains.lineSequence()
        .mapNotNull { normalizeRuleTestDomain(it) }
        .distinct()
        .take(limit)
        .toList()

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
    val batchResults: List<RuleTestResult> = emptyList(),
    val message: String? = null,
    val messageIsError: Boolean = false
)

@HiltViewModel
class RuleTestViewModel @Inject constructor(
    private val blocklist: BlocklistHolder,
    private val repository: HostShieldRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RuleTestState())
    val state = _state.asStateFlow()

    fun setTestDomain(d: String) { _state.update { it.copy(testDomain = d, message = null) } }
    fun setBatchInput(s: String) { _state.update { it.copy(batchInput = s, message = null) } }
    fun clearMessage() { _state.update { it.copy(message = null, messageIsError = false) } }

    fun testDomain() {
        if (_state.value.isTesting) return
        val domain = normalizeRuleTestDomain(_state.value.testDomain)
        if (domain == null) {
            _state.update {
                it.copy(
                    message = "Enter a valid domain such as ads.example.com.",
                    messageIsError = true
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null, messageIsError = false) }
            try {
                val result = testSingleDomain(domain)
                _state.update {
                    it.copy(
                        isTesting = false,
                        results = listOf(result) + it.results.take(29)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(RULE_TEST_SCREEN_TAG, "Single rule test failed for $domain", e)
                _state.update {
                    it.copy(
                        isTesting = false,
                        message = "Rule test could not complete. Refresh sources and try again.",
                        messageIsError = true
                    )
                }
            }
        }
    }

    fun testBatch() {
        if (_state.value.isTesting) return
        val domains = normalizeRuleTestDomains(_state.value.batchInput)
        if (domains.isEmpty()) {
            _state.update {
                it.copy(
                    message = "Paste at least one valid domain, one per line.",
                    messageIsError = true
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null, messageIsError = false) }
            try {
                val results = domains.map { testSingleDomain(it) }
                _state.update { it.copy(isTesting = false, batchResults = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(RULE_TEST_SCREEN_TAG, "Batch rule test failed", e)
                _state.update {
                    it.copy(
                        isTesting = false,
                        message = "Batch test could not complete. Try fewer domains or refresh the blocklists.",
                        messageIsError = true
                    )
                }
            }
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
    val canTestSingle = state.testDomain.isNotBlank() && !state.isTesting

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "Rule tester",
            subtitle = "Preview how domains match block and allow rules",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Single domain test
            item {
                OutlinedTextField(
                    value = state.testDomain,
                    onValueChange = { viewModel.setTestDomain(it) },
                    label = { Text("Domain") },
                    placeholder = { Text("ads.example.com", color = TextDim) },
                    leadingIcon = { Icon(Icons.Filled.Dns, null, tint = TextDim) },
                    trailingIcon = {
                        if (state.isTesting) CircularProgressIndicator(Modifier.size(20.dp), color = Teal, strokeWidth = 2.dp)
                        else IconButton(
                            onClick = { viewModel.testDomain() },
                            enabled = canTestSingle
                        ) {
                            Icon(Icons.Filled.PlayArrow, "Test domain", tint = Teal)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { if (canTestSingle) viewModel.testDomain() },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
            }

            if (state.isTesting) {
                item {
                    HostShieldLoadingState(
                        title = "Testing rules",
                        message = "Checking enabled user rules and source blocklists.",
                        accent = Teal,
                    )
                }
            } else if (state.results.isEmpty() && state.batchResults.isEmpty()) {
                item {
                    HostShieldCompactState(
                        icon = Icons.Filled.Search,
                        title = "No test results yet",
                        message = "Enter one domain or paste a batch to see the matching rule path before making changes.",
                        accent = Teal,
                    )
                }
            }

            state.message?.let { message ->
                item {
                    HostShieldStatusBanner(
                        icon = if (state.messageIsError) Icons.Filled.Error else Icons.Filled.Info,
                        title = if (state.messageIsError) "Rule test needs attention" else "Rule test ready",
                        message = message,
                        accent = if (state.messageIsError) Yellow else Teal,
                        onDismiss = { viewModel.clearMessage() },
                    )
                }
            }

            // Single results
            itemsIndexed(
                state.results,
                key = { index, result -> "single-$index-${result.domain}-${result.matchedBy}" },
            ) { _, result ->
                ResultRow(result)
            }

            // Batch test
            item {
                Spacer(Modifier.height(8.dp))
                HostShieldPanelHeader(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    title = "Batch test",
                    subtitle = "Up to 100 unique domains, one per line",
                    accent = Blue,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.batchInput,
                    onValueChange = { viewModel.setBatchInput(it) },
                    label = { Text("Batch domains") },
                    placeholder = { Text("ads.example.com\nmetrics.example.net", color = TextDim) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Test all", fontSize = 12.sp) }
            }

            // Batch results
            if (state.batchResults.isNotEmpty()) {
                item {
                    val blocked = state.batchResults.count { it.isBlocked }
                    Text("$blocked/${state.batchResults.size} blocked", color = TextDim, fontSize = 11.sp)
                }
                itemsIndexed(
                    state.batchResults,
                    key = { index, result -> "batch-$index-${result.domain}-${result.matchedBy}" },
                ) { _, result -> ResultRow(result) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ResultRow(result: RuleTestResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                val status = if (result.isBlocked) "Blocked" else "Allowed"
                contentDescription = "${result.domain}. $status. ${result.matchedBy}"
            },
        shape = RoundedCornerShape(10.dp),
        color = if (result.isBlocked) Red.copy(alpha = 0.06f) else Surface2
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                .background(if (result.isBlocked) Red else Green))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.domain,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    result.matchedBy,
                    color = TextDim,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
