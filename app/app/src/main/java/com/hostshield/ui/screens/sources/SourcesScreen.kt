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
import com.hostshield.data.source.SourceDownloader
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
    private val repository: HostShieldRepository,
    private val downloader: SourceDownloader
) : ViewModel() {
    val sources: StateFlow<List<HostSource>> = repository.getAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private val _healthCheckMessage = MutableStateFlow<String?>(null)
    val healthCheckMessage: StateFlow<String?> = _healthCheckMessage.asStateFlow()
    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

    init {
        // Track when sources have emitted their first real value
        viewModelScope.launch {
            sources.first { true }
            _isLoading.value = false
        }
    }

    fun toggleSource(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleSource(id, enabled)
            } catch (e: Exception) {
                _error.value = "Failed to toggle source: ${e.message}"
            }
        }
    }
    fun deleteSource(source: HostSource) {
        viewModelScope.launch {
            try {
                repository.deleteSource(source)
            } catch (e: Exception) {
                _error.value = "Failed to delete source: ${e.message}"
            }
        }
    }
    fun addSource(url: String, label: String, category: SourceCategory) {
        viewModelScope.launch {
            try {
                repository.addSource(HostSource(url = url, label = label, category = category))
            } catch (e: Exception) {
                _error.value = "Failed to add source: ${e.message}"
            }
        }
    }

    fun checkAllSourceHealth() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isCheckingHealth.value = true
            _healthCheckMessage.value = "Checking sources..."
            try {
                val allSources = sources.value
                var ok = 0; var fail = 0
                for (source in allSources) {
                    if (!source.enabled) continue
                    val result = downloader.validate(source.url)
                    result.onSuccess { ok++ }.onFailure { fail++ }
                }
                _healthCheckMessage.value = "$ok reachable, $fail unreachable"
            } catch (e: Exception) {
                _error.value = "Health check failed: ${e.message}"
                _healthCheckMessage.value = null
            } finally {
                _isCheckingHealth.value = false
            }
        }
    }
}

@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel(),
    onNavigateToGallery: () -> Unit = {}
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val healthMsg by viewModel.healthCheckMessage.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingHealth.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.CloudDownload, null, tint = Teal, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Loading sources", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Checking configured blocklists and health data.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = Teal, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Error banner
            if (error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Red.copy(alpha = 0.1f)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, tint = Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(error ?: "", color = Red, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, "Dismiss", tint = Red, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sources", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        IconButton(
                            onClick = { viewModel.checkAllSourceHealth() },
                            enabled = !isChecking,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isChecking) CircularProgressIndicator(Modifier.size(14.dp), color = Teal, strokeWidth = 2.dp)
                            else Icon(Icons.Filled.HealthAndSafety, "Health check", tint = TextDim, modifier = Modifier.size(18.dp))
                        }
                        Surface(
                            onClick = onNavigateToGallery,
                            shape = RoundedCornerShape(8.dp),
                            color = Mauve.copy(alpha = 0.12f),
                            contentColor = Mauve,
                            modifier = Modifier
                                .size(40.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Explore, "Browse source gallery", modifier = Modifier.size(18.dp))
                            }
                        }
                        Surface(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Teal.copy(alpha = 0.14f),
                            contentColor = Teal,
                            modifier = Modifier
                                .size(40.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, "Add source", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                healthMsg?.let { msg ->
                    Spacer(Modifier.height(4.dp))
                    Text(msg, color = TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))

                // Summary stats
                val totalDomains = sources.filter { it.enabled }.sumOf { it.entryCount }
                val totalSize = sources.filter { it.enabled }.sumOf { it.sizeBytes }
                val unhealthy = sources.count { it.health == SourceHealth.ERROR || it.health == SourceHealth.DEAD }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Surface2, modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(NumberFormat.getNumberInstance().format(totalDomains), color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Domains", color = TextDim, fontSize = 9.sp)
                        }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Surface2, modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val sizeLabel = if (totalSize > 1_000_000) "${"%.1f".format(totalSize / 1_000_000f)} MB"
                                else if (totalSize > 1000) "${totalSize / 1000} KB" else "$totalSize B"
                            Text(sizeLabel, color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Total Size", color = TextDim, fontSize = 9.sp)
                        }
                    }
                    if (unhealthy > 0) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Red.copy(alpha = 0.08f), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$unhealthy", color = Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Unhealthy", color = Red.copy(alpha = 0.7f), fontSize = 9.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val grouped = sources.groupBy { it.category }
            if (sources.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(28.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.CloudOff, null, tint = TextDim, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No sources configured", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add a trusted blocklist or browse the gallery to start protection.",
                                color = TextDim,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
            SourceCategory.entries.forEach { category ->
                val items = grouped[category] ?: return@forEach
                item {
                    Text(
                        category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = categoryColor(category),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        letterSpacing = 0.sp
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
            item { Spacer(Modifier.height(140.dp)) }
        }
        } // end else (not loading)
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
                    if (source.domainsAdded > 0 || source.domainsRemoved > 0) {
                        Spacer(Modifier.width(8.dp))
                        if (source.domainsAdded > 0) {
                            Text(
                                "+${source.domainsAdded}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Green.copy(alpha = 0.8f)
                            )
                        }
                        if (source.domainsRemoved > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "-${source.domainsRemoved}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Red.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            if (!source.isBuiltin) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "Delete ${source.label}", tint = Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, label: String, category: SourceCategory) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SourceCategory.ADS) }

    // Validate URL: must parse as http:// or https://. https:// is the only safe
    // choice for remote blocklists, but http:// is allowed for LAN mirrors.
    val urlValid = remember(url) {
        val trimmed = url.trim()
        try {
            val parsed = java.net.URL(trimmed)
            (parsed.protocol == "http" || parsed.protocol == "https") && !parsed.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
    val labelValid = label.trim().isNotEmpty()
    val canSubmit = urlValid && labelValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(12.dp),
        title = { Text("Add source", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Source name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Source URL") }, singleLine = true,
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = if (url.isNotBlank() && !urlValid) {
                        { Text("Use a complete http:// or https:// URL.", color = Red, fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    "Category",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SourceCategory.entries.forEach { c ->
                        val selected = c == category
                        FilterChip(
                            selected = selected,
                            onClick = { category = c },
                            label = { Text(c.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Surface2,
                                labelColor = TextDim,
                                selectedContainerColor = categoryColor(c).copy(alpha = 0.18f),
                                selectedLabelColor = categoryColor(c),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onAdd(url.trim(), label.trim(), category) },
                enabled = canSubmit
            ) { Text("Add source", color = Teal, fontWeight = FontWeight.SemiBold) }
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
    SourceCategory.ALLOWLIST -> Green
    SourceCategory.CUSTOM -> Yellow
}

private fun formatTimestamp(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
} catch (e: Exception) { "Unknown" }
