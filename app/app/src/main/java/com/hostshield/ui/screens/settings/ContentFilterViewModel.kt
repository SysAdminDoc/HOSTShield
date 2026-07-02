package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.ContentCategory
import com.hostshield.service.ContentFilterManager
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityToggle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentFilterViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val contentFilterManager: ContentFilterManager,
) : ViewModel() {

    val enabledCategories = prefs.contentFilterCategories
        .map { names -> names.mapNotNull { runCatching { ContentCategory.valueOf(it) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val categories: List<ContentCategory> = contentFilterManager.getCategories()

    fun getDomainCount(category: ContentCategory): Int =
        contentFilterManager.getDomainsForCategory(category).size

    val totalDomainCount: Int get() = contentFilterManager.totalDomainCount

    fun toggle(category: ContentCategory, enabled: Boolean) {
        viewModelScope.launch {
            val current = enabledCategories.value.map { it.name }.toMutableSet()
            if (enabled) current.add(category.name) else current.remove(category.name)
            prefs.setContentFilterCategories(current)
        }
    }
}
