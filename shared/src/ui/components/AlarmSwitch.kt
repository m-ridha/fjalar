package com.muhammadridha.fjalar.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.muhammadridha.fjalar.ui.theme.LiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme

/** The same visible on/off treatment for alarm arming and editor alert controls. */
internal data class AlarmSwitchPalette(
    val checkedThumb: Color,
    val checkedTrack: Color,
    val uncheckedThumb: Color,
    val uncheckedTrack: Color,
)

internal fun LiquidGlassColors.alarmSwitchPalette() = AlarmSwitchPalette(
    checkedThumb = primary,
    checkedTrack = primary.copy(alpha = 0.4f),
    uncheckedThumb = textSecondary.copy(alpha = 0.7f),
    uncheckedTrack = textSecondary.copy(alpha = 0.2f),
)

@Composable
internal fun AlarmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LiquidGlassTheme.colors.alarmSwitchPalette()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.checkedThumb,
            checkedTrackColor = palette.checkedTrack,
            uncheckedThumbColor = palette.uncheckedThumb,
            uncheckedTrackColor = palette.uncheckedTrack,
        ),
    )
}
