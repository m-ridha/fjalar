package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val deleteLabel = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, locale)

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

            AlarmSwitch(
                checked = alarm.isEnabled,
                onCheckedChange = { checked -> onToggle(alarm.id, checked) },
                label = alarm.label,
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
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
        }
        if (onDelete != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = { onDelete(alarm) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.danger),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.danger.copy(alpha = 0.12f),
                        contentColor = colors.danger,
                    ),
                ) {
                    Text(text = deleteLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
