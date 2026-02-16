package com.hostshield.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.ui.theme.*
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin

// HostShield v1.0.0 — Premium Home Dashboard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToLogs: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onRequestVpnPermission: ((Boolean) -> Unit) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle VPN permission request
    LaunchedEffect(state.needsVpnPermission) {
        if (state.needsVpnPermission) {
            onRequestVpnPermission { granted ->
                viewModel.onVpnPermissionResult(granted)
            }
        }
    }

    // Show snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.dismissSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // Brand header
        BrandHeader()

        // Universal search
        var searchQuery by remember { mutableStateOf("") }
        var searchExpanded by remember { mutableStateOf(false) }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; searchExpanded = it.isNotBlank() },
            placeholder = { Text("Search domains, rules, apps...", color = TextDim, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = ""; searchExpanded = false }) {
                        Icon(Icons.Filled.Close, null, tint = TextDim, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            )
        )
        AnimatedVisibility(visible = searchExpanded && searchQuery.length >= 2) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Surface1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Surface(
                            onClick = { onNavigateToLogs(); searchExpanded = false },
                            shape = RoundedCornerShape(8.dp),
                            color = Surface2
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Dns, null, tint = Blue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Search \"$searchQuery\" in DNS Logs", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            onClick = { onNavigateToApps(); searchExpanded = false },
                            shape = RoundedCornerShape(8.dp),
                            color = Surface2
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Apps, null, tint = Mauve, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Search \"$searchQuery\" in App Activity", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Shield Orb — the centerpiece
        ShieldOrb(
            isEnabled = state.isEnabled,
            isApplying = state.isApplying,
            blockedCount = state.totalDomainsBlocked,
            onToggle = { viewModel.toggleBlocking() }
        )

        Spacer(Modifier.height(6.dp))

        // Status label
        StatusLabel(state.isEnabled, state.isApplying)

        // Progress message
        AnimatedVisibility(
            visible = state.isApplying && state.progressMessage.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = state.progressMessage,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center
            )
        }

        // Error banner
        state.errorMessage?.let { error ->
            ErrorBanner(error) { viewModel.dismissError() }
        }

        Spacer(Modifier.height(24.dp))

        // Stats grid
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Shield,
                    value = formatNumber(state.totalDomainsBlocked),
                    label = "Domains Blocked",
                    accent = Teal,
                    glowColor = TealGlow
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Block,
                    value = formatNumber(state.blockedToday),
                    label = "Blocked Today",
                    accent = Red,
                    glowColor = Red
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Dns,
                    value = formatNumber(state.totalQueriesToday),
                    label = "Queries Today",
                    accent = Blue,
                    glowColor = Blue
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CloudDownload,
                    value = state.enabledSources.toString(),
                    label = "Active Sources",
                    accent = Mauve,
                    glowColor = Mauve
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // DNS Log quick-access
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLogs() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(Blue.copy(alpha = 0.15f), Teal.copy(alpha = 0.1f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Dns, null, tint = Blue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DNS Request Log",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (state.totalQueriesToday > 0)
                                "${formatNumber(state.blockedToday)} blocked of ${formatNumber(state.totalQueriesToday)} queries today"
                            else "Tap to view all DNS queries",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // App Activity card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToApps() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(Mauve.copy(alpha = 0.15f), Flamingo.copy(alpha = 0.1f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Apps, null, tint = Mauve, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "App Activity",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "See which apps make the most DNS queries",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

        // Mode selector
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Teal.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tune, null, tint = Teal, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Blocking Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = "Root",
                            icon = Icons.Filled.AdminPanelSettings,
                            selected = state.blockMethod == com.hostshield.data.model.BlockMethod.ROOT_HOSTS,
                            enabled = state.isRootAvailable,
                            onClick = { viewModel.setBlockMethod(com.hostshield.data.model.BlockMethod.ROOT_HOSTS) }
                        )
                        ModeChip(
                            label = "VPN",
                            icon = Icons.Filled.VpnLock,
                            selected = state.blockMethod == com.hostshield.data.model.BlockMethod.VPN,
                            enabled = true,
                            onClick = { viewModel.setBlockMethod(com.hostshield.data.model.BlockMethod.VPN) }
                        )
                    }
                    if (state.lastApplyTime > 0L) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, null, tint = TextDim, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Applied ${formatLastApply(state.lastApplyTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Quick Actions
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Peach.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.FlashOn, null, tint = Peach, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Quick Actions",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionRow(
                        icon = Icons.Filled.Refresh,
                        label = "Update & Apply",
                        subtitle = "Download latest sources and apply",
                        color = Teal,
                        enabled = !state.isApplying,
                        onClick = { viewModel.applyBlocking() }
                    )
                    Spacer(Modifier.height(4.dp))
                    ActionRow(
                        icon = Icons.Filled.RestartAlt,
                        label = "Restore Default Hosts",
                        subtitle = "Remove all blocking rules",
                        color = TextSecondary,
                        enabled = !state.isApplying,
                        onClick = { viewModel.disableBlocking() }
                    )
                }
            }
        }

        // Root warning
        if (!state.isRootAvailable) {
            Spacer(Modifier.height(12.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                borderBrush = Brush.linearGradient(
                    colors = listOf(Yellow.copy(alpha = 0.3f), Yellow.copy(alpha = 0.05f))
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Yellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Root Not Detected", color = Yellow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Grant root permission or use VPN mode.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // Snackbar overlay
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = Surface2,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(12.dp)
        )
    }
    } // Box
}

