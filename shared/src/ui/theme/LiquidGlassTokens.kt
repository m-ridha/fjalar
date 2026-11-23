package com.muhammadridha.fjalar.ui.theme

import androidx.compose.ui.graphics.Color

enum class GlassLevel {
    PILL,
    SHEET,
    MODAL,
}

data class LiquidGlassColors(
    val canvas: Color,
    val primary: Color,
    val onPrimary: Color,
    val armed: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val glassPill: Color,
    val glassSheet: Color,
    val glassModal: Color,
    val rimHighlight: Color,
    val borderSubtle: Color,
    val isDark: Boolean,
    val isReducedTransparency: Boolean = false,
) {
    fun glassBackground(level: GlassLevel): Color = when (level) {
        GlassLevel.PILL -> glassPill
        GlassLevel.SHEET -> glassSheet
        GlassLevel.MODAL -> glassModal
    }

    fun withReducedTransparency(enabled: Boolean = true): LiquidGlassColors {
        if (!enabled) {
            return if (isDark) DarkLiquidGlassColors else LightLiquidGlassColors
        }
        return if (isDark) ReducedTransparencyDarkLiquidGlassColors else ReducedTransparencyLightLiquidGlassColors
    }
}

val LightLiquidGlassColors = LiquidGlassColors(
    canvas = Color(0xFFF2F3F8),
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    armed = Color(0xFF34C759),
    danger = Color(0xFFFF3B30),
    textPrimary = Color(0xFF1A1B1F),
    textSecondary = Color(0xFF414755),
    glassPill = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    glassSheet = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    glassModal = Color(0xFFFFFFFF).copy(alpha = 0.92f),
    rimHighlight = Color(0xFFFFFFFF).copy(alpha = 0.92f),
    borderSubtle = Color(0xFF1A1B1F).copy(alpha = 0.12f),
    isDark = false,
    isReducedTransparency = false,
)

val DarkLiquidGlassColors = LiquidGlassColors(
    canvas = Color(0xFF0C0E14),
    primary = Color(0xFF00D2FF),
    onPrimary = Color(0xFF0C0E14),
    armed = Color(0xFF00E676),
    danger = Color(0xFFFF453A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF94A3B8),
    glassPill = Color(0xFF1E2330).copy(alpha = 0.80f),
    glassSheet = Color(0xFF181C26).copy(alpha = 0.82f),
    glassModal = Color(0xFF181C26).copy(alpha = 0.90f),
    rimHighlight = Color(0xFFFFFFFF).copy(alpha = 0.28f),
    borderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.14f),
    isDark = true,
    isReducedTransparency = false,
)

val ReducedTransparencyLightLiquidGlassColors = LiquidGlassColors(
    canvas = Color(0xFFF2F3F8),
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    armed = Color(0xFF34C759),
    danger = Color(0xFFFF3B30),
    textPrimary = Color(0xFF1A1B1F),
    textSecondary = Color(0xFF414755),
    glassPill = Color(0xFFF4F3F8).copy(alpha = 0.92f),
    glassSheet = Color(0xFFF4F3F8).copy(alpha = 0.92f),
    glassModal = Color(0xFFF4F3F8).copy(alpha = 0.92f),
    rimHighlight = Color(0xFF1A1B1F).copy(alpha = 0.30f),
    borderSubtle = Color(0xFF1A1B1F).copy(alpha = 0.18f),
    isDark = false,
    isReducedTransparency = true,
)

val ReducedTransparencyDarkLiquidGlassColors = LiquidGlassColors(
    canvas = Color(0xFF0C0E14),
    primary = Color(0xFF00D2FF),
    onPrimary = Color(0xFF0C0E14),
    armed = Color(0xFF00E676),
    danger = Color(0xFFFF453A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF94A3B8),
    glassPill = Color(0xFF191B22).copy(alpha = 0.92f),
    glassSheet = Color(0xFF191B22).copy(alpha = 0.92f),
    glassModal = Color(0xFF191B22).copy(alpha = 0.92f),
    rimHighlight = Color(0xFFFFFFFF).copy(alpha = 0.45f),
    borderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.25f),
    isDark = true,
    isReducedTransparency = true,
)
