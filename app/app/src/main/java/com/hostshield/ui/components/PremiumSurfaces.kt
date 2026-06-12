package com.hostshield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.ui.theme.Surface1
import com.hostshield.ui.theme.Surface2
import com.hostshield.ui.theme.Surface3
import com.hostshield.ui.theme.Teal
import com.hostshield.ui.theme.TextDim
import com.hostshield.ui.theme.TextPrimary
import com.hostshield.ui.theme.TextSecondary

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
                            .heightIn(min = 36.dp)
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss $title", tint = TextDim, modifier = Modifier.size(16.dp))
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
        modifier = modifier.fillMaxWidth(),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.heightIn(min = 40.dp),
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
                            color = Surface2,
                            modifier = Modifier.heightIn(min = 40.dp),
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
