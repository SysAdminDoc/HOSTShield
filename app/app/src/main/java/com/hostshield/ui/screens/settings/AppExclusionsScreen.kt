package com.hostshield.ui.screens.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean)

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
            val current = excludedApps.value.toMutableSet()
            if (packageName in current) current.remove(packageName) else current.add(packageName)
            prefs.setExcludedApps(current)
        }
    }
}

@Composable
fun AppExclusionsScreen(viewModel: AppExclusionsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val excluded by viewModel.excludedApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystem by viewModel.showSystem.collectAsStateWithLifecycle()

    val allApps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) }
            .sortedBy { it.label.lowercase() }
    }
    val filteredApps = remember(searchQuery, showSystem, allApps) {
        allApps.filter { (showSystem || !it.isSystem) && (searchQuery.isBlank() || it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("App Exclusions", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("${excluded.size} apps excluded", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = { viewModel.toggleShowSystem() }) {
                Icon(if (showSystem) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle system", tint = if (showSystem) Teal else TextDim)
            }
        }

        OutlinedTextField(
            value = searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search apps...", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = Surface3, cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isExcluded = app.packageName in excluded
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(app.packageName, color = TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = isExcluded, onCheckedChange = { viewModel.toggleApp(app.packageName) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Peach, checkedTrackColor = Peach.copy(alpha = 0.25f), uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3)
                    )
                }
                HorizontalDivider(color = Surface2)
            }
        }
    }
}
