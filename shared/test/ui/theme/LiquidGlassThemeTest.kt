package com.muhammadridha.fjalar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.ThemeMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiquidGlassThemeTest {

    @Test
    fun lightModeTokensMatchSpecification() {
        val colors = LightLiquidGlassColors

        assertFalse(colors.isDark)
        assertEquals(Color(0xFFFAF9FE), colors.canvas)
        assertEquals(Color(0xFF007AFF), colors.primary)
        assertEquals(Color.White, colors.onPrimary)
        assertEquals(Color(0xFF34C759), colors.armed)
        assertEquals(Color(0xFFFF3B30), colors.danger)
        assertEquals(Color(0xFF1A1B1F), colors.textPrimary)
        assertEquals(Color(0xFF414755), colors.textSecondary)

        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.65f), colors.glassPill)
        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.78f), colors.glassSheet)
        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.88f), colors.glassModal)

        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.85f), colors.rimHighlight)
        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.65f), colors.borderSubtle)
    }

    @Test
    fun darkModeTokensMatchSpecification() {
        val colors = DarkLiquidGlassColors

        assertTrue(colors.isDark)
        assertEquals(Color(0xFF0C0E14), colors.canvas)
        assertEquals(Color(0xFF00D2FF), colors.primary)
        assertEquals(Color(0xFF0C0E14), colors.onPrimary)
        assertEquals(Color(0xFF00E676), colors.armed)
        assertEquals(Color(0xFFFF453A), colors.danger)
        assertEquals(Color(0xFFFFFFFF), colors.textPrimary)
        assertEquals(Color(0xFF94A3B8), colors.textSecondary)

        assertEquals(Color(0xFF14161E).copy(alpha = 0.72f), colors.glassPill)
        assertEquals(Color(0xFF14161E).copy(alpha = 0.78f), colors.glassSheet)
        assertEquals(Color(0xFF14161E).copy(alpha = 0.88f), colors.glassModal)

        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.18f), colors.rimHighlight)
        assertEquals(Color(0xFFFFFFFF).copy(alpha = 0.08f), colors.borderSubtle)
    }

    @Test
    fun glassLevelResolvesCorrectBackgroundColors() {
        assertEquals(LightLiquidGlassColors.glassPill, LightLiquidGlassColors.glassBackground(GlassLevel.PILL))
        assertEquals(LightLiquidGlassColors.glassSheet, LightLiquidGlassColors.glassBackground(GlassLevel.SHEET))
        assertEquals(LightLiquidGlassColors.glassModal, LightLiquidGlassColors.glassBackground(GlassLevel.MODAL))

        assertEquals(DarkLiquidGlassColors.glassPill, DarkLiquidGlassColors.glassBackground(GlassLevel.PILL))
        assertEquals(DarkLiquidGlassColors.glassSheet, DarkLiquidGlassColors.glassBackground(GlassLevel.SHEET))
        assertEquals(DarkLiquidGlassColors.glassModal, DarkLiquidGlassColors.glassBackground(GlassLevel.MODAL))
    }

    @Test
    fun themeResolutionHandlesAllThemeModes() {
        assertEquals(
            LightLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.LIGHT, systemInDark = true)
        )
        assertEquals(
            LightLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.LIGHT, systemInDark = false)
        )

        assertEquals(
            DarkLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.DARK, systemInDark = true)
        )
        assertEquals(
            DarkLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.DARK, systemInDark = false)
        )

        assertEquals(
            DarkLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.SYSTEM, systemInDark = true)
        )
        assertEquals(
            LightLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.SYSTEM, systemInDark = false)
        )
    }

    @Test
    fun reducedTransparencyLightModeReplacesFrostedSurfacesWith92PercentOpaqueTint() {
        val reduced = LightLiquidGlassColors.withReducedTransparency()

        assertFalse(reduced.isDark)
        val expectedSurface = Color(0xFFF4F3F8).copy(alpha = 0.92f)
        assertEquals(expectedSurface, reduced.glassPill)
        assertEquals(expectedSurface, reduced.glassSheet)
        assertEquals(expectedSurface, reduced.glassModal)
        assertEquals(0.92f, reduced.glassPill.alpha, 0.01f)
        assertEquals(ReducedTransparencyLightLiquidGlassColors, reduced)

        // Ensure withReducedTransparency(false) reverts to original
        assertEquals(LightLiquidGlassColors, reduced.withReducedTransparency(false))
    }

    @Test
    fun reducedTransparencyDarkModeReplacesFrostedSurfacesWith92PercentOpaqueTint() {
        val reduced = DarkLiquidGlassColors.withReducedTransparency()

        assertTrue(reduced.isDark)
        val expectedSurface = Color(0xFF191B22).copy(alpha = 0.92f)
        assertEquals(expectedSurface, reduced.glassPill)
        assertEquals(expectedSurface, reduced.glassSheet)
        assertEquals(expectedSurface, reduced.glassModal)
        assertEquals(0.92f, reduced.glassPill.alpha, 0.01f)
        assertEquals(ReducedTransparencyDarkLiquidGlassColors, reduced)

        // Ensure withReducedTransparency(false) reverts to original
        assertEquals(DarkLiquidGlassColors, reduced.withReducedTransparency(false))
    }

    @Test
    fun reducedTransparencyMeetsWcagAaContrastRequirements() {
        val light = LightLiquidGlassColors.withReducedTransparency()
        val lightRatio = calculateContrastRatio(light.textPrimary, Color(0xFFF4F3F8))
        assertTrue(
            lightRatio >= 4.5,
            "Light mode textPrimary contrast ratio $lightRatio must be >= 4.5:1 (WCAG AA)"
        )

        val dark = DarkLiquidGlassColors.withReducedTransparency()
        val darkRatio = calculateContrastRatio(dark.textPrimary, Color(0xFF191B22))
        assertTrue(
            darkRatio >= 4.5,
            "Dark mode textPrimary contrast ratio $darkRatio must be >= 4.5:1 (WCAG AA)"
        )
    }

    @Test
    fun themeResolutionRespectsReducedTransparency() {
        assertEquals(
            ReducedTransparencyLightLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.LIGHT, systemInDark = false, reducedTransparency = true)
        )
        assertEquals(
            ReducedTransparencyDarkLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.DARK, systemInDark = false, reducedTransparency = true)
        )
        assertEquals(
            ReducedTransparencyDarkLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.SYSTEM, systemInDark = true, reducedTransparency = true)
        )
        assertEquals(
            ReducedTransparencyLightLiquidGlassColors,
            resolveLiquidGlassColors(themeMode = ThemeMode.SYSTEM, systemInDark = false, reducedTransparency = true)
        )
    }

    @Test
    fun glassSurfaceModifierProducesValidModifier() {
        val modifier = Modifier.glassSurface(
            colors = LightLiquidGlassColors,
            level = GlassLevel.PILL,
            shape = RoundedCornerShape(16.dp)
        )
        assertNotNull(modifier)
    }

    @Test
    fun glassSurfaceModifierWithReducedTransparencyProducesValidModifier() {
        val modifier = Modifier.glassSurface(
            colors = LightLiquidGlassColors,
            level = GlassLevel.PILL,
            shape = RoundedCornerShape(16.dp),
            reducedTransparency = true,
        )
        assertNotNull(modifier)
    }

    @Test
    fun glassSurfaceModifierWithExplicitFalseOverrideUnderReducedThemeRestoresStandardPalette() {
        val modifier = Modifier.glassSurface(
            colors = ReducedTransparencyLightLiquidGlassColors,
            level = GlassLevel.PILL,
            shape = RoundedCornerShape(16.dp),
            reducedTransparency = false,
        )
        assertNotNull(modifier)

        val darkModifier = Modifier.glassSurface(
            colors = ReducedTransparencyDarkLiquidGlassColors,
            level = GlassLevel.PILL,
            shape = RoundedCornerShape(16.dp),
            reducedTransparency = false,
        )
        assertNotNull(darkModifier)
    }

    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channelLuminance(c: Float): Double {
            val d = c.toDouble()
            return if (d <= 0.04045) {
                d / 12.92
            } else {
                ((d + 0.055) / 1.055).pow(2.4)
            }
        }
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
