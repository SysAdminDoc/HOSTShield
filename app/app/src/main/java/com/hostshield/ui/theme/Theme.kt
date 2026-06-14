package com.hostshield.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

// Core palette. These names are used throughout the UI; the active palette is
// switched by HostShieldTheme so existing screens inherit the selected variant.
var Black by mutableStateOf(StandardHostShieldPalette.black)
    private set
var Surface0 by mutableStateOf(StandardHostShieldPalette.surface0)
    private set
var Surface1 by mutableStateOf(StandardHostShieldPalette.surface1)
    private set
var Surface2 by mutableStateOf(StandardHostShieldPalette.surface2)
    private set
var Surface3 by mutableStateOf(StandardHostShieldPalette.surface3)
    private set
var Surface4 by mutableStateOf(StandardHostShieldPalette.surface4)
    private set

// Accent colors
var Teal by mutableStateOf(StandardHostShieldPalette.teal)
    private set
var TealBright by mutableStateOf(StandardHostShieldPalette.tealBright)
    private set
var TealDim by mutableStateOf(StandardHostShieldPalette.tealDim)
    private set
var TealGlow by mutableStateOf(StandardHostShieldPalette.tealGlow)
    private set
var Mauve by mutableStateOf(StandardHostShieldPalette.mauve)
    private set
var MauveDim by mutableStateOf(StandardHostShieldPalette.mauveDim)
    private set
var Green by mutableStateOf(StandardHostShieldPalette.green)
    private set
var Red by mutableStateOf(StandardHostShieldPalette.red)
    private set
var Yellow by mutableStateOf(StandardHostShieldPalette.yellow)
    private set
var Blue by mutableStateOf(StandardHostShieldPalette.blue)
    private set
var Peach by mutableStateOf(StandardHostShieldPalette.peach)
    private set
var Flamingo by mutableStateOf(StandardHostShieldPalette.flamingo)
    private set
var Sky by mutableStateOf(StandardHostShieldPalette.sky)
    private set

// Text hierarchy
var TextPrimary by mutableStateOf(StandardHostShieldPalette.textPrimary)
    private set
var TextSecondary by mutableStateOf(StandardHostShieldPalette.textSecondary)
    private set
var TextDim by mutableStateOf(StandardHostShieldPalette.textDim)
    private set

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

private fun applyHostShieldPalette(palette: HostShieldPalette) {
    Black = palette.black
    Surface0 = palette.surface0
    Surface1 = palette.surface1
    Surface2 = palette.surface2
    Surface3 = palette.surface3
    Surface4 = palette.surface4
    Teal = palette.teal
    TealBright = palette.tealBright
    TealDim = palette.tealDim
    TealGlow = palette.tealGlow
    Mauve = palette.mauve
    MauveDim = palette.mauveDim
    Green = palette.green
    Red = palette.red
    Yellow = palette.yellow
    Blue = palette.blue
    Peach = palette.peach
    Flamingo = palette.flamingo
    Sky = palette.sky
    TextPrimary = palette.textPrimary
    TextSecondary = palette.textSecondary
    TextDim = palette.textDim
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
    SideEffect {
        applyHostShieldPalette(palette)
    }
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
                window.statusBarColor = Black.toArgb()
                window.navigationBarColor = Black.toArgb()
            }
        }
    }

    CompositionLocalProvider(LocalHighContrastAmoled provides highContrastAmoled) {
        MaterialTheme(
            colorScheme = hostShieldColorScheme(palette, highContrastAmoled),
            typography = HostShieldTypography,
            shapes = HostShieldShapes,
            content = content
        )
    }
}
