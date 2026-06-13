package com.hostshield.ui.screens.lists

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: HostShieldRepository
) : ViewModel() {
    val rules: StateFlow<List<UserRule>> = repository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(hostname: String, type: RuleType, redirectIp: String = "", comment: String = "", isRegex: Boolean = false) {
        val isWild = !isRegex && hostname.startsWith("*.")
        viewModelScope.launch {
            repository.addRule(UserRule(
                hostname = hostname.trim().let { if (isRegex) it else it.lowercase() },
                type = type, redirectIp = redirectIp,
                comment = comment, isWildcard = isWild, isRegex = isRegex
            ))
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleRule(id, enabled) }
    }

    fun deleteRule(rule: UserRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(viewModel: RulesViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<RuleType?>(null) }
    var clipboardMessage by remember { mutableStateOf<String?>(null) }
    var pendingDeleteRule by remember { mutableStateOf<UserRule?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val filtered = remember(rules, filterType) {
        if (filterType == null) rules else rules.filter { it.type == filterType }
    }
    val pasteDomainsFromClipboard = {
        scope.launch {
            val text = clipboard.getClipEntry()
                ?.clipData?.getItemAt(0)?.text?.toString() ?: ""
            val domains = text.lines()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && it.contains('.') && !it.startsWith("#") }
                .distinct()
            if (domains.isNotEmpty()) {
                domains.forEach { viewModel.addRule(it, RuleType.BLOCK) }
                clipboardMessage = "Added ${domains.size} domains from clipboard"
            } else {
                clipboardMessage = "No valid domains in clipboard"
            }
        }
        Unit
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Rules",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            modifier = Modifier.accessibilityHeading()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Create exact, wildcard, regex, allow, and redirect rules.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = pasteDomainsFromClipboard,
                            shape = RoundedCornerShape(8.dp),
                            color = Surface3.copy(alpha = 0.8f),
                            contentColor = Teal,
                            modifier = Modifier
                                .size(40.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.ContentPaste, "Paste domains", modifier = Modifier.size(18.dp))
                            }
                        }
                        Surface(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Teal.copy(alpha = 0.14f),
                            contentColor = Teal,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag(HostShieldTestTags.Rules.AddButton)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, "Add rule", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypeChip(null, "All", filterType == null) { filterType = null }
                    TypeChip(RuleType.BLOCK, "Block", filterType == RuleType.BLOCK) { filterType = RuleType.BLOCK }
                    TypeChip(RuleType.ALLOW, "Allow", filterType == RuleType.ALLOW) { filterType = RuleType.ALLOW }
                    TypeChip(RuleType.REDIRECT, "Redirect", filterType == RuleType.REDIRECT) { filterType = RuleType.REDIRECT }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                item {
                    val filteredByType = filterType != null
                    HostShieldEmptyState(
                        icon = if (filteredByType) Icons.Filled.FilterAltOff else Icons.AutoMirrored.Filled.Rule,
                        title = if (filteredByType) "No rules in this filter" else "No custom rules yet",
                        message = if (filteredByType) {
                            "This rule type has no entries. Switch filters to review the full rule set."
                        } else {
                            "Create a block, allow, redirect, wildcard, or regex rule when source lists need a precise override."
                        },
                        accent = if (filteredByType) Blue else Teal,
                        primaryActionLabel = if (filteredByType) "Show all" else "Add rule",
                        onPrimaryAction = if (filteredByType) {
                            { filterType = null }
                        } else {
                            { showAddDialog = true }
                        },
                        secondaryActionLabel = if (filteredByType) null else "Paste domains",
                        onSecondaryAction = if (filteredByType) null else pasteDomainsFromClipboard,
                    )
                }
            }

            items(filtered, key = { it.id }) { rule ->
                RuleItem(
                    rule = rule,
                    onToggle = { viewModel.toggleRule(rule.id, it) },
                    onDelete = { pendingDeleteRule = rule }
                )
            }
            item { Spacer(Modifier.height(140.dp)) }
        }

        // Clipboard message snackbar
        clipboardMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                clipboardMessage = null
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 20.dp, end = 20.dp)
                    .accessibilityLiveRegion(msg),
                shape = RoundedCornerShape(10.dp),
                color = Surface2
            ) {
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Teal, fontSize = 12.sp)
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { host, type, redir, comment ->
                val isRegex = comment.startsWith("REGEX:")
                val cleanComment = if (isRegex) comment.removePrefix("REGEX:") else comment
                viewModel.addRule(host, type, redir, cleanComment, isRegex)
                showAddDialog = false
            }
        )
    }

    pendingDeleteRule?.let { rule ->
        ConfirmDestructiveDialog(
            title = "Delete rule?",
            body = "This removes ${rule.hostname} from custom rules. Existing log history remains unchanged.",
            confirmLabel = "Delete rule",
            onConfirm = { viewModel.deleteRule(rule) },
            onDismiss = { pendingDeleteRule = null },
        )
    }
}

