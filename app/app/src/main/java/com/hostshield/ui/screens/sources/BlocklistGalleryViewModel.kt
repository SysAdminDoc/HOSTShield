package com.hostshield.ui.screens.sources

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.repository.HostShieldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

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
