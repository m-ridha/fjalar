package com.muhammadridha.fjalar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassSurfaceSpec(
    val backgroundColor: Color,
    val borderWidth: Dp,
    val rimHighlight: Color,
    val borderSubtle: Color,
    val isReducedTransparency: Boolean,
)

fun resolveGlassSurfaceSpec(
    colors: LiquidGlassColors,
    level: GlassLevel,
    reducedTransparency: Boolean = colors.isReducedTransparency,
): GlassSurfaceSpec {
    val effectiveColors = colors.withReducedTransparency(reducedTransparency)
    val borderWidth = if (effectiveColors.isReducedTransparency) 1.5.dp else 1.dp
    return GlassSurfaceSpec(
        backgroundColor = effectiveColors.glassBackground(level),
        borderWidth = borderWidth,
        rimHighlight = effectiveColors.rimHighlight,
        borderSubtle = effectiveColors.borderSubtle,
        isReducedTransparency = effectiveColors.isReducedTransparency,
    )
}

fun Modifier.glassSurface(
    colors: LiquidGlassColors,
    level: GlassLevel,
    shape: Shape = RoundedCornerShape(16.dp),
    reducedTransparency: Boolean = colors.isReducedTransparency,
): Modifier {
    val spec = resolveGlassSurfaceSpec(colors, level, reducedTransparency)
    val rimBrush = Brush.linearGradient(
        colors = listOf(spec.rimHighlight, spec.borderSubtle),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    return this
        .background(color = spec.backgroundColor, shape = shape)
        .border(width = spec.borderWidth, brush = rimBrush, shape = shape)
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
