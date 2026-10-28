package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.components.AlarmSwitch
import com.muhammadridha.fjalar.ui.components.DeleteAlarmConfirmationDialog
import com.muhammadridha.fjalar.ui.components.dayChipLabels
import com.muhammadridha.fjalar.ui.components.formatDistance
import com.muhammadridha.fjalar.ui.components.formatMinuteOfDay
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * In-memory editable draft representation of a [LocationAlarm].
 * Holds state during screen navigation (e.g. to/from MapPickerScreen)
 * ensuring all uncommitted fields remain 100% preserved.
 */
data class ConfigureAlarmDraft(
    val alarmId: String? = null,
    val label: String = "",
    val radiusMeters: Int = 500,
    val coordinates: GeoCoordinates? = null,
    val addressLabel: String? = null,
    val scheduleType: ScheduleType = ScheduleType.SCHEDULED,
    val startMinuteOfDay: Int = 7 * 60,
    val endMinuteOfDay: Int = 9 * 60 + 30,
    val daysOfWeekMask: Int = AlarmSchedule.WEEKDAYS,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
) {
    fun withNewCoordinates(newCoordinates: GeoCoordinates): ConfigureAlarmDraft =
        copy(coordinates = newCoordinates, addressLabel = if (newCoordinates == coordinates) addressLabel else null)

    fun deletionTarget(alarms: List<LocationAlarm>): LocationAlarm? =
        alarms.firstOrNull { it.id == alarmId }

    fun toLocationAlarm(existingAlarm: LocationAlarm? = null): LocationAlarm {
        val id = alarmId ?: existingAlarm?.id ?: "alarm-${Clock.System.now().toEpochMilliseconds()}"
        val coords = requireNotNull(coordinates ?: existingAlarm?.coordinates) { "Destination is required" }
        val finalSchedule = if (scheduleType == ScheduleType.SCHEDULED) {
            AlarmSchedule(
                type = ScheduleType.SCHEDULED,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                daysOfWeekMask = daysOfWeekMask,
            )
        } else {
            AlarmSchedule.ONE_TIME
        }
        val finalState = existingAlarm?.state ?: if (scheduleType == ScheduleType.ONE_TIME) {
            AlarmState.ARMED_MONITORING
        } else {
            AlarmState.SCHEDULED_IDLE
        }
        return LocationAlarm(
            id = id,
            label = label.trim(),
            coordinates = coords,
            radius = TriggerRadius(radiusMeters),
            schedule = finalSchedule,
            soundConfig = AlarmSoundConfig(
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
            ),
            state = finalState,
            addressLabel = addressLabel,
            suppressedUntil = existingAlarm?.suppressedUntil,
            snoozedUntil = existingAlarm?.snoozedUntil,
        )
    }

    companion object {
        fun fromAlarm(alarm: LocationAlarm): ConfigureAlarmDraft = ConfigureAlarmDraft(
            alarmId = alarm.id,
            label = alarm.label,
            radiusMeters = alarm.radius.meters,
            coordinates = alarm.coordinates,
            addressLabel = alarm.addressLabel,
            scheduleType = alarm.schedule.type,
            startMinuteOfDay = alarm.schedule.startMinuteOfDay ?: (7 * 60),
            endMinuteOfDay = alarm.schedule.endMinuteOfDay ?: (9 * 60 + 30),
            daysOfWeekMask = alarm.schedule.daysOfWeekMask.takeIf { it != 0 } ?: AlarmSchedule.WEEKDAYS,
            soundEnabled = alarm.soundConfig.soundEnabled,
            vibrateEnabled = alarm.soundConfig.vibrateEnabled,
        )
    }
}

