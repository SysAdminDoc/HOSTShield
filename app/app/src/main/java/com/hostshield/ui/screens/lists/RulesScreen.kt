package com.hostshield.ui.screens.lists

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldScreenHeader
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.BackupRestoreUtil

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
                HostShieldScreenHeader(
                    title = "Rules",
                    subtitle = "Exact, wildcard, regex, allow, and redirect overrides.",
                ) {
                    HostShieldActionIconButton(
                        icon = Icons.Filled.ContentPaste,
                        contentDescription = "Paste domains",
                        onClick = pasteDomainsFromClipboard,
                        accent = Blue,
                    )
                    HostShieldActionIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Add rule",
                        onClick = { showAddDialog = true },
                        accent = Teal,
                        modifier = Modifier.testTag(HostShieldTestTags.Rules.AddButton),
                    )
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
    HostShieldFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        accent = color,
        semanticsLabel = "$label rule filter",
    )
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
                    Text(
                        rule.hostname,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
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
                    Text("-> ${rule.redirectIp}", color = Peach, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (rule.comment.isNotEmpty()) {
                    Text(rule.comment, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, RuleType, String, String) -> Unit) {
    var hostname by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RuleType.BLOCK) }
    var redirectIp by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var regexError by remember { mutableStateOf<String?>(null) }
    val redirectError = remember(type, redirectIp) {
        when {
            type != RuleType.REDIRECT -> null
            redirectIp.isBlank() -> "Redirect rules need an IPv4 or IPv6 address."
            BackupRestoreUtil.isValidRedirectIp(redirectIp) -> null
            else -> "Enter a valid IPv4 or IPv6 address."
        }
    }
    val canSubmit = hostname.isNotBlank() && regexError == null && redirectError == null

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Surface1, shape = RoundedCornerShape(12.dp),
        title = { Text("Add rule", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuleType.entries.forEach { rt ->
                        TypeChip(rt, rt.name.lowercase().replaceFirstChar { it.uppercase() }, type == rt) { type = rt }
                    }
                }
                OutlinedTextField(
                    value = hostname, onValueChange = {
                        hostname = it
                        if (isRegex) {
                            regexError = validateRegexPattern(it)
                        }
                    },
                    label = { Text(if (isRegex) "Regex pattern" else "Hostname") },
                    placeholder = { Text(if (isRegex) ".*\\.ad[sv]?\\." else "*.example.com", color = TextDim) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isRegex) KeyboardType.Text else KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HostShieldTestTags.Rules.HostnameField),
                    colors = fieldColors(),
                    isError = regexError != null
                )
                if (regexError != null) {
                    Text(regexError!!, color = Red, fontSize = 10.sp)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Checkbox) {
                            isRegex = !isRegex
                            regexError = if (isRegex && hostname.isNotBlank()) {
                                validateRegexPattern(hostname)
                            } else null
                        }
                        .accessibilityToggle("Regex pattern", isRegex),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRegex,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Mauve, uncheckedColor = TextDim, checkmarkColor = Color.Black),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Regex pattern", color = if (isRegex) Mauve else TextDim, fontSize = 12.sp)
                }
                if (type == RuleType.REDIRECT) {
                    OutlinedTextField(
                        value = redirectIp,
                        onValueChange = { redirectIp = it.trim() },
                        label = { Text("Redirect IP") },
                        singleLine = true,
                        isError = redirectError != null,
                        supportingText = redirectError?.let { message ->
                            { Text(message, color = Red, fontSize = 11.sp) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSubmit) {
                        onAdd(hostname, type, redirectIp, if (isRegex) "REGEX:$comment" else comment)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.testTag(HostShieldTestTags.Rules.ConfirmAddButton)
            ) { Text("Add rule", color = if (canSubmit) Teal else TextDim) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal, unfocusedBorderColor = Surface3, cursorColor = Teal,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedLabelColor = Teal, unfocusedLabelColor = TextDim, focusedPlaceholderColor = TextDim,
    errorBorderColor = Red, errorCursorColor = Red, errorLabelColor = Red
)

private fun validateRegexPattern(pattern: String): String? =
    try {
        Regex(pattern)
        null
    } catch (_: Exception) {
        "Invalid regex pattern."
    }

@Composable
private fun ruleColor(type: RuleType?): Color = when (type) {
    RuleType.BLOCK -> Red; RuleType.ALLOW -> Green; RuleType.REDIRECT -> Peach; null -> Teal
}
