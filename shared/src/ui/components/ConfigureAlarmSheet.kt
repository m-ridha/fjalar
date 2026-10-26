package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.editor_active_window
import com.muhammadridha.fjalar.resources.editor_alert_and_haptics
import com.muhammadridha.fjalar.resources.editor_cancel
import com.muhammadridha.fjalar.resources.editor_earphone_override
import com.muhammadridha.fjalar.resources.editor_end_time
import com.muhammadridha.fjalar.resources.editor_label_placeholder
import com.muhammadridha.fjalar.resources.editor_loud_sound
import com.muhammadridha.fjalar.resources.editor_mode_one_time
import com.muhammadridha.fjalar.resources.editor_mode_scheduled
import com.muhammadridha.fjalar.resources.editor_save
import com.muhammadridha.fjalar.resources.editor_start_time
import com.muhammadridha.fjalar.resources.editor_strong_vibrate
import com.muhammadridha.fjalar.resources.editor_time_step_minus_15
import com.muhammadridha.fjalar.resources.editor_time_step_plus_15
import com.muhammadridha.fjalar.resources.editor_title
import com.muhammadridha.fjalar.resources.editor_trigger_radius
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun ConfigureAlarmSheet(
    onSave: (LocationAlarm) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialAlarm: LocationAlarm? = null,
    initialLabel: String? = null,
    selectedCoordinates: GeoCoordinates? = null,
    onRadiusChanged: (TriggerRadius) -> Unit = {},
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors

    var label by remember(initialAlarm, initialLabel) {
        mutableStateOf(initialLabel ?: initialAlarm?.label ?: "")
    }
    var radiusMeters by remember {
        mutableIntStateOf(initialAlarm?.radius?.meters ?: 500)
    }
    var scheduleType by remember {
        mutableStateOf(initialAlarm?.schedule?.type ?: ScheduleType.SCHEDULED)
    }
    var startMinute by remember {
        mutableIntStateOf(initialAlarm?.schedule?.startMinuteOfDay ?: (7 * 60))
    }
    var endMinute by remember {
        mutableIntStateOf(initialAlarm?.schedule?.endMinuteOfDay ?: (9 * 60 + 30))
    }
    var daysMask by remember {
        mutableIntStateOf(
            initialAlarm?.schedule?.daysOfWeekMask?.takeIf { it != 0 } ?: AlarmSchedule.WEEKDAYS
        )
    }

    var soundEnabled by remember {
        mutableStateOf(initialAlarm?.soundConfig?.soundEnabled ?: true)
    }
    var vibrateEnabled by remember {
        mutableStateOf(initialAlarm?.soundConfig?.vibrateEnabled ?: true)
    }
    var earphoneOverride by remember { mutableStateOf(false) }

    val isScheduleValid = scheduleType != ScheduleType.SCHEDULED ||
        (startMinute != endMinute && daysMask != 0)
    val isSaveEnabled = label.isNotBlank() && isScheduleValid

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                level = GlassLevel.MODAL,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Modal Header: Cancel, Title, Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(Res.string.editor_cancel),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(Res.string.editor_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )

            TextButton(
                onClick = {
                    if (isSaveEnabled) {
                        val finalSchedule = if (scheduleType == ScheduleType.SCHEDULED) {
                            AlarmSchedule(
                                type = ScheduleType.SCHEDULED,
                                startMinuteOfDay = startMinute,
                                endMinuteOfDay = endMinute,
                                daysOfWeekMask = daysMask,
                            )
                        } else {
                            AlarmSchedule.ONE_TIME
                        }

                        val id = initialAlarm?.id ?: "alarm-${Clock.System.now().toEpochMilliseconds()}"
                        val coordinates = selectedCoordinates ?: initialAlarm?.coordinates ?: GeoCoordinates.DEFAULT_JAKARTA
                        val updated = LocationAlarm(
                            id = id,
                            label = label.trim(),
                            coordinates = coordinates,
                            radius = TriggerRadius(radiusMeters),
                            schedule = finalSchedule,
                            soundConfig = initialAlarm?.soundConfig?.copy(
                                soundEnabled = soundEnabled,
                                vibrateEnabled = vibrateEnabled,
                            ) ?: AlarmSoundConfig(
                                soundEnabled = soundEnabled,
                                vibrateEnabled = vibrateEnabled,
                            ),
                            state = initialAlarm?.state ?: if (scheduleType == ScheduleType.ONE_TIME) {
                                AlarmState.ARMED_MONITORING
                            } else {
                                AlarmState.SCHEDULED_IDLE
                            },
                            addressLabel = initialAlarm?.addressLabel,
                            suppressedUntil = initialAlarm?.suppressedUntil,
                        )
                        onSave(updated)
                    }
                },
                enabled = isSaveEnabled,
            ) {
                Text(
                    text = stringResource(Res.string.editor_save),
                    color = if (isSaveEnabled) colors.primary else colors.textSecondary.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Label input
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = {
                Text(
                    text = stringResource(Res.string.editor_label_placeholder),
                    color = colors.textSecondary.copy(alpha = 0.6f),
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.borderSubtle,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Trigger Radius section
        Text(
            text = stringResource(Res.string.editor_trigger_radius),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Preset Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets = listOf(250, 500, 1000, 2000, 5000)
            for (preset in presets) {
                val isSelected = radiusMeters == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.primary else colors.glassPill)
                        .clickable(role = Role.Button) {
                            radiusMeters = preset
                            onRadiusChanged(TriggerRadius(preset))
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatDistance(preset),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (isSelected) colors.onPrimary else colors.textPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Radius Slider
        Slider(
            value = radiusMeters.toFloat(),
            onValueChange = {
                val newRadius = it.roundToInt().coerceIn(100, 10000)
                radiusMeters = newRadius
                onRadiusChanged(TriggerRadius(newRadius))
            },
            valueRange = 100f..10000f,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.textSecondary.copy(alpha = 0.2f),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Selector: One-Time Now vs Scheduled Window
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .padding(4.dp),
        ) {
            // One-Time
            val isOneTime = scheduleType == ScheduleType.ONE_TIME
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isOneTime) colors.primary else Color.Transparent)
                    .clickable(role = Role.Button) { scheduleType = ScheduleType.ONE_TIME }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.editor_mode_one_time),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isOneTime) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isOneTime) colors.onPrimary else colors.textPrimary,
                )
            }

            // Scheduled
            val isScheduled = scheduleType == ScheduleType.SCHEDULED
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isScheduled) colors.primary else Color.Transparent)
                    .clickable(role = Role.Button) { scheduleType = ScheduleType.SCHEDULED }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.editor_mode_scheduled),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isScheduled) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isScheduled) colors.onPrimary else colors.textPrimary,
                )
            }
        }

        // Scheduled Window controls
        if (scheduleType == ScheduleType.SCHEDULED) {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(
                    Res.string.editor_active_window,
                    formatMinuteOfDay(startMinute),
                    formatMinuteOfDay(endMinute),
                ),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Time Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimeStepper(
                    label = stringResource(Res.string.editor_start_time, formatMinuteOfDay(startMinute)),
                    minuteOfDay = startMinute,
                    onChange = { startMinute = it },
                    modifier = Modifier.weight(1f),
                )
                TimeStepper(
                    label = stringResource(Res.string.editor_end_time, formatMinuteOfDay(endMinute)),
                    minuteOfDay = endMinute,
                    onChange = { endMinute = it },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7 Day-of-week chips (M T W T F S S / Sn Sl Rb Km Jm Sb Mg)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val dayLabels = dayChipLabels(locale)
                for (i in 0..6) {
                    val bit = 1 shl i
                    val isDayActive = (daysMask and bit) != 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (isDayActive) colors.primary else colors.glassPill)
                            .clickable(role = Role.Button) {
                                val toggled = daysMask xor bit
                                if (toggled != 0) {
                                    daysMask = toggled
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayLabels[i],
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isDayActive) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isDayActive) colors.onPrimary else colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sound & Haptics section
        Text(
            text = stringResource(Res.string.editor_alert_and_haptics),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Loud Sound Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.editor_loud_sound),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Switch(
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    if (!soundEnabled && !vibrateEnabled) vibrateEnabled = true
                },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.primary),
            )
        }

        // Strong Vibrate Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.editor_strong_vibrate),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Switch(
                checked = vibrateEnabled,
                onCheckedChange = {
                    vibrateEnabled = it
                    if (!vibrateEnabled && !soundEnabled) soundEnabled = true
                },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.primary),
            )
        }

        // Earphone Override Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.editor_earphone_override),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Switch(
                checked = earphoneOverride,
                onCheckedChange = { earphoneOverride = it },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.primary),
            )
        }
    }
}

@Composable
private fun TimeStepper(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidGlassTheme.colors
    Column(
        modifier = modifier
            .glassSurface(level = GlassLevel.PILL, shape = RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable(role = Role.Button) { onChange((minuteOfDay - 15 + 1440) % 1440) }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.editor_time_step_minus_15),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable(role = Role.Button) { onChange((minuteOfDay + 15) % 1440) }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.editor_time_step_plus_15),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}
