package com.hostshield.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Material 3 AMOLED theme

internal data class HostShieldPalette(
    val black: Color,
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val teal: Color,
    val tealBright: Color,
    val tealDim: Color,
    val tealGlow: Color,
    val mauve: Color,
    val mauveDim: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
    val blue: Color,
    val peach: Color,
    val flamingo: Color,
    val sky: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color
)

internal val StandardHostShieldPalette = HostShieldPalette(
    black = Color(0xFF000000),
    surface0 = Color(0xFF08080D),
    surface1 = Color(0xFF0F0F17),
    surface2 = Color(0xFF161621),
    surface3 = Color(0xFF1E1E2E),
    surface4 = Color(0xFF262638),
    teal = Color(0xFF94E2D5),
    tealBright = Color(0xFFB4F5E8),
    tealDim = Color(0xFF5BA89D),
    tealGlow = Color(0xFF00D4AA),
    mauve = Color(0xFFCBA6F7),
    mauveDim = Color(0xFF9B78C4),
    green = Color(0xFFA6E3A1),
    red = Color(0xFFF38BA8),
    yellow = Color(0xFFF9E2AF),
    blue = Color(0xFF89B4FA),
    peach = Color(0xFFFAB387),
    flamingo = Color(0xFFF2CDCD),
    sky = Color(0xFF89DCEB),
    textPrimary = Color(0xFFE2E8F8),
    textSecondary = Color(0xFF8B92A8),
    textDim = Color(0xFF4A4E62)
)

internal val HighContrastAmoledPalette = HostShieldPalette(
    black = Color(0xFF000000),
    surface0 = Color(0xFF000000),
    surface1 = Color(0xFF050505),
    surface2 = Color(0xFF0C0F14),
    surface3 = Color(0xFF171D26),
    surface4 = Color(0xFF27313D),
    teal = Color(0xFF7FFFEA),
    tealBright = Color(0xFFC8FFF6),
    tealDim = Color(0xFF5DE6D6),
    tealGlow = Color(0xFF00F5D4),
    mauve = Color(0xFFDDB8FF),
    mauveDim = Color(0xFFC093F2),
    green = Color(0xFFB8FFB0),
    red = Color(0xFFFF7AA8),
    yellow = Color(0xFFFFE27A),
    blue = Color(0xFF9ED0FF),
    peach = Color(0xFFFFC08A),
    flamingo = Color(0xFFFFD7E8),
    sky = Color(0xFF99EEFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD9E2F2),
    textDim = Color(0xFFAAB6C8)
)

internal val LocalHostShieldPalette = staticCompositionLocalOf { StandardHostShieldPalette }

// Core palette. These names are used throughout the UI; HostShieldTheme now
// resolves them per composition instead of mutating global color state.
val Black: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.black
val Surface0: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.surface0
val Surface1: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.surface1
val Surface2: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.surface2
val Surface3: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.surface3
val Surface4: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.surface4

// Accent colors
val Teal: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.teal
val TealBright: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.tealBright
val TealDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.tealDim
val TealGlow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.tealGlow
val Mauve: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.mauve
val MauveDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.mauveDim
val Green: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.green
val Red: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.red
val Yellow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.yellow
val Blue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.blue
val Peach: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.peach
val Flamingo: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.flamingo
val Sky: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.sky

// Text hierarchy
val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.textPrimary
val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.textSecondary
val TextDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHostShieldPalette.current.textDim

val LocalHighContrastAmoled = staticCompositionLocalOf { false }

internal fun hostShieldPalette(
    highContrastAmoled: Boolean,
    accentColor: String = "teal"
): HostShieldPalette {
    val base = if (highContrastAmoled) HighContrastAmoledPalette else StandardHostShieldPalette
    return base.withAccent(accentColor)
}

private fun HostShieldPalette.withAccent(accentColor: String): HostShieldPalette = when (accentColor.lowercase()) {
    "blue" -> copy(
        teal = blue,
        tealBright = sky,
        tealDim = Color(0xFF5F86D8),
        tealGlow = blue,
    )
    "purple" -> copy(
        teal = mauve,
        tealBright = flamingo,
        tealDim = mauveDim,
        tealGlow = mauve,
    )
    "green" -> copy(
        teal = green,
        tealBright = Color(0xFFD5FFD0),
        tealDim = Color(0xFF6FAF67),
        tealGlow = green,
    )
    "pink" -> copy(
        teal = red,
        tealBright = Color(0xFFFFC4D6),
        tealDim = Color(0xFFC65D7D),
        tealGlow = red,
    )
    "peach" -> copy(
        teal = peach,
        tealBright = Color(0xFFFFD6B0),
        tealDim = Color(0xFFC67E52),
        tealGlow = peach,
    )
    else -> this
}

internal fun hostShieldColorScheme(
    palette: HostShieldPalette,
    highContrastAmoled: Boolean = false
) = darkColorScheme(
    primary = palette.teal,
    onPrimary = palette.black,
    primaryContainer = palette.tealDim.copy(alpha = if (highContrastAmoled) 0.28f else 0.15f),
    onPrimaryContainer = palette.teal,
    secondary = palette.mauve,
    onSecondary = palette.black,
    secondaryContainer = palette.mauveDim.copy(alpha = if (highContrastAmoled) 0.28f else 0.15f),
    onSecondaryContainer = palette.mauve,
    tertiary = palette.peach,
    onTertiary = palette.black,
    error = palette.red,
    onError = palette.black,
    errorContainer = palette.red.copy(alpha = if (highContrastAmoled) 0.28f else 0.15f),
    onErrorContainer = palette.red,
    background = palette.black,
    onBackground = palette.textPrimary,
    surface = palette.surface0,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surface2,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.surface4,
    outlineVariant = palette.surface3,
    inverseSurface = palette.textPrimary,
    inverseOnSurface = palette.black,
    surfaceTint = palette.teal
)

private val DefaultHostShieldTypography = Typography()

val HostShieldTypography = Typography(
    headlineLarge = DefaultHostShieldTypography.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = DefaultHostShieldTypography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = DefaultHostShieldTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = DefaultHostShieldTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = DefaultHostShieldTypography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = DefaultHostShieldTypography.bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = DefaultHostShieldTypography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = DefaultHostShieldTypography.bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = DefaultHostShieldTypography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = DefaultHostShieldTypography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = DefaultHostShieldTypography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.sp,
    ),
)

private val HostShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun HostShieldTheme(
    highContrastAmoled: Boolean = false,
    accentColor: String = "teal",
    content: @Composable () -> Unit
) {
    val palette = hostShieldPalette(highContrastAmoled, accentColor)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT < 35) {
                window.statusBarColor = palette.black.toArgb()
                window.navigationBarColor = palette.black.toArgb()
            }
        }
    }

    CompositionLocalProvider(
        LocalHighContrastAmoled provides highContrastAmoled,
        LocalHostShieldPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = hostShieldColorScheme(palette, highContrastAmoled),
            typography = HostShieldTypography,
            shapes = HostShieldShapes,
            content = content
        )
    }
}
