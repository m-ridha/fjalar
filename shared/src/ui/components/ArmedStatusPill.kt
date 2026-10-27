package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface

@Composable
fun ArmedStatusPill(
    alarm: LocationAlarm,
    distanceMeters: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = LiquidGlassTheme.colors
    val formattedDistance = formatDistance(distanceMeters ?: alarm.radius.meters)

    val statusText = if (alarm.schedule.type == ScheduleType.SCHEDULED) {
        val startStr = formatMinuteOfDay(alarm.schedule.startMinuteOfDay ?: 0)
        val endStr = formatMinuteOfDay(alarm.schedule.endMinuteOfDay ?: 0)
        fjalarString(FjalarStringKey.STATUS_ARMED_SCHEDULED, alarm.label, formattedDistance, startStr, endStr)
    } else {
        fjalarString(FjalarStringKey.STATUS_ARMED_ONE_TIME, alarm.label, formattedDistance)
    }

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Glowing Emerald Indicator Dot
        Box(
            modifier = Modifier.size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(colors.armed.copy(alpha = 0.25f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colors.armed, CircleShape),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
