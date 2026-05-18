package com.hostshield.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.theme.*
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin

// ── Brand Header ────────────────────────────────────────────

@Composable
fun BrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.semantics(mergeDescendants = true) {
            heading()
            contentDescription = "HostShield"
        }
    ) {
        Icon(Icons.Filled.Shield, null, tint = Teal, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "HostShield",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 0.sp
        )
    }
}

// ── Shield Orb ──────────────────────────────────────────────

@Composable
fun ShieldOrb(
    isEnabled: Boolean,
    isApplying: Boolean,
    blockedCount: Int,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "spinner"
    )

    val orbScale by animateFloatAsState(
        if (isApplying) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "scale"
    )
    val activeGlow by animateFloatAsState(
        if (isEnabled) 1f else 0f, tween(600), label = "activeGlow"
    )
    val accentColor by animateColorAsState(
        if (isEnabled) TealGlow else TextDim, tween(500), label = "accent"
    )

    val orbSizeDp = 164.dp
    val totalSizeDp = orbSizeDp + 48.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(totalSizeDp)
            .scale(orbScale)
            .testTag(HostShieldTestTags.Home.ShieldOrb)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = when {
                    isApplying -> "HostShield is applying protection"
                    isEnabled -> "HostShield protection is active. Tap to pause protection."
                    else -> "HostShield protection is off. Tap to activate protection."
                }
                stateDescription = when {
                    isApplying -> "Applying"
                    isEnabled -> "Active"
                    else -> "Inactive"
                }
                if (isApplying) disabled()
            }
            .clickable(enabled = !isApplying, role = Role.Button) { onToggle() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val orbRadius = orbSizeDp.toPx() / 2f

            // Ambient glow
            if (activeGlow > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TealGlow.copy(alpha = glowPulse * activeGlow * 0.35f),
                            TealGlow.copy(alpha = glowPulse * activeGlow * 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = orbRadius * 1.5f
                    ),
                    radius = orbRadius * 1.5f,
                    center = Offset(cx, cy)
                )
            }

            // Outer rotating ring
            rotate(ringRotation, pivot = Offset(cx, cy)) {
                val ringR = orbRadius + 8.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to accentColor.copy(alpha = 0.55f * activeGlow + 0.08f),
                        0.25f to accentColor.copy(alpha = 0.01f),
                        0.5f to accentColor.copy(alpha = 0.01f),
                        0.75f to accentColor.copy(alpha = 0.01f),
                        1f to accentColor.copy(alpha = 0.55f * activeGlow + 0.08f),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(cx - ringR, cy - ringR),
                    size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2)
                )
            }

            // Secondary counter-rotating ring
            rotate(-ringRotation * 0.6f, pivot = Offset(cx, cy)) {
                val ringR2 = orbRadius + 2.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to accentColor.copy(alpha = 0.2f * activeGlow + 0.04f),
                        0.5f to Color.Transparent,
                        1f to accentColor.copy(alpha = 0.2f * activeGlow + 0.04f),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 1.dp.toPx()),
                    topLeft = Offset(cx - ringR2, cy - ringR2),
                    size = androidx.compose.ui.geometry.Size(ringR2 * 2, ringR2 * 2)
                )
            }

            // Orb body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Surface3, Surface1.copy(alpha = 0.95f), Surface0),
                    center = Offset(cx, cy),
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = Offset(cx, cy)
            )

            // Orb border
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.35f),
                        Surface3.copy(alpha = 0.25f),
                        accentColor.copy(alpha = 0.12f)
                    ),
                    start = Offset(cx - orbRadius, cy - orbRadius),
                    end = Offset(cx + orbRadius, cy + orbRadius)
                ),
                radius = orbRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Spinner arc (while applying)
            if (isApplying) {
                val spinR = 28.dp.toPx()
                rotate(spinnerRotation, pivot = Offset(cx, cy)) {
                    drawArc(
                        color = Teal,
                        startAngle = 0f, sweepAngle = 100f, useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(cx - spinR, cy - spinR),
                        size = androidx.compose.ui.geometry.Size(spinR * 2, spinR * 2)
                    )
                }
            }

            // Orbiting particles
            if (isEnabled && !isApplying) {
                val particleAngles = floatArrayOf(0f, 72f, 144f, 216f, 288f)
                particleAngles.forEachIndexed { i, baseAngle ->
                    val angle = baseAngle + ringRotation * (0.3f + i * 0.1f)
                    val pRadius = orbRadius + 7.dp.toPx()
                    val rad = Math.toRadians(angle.toDouble())
                    val px = cx + (cos(rad) * pRadius).toFloat()
                    val py = cy + (sin(rad) * pRadius).toFloat()
                    val dotR = (1.5f + (i % 2) * 0.5f).dp.toPx()
                    drawCircle(
                        color = Teal.copy(alpha = 0.45f + (i % 3) * 0.15f),
                        radius = dotR,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Shield icon + count (overlaid on orb center)
        if (!isApplying) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(42.dp)
                )
                if (blockedCount > 0 && isEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatCompact(blockedCount),
                        color = accentColor.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }
            }
        }
    }
}

