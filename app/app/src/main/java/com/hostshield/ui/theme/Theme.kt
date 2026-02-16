package com.hostshield.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// HostShield v1.0.0 — Premium AMOLED Theme

// Core palette
val Black = Color(0xFF000000)
val Surface0 = Color(0xFF08080D)
val Surface1 = Color(0xFF0F0F17)
val Surface2 = Color(0xFF161621)
val Surface3 = Color(0xFF1E1E2E)
val Surface4 = Color(0xFF262638)

// Accent colors
val Teal = Color(0xFF94E2D5)
val TealBright = Color(0xFFB4F5E8)
val TealDim = Color(0xFF5BA89D)
val TealGlow = Color(0xFF00D4AA)
val Mauve = Color(0xFFCBA6F7)
val MauveDim = Color(0xFF9B78C4)
val Green = Color(0xFFA6E3A1)
val Red = Color(0xFFF38BA8)
val Yellow = Color(0xFFF9E2AF)
val Blue = Color(0xFF89B4FA)
val Peach = Color(0xFFFAB387)
val Flamingo = Color(0xFFF2CDCD)
val Sky = Color(0xFF89DCEB)

// Text hierarchy
val TextPrimary = Color(0xFFE2E8F8)
val TextSecondary = Color(0xFF8B92A8)
val TextDim = Color(0xFF4A4E62)

private val DarkColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Black,
    primaryContainer = TealDim.copy(alpha = 0.15f),
    onPrimaryContainer = Teal,
    secondary = Mauve,
    onSecondary = Black,
    secondaryContainer = MauveDim.copy(alpha = 0.15f),
    onSecondaryContainer = Mauve,
    tertiary = Peach,
    onTertiary = Black,
    error = Red,
    onError = Black,
    errorContainer = Red.copy(alpha = 0.15f),
    onErrorContainer = Red,
    background = Black,
    onBackground = TextPrimary,
    surface = Surface0,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Surface3,
    outlineVariant = Surface2,
    inverseSurface = TextPrimary,
    inverseOnSurface = Black,
    surfaceTint = Teal
)

val HostShieldTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = Typography().bodyLarge.copy(lineHeight = 24.sp),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun HostShieldTheme(content: @Composable () -> Unit) {
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

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = HostShieldTypography,
        content = content
    )
}
