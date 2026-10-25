package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.card_loud_and_vibrate
import com.muhammadridha.fjalar.resources.card_one_time_trip
import com.muhammadridha.fjalar.resources.card_skip_today
import com.muhammadridha.fjalar.resources.card_skipped_today
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmCard(
    alarm: LocationAlarm,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onEdit: (alarm: LocationAlarm) -> Unit,
    modifier: Modifier = Modifier,
    onSkipToday: (id: String) -> Unit = {},
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors

    val scheduleSummary = if (alarm.schedule.type == ScheduleType.SCHEDULED) {
        val startStr = formatMinuteOfDay(alarm.schedule.startMinuteOfDay ?: 0)
        val endStr = formatMinuteOfDay(alarm.schedule.endMinuteOfDay ?: 0)
        val daysStr = formatDaysOfWeek(alarm.schedule.daysOfWeekMask, locale)
        if (daysStr.isNotBlank()) "$startStr – $endStr • $daysStr" else "$startStr – $endStr"
    } else {
        stringResource(Res.string.card_one_time_trip, formatDistance(alarm.radius.meters))
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
                text = formatDistance(alarm.radius.meters),
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
            Text(
                text = stringResource(Res.string.card_loud_and_vibrate),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                modifier = Modifier
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            // Skip Today control (for scheduled alarms)
            if (alarm.schedule.type == ScheduleType.SCHEDULED) {
                if (alarm.state == AlarmState.COMPLETED_FOR_WINDOW) {
                    Text(
                        text = stringResource(Res.string.card_skipped_today),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                } else if (alarm.isEnabled) {
                    Text(
                        text = stringResource(Res.string.card_skip_today),
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
