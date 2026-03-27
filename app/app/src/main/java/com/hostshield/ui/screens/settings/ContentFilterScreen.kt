package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.ContentCategory
import com.hostshield.service.ContentFilterManager
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
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

@Composable
fun ContentFilterScreen(
    onBack: () -> Unit,
    viewModel: ContentFilterViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabledCategories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary)
            }
            Text("Content Filtering", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        // Summary card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .background(Mauve.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.FilterList, null, tint = Mauve, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "${enabled.size} categories active",
                        color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${viewModel.totalDomainCount} domains indexed",
                        color = TextDim, fontSize = 12.sp,
                    )
                }
            }
        }

        Text(
            "Block entire categories of content at the DNS level. Changes take effect immediately.",
            color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(4.dp))

        // Category toggles
        viewModel.categories.forEach { category ->
            val isEnabled = category in enabled
            val domainCount = viewModel.getDomainCount(category)
            CategoryCard(
                category = category,
                domainCount = domainCount,
                enabled = isEnabled,
                onToggle = { viewModel.toggle(category, it) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryCard(
    category: ContentCategory,
    domainCount: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val icon = when (category) {
        ContentCategory.ADULT -> Icons.Filled.Block
        ContentCategory.GAMBLING -> Icons.Filled.Star
        ContentCategory.SOCIAL_MEDIA -> Icons.Filled.People
        ContentCategory.GAMING -> Icons.Filled.VideogameAsset
        ContentCategory.STREAMING -> Icons.Filled.PlayArrow
        ContentCategory.DATING -> Icons.Filled.Favorite
        ContentCategory.DRUGS -> Icons.Filled.Warning
        ContentCategory.WEAPONS -> Icons.Filled.RemoveCircle
        ContentCategory.PIRACY -> Icons.Filled.ContentCopy
        ContentCategory.CRYPTO -> Icons.Filled.AttachMoney
        ContentCategory.NEWS -> Icons.Filled.Article
        ContentCategory.SHOPPING -> Icons.Filled.ShoppingCart
        ContentCategory.VPN_PROXY -> Icons.Filled.VpnLock
        ContentCategory.MALWARE -> Icons.Filled.BugReport
        ContentCategory.SOCIAL -> Icons.Filled.Forum
    }
    val color = when (category) {
        ContentCategory.ADULT -> Red
        ContentCategory.GAMBLING -> Yellow
        ContentCategory.SOCIAL_MEDIA -> Blue
        ContentCategory.GAMING -> Green
        ContentCategory.STREAMING -> Peach
        ContentCategory.DATING -> Red
        ContentCategory.DRUGS -> Yellow
        ContentCategory.WEAPONS -> Red
        ContentCategory.PIRACY -> Mauve
        ContentCategory.CRYPTO -> Teal
        ContentCategory.NEWS -> Sky
        ContentCategory.SHOPPING -> Peach
        ContentCategory.VPN_PROXY -> Blue
        ContentCategory.MALWARE -> Red
        ContentCategory.SOCIAL -> Blue
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.displayName,
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    "${category.description} · $domainCount domains",
                    color = TextDim, fontSize = 11.sp,
                )
            }
            Switch(
                checked = enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.25f),
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = Surface3,
                ),
            )
        }
    }
}
