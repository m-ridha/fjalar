package com.muhammadridha.fjalar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.glassSurface(
    colors: LiquidGlassColors,
    level: GlassLevel,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier {
    val backgroundColor = colors.glassBackground(level)
    val rimBrush = Brush.linearGradient(
        colors = listOf(colors.rimHighlight, colors.borderSubtle),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    return this
        .background(color = backgroundColor, shape = shape)
        .border(width = 1.dp, brush = rimBrush, shape = shape)
}

fun Modifier.glassSurface(
    level: GlassLevel,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier = composed {
    val colors = LocalLiquidGlassColors.current
    glassSurface(
        colors = colors,
        level = level,
        shape = shape,
    )
}