private val RADIUS_PRESETS = listOf(300, 500, 1000, 2000, 3000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigureAlarmScreen(
    draft: ConfigureAlarmDraft,
    onDraftChange: (ConfigureAlarmDraft) -> Unit,
    onSave: (ConfigureAlarmDraft) -> Unit,
    onBack: () -> Unit,
    onNavigateToPicker: (GeoCoordinates) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
    isOperationPending: Boolean = false,
    hasOperationFailure: Boolean = false,
    persistedAlarm: LocationAlarm? = null,
) {
    val colors = LiquidGlassTheme.colors

    val isScheduleValid = draft.scheduleType != ScheduleType.SCHEDULED ||
        (draft.startMinuteOfDay != draft.endMinuteOfDay && draft.daysOfWeekMask != 0)
    val isSaveEnabled = draft.label.isNotBlank() && draft.coordinates != null &&
        isScheduleValid && !isOperationPending

    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .then(WindowInsetsLayoutHelper.horizontalSafeDrawingPaddingModifier()),
    ) {
        // Safe Top Bar
        FlowRow(
            modifier = WindowInsetsLayoutHelper
                .hudTopPaddingModifier(Modifier)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Back Button
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.NAV_BACK, locale),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    ),
                )
            }

            // Screen Title
            Text(
                text = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, locale),
                modifier = Modifier.weight(1f).widthIn(min = 96.dp).heightIn(min = 48.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )

            // Save CTA
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        enabled = isSaveEnabled,
                        onClick = { if (isSaveEnabled) onSave(draft) },
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_SAVE, locale),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSaveEnabled) colors.primary else colors.textSecondary.copy(alpha = 0.4f),
                    ),
                )
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(WindowInsetsLayoutHelper.navigationBottomPaddingModifier())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (hasOperationFailure) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.OPERATION_FAILED, locale),
                    color = colors.danger,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            // Label input field
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onDraftChange(draft.copy(label = it)) },
                placeholder = {
                    Text(
                        text = FjalarStrings.get(FjalarStringKey.EDITOR_LABEL_PLACEHOLDER, locale),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Location Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(level = GlassLevel.SHEET, shape = RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (draft.addressLabel.isNullOrBlank()) {
                                FjalarStrings.get(FjalarStringKey.PINNED_LOCATION, locale)
                            } else {
                                draft.addressLabel
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val coords = draft.coordinates
                        val coordText = if (coords != null) {
                            val lat = (coords.latitude * 10000.0).roundToInt() / 10000.0
                            val lon = (coords.longitude * 10000.0).roundToInt() / 10000.0
                            "📍 $lat, $lon"
                        } else {
                            FjalarStrings.get(FjalarStringKey.LOCATION_NOT_SET, locale)
                        }
                        Text(
                            text = coordText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = "tnum",
                                color = colors.textSecondary,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                            .clickable(
                                role = Role.Button,
                                enabled = !isOperationPending,
                                onClick = {
                                    val target = draft.coordinates ?: GeoCoordinates(0.0, 0.0)
                                    onNavigateToPicker(target)
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = FjalarStrings.get(FjalarStringKey.PICKER_CHANGE_PIN, locale),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Trigger Radius section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_TRIGGER_RADIUS, locale),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = formatDistance(draft.radiusMeters, locale),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.primary,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preset Pills
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (preset in RADIUS_PRESETS) {
                    val isSelected = draft.radiusMeters == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 64.dp)
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.primary else colors.glassPill)
                            .clickable(role = Role.Button) {
                                onDraftChange(draft.copy(radiusMeters = preset))
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatDistance(preset, locale),
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

            // Slider
            Slider(
                value = draft.radiusMeters.toFloat(),
                onValueChange = {
                    val clamped = it.roundToInt().coerceIn(100, 10000)
                    onDraftChange(draft.copy(radiusMeters = clamped))
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
                val isOneTime = draft.scheduleType == ScheduleType.ONE_TIME
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(if (isOneTime) colors.primary else Color.Transparent)
                        .clickable(role = Role.Button) {
                            onDraftChange(draft.copy(scheduleType = ScheduleType.ONE_TIME))
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = FjalarStrings.get(FjalarStringKey.EDITOR_MODE_ONE_TIME, locale),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isOneTime) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isOneTime) colors.onPrimary else colors.textPrimary,
                    )
                }

                val isScheduled = draft.scheduleType == ScheduleType.SCHEDULED
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(if (isScheduled) colors.primary else Color.Transparent)
                        .clickable(role = Role.Button) {
                            onDraftChange(draft.copy(scheduleType = ScheduleType.SCHEDULED))
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = FjalarStrings.get(FjalarStringKey.EDITOR_MODE_SCHEDULED, locale),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isScheduled) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isScheduled) colors.onPrimary else colors.textPrimary,
                    )
                }
            }

            // Scheduled Window Controls
            if (draft.scheduleType == ScheduleType.SCHEDULED) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = FjalarStrings.get(
                        FjalarStringKey.EDITOR_ACTIVE_WINDOW,
                        locale,
                        formatMinuteOfDay(draft.startMinuteOfDay),
                        formatMinuteOfDay(draft.endMinuteOfDay),
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditorTimeStepper(
                        label = FjalarStrings.get(
                            FjalarStringKey.EDITOR_START_TIME,
                            locale,
                            formatMinuteOfDay(draft.startMinuteOfDay),
                        ),
                        minuteOfDay = draft.startMinuteOfDay,
                        onChange = { onDraftChange(draft.copy(startMinuteOfDay = it)) },
                        locale = locale,
                        modifier = Modifier.weight(1f).widthIn(min = 152.dp),
                    )
                    EditorTimeStepper(
                        label = FjalarStrings.get(
                            FjalarStringKey.EDITOR_END_TIME,
                            locale,
                            formatMinuteOfDay(draft.endMinuteOfDay),
                        ),
                        minuteOfDay = draft.endMinuteOfDay,
                        onChange = { onDraftChange(draft.copy(endMinuteOfDay = it)) },
                        locale = locale,
                        modifier = Modifier.weight(1f).widthIn(min = 152.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Day chips wrap on narrow panes and at larger text sizes.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val dayLabels = dayChipLabels(locale)
                    for (i in 0..6) {
                        val bit = 1 shl i
                        val isDayActive = (draft.daysOfWeekMask and bit) != 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 48.dp)
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .background(if (isDayActive) colors.primary else colors.glassPill)
                                .clickable(role = Role.Button) {
                                    val toggled = draft.daysOfWeekMask xor bit
                                    if (toggled != 0) {
                                        onDraftChange(draft.copy(daysOfWeekMask = toggled))
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

            // Sound & Haptics Section
            Text(
                text = FjalarStrings.get(FjalarStringKey.EDITOR_ALERT_AND_HAPTICS, locale),
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
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_LOUD_SOUND, locale),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                AlarmSwitch(
                    checked = draft.soundEnabled,
                    label = FjalarStrings.get(FjalarStringKey.EDITOR_LOUD_SOUND, locale),
                    enabled = !isOperationPending,
                    onCheckedChange = { checked ->
                        val newSound = checked
                        val newVibrate = if (!newSound && !draft.vibrateEnabled) true else draft.vibrateEnabled
                        onDraftChange(draft.copy(soundEnabled = newSound, vibrateEnabled = newVibrate))
                    },
                )
            }

            // Strong Vibrate Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_STRONG_VIBRATE, locale),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                AlarmSwitch(
                    checked = draft.vibrateEnabled,
                    label = FjalarStrings.get(FjalarStringKey.EDITOR_STRONG_VIBRATE, locale),
                    enabled = !isOperationPending,
                    onCheckedChange = { checked ->
                        val newVibrate = checked
                        val newSound = if (!newVibrate && !draft.soundEnabled) true else draft.soundEnabled
                        onDraftChange(draft.copy(soundEnabled = newSound, vibrateEnabled = newVibrate))
                    },
                )
            }

            // Delete Alarm Button (if editing existing alarm)
            if (persistedAlarm != null && onDelete != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.danger.copy(alpha = 0.12f))
                        .clickable(role = Role.Button) { showDeleteDialog = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = FjalarStrings.get(FjalarStringKey.EDITOR_DELETE_ALARM, locale),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.danger,
                        ),
                    )
                }

                if (showDeleteDialog) {
                    DeleteAlarmConfirmationDialog(
                        alarm = persistedAlarm,
                        onConfirm = { id ->
                            showDeleteDialog = false
                            onDelete(id)
                        },
                        onDismiss = { showDeleteDialog = false },
                        locale = locale,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorTimeStepper(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    locale: AppLocale,
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
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_TIME_STEP_MINUS_15, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable(role = Role.Button) { onChange((minuteOfDay + 15) % 1440) }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_TIME_STEP_PLUS_15, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}
