package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmCard(
    alarm: LocationAlarm,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onEdit: (alarm: LocationAlarm) -> Unit,
    modifier: Modifier = Modifier,
    onSkipToday: (id: String) -> Unit = {},
    onDelete: ((alarm: LocationAlarm) -> Unit)? = null,
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors
    val deleteLabel = FjalarStrings.get(FjalarStringKey.EDITOR_DELETE_ALARM, locale)

    val scheduleSummary = if (alarm.schedule.type == ScheduleType.SCHEDULED) {
        val startStr = formatMinuteOfDay(alarm.schedule.startMinuteOfDay ?: 0)
        val endStr = formatMinuteOfDay(alarm.schedule.endMinuteOfDay ?: 0)
        val daysStr = formatDaysOfWeek(alarm.schedule.daysOfWeekMask, locale)
        if (daysStr.isNotBlank()) "$startStr – $endStr • $daysStr" else "$startStr – $endStr"
    } else {
        fjalarString(FjalarStringKey.CARD_ONE_TIME_TRIP, formatDistance(alarm.radius.meters, locale))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(level = GlassLevel.PILL, shape = RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = { onEdit(alarm) })
            .padding(16.dp),
    ) {
        // Top row: Label + optional address, Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            ) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!alarm.addressLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = alarm.addressLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                            .clickable(role = Role.Button) { onDelete(alarm) }
                            .semantics { contentDescription = deleteLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "🗑️",
                            fontSize = 14.sp,
                        )
                    }
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { checked -> onToggle(alarm.id, checked) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                        uncheckedThumbColor = colors.textSecondary.copy(alpha = 0.7f),
                        uncheckedTrackColor = colors.textSecondary.copy(alpha = 0.2f),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Schedule and timing line
        Text(
            text = scheduleSummary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Medium,
            ),
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Pills Row: Radius, Sound & Vibration, Skip Today Action
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Radius pill
            Text(
                text = formatDistance(alarm.radius.meters, locale),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
                modifier = Modifier
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            // Sound & Haptics badge
            val soundBadgeText = when {
                alarm.soundConfig.soundEnabled && alarm.soundConfig.vibrateEnabled ->
                    fjalarString(FjalarStringKey.CARD_LOUD_AND_VIBRATE)
                alarm.soundConfig.soundEnabled ->
                    fjalarString(FjalarStringKey.EDITOR_LOUD_SOUND)
                alarm.soundConfig.vibrateEnabled ->
                    fjalarString(FjalarStringKey.EDITOR_STRONG_VIBRATE)
                else -> null
            }

            if (soundBadgeText != null) {
                Text(
                    text = soundBadgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    modifier = Modifier
                        .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            // Skip Today control (for scheduled alarms)
            if (alarm.schedule.type == ScheduleType.SCHEDULED) {
                if (alarm.state == AlarmState.COMPLETED_FOR_WINDOW) {
                    Text(
                        text = fjalarString(FjalarStringKey.CARD_SKIPPED_TODAY),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                } else if (alarm.isEnabled) {
                    Text(
                        text = fjalarString(FjalarStringKey.CARD_SKIP_TODAY),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        ),
                        modifier = Modifier
                            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                            .clickable(role = Role.Button) { onSkipToday(alarm.id) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
