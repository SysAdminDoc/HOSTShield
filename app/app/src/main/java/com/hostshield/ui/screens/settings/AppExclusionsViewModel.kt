package com.hostshield.ui.screens.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityToggle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppExclusionsViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {
    val excludedApps: StateFlow<Set<String>> = prefs.excludedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _showSystem = MutableStateFlow(false)
    val showSystem = _showSystem.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun toggleShowSystem() { _showSystem.update { !it } }
    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            // Atomic transform; reading the StateFlow value could be stale
            // between two quick toggles and drop one change.
            val currentlyExcluded = packageName in excludedApps.value
            prefs.toggleExcludedApp(packageName, !currentlyExcluded)
        }
    }
}
