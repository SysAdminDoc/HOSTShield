package com.hostshield.ui.screens.sources

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: HostShieldRepository
) : ViewModel() {
    val sources: StateFlow<List<HostSource>> = repository.getAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSource(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleSource(id, enabled) }
    }
    fun deleteSource(source: HostSource) {
        viewModelScope.launch { repository.deleteSource(source) }
    }
    fun addSource(url: String, label: String, category: SourceCategory) {
        viewModelScope.launch {
            repository.addSource(HostSource(url = url, label = label, category = category))
        }
    }
}

@Composable
fun SourcesScreen(viewModel: SourcesViewModel = hiltViewModel()) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
                    Text("Sources", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Teal.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${sources.count { it.enabled }} active",
                            style = MaterialTheme.typography.labelMedium,
                            color = Teal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val grouped = sources.groupBy { it.category }
            SourceCategory.entries.forEach { category ->
                val items = grouped[category] ?: return@forEach
                item {
                    Text(
                        category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = categoryColor(category),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        letterSpacing = 0.5.sp
                    )
                }
                items(items, key = { it.id }) { source ->
                    SourceItem(
                        source = source,
                        onToggle = { viewModel.toggleSource(source.id, it) },
                        onDelete = { viewModel.deleteSource(source) }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Teal,
            contentColor = Color.Black,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Add, "Add source")
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, label, cat ->
                viewModel.addSource(url, label, cat)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SourceItem(source: HostSource, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        source.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    if (source.isBuiltin) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Mauve.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("BUILT-IN", style = MaterialTheme.typography.labelSmall, color = Mauve, fontSize = 9.sp)
                        }
                    }
                    val healthBadge = when (source.health) {
                        SourceHealth.ERROR -> "ERROR" to Red
                        SourceHealth.DEAD -> "DEAD" to Red
                        SourceHealth.STALE -> "STALE" to Yellow
                        else -> null
                    }
                    healthBadge?.let { (text, color) ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(color.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 9.sp)
                        }
                    }
                }
                if (source.description.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(source.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (source.entryCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Teal.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${NumberFormat.getNumberInstance().format(source.entryCount)} entries",
                            style = MaterialTheme.typography.labelSmall, color = Teal.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (source.lastUpdated > 0) {
                        Text(
                            formatTimestamp(source.lastUpdated),
                            style = MaterialTheme.typography.labelSmall, color = TextDim
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            if (!source.isBuiltin) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }

            Switch(
                checked = source.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Teal,
                    checkedTrackColor = Teal.copy(alpha = 0.25f),
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = Surface3
                )
            )
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, label: String, category: SourceCategory) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SourceCategory.ADS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Add Source", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank() && label.isNotBlank()) onAdd(url, label, category) },
                enabled = url.isNotBlank() && label.isNotBlank()
            ) { Text("Add", color = Teal, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

private fun categoryColor(cat: SourceCategory): Color = when (cat) {
    SourceCategory.ADS -> Teal
    SourceCategory.TRACKERS -> Blue
    SourceCategory.MALWARE -> Red
    SourceCategory.ADULT -> Flamingo
    SourceCategory.SOCIAL -> Mauve
    SourceCategory.CRYPTO -> Peach
    SourceCategory.CUSTOM -> Yellow
}

private fun formatTimestamp(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
} catch (e: Exception) { "Unknown" }
