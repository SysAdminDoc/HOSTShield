package com.hostshield.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.R
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchHistory: List<String>,
    onSaveSearch: (String) -> Unit,
    onSearchLogs: (String) -> Unit,
    onSearchApps: (String) -> Unit
) {
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { onSearchQueryChange(it); onSearchExpandedChange(it.isNotBlank()) },
        placeholder = { Text(stringResource(R.string.home_search_placeholder), color = TextDim, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (searchQuery.isNotBlank()) {
                IconButton(onClick = { onSearchQueryChange(""); onSearchExpandedChange(false) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.home_search_clear), tint = TextDim, modifier = Modifier.size(16.dp))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).defaultMinSize(minHeight = 52.dp),
        singleLine = true, shape = RoundedCornerShape(12.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
            cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
        )
    )
    // Search history chips when field is focused but empty
    if (searchQuery.isBlank() && searchHistory.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            searchHistory.take(6).forEach { term ->
                HostShieldFilterChip(
                    label = term,
                    selected = false,
                    onClick = { onSearchQueryChange(term); onSearchExpandedChange(true); onSaveSearch(term) },
                    accent = Teal,
                    semanticsLabel = stringResource(R.string.home_recent_search, term),
                )
            }
        }
    }
    AnimatedVisibility(visible = searchExpanded && searchQuery.length >= 2) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Surface1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Surface(
                        onClick = { onSaveSearch(searchQuery); onSearchLogs(searchQuery); onSearchExpandedChange(false) },
                        shape = RoundedCornerShape(8.dp),
                        color = Surface2
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Dns, null, tint = Blue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_search_dns_logs, searchQuery), color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        onClick = { onSaveSearch(searchQuery); onSearchApps(searchQuery); onSearchExpandedChange(false) },
                        shape = RoundedCornerShape(8.dp),
                        color = Surface2
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Apps, null, tint = Mauve, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_search_app_activity, searchQuery), color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
