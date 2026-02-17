package com.hostshield.ui.screens.lists

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.hostshield.data.model.UserRule
import com.hostshield.data.repository.HostShieldRepository
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

    fun addRule(hostname: String, type: RuleType, redirectIp: String = "", comment: String = "") {
        val isWild = hostname.startsWith("*.")
        viewModelScope.launch {
            repository.addRule(UserRule(
                hostname = hostname.lowercase().trim(),
                type = type, redirectIp = redirectIp,
                comment = comment, isWildcard = isWild
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

@Composable
fun RulesScreen(viewModel: RulesViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<RuleType?>(null) }

    val filtered = remember(rules, filterType) {
        if (filterType == null) rules else rules.filter { it.type == filterType }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Rules", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip(null, "All", filterType == null) { filterType = null }
                    TypeChip(RuleType.BLOCK, "Block", filterType == RuleType.BLOCK) { filterType = RuleType.BLOCK }
                    TypeChip(RuleType.ALLOW, "Allow", filterType == RuleType.ALLOW) { filterType = RuleType.ALLOW }
                    TypeChip(RuleType.REDIRECT, "Redirect", filterType == RuleType.REDIRECT) { filterType = RuleType.REDIRECT }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Rule, null, tint = TextDim, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No rules yet", color = TextSecondary, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap + to add block, allow, or redirect rules", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            items(filtered, key = { it.id }) { rule ->
                RuleItem(rule = rule, onToggle = { viewModel.toggleRule(rule.id, it) }, onDelete = { viewModel.deleteRule(rule) })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Teal, contentColor = Color.Black,
            shape = RoundedCornerShape(16.dp)
        ) { Icon(Icons.Filled.Add, "Add rule") }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { host, type, redir, comment ->
                viewModel.addRule(host, type, redir, comment)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun TypeChip(type: RuleType?, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = ruleColor(type)
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = if (selected) color.copy(alpha = 0.12f) else Surface2) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (selected) color else TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RuleItem(rule: UserRule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val color = ruleColor(rule.type)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(when (rule.type) {
                    RuleType.BLOCK -> Icons.Filled.Block
                    RuleType.ALLOW -> Icons.Filled.CheckCircle
                    RuleType.REDIRECT -> Icons.Filled.AltRoute
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
                }
                if (rule.type == RuleType.REDIRECT && rule.redirectIp.isNotEmpty()) {
                    Text("-> ${rule.redirectIp}", color = Peach, fontSize = 11.sp)
                }
                if (rule.comment.isNotEmpty()) {
                    Text(rule.comment, color = TextDim, fontSize = 11.sp, maxLines = 1)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, null, tint = Red.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = rule.enabled, onCheckedChange = onToggle,
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

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Surface1, shape = RoundedCornerShape(20.dp),
        title = { Text("Add Rule", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.entries.forEach { rt ->
                        TypeChip(rt, rt.name.lowercase().replaceFirstChar { it.uppercase() }, type == rt) { type = rt }
                    }
                }
                OutlinedTextField(value = hostname, onValueChange = { hostname = it }, label = { Text("Hostname") }, placeholder = { Text("*.example.com", color = TextDim) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                if (type == RuleType.REDIRECT) {
                    OutlinedTextField(value = redirectIp, onValueChange = { redirectIp = it }, label = { Text("Redirect IP") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                }
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            }
        },
        confirmButton = { TextButton(onClick = { if (hostname.isNotBlank()) onAdd(hostname, type, redirectIp, comment) }, enabled = hostname.isNotBlank()) { Text("Add", color = Teal) } },
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
