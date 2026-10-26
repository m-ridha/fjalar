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
    reducedTransparency: Boolean = colors.isReducedTransparency,
): Modifier {
    val effectiveColors = colors.withReducedTransparency(reducedTransparency)
    val backgroundColor = effectiveColors.glassBackground(level)
    val rimBrush = Brush.linearGradient(
        colors = listOf(effectiveColors.rimHighlight, effectiveColors.borderSubtle),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    val borderWidth = if (effectiveColors.isReducedTransparency) 1.5.dp else 1.dp
    return this
        .background(color = backgroundColor, shape = shape)
        .border(width = borderWidth, brush = rimBrush, shape = shape)
}

fun Modifier.glassSurface(
    level: GlassLevel,
    shape: Shape = RoundedCornerShape(16.dp),
    reducedTransparency: Boolean? = null,
): Modifier = composed {
    val colors = LocalLiquidGlassColors.current
    val isReduced = reducedTransparency ?: LocalReducedTransparency.current
    glassSurface(
        colors = colors,
        level = level,
        shape = shape,
        reducedTransparency = isReduced,
    )
}
