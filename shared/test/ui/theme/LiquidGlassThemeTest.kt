package com.muhammadridha.fjalar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.ThemeMode
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
    fun glassSurfaceModifierProducesValidModifier() {
        val modifier = Modifier.glassSurface(
            colors = LightLiquidGlassColors,
            level = GlassLevel.PILL,
            shape = RoundedCornerShape(16.dp)
        )
        assertNotNull(modifier)
    }
}
