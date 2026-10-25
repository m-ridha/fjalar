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
) {
    fun glassBackground(level: GlassLevel): Color = when (level) {
        GlassLevel.PILL -> glassPill
        GlassLevel.SHEET -> glassSheet
        GlassLevel.MODAL -> glassModal
    }
}

val LightLiquidGlassColors = LiquidGlassColors(
    canvas = Color(0xFFFAF9FE),
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    armed = Color(0xFF34C759),
    danger = Color(0xFFFF3B30),
    textPrimary = Color(0xFF1A1B1F),
    textSecondary = Color(0xFF414755),
    glassPill = Color(0xFFFFFFFF).copy(alpha = 0.65f),
    glassSheet = Color(0xFFFFFFFF).copy(alpha = 0.78f),
    glassModal = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    rimHighlight = Color(0xFFFFFFFF).copy(alpha = 0.85f),
    borderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.65f),
    isDark = false,
)

val DarkLiquidGlassColors = LiquidGlassColors(
    canvas = Color(0xFF0C0E14),
    primary = Color(0xFF00D2FF),
    onPrimary = Color(0xFF0C0E14),
    armed = Color(0xFF00E676),
    danger = Color(0xFFFF453A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF94A3B8),
    glassPill = Color(0xFF14161E).copy(alpha = 0.72f),
    glassSheet = Color(0xFF14161E).copy(alpha = 0.78f),
    glassModal = Color(0xFF14161E).copy(alpha = 0.88f),
    rimHighlight = Color(0xFFFFFFFF).copy(alpha = 0.18f),
    borderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    isDark = true,
)
