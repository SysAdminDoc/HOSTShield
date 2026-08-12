package com.hostshield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.theme.Blue
import com.hostshield.ui.theme.Surface2
import com.hostshield.ui.theme.Surface3
import com.hostshield.ui.theme.Teal
import com.hostshield.ui.theme.TextDim
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostShieldSavedFilterBar(
    screen: String,
    savedFilters: List<SavedDenseListFilter>,
    canSaveCurrent: Boolean,
    onSaveCurrent: () -> Unit,
    onApplyFilter: (SavedDenseListFilter) -> Unit,
    onClearSavedFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!canSaveCurrent && savedFilters.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HostShieldInlineAction(
            label = "Save filter",
            icon = Icons.Filled.Save,
            enabled = canSaveCurrent,
            onClick = onSaveCurrent,
            accent = Teal,
            modifier = Modifier.testTag(HostShieldTestTags.DenseList.SaveFilter),
        )
        savedFilters.forEach { filter ->
            HostShieldFilterChip(
                label = filter.label,
                selected = false,
                onClick = { onApplyFilter(filter) },
                accent = Blue,
                leadingIcon = Icons.Filled.Bookmark,
                semanticsLabel = "Apply saved filter ${filter.label}",
                modifier = Modifier.testTag(HostShieldTestTags.DenseList.savedFilter(screen, filter.label)),
            )
        }
        if (savedFilters.isNotEmpty()) {
            HostShieldInlineAction(
                label = "Clear saved",
                icon = Icons.Filled.ClearAll,
                onClick = onClearSavedFilters,
                accent = TextDim,
                modifier = Modifier.testTag(HostShieldTestTags.DenseList.ClearSavedFilters),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostShieldDenseListJumpBar(
    screen: String,
    label: String,
    totalItems: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    minItems: Int = 20,
) {
    if (totalItems < minItems) return
    val scope = rememberCoroutineScope()
    val lastIndex = (totalItems - 1).coerceAtLeast(0)
    val middleIndex = (totalItems / 2).coerceIn(0, lastIndex)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DenseJumpAction(
            label = "Top",
            contentDescription = "Jump to top of $label",
            icon = Icons.Filled.KeyboardArrowUp,
            testTag = HostShieldTestTags.DenseList.jump(screen, "top"),
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
        )
        DenseJumpAction(
            label = "Middle",
            contentDescription = "Jump to middle of $label",
            icon = Icons.Filled.UnfoldMore,
            testTag = HostShieldTestTags.DenseList.jump(screen, "middle"),
            onClick = { scope.launch { listState.animateScrollToItem(middleIndex) } },
        )
        DenseJumpAction(
            label = "End",
            contentDescription = "Jump to end of $label",
            icon = Icons.Filled.KeyboardArrowDown,
            testTag = HostShieldTestTags.DenseList.jump(screen, "end"),
            onClick = { scope.launch { listState.animateScrollToItem(lastIndex) } },
        )
    }
}

@Composable
private fun DenseJumpAction(
    label: String,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Surface2.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.42f)),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = Teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
