package com.hostshield.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import kotlin.math.pow
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun highContrastAmoledUsesPureBlackBaseSurfaces() {
        val palette = hostShieldPalette(highContrastAmoled = true)

        assertThat(palette.black).isEqualTo(Color.Black)
        assertThat(palette.surface0).isEqualTo(Color.Black)
    }

    @Test
    fun highContrastAmoledKeepsTextAndSemanticColorsLegible() {
        val palette = hostShieldPalette(highContrastAmoled = true)
        val surfaces = listOf(palette.surface0, palette.surface1, palette.surface2, palette.surface3)

        surfaces.forEach { surface ->
            assertThat(contrastRatio(palette.textPrimary, surface)).isAtLeast(12.0)
            assertThat(contrastRatio(palette.textSecondary, surface)).isAtLeast(8.0)
            assertThat(contrastRatio(palette.textDim, surface)).isAtLeast(4.5)
        }

        listOf(
            palette.teal,
            palette.green,
            palette.red,
            palette.yellow,
            palette.blue,
            palette.peach,
            palette.mauve
        ).forEach { semanticColor ->
            assertThat(contrastRatio(semanticColor, palette.surface0)).isAtLeast(7.0)
            assertThat(contrastRatio(semanticColor, palette.surface1)).isAtLeast(7.0)
        }
    }

    @Test
    fun highContrastMaterialSchemeMirrorsPaletteTokens() {
        val palette = hostShieldPalette(highContrastAmoled = true)
        val scheme = hostShieldColorScheme(palette)

        assertThat(scheme.background).isEqualTo(Color.Black)
        assertThat(scheme.surface).isEqualTo(palette.surface0)
        assertThat(scheme.onSurface).isEqualTo(palette.textPrimary)
        assertThat(scheme.error).isEqualTo(palette.red)
        assertThat(contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant)).isAtLeast(8.0)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = relativeLuminance(foreground).coerceAtLeast(relativeLuminance(background))
        val darker = relativeLuminance(foreground).coerceAtMost(relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
}
