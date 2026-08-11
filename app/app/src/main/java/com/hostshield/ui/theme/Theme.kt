package com.hostshield.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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

internal fun HostShieldPalette.withAccent(accentColor: String): HostShieldPalette = when (accentColor.lowercase()) {
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

internal val LightHostShieldPalette = HostShieldPalette(
    black = Color(0xFFF5F5F8),
    surface0 = Color(0xFFFFFFFF),
    surface1 = Color(0xFFF5F5F8),
    surface2 = Color(0xFFEBEBF0),
    surface3 = Color(0xFFDCDCE5),
    surface4 = Color(0xFFCCCCD8),
    teal = Color(0xFF00897B),
    tealBright = Color(0xFF00695C),
    tealDim = Color(0xFF4DB6AC),
    tealGlow = Color(0xFF009688),
    mauve = Color(0xFF7E57C2),
    mauveDim = Color(0xFFB39DDB),
    green = Color(0xFF388E3C),
    red = Color(0xFFD32F2F),
    yellow = Color(0xFFF9A825),
    blue = Color(0xFF1976D2),
    peach = Color(0xFFE64A19),
    flamingo = Color(0xFFD81B60),
    sky = Color(0xFF0288D1),
    textPrimary = Color(0xFF1C1B1F),
    textSecondary = Color(0xFF49454F),
    textDim = Color(0xFF79747E)
)

internal fun paletteFromDynamicScheme(scheme: ColorScheme): HostShieldPalette = HostShieldPalette(
    black = scheme.background,
    surface0 = scheme.surface,
    surface1 = scheme.surfaceContainerLow,
    surface2 = scheme.surfaceContainer,
    surface3 = scheme.surfaceContainerHigh,
    surface4 = scheme.surfaceContainerHighest,
    teal = scheme.primary,
    tealBright = scheme.primaryContainer,
    tealDim = scheme.primary.copy(alpha = 0.6f),
    tealGlow = scheme.primary,
    mauve = scheme.secondary,
    mauveDim = scheme.secondary.copy(alpha = 0.6f),
    // Semantic colors must stay visually distinct even under Material You:
    // mapping green→primary and yellow/peach→tertiary made "Allowed" identical
    // to the accent and yellow identical to peach. Derive them from fixed
    // semantic seeds harmonized toward the wallpaper primary so status meaning
    // survives while still matching the dynamic theme.
    green = lerp(Color(0xFF43A047), scheme.primary, 0.25f),
    red = scheme.error,
    yellow = lerp(Color(0xFFF9A825), scheme.primary, 0.20f),
    blue = scheme.secondary,
    peach = lerp(Color(0xFFFF8A65), scheme.primary, 0.20f),
    flamingo = lerp(Color(0xFFEC407A), scheme.primary, 0.20f),
    sky = scheme.secondary.copy(alpha = 0.8f),
    textPrimary = scheme.onBackground,
    textSecondary = scheme.onSurfaceVariant,
    textDim = scheme.outline
)

internal fun hostShieldLightColorScheme(palette: HostShieldPalette) = lightColorScheme(
    primary = palette.teal,
    onPrimary = Color.White,
    primaryContainer = palette.tealDim.copy(alpha = 0.12f),
    onPrimaryContainer = palette.teal,
    secondary = palette.mauve,
    onSecondary = Color.White,
    secondaryContainer = palette.mauveDim.copy(alpha = 0.12f),
    onSecondaryContainer = palette.mauve,
    tertiary = palette.peach,
    onTertiary = Color.White,
    error = palette.red,
    onError = Color.White,
    errorContainer = palette.red.copy(alpha = 0.08f),
    onErrorContainer = palette.red,
    background = palette.black,
    onBackground = palette.textPrimary,
    surface = palette.surface0,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surface2,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.surface4,
    outlineVariant = palette.surface3,
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    surfaceTint = palette.teal
)

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
    dynamicColor: Boolean = false,
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemDark
        else -> true
    }

    val view = LocalView.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && isDark -> dynamicDarkColorScheme(LocalContext.current)
        useDynamic && !isDark -> dynamicLightColorScheme(LocalContext.current)
        isDark -> hostShieldColorScheme(
            hostShieldPalette(highContrastAmoled, accentColor), highContrastAmoled
        )
        else -> hostShieldLightColorScheme(LightHostShieldPalette.withAccent(accentColor))
    }

    val palette = if (useDynamic) {
        paletteFromDynamicScheme(colorScheme)
    } else if (isDark) {
        hostShieldPalette(highContrastAmoled, accentColor)
    } else {
        LightHostShieldPalette.withAccent(accentColor)
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 35) {
                window.statusBarColor = palette.black.toArgb()
                window.navigationBarColor = palette.black.toArgb()
            }
            // The XML windowBackground is a fixed black and there is no values-night
            // split (the in-app theme setting wins over the system one anyway), so in
            // light mode the window layer stayed black for the app's lifetime — a
            // black flash on IME resize and in overscroll stretch areas.
            window.setBackgroundDrawable(ColorDrawable(palette.black.toArgb()))
        }
    }

    CompositionLocalProvider(
        LocalHighContrastAmoled provides highContrastAmoled,
        LocalHostShieldPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HostShieldTypography,
            shapes = HostShieldShapes,
            content = content
        )
    }
}
