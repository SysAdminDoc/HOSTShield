package com.hostshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.hostshield.R

private val Teal = Color(0xFF94E2D5)
private val DarkTeal = Color(0xFF74C7B5)
private val InactiveGray = Color(0xFF6C7086)
private val DangerRed = Color(0xFFE06C75)

/**
 * Main shield Lottie animation composable.
 *
 * @param modifier Modifier for layout customization.
 * @param isActive Controls playback: plays continuously when true, pauses at first frame when false.
 * @param speed Playback speed multiplier.
 */
@Composable
fun ShieldAnimation(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    speed: Float = 1f,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.shield_animation)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isActive,
        speed = speed,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false,
    )

    LottieAnimation(
        composition = composition,
        progress = { if (isActive) progress else 0f },
        modifier = modifier,
    )
}

/**
 * Shield animation with a status overlay showing the blocked count and
 * a red tint when protection is inactive.
 *
 * @param modifier Modifier for layout customization.
 * @param isProtecting Whether the shield is actively protecting.
 * @param blockedCount Number of blocked threats to display.
 */
@Composable
fun ShieldStatusIndicator(
    modifier: Modifier = Modifier,
    isProtecting: Boolean = true,
    blockedCount: Int = 0,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.shield_animation)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isProtecting,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false,
    )

    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = if (!isProtecting) {
                android.graphics.PorterDuffColorFilter(
                    DangerRed.copy(alpha = 0.6f).toArgb(),
                    android.graphics.PorterDuff.Mode.SRC_ATOP
                )
            } else {
                null
            },
            keyPath = arrayOf("**")
        )
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { if (isProtecting) progress else 0f },
            modifier = Modifier.fillMaxSize(),
            dynamicProperties = dynamicProperties,
        )

        if (blockedCount > 0) {
            Text(
                text = formatBlockedCount(blockedCount),
                color = if (isProtecting) Teal else DangerRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * Interactive shield that can be tapped to toggle protection on/off.
 * Features a scale pulse on click and smooth color transitions.
 *
 * @param modifier Modifier for layout customization.
 * @param isActive Whether the shield is currently active.
 * @param onToggle Callback invoked when the shield is tapped.
 */
@Composable
fun AnimatedShieldToggle(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onToggle: () -> Unit = {},
) {
    var isTapped by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isTapped) 1.15f else 1f,
        animationSpec = keyframes {
            durationMillis = 300
            1.15f at 100
            1f at 300
        },
        finishedListener = { isTapped = false },
        label = "shieldToggleScale",
    )

    val tintColor by animateColorAsState(
        targetValue = if (isActive) Teal else InactiveGray,
        animationSpec = tween(durationMillis = 400),
        label = "shieldToggleColor",
    )

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.shield_animation)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isActive,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false,
    )

    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = android.graphics.PorterDuffColorFilter(
                tintColor.toArgb(),
                android.graphics.PorterDuff.Mode.SRC_ATOP
            ),
            keyPath = arrayOf("**")
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                isTapped = true
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { if (isActive) progress else 0f },
            modifier = Modifier.fillMaxSize(),
            dynamicProperties = dynamicProperties,
        )
    }
}

private fun formatBlockedCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M+"
        count >= 1_000 -> "${count / 1_000}K+"
        else -> count.toString()
    }
}

// --- Previews ---

@Preview(
    name = "Shield Active",
    showBackground = true,
    backgroundColor = 0xFF1E1E2E,
)
@Composable
private fun ShieldAnimationActivePreview() {
    ShieldAnimation(
        modifier = Modifier.size(200.dp),
        isActive = true,
    )
}

@Preview(
    name = "Shield Inactive",
    showBackground = true,
    backgroundColor = 0xFF1E1E2E,
)
@Composable
private fun ShieldAnimationInactivePreview() {
    ShieldAnimation(
        modifier = Modifier.size(200.dp),
        isActive = false,
    )
}

@Preview(
    name = "Status Protecting",
    showBackground = true,
    backgroundColor = 0xFF1E1E2E,
)
@Composable
private fun ShieldStatusProtectingPreview() {
    ShieldStatusIndicator(
        modifier = Modifier.size(200.dp),
        isProtecting = true,
        blockedCount = 1234,
    )
}

@Preview(
    name = "Status Not Protecting",
    showBackground = true,
    backgroundColor = 0xFF1E1E2E,
)
@Composable
private fun ShieldStatusNotProtectingPreview() {
    ShieldStatusIndicator(
        modifier = Modifier.size(200.dp),
        isProtecting = false,
        blockedCount = 42,
    )
}

@Preview(
    name = "Toggle Active",
    showBackground = true,
    backgroundColor = 0xFF1E1E2E,
)
@Composable
private fun AnimatedShieldToggleActivePreview() {
    AnimatedShieldToggle(
        modifier = Modifier.size(200.dp),
        isActive = true,
        onToggle = {},
    )
}

@Preview(
    name = "Toggle Inactive",
    showBackground = true,
    backgroundColor = 0xFF1E1E2E,
)
@Composable
private fun AnimatedShieldToggleInactivePreview() {
    AnimatedShieldToggle(
        modifier = Modifier.size(200.dp),
        isActive = false,
        onToggle = {},
    )
}
