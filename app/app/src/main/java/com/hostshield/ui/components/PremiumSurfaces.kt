package com.hostshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.R
import com.hostshield.ui.theme.Surface1
import com.hostshield.ui.theme.Surface2
import com.hostshield.ui.theme.Surface3
import com.hostshield.ui.theme.Teal
import com.hostshield.ui.theme.TextDim
import com.hostshield.ui.theme.TextPrimary
import com.hostshield.ui.theme.TextSecondary

data class HostShieldSegmentOption<T>(
    val value: T,
    val label: String,
    val accent: Color,
    val icon: ImageVector? = null,
)

@Composable
fun HostShieldBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 8.dp,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val backLabel = stringResource(R.string.action_back)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = backLabel
                },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        HostShieldScreenHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
            actions = actions,
        )
    }
}

@Composable
fun HostShieldScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (subtitle.isNullOrBlank()) title else "$title. $subtitle"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(Modifier.width(6.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (actions != null) {
            Spacer(Modifier.width(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun HostShieldActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val containerColor = when {
        !enabled -> Surface2.copy(alpha = 0.30f)
        selected -> accent.copy(alpha = 0.16f)
        else -> Surface2.copy(alpha = 0.82f)
    }
    val borderColor = when {
        !enabled -> Surface3.copy(alpha = 0.26f)
        selected -> accent.copy(alpha = 0.30f)
        else -> Surface3.copy(alpha = 0.48f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        contentColor = if (enabled) accent else TextDim.copy(alpha = 0.55f),
        modifier = modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (selected) stateDescription = "Selected"
                if (!enabled) disabled()
            },
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun HostShieldFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    semanticsLabel: String = label,
) {
    val bgColor = if (selected) accent.copy(alpha = 0.14f) else Surface2.copy(alpha = 0.72f)
    val borderColor = if (selected) accent.copy(alpha = 0.28f) else Surface3.copy(alpha = 0.42f)
    val animatedBg = animateColorAsState(bgColor, tween(160), label = "filterBg").value
    val animatedBorder = animateColorAsState(borderColor, tween(160), label = "filterBorder").value
    val contentColor = when {
        !enabled -> TextDim.copy(alpha = 0.55f)
        selected -> accent
        else -> TextSecondary
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = animatedBg,
        border = BorderStroke(1.dp, animatedBorder),
        modifier = modifier
            .heightIn(min = 44.dp)
            .semantics {
                role = Role.Tab
                contentDescription = semanticsLabel
                stateDescription = when {
                    !enabled -> "Disabled"
                    selected -> "Selected"
                    else -> "Not selected"
                }
                if (!enabled) disabled()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leadingIcon?.let { icon ->
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HostShieldInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingChevron: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) accent.copy(alpha = 0.11f) else Surface2.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.16f) else Surface3.copy(alpha = 0.30f)),
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = if (enabled) accent else TextDim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                color = if (enabled) accent else TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingChevron) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (enabled) accent else TextDim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun <T> HostShieldSegmentedTabs(
    options: List<HostShieldSegmentOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    semanticsLabel: String = "Segmented control",
) {
    if (options.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Surface1.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.58f)),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                Surface(
                    onClick = { onSelected(option.value) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "$semanticsLabel: ${option.label}${if (isSelected) ", selected" else ""}"
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) option.accent.copy(alpha = 0.16f) else Color.Transparent,
                    border = if (isSelected) {
                        BorderStroke(1.dp, option.accent.copy(alpha = 0.24f))
                    } else {
                        BorderStroke(1.dp, Color.Transparent)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        option.icon?.let { icon ->
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) option.accent else TextSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            option.label,
                            color = if (isSelected) option.accent else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HostShieldPanelHeader(
    icon: ImageVector,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (subtitle.isNullOrBlank()) title else "$title. $subtitle"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.12f)),
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun HostShieldStatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    announce: Boolean = true,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (message.isBlank()) title else "$title. $message"
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.12f),
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
                if (message.isNotBlank()) {
                    Text(
                        message,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        onClick = onAction,
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = actionLabel
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(actionLabel, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            if (onDismiss != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Dismiss $title"
                        },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun HostShieldCompactState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $message"
            },
        shape = RoundedCornerShape(10.dp),
        color = Surface2.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.10f)) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.78f), modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                Text(message, color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun HostShieldLoadingState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $message"
                liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(10.dp),
        color = Surface2.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(message, color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HostShieldEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $message"
            },
        shape = RoundedCornerShape(12.dp),
        color = Surface1.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.10f)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.88f), modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(5.dp))
            Text(
                message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = primaryActionLabel
                            },
                    ) {
                        Text(
                            primaryActionLabel,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        Surface(
                            onClick = onSecondaryAction,
                            shape = RoundedCornerShape(8.dp),
                            color = Surface2.copy(alpha = 0.86f),
                            border = BorderStroke(1.dp, Surface3.copy(alpha = 0.48f)),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = secondaryActionLabel
                                },
                        ) {
                            Text(
                                secondaryActionLabel,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HostShieldTrustStrip(
    leading: String,
    message: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                leading,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 54.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(message, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun HostShieldMetricTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Surface2.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.55f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextDim, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
        }
    }
}
