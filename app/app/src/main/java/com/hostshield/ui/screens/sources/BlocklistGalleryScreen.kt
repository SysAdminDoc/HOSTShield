package com.hostshield.ui.screens.sources

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldStatusBanner
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
    val category: SourceCategory,
    val warning: String? = null,
    val tier: String? = null
)

data class GalleryState(
    val lists: Map<SourceCategory, List<CuratedList>> = emptyMap(),
    val existingUrls: Set<String> = emptySet(),
    val addedUrls: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false
)

@HiltViewModel
class BlocklistGalleryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: HostShieldRepository
) : ViewModel() {
    private val _state = MutableStateFlow(GalleryState())
    val state = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
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
                            category = category,
                            warning = item.optString("warning").ifBlank { null },
                            tier = item.optString("tier").ifBlank { null }
                        ))
                    }
                    lists[category] = catLists
                }

                // Get existing source URLs to show "already added" state
                val existing = repository.getAllSources().first().map { it.url }.toSet()

                _state.update {
                    it.copy(
                        lists = lists,
                        existingUrls = existing,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("BlocklistGallery", "Failed to load curated blocklists", e)
                _state.update {
                    it.copy(
                        lists = emptyMap(),
                        isLoading = false,
                        errorMessage = "Could not load curated blocklists. Try again."
                    )
                }
            }
        }
    }

    fun retryLoad() = load()

    fun addList(list: CuratedList) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addSource(HostSource(
                    url = list.url,
                    label = list.label,
                    description = list.description,
                    category = list.category,
                    isBuiltin = false
                ))
                _state.update { it.copy(
                    addedUrls = it.addedUrls + list.url,
                    message = "Added ${list.label}",
                    messageIsError = false
                ) }
            } catch (e: Exception) {
                android.util.Log.e("BlocklistGallery", "Failed to add curated blocklist ${list.url}", e)
                _state.update {
                    it.copy(
                        message = "Could not add ${list.label}. Try again.",
                        messageIsError = true
                    )
                }
            }
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

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        val total = state.lists.values.sumOf { it.size }
        HostShieldBackHeader(
            title = "Blocklist gallery",
            subtitle = "$total curated lists across ${state.lists.size} categories",
            onBack = onBack,
        )

        // Category chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HostShieldFilterChip(
                label = "All",
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                accent = Teal,
            )
            SourceCategory.entries.filter { state.lists.containsKey(it) }.forEach { category ->
                HostShieldFilterChip(
                    label = category.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    accent = galleryCategoryColor(category),
                )
            }
        }

        // Snackbar-style message
        AnimatedVisibility(visible = state.message != null) {
            HostShieldStatusBanner(
                icon = if (state.messageIsError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (state.messageIsError) "Gallery action failed" else "Gallery updated",
                message = state.message ?: "",
                accent = if (state.messageIsError) Red else Green,
                onDismiss = { viewModel.clearMessage() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val galleryError = state.errorMessage
        when {
            state.isLoading -> {
                HostShieldLoadingState(
                    title = "Loading blocklists",
                    message = "Preparing curated source recommendations.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            galleryError != null -> {
                HostShieldEmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "Gallery unavailable",
                    message = galleryError,
                    accent = Red,
                    primaryActionLabel = "Retry",
                    onPrimaryAction = { viewModel.retryLoad() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            state.lists.isEmpty() -> {
                HostShieldEmptyState(
                    icon = Icons.Filled.FilterAltOff,
                    title = "No curated lists",
                    message = "Curated source recommendations are not available in this build.",
                    accent = Teal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            else -> {
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
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                Text(
                    list.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(list.entries + " entries", color = TextDim, fontSize = 10.sp)
                    list.tier?.let { tier ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(galleryCategoryColor(list.category).copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(tier, color = galleryCategoryColor(list.category), fontSize = 8.sp, letterSpacing = 0.sp)
                        }
                    }
                }
                list.warning?.let { warning ->
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, null, tint = Yellow, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            warning,
                            color = Yellow,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
                    modifier = Modifier.size(48.dp),
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

@Composable
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