@Composable
private fun TypeChip(type: RuleType?, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = ruleColor(type)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) color.copy(alpha = 0.12f) else Surface2,
        modifier = Modifier
            .heightIn(min = 34.dp)
            .accessibilitySelection("$label rule filter", selected)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (selected) color else TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RuleItem(rule: UserRule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val color = ruleColor(rule.type)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(when (rule.type) {
                    RuleType.BLOCK -> Icons.Filled.Block
                    RuleType.ALLOW -> Icons.Filled.CheckCircle
                    RuleType.REDIRECT -> Icons.AutoMirrored.Filled.AltRoute
                }, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.hostname, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                    if (rule.isWildcard) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(3.dp), color = Mauve.copy(alpha = 0.1f)) {
                            Text("WILDCARD", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), color = Mauve, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rule.isRegex) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(3.dp), color = Blue.copy(alpha = 0.1f)) {
                            Text("REGEX", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), color = Blue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (rule.type == RuleType.REDIRECT && rule.redirectIp.isNotEmpty()) {
                    Text("-> ${rule.redirectIp}", color = Peach, fontSize = 11.sp)
                }
                if (rule.comment.isNotEmpty()) {
                    Text(rule.comment, color = TextDim, fontSize = 11.sp, maxLines = 1)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, "Delete ${rule.hostname}", tint = Red.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = rule.enabled, onCheckedChange = onToggle,
                modifier = Modifier.accessibilityToggle("Enable ${rule.hostname} rule", rule.enabled),
                colors = SwitchDefaults.colors(checkedThumbColor = color, checkedTrackColor = color.copy(alpha = 0.25f), uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3)
            )
        }
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, RuleType, String, String) -> Unit) {
    var hostname by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RuleType.BLOCK) }
    var redirectIp by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var regexError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Surface1, shape = RoundedCornerShape(12.dp),
        title = { Text("Add rule", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.entries.forEach { rt ->
                        TypeChip(rt, rt.name.lowercase().replaceFirstChar { it.uppercase() }, type == rt) { type = rt }
                    }
                }
                OutlinedTextField(
                    value = hostname, onValueChange = {
                        hostname = it
                        if (isRegex) {
                            regexError = try { Regex(it); null } catch (e: Exception) { e.message?.take(50) }
                        }
                    },
                    label = { Text(if (isRegex) "Regex pattern" else "Hostname") },
                    placeholder = { Text(if (isRegex) ".*\\.ad[sv]?\\." else "*.example.com", color = TextDim) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HostShieldTestTags.Rules.HostnameField),
                    colors = fieldColors(),
                    isError = regexError != null
                )
                if (regexError != null) {
                    Text(regexError!!, color = Red, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isRegex, onCheckedChange = {
                            isRegex = it
                            regexError = if (it && hostname.isNotBlank()) {
                                try { Regex(hostname); null } catch (e: Exception) { e.message?.take(50) }
                            } else null
                        },
                        colors = CheckboxDefaults.colors(checkedColor = Mauve, uncheckedColor = TextDim, checkmarkColor = Color.Black),
                        modifier = Modifier.size(20.dp).accessibilityToggle("Regex pattern", isRegex)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Regex pattern", color = if (isRegex) Mauve else TextDim, fontSize = 12.sp)
                }
                if (type == RuleType.REDIRECT) {
                    OutlinedTextField(value = redirectIp, onValueChange = { redirectIp = it }, label = { Text("Redirect IP") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                }
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (hostname.isNotBlank() && regexError == null) {
                        onAdd(hostname, type, redirectIp, if (isRegex) "REGEX:$comment" else comment)
                    }
                },
                enabled = hostname.isNotBlank() && regexError == null,
                modifier = Modifier.testTag(HostShieldTestTags.Rules.ConfirmAddButton)
            ) { Text("Add rule", color = Teal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal, unfocusedBorderColor = Surface3, cursorColor = Teal,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedLabelColor = Teal, unfocusedLabelColor = TextDim, focusedPlaceholderColor = TextDim
)

private fun ruleColor(type: RuleType?): Color = when (type) {
    RuleType.BLOCK -> Red; RuleType.ALLOW -> Green; RuleType.REDIRECT -> Peach; null -> Teal
}
