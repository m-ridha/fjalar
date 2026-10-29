package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.glassSurface

/**
 * The compass follows the measured GPS button with an 8 dp touch gap. Keeping both in the
 * same safe-area layout avoids native compass offsets drifting after wrapping or rotation.
 */
@Composable
fun MapNavigationControls(
    onRecenter: () -> Unit,
    bearing: Double,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapRecenterControl(onRecenter)
        MapCompassControl(bearing, onResetNorth)
    }
}

@Composable
fun MapRecenterControl(onRecenter: () -> Unit) {
    val colors = LiquidGlassTheme.colors
    val recenterDescription = fjalarString(FjalarStringKey.HUD_RECENTER_LOCATION)
    Box(
        modifier = Modifier.size(48.dp)
            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onRecenter)
            .semantics { contentDescription = recenterDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text("◎", fontSize = 20.sp, color = colors.primary)
    }
}

@Composable
fun MapCompassControl(bearing: Double, onResetNorth: () -> Unit) {
    val colors = LiquidGlassTheme.colors
    val compassDescription = fjalarString(FjalarStringKey.HUD_COMPASS_NORTH)
    Box(
        modifier = Modifier.size(48.dp)
            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onResetNorth)
            .semantics { contentDescription = compassDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp).rotate(-bearing.toFloat())) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val north = Path().apply {
                moveTo(center.x, 0f)
                lineTo(center.x - size.width * 0.2f, center.y)
                lineTo(center.x + size.width * 0.2f, center.y)
                close()
            }
            val south = Path().apply {
                moveTo(center.x, size.height)
                lineTo(center.x - size.width * 0.2f, center.y)
                lineTo(center.x + size.width * 0.2f, center.y)
                close()
            }
            drawPath(north, Color(0xFFFF3B30))
            drawPath(south, colors.textPrimary)
        }
    }
}
