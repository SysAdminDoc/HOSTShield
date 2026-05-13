package com.hostshield.ui.screens.sources

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

data class CuratedList(
    val label: String,
    val url: String,
    val description: String,
    val entries: String,
    val recommended: Boolean,
    val category: SourceCategory
)

data class GalleryState(
    val lists: Map<SourceCategory, List<CuratedList>> = emptyMap(),
    val existingUrls: Set<String> = emptySet(),
    val addedUrls: Set<String> = emptySet(),
    val message: String? = null
)

@HiltViewModel
class BlocklistGalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: HostShieldRepository
) : ViewModel() {
    private val _state = MutableStateFlow(GalleryState())
    val state = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = context.assets.open("curated_blocklists.json").bufferedReader().readText()
            val categories = JSONArray(json)
            val lists = mutableMapOf<SourceCategory, List<CuratedList>>()

            for (i in 0 until categories.length()) {
                val catObj = categories.getJSONObject(i)
                val catName = catObj.getString("category")
                val category = try { SourceCategory.valueOf(catName) } catch (_: Exception) { continue }
                val items = catObj.getJSONArray("lists")
                val catLists = mutableListOf<CuratedList>()
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    catLists.add(CuratedList(
                        label = item.getString("label"),
                        url = item.getString("url"),
                        description = item.getString("description"),
                        entries = item.getString("entries"),
                        recommended = item.optBoolean("recommended", false),
                        category = category
                    ))
                }
                lists[category] = catLists
            }

            // Get existing source URLs to show "already added" state
            val existing = repository.getAllSources().first().map { it.url }.toSet()

            _state.update { it.copy(lists = lists, existingUrls = existing) }
        }
    }

    fun addList(list: CuratedList) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSource(HostSource(
                url = list.url,
                label = list.label,
                description = list.description,
                category = list.category,
                isBuiltin = false
            ))
            _state.update { it.copy(
                addedUrls = it.addedUrls + list.url,
                message = "Added ${list.label}"
            ) }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
}

@Composable
fun BlocklistGalleryScreen(
    viewModel: BlocklistGalleryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf<SourceCategory?>(null) }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Blocklist Gallery", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                val total = state.lists.values.sumOf { it.size }
                Text("$total curated lists across ${state.lists.size} categories", color = TextDim, fontSize = 11.sp)
            }
        }

        // Category chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal.copy(alpha = 0.15f),
                    selectedLabelColor = Teal
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Surface3, selectedBorderColor = Teal.copy(alpha = 0.3f),
                    enabled = true, selected = selectedCategory == null
                )
            )
        }

        // Snackbar-style message
        AnimatedVisibility(visible = state.message != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Green.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(state.message ?: "", color = Green, fontSize = 12.sp)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val categories = if (selectedCategory != null) {
                state.lists.filter { it.key == selectedCategory }
            } else state.lists

            categories.forEach { (category, lists) ->
                item {
                    Text(
                        category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = galleryCategoryColor(category),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        letterSpacing = 0.sp
                    )
                }
                items(lists, key = { it.url }) { list ->
                    val isExisting = list.url in state.existingUrls || list.url in state.addedUrls
                    GalleryListItem(
                        list = list,
                        isAdded = isExisting,
                        onAdd = { viewModel.addList(list) }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GalleryListItem(
    list: CuratedList,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        list.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    if (list.recommended) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Green.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("RECOMMENDED", style = MaterialTheme.typography.labelSmall,
                                color = Green, fontSize = 8.sp, letterSpacing = 0.sp)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(list.description, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(list.entries + " entries", color = TextDim, fontSize = 10.sp)
            }

            Spacer(Modifier.width(8.dp))

            if (isAdded) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Teal.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Added", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                FilledIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Teal.copy(alpha = 0.15f),
                        contentColor = Teal
                    )
                ) {
                    Icon(Icons.Filled.Add, "Add", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun galleryCategoryColor(cat: SourceCategory): Color = when (cat) {
    SourceCategory.ADS -> Teal
    SourceCategory.TRACKERS -> Blue
    SourceCategory.MALWARE -> Red
    SourceCategory.ADULT -> Flamingo
    SourceCategory.SOCIAL -> Mauve
    SourceCategory.CRYPTO -> Peach
    SourceCategory.ALLOWLIST -> Green
    SourceCategory.CUSTOM -> Yellow
}