// ── Brand Header ────────────────────────────────────────────

@Composable
private fun BrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Shield, null, tint = Teal, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "HostShield",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp
        )
    }
}

// ── Shield Orb ──────────────────────────────────────────────

@Composable
private fun ShieldOrb(
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
    val totalSizeDp = orbSizeDp + 48.dp // extra space for glow bleed + rings

    // Everything drawn on a single Canvas — no child Boxes, no blur, no square artifacts
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(totalSizeDp)
            .scale(orbScale)
            .clickable(enabled = !isApplying) { onToggle() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val orbRadius = orbSizeDp.toPx() / 2f

            // ── Ambient glow (soft radial gradient, no blur needed) ──
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

            // ── Outer rotating ring ──
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

            // ── Secondary counter-rotating ring ──
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

            // ── Orb body (radial gradient circle) ──
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Surface3, Surface1.copy(alpha = 0.95f), Surface0),
                    center = Offset(cx, cy),
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = Offset(cx, cy)
            )

            // ── Orb border ──
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

            // ── Spinner arc (while applying) ──
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

            // ── Orbiting particles ──
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

        // ── Shield icon + count (overlaid on orb center) ──
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
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ── Status Label ────────────────────────────────────────────

@Composable
private fun StatusLabel(isEnabled: Boolean, isApplying: Boolean) {
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
            isApplying -> "Applying..."
            isEnabled -> "Protection Active"
            else -> "Tap to Activate"
        },
        style = MaterialTheme.typography.titleMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp
    )
}

// ── Stat Tile ───────────────────────────────────────────────

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    glowColor: Color
) {
    GlassCard(modifier = modifier) {
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
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 0.2.sp
            )
        }
    }
}

// ── Mode Chip ───────────────────────────────────────────────

@Composable
private fun ModeChip(
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
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp))
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
private fun ActionRow(
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
            .clickable(enabled = enabled, onClick = onClick)
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
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
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
                Icon(Icons.Filled.Close, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
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
            .clip(RoundedCornerShape(16.dp))
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
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        content()
    }
}

// ── Helpers ─────────────────────────────────────────────────

private fun formatNumber(n: Int): String =
    NumberFormat.getNumberInstance().format(n)

private fun formatCompact(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 10_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> n.toString()
}

private fun formatLastApply(ms: Long): String = try {
    java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
} catch (e: Exception) { "Unknown" }