// ── Status Label ────────────────────────────────────────────

@Composable
fun StatusLabel(isEnabled: Boolean, isApplying: Boolean) {
    val color by animateColorAsState(
        targetValue = when {
            isApplying -> TextSecondary
            isEnabled -> Teal
            else -> TextDim
        },
        animationSpec = tween(400), label = "statusColor"
    )

    Text(
        text = when {
            isApplying -> "Applying"
            isEnabled -> "Protection active"
            else -> "Tap to activate"
        },
        style = MaterialTheme.typography.titleMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = when {
                isApplying -> "Applying"
                isEnabled -> "Active"
                else -> "Inactive"
            }
        }
    )
}

// ── Stat Tile ───────────────────────────────────────────────

@Composable
fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    glowColor: Color,
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .semantics { contentDescription = "$label, $value" }
                    .clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 0.sp
            )
        }
    }
}

// ── Mode Chip ───────────────────────────────────────────────

@Composable
fun ModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Teal.copy(alpha = 0.12f) else Surface2,
        animationSpec = tween(200), label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Teal.copy(alpha = 0.4f) else Surface3,
        animationSpec = tween(200), label = "chipBorder"
    )
    val contentColor = when {
        !enabled -> TextDim
        selected -> Teal
        else -> TextSecondary
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .semantics {
                contentDescription = "$label mode"
                stateDescription = when {
                    !enabled -> "Unavailable"
                    selected -> "Selected"
                    else -> "Not selected"
                }
                if (!enabled) disabled()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!enabled) {
                Spacer(Modifier.width(6.dp))
                Text("N/A", color = TextDim, fontSize = 10.sp)
            }
        }
    }
}

// ── Action Row ──────────────────────────────────────────────

@Composable
fun ActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "$label. $subtitle"
                stateDescription = if (enabled) "Available" else "Unavailable"
                if (!enabled) disabled()
            }
            .background(if (enabled) Color.Transparent else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (enabled) color else TextDim, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) TextPrimary else TextDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
    }
}

// ── Error Banner ────────────────────────────────────────────

@Composable
fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Error, null, tint = Red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(error, color = Red.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Dismiss error", tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Module Card (Protection Modules) ────────────────────────

@Composable
fun ModuleCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    status: String,
    detail: String,
    accent: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) accent.copy(alpha = 0.4f) else Surface3,
        animationSpec = tween(300), label = "moduleBorder"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.08f else 0f,
        animationSpec = tween(300), label = "moduleBg"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = bgAlpha),
                        Surface1.copy(alpha = 0.85f)
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .semantics {
                role = Role.Button
                contentDescription = "$title module. $status. $detail"
                stateDescription = if (isActive) "Active" else "Off"
            }
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                status,
                color = if (isActive) accent else TextDim,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(detail, color = TextDim, fontSize = 10.sp)
        }
    }
}

// ── Live Log Row ────────────────────────────────────────────

@Composable
fun LiveLogRow(entry: com.hostshield.data.model.DnsLogEntry) {
    val dotColor = if (entry.blocked) Red else Green
    val timeStr = remember(entry.timestamp) {
        try {
            java.time.Instant.ofEpochMilli(entry.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (_: Exception) { "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (entry.blocked) Red.copy(alpha = 0.04f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(color = dotColor, radius = size.minDimension / 2f)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.hostname,
            color = if (entry.blocked) Red.copy(alpha = 0.85f) else TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (entry.appLabel.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = entry.appLabel,
                color = TextDim,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(timeStr, color = TextDim, fontSize = 9.sp)
    }
}

// ── Feature Access Card ─────────────────────────────────────

@Composable
fun FeatureAccessCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    gradientEnd: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier
            .semantics { contentDescription = "$title. $subtitle" }
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.15f), gradientEnd.copy(alpha = 0.08f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}

// ── Feature Badge ───────────────────────────────────────────

@Composable
fun FeatureBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Glass Card ──────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderBrush: Brush = Brush.linearGradient(
        colors = listOf(
            Surface4.copy(alpha = 0.8f),
            Surface3.copy(alpha = 0.2f)
        )
    ),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Surface1.copy(alpha = 0.85f),
                        Surface0.copy(alpha = 0.9f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        content()
    }
}

// ── Helpers ─────────────────────────────────────────────────

fun formatNumber(n: Int): String =
    NumberFormat.getNumberInstance().format(n)

fun formatCompact(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 10_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> n.toString()
}

fun formatLastApply(ms: Long): String = try {
    java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
} catch (e: Exception) { "Unknown" }
