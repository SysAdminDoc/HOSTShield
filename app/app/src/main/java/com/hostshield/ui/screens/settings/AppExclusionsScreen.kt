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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean)

@Composable
fun AppExclusionsScreen(viewModel: AppExclusionsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val excluded by viewModel.excludedApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystem by viewModel.showSystem.collectAsStateWithLifecycle()

    val allApps by produceState<List<AppInfo>?>(initialValue = null, pm, context.packageName) {
        value = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val installedApps = allApps.orEmpty()
    val filteredApps = remember(searchQuery, showSystem, allApps) {
        installedApps.filter { (showSystem || !it.isSystem) && (searchQuery.isBlank() || it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "App Exclusions",
            subtitle = if (allApps == null) "Loading installed apps" else "${excluded.size} excluded · ${filteredApps.size} visible",
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = if (showSystem) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showSystem) "Hide system apps" else "Show system apps",
                    onClick = { viewModel.toggleShowSystem() },
                    accent = if (showSystem) Teal else TextDim,
                    selected = showSystem,
                    modifier = Modifier.accessibilityToggle("Show system apps", showSystem),
                )
            }
        )

        OutlinedTextField(
            value = searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search app name or package", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = Surface3, cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )

        Spacer(Modifier.height(8.dp))

        if (allApps == null) {
            HostShieldLoadingState(
                title = "Loading installed apps",
                message = "Building the exclusion list without blocking the interface.",
                accent = Peach,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (filteredApps.isEmpty()) {
                    item {
                        HostShieldEmptyState(
                            icon = Icons.Filled.Apps,
                            title = if (searchQuery.isBlank()) "No apps to show" else "No apps match this search",
                            message = if (searchQuery.isBlank() && !showSystem) {
                                "Only user-installed apps are shown. Use the visibility control to review system apps."
                            } else {
                                "Try a different app name or package id."
                            },
                            accent = Peach,
                        )
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isExcluded = app.packageName in excluded
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(app.packageName, color = TextDim, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(
                                    checked = isExcluded, onCheckedChange = { viewModel.toggleApp(app.packageName) },
                                    modifier = Modifier.accessibilityToggle("Exclude ${app.label} from blocking", isExcluded),
                                    colors = SwitchDefaults.colors(checkedThumbColor = Peach, checkedTrackColor = Peach.copy(alpha = 0.25f), uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3)
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}
