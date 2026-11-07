package com.muhammadridha.fjalar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.localization.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

fun compassSemanticDescription(bearing: Double, locale: AppLocale): String {
    val normalized = if (!bearing.isFinite()) 0.0 else ((bearing % 360.0) + 360.0) % 360.0
    val rounded = (normalized.roundToInt()) % 360
    val dirKey = FjalarStrings.compassDirectionKey(normalized)
    val dirName = FjalarStrings.get(dirKey, locale)
    return FjalarStrings.get(FjalarStringKey.ACCESSIBLE_COMPASS_BEARING, locale, rounded, dirName)
}

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
    val recenterDescription = fjalarString(FjalarStringKey.HUD_RECENTER_LOCATION)
    Box(
        modifier = Modifier.size(48.dp)
            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onRecenter)
            .semantics {
                role = Role.Button
                contentDescription = recenterDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            val radius = size.minDimension / 2f
            val locationBlue = Color(0xFF007AFF)
            drawCircle(color = locationBlue.copy(alpha = 0.20f), radius = radius)
            drawCircle(color = Color.White, radius = radius * 0.70f)
            drawCircle(color = locationBlue, radius = radius * 0.49f)
        }
    }
}

@Composable
fun MapCompassControl(bearing: Double, onResetNorth: () -> Unit) {
    // Treat small camera rounding errors and full turns as north. Key the effect to
    // this boolean so continuous camera callbacks do not restart the hide timer.
    val normalizedBearing = ((bearing % 360.0) + 360.0) % 360.0
    val isFacingNorth = normalizedBearing.isFinite() &&
        (abs(normalizedBearing) <= 1.0 || abs(normalizedBearing - 360.0) <= 1.0)
    var isVisible by remember { mutableStateOf(!isFacingNorth) }
    LaunchedEffect(isFacingNorth) {
        if (isFacingNorth) {
            delay(1_000)
            isVisible = false
        } else {
            isVisible = true
        }
    }

    // Keep the control's slot stable so the armed status and GPS button do not move.
    Box(Modifier.size(48.dp)) {
        AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
            CompassButton(bearing, onResetNorth)
        }
    }
}

@Composable
private fun CompassButton(bearing: Double, onResetNorth: () -> Unit) {
    val colors = LiquidGlassTheme.colors
    val compassDescription = compassSemanticDescription(bearing, LiquidGlassTheme.locale)
    Box(
        modifier = Modifier.size(48.dp)
            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onResetNorth)
            .semantics {
                role = Role.Button
                contentDescription = compassDescription
            },
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
