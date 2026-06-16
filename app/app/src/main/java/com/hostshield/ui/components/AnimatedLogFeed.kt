package com.hostshield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.ui.theme.Green
import com.hostshield.ui.theme.Red
import com.hostshield.ui.theme.Teal
import com.hostshield.ui.theme.TextDim
import com.hostshield.ui.theme.TextSecondary

/**
 * Animated real-time DNS log feed (Roadmap #31).
 *
 * Provides smooth entry/exit animations for live DNS query entries,
 * including slide-in, fade, and pulsing status dots for blocked queries.
 */

// ── Main Feed ────────────────────────────────────────────────────

/**
 * Animated live log feed with entry animations.
 * New entries slide in from top with a fade effect.
 * Blocked entries pulse their status dot.
 *
 * @param entries DNS log entries, newest first (max ~10 recommended)
 * @param modifier Layout modifier
 * @param maxVisible Maximum entries to display
 */
@Composable
fun AnimatedLogFeed(
    entries: List<DnsLogEntry>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 8
) {
    val visibleEntries = entries.take(maxVisible)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for ((index, entry) in visibleEntries.withIndex()) {
            key(entry.id) {
                AnimatedLogEntry(
                    entry = entry,
                    delayMs = index * 30 // stagger animation
                )
            }
        }
    }
}

// ── Animated Entry ───────────────────────────────────────────────

@Composable
private fun AnimatedLogEntry(
    entry: DnsLogEntry,
    delayMs: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(animationSpec = tween(200)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(150)) + shrinkVertically()
    ) {
        AnimatedLogRow(entry = entry)
    }
}

// ── Animated Row ─────────────────────────────────────────────────

@Composable
fun AnimatedLogRow(
    entry: DnsLogEntry,
    modifier: Modifier = Modifier
) {
    val dotColor = if (entry.blocked) Red else Green

    // Pulsing dot for blocked entries
    val pulseAlpha: Float = if (entry.blocked) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        alpha
    } else {
        1f
    }

    // Slide-in background highlight for new entries
    val highlightAlpha = remember { Animatable(0.15f) }
    LaunchedEffect(entry.id) {
        highlightAlpha.animateTo(
            targetValue = if (entry.blocked) 0.04f else 0f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }

    val bgColor by animateColorAsState(
        targetValue = if (entry.blocked) Red.copy(alpha = highlightAlpha.value)
        else Teal.copy(alpha = highlightAlpha.value),
        animationSpec = tween(400),
        label = "bgColor"
    )

    val timeStr = remember(entry.timestamp) {
        try {
            java.time.Instant.ofEpochMilli(entry.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (_: Exception) { "" }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated status dot
        Canvas(modifier = Modifier.size(6.dp).alpha(pulseAlpha)) {
            drawCircle(color = dotColor, radius = size.minDimension / 2f)
        }
        Spacer(Modifier.width(8.dp))

        // Domain name
        Text(
            text = entry.hostname,
            color = if (entry.blocked) Red.copy(alpha = 0.85f) else TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis
        )

        // App label
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

        // Query type badge
        if (entry.queryType.isNotEmpty() && entry.queryType != "A") {
            Text(
                text = entry.queryType,
                color = Teal.copy(alpha = 0.6f),
                fontSize = 8.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Teal.copy(alpha = 0.08f))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        // Timestamp
        Text(timeStr, color = TextDim, fontSize = 9.sp)
    }
}

// ── Live Activity Indicator ──────────────────────────────────────

/**
 * Animated dot indicator showing live query activity.
 * Pulses when queries are flowing, static when idle.
 */
@Composable
fun LiveActivityIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveScale"
    )

    val dotAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "dotAlpha"
    )
    val activeColor = Green
    val inactiveColor = TextDim

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .size(8.dp)
                .alpha(dotAlpha)
        ) {
            val r = if (isActive) size.minDimension / 2f * scale else size.minDimension / 2f * 0.6f
            // Outer glow
            if (isActive) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.2f),
                    radius = size.minDimension / 2f
                )
            }
            // Inner dot
            drawCircle(
                color = if (isActive) activeColor else inactiveColor,
                radius = r
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isActive) "Live" else "Paused",
            color = if (isActive) activeColor.copy(alpha = 0.8f) else inactiveColor,
            fontSize = 10.sp
        )
    }
}

// ── Query Rate Sparkline ─────────────────────────────────────────

/**
 * Mini sparkline showing recent query rate (queries per second over last N intervals).
 * Animates new data points sliding in from the right.
 */
@Composable
fun QueryRateSparkline(
    rates: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Teal,
    maxPoints: Int = 30
) {
    val displayRates = rates.takeLast(maxPoints)
    if (displayRates.isEmpty()) return

    val maxRate = (displayRates.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val w = size.width
        val h = size.height
        val step = w / (maxPoints - 1).coerceAtLeast(1)
        val startX = w - (displayRates.size - 1) * step

        // Draw line segments
        for (i in 1 until displayRates.size) {
            val x0 = startX + (i - 1) * step
            val x1 = startX + i * step
            val y0 = h - (displayRates[i - 1] / maxRate) * h * 0.8f
            val y1 = h - (displayRates[i] / maxRate) * h * 0.8f

            drawLine(
                color = lineColor.copy(alpha = 0.6f),
                start = androidx.compose.ui.geometry.Offset(x0, y0),
                end = androidx.compose.ui.geometry.Offset(x1, y1),
                strokeWidth = 1.5f.dp.toPx()
            )
        }

        // Draw dots at data points
        for (i in displayRates.indices) {
            val x = startX + i * step
            val y = h - (displayRates[i] / maxRate) * h * 0.8f
            drawCircle(
                color = lineColor,
                radius = 1.5f.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
