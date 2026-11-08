package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val radiusMeters: Int = TriggerRadius.DEFAULT.meters,
    val coordinates: GeoCoordinates? = null,
    val addressLabel: String? = null,
    val scheduleType: ScheduleType = ScheduleType.SCHEDULED,
    val startMinuteOfDay: Int = 7 * 60,
    val endMinuteOfDay: Int = 9 * 60 + 30,
    val daysOfWeekMask: Int = AlarmSchedule.WEEKDAYS,
    val enabled: Boolean = true,
    val initialEnabled: Boolean = enabled,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
) {
    /** Only a net switch change may replace the latest persisted runtime state. */
    val enabledOverride: Boolean?
        get() = if (alarmId == null || enabled != initialEnabled) enabled else null

    fun displayedEnabled(existingAlarm: LocationAlarm?): Boolean =
        enabledOverride ?: existingAlarm?.isEnabled ?: enabled

    fun withEnabled(value: Boolean, displayedEnabled: Boolean): ConfigureAlarmDraft = copy(
        enabled = value,
        initialEnabled = if (enabledOverride == null) displayedEnabled else initialEnabled,
    )

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
        val enabledState = if (scheduleType == ScheduleType.ONE_TIME) {
            AlarmState.ARMED_MONITORING
        } else {
            AlarmState.SCHEDULED_IDLE
        }
        val finalState = when (enabledOverride) {
            false -> AlarmState.DISABLED
            true -> existingAlarm?.state?.takeUnless { it == AlarmState.DISABLED } ?: enabledState
            null -> existingAlarm?.state ?: enabledState
        }
        return LocationAlarm(
            id = id,
            label = label.trim(),
            coordinates = coords,
            radius = TriggerRadius(radiusMeters),
            schedule = finalSchedule,
            soundConfig = (existingAlarm?.soundConfig ?: AlarmSoundConfig()).copy(
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
            enabled = alarm.isEnabled,
            soundEnabled = alarm.soundConfig.soundEnabled,
            vibrateEnabled = alarm.soundConfig.vibrateEnabled,
        )
    }
}

fun configureAlarmRadiusSemanticDescription(radiusMeters: Int, locale: AppLocale): String {
    val formattedRadius = formatDistance(radiusMeters, locale)
    return FjalarStrings.get(FjalarStringKey.ACCESSIBLE_RADIUS_SLIDER, locale, formattedRadius)
}

val CONFIGURE_ALARM_DAY_NAME_KEYS = listOf(
    FjalarStringKey.DAY_NAME_MONDAY,
    FjalarStringKey.DAY_NAME_TUESDAY,
    FjalarStringKey.DAY_NAME_WEDNESDAY,
    FjalarStringKey.DAY_NAME_THURSDAY,
    FjalarStringKey.DAY_NAME_FRIDAY,
    FjalarStringKey.DAY_NAME_SATURDAY,
    FjalarStringKey.DAY_NAME_SUNDAY,
)

fun configureAlarmDayChipContentDescription(dayIndex: Int, locale: AppLocale): String {
    return FjalarStrings.get(CONFIGURE_ALARM_DAY_NAME_KEYS[dayIndex], locale)
}

fun configureAlarmDayChipStateDescription(isSelected: Boolean, locale: AppLocale): String {
    return if (isSelected) {
        FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DAY_SELECTED, locale)
    } else {
        FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DAY_NOT_SELECTED, locale)
    }
}

const val CONFIGURE_ALARM_PILL_GRID_COLUMNS = 5

val CONFIGURE_ALARM_RADIUS_PRESET_ROWS: List<List<TriggerRadius>> = listOf(
    TriggerRadius.PRESETS.filter { it.meters < 1_000 },
    TriggerRadius.PRESETS.filter { it.meters >= 1_000 },
)

val CONFIGURE_ALARM_DAY_CHIP_ROWS: List<List<Int>> = listOf(
    listOf(0, 1, 2, 3, 4),
    listOf(5, 6),
)

private const val RADIUS_SLIDER_MAX_METERS = 3000

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigureAlarmScreen(
    draft: ConfigureAlarmDraft,
    onDraftChange: (ConfigureAlarmDraft) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNavigateToPicker: (GeoCoordinates) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    locale: AppLocale = LiquidGlassTheme.locale,
    isOperationPending: Boolean = false,
    hasOperationFailure: Boolean = false,
    persistedAlarm: LocationAlarm? = null,
) {
    val colors = LiquidGlassTheme.colors

    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<EditorTimeField?>(null) }

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

            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clickable(enabled = !isOperationPending, role = Role.Button, onClick = onSave)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_SAVE, locale),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Text(
            text = FjalarStrings.get(FjalarStringKey.EDITOR_BACK_SAVES_CHANGES, locale),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )

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
                enabled = !isOperationPending,
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

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.EDITOR_ENABLE_ALARM, locale),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                AlarmSwitch(
                    checked = draft.displayedEnabled(persistedAlarm),
                    label = FjalarStrings.get(FjalarStringKey.EDITOR_ENABLE_ALARM, locale),
                    enabled = !isOperationPending,
                    onCheckedChange = { enabled ->
                        onDraftChange(draft.withEnabled(enabled, draft.displayedEnabled(persistedAlarm)))
                    },
                )
            }

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
                            text = FjalarStrings.get(FjalarStringKey.PINNED_LOCATION, locale),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (!draft.addressLabel.isNullOrBlank()) {
                            Text(
                                text = draft.addressLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
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

            // Preset Pills (Row 1: sub-km presets; Row 2: km presets — equal 5-column pill widths, center aligned)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val pillSpacing = 8.dp
                val pillWidth = ((maxWidth - pillSpacing * (CONFIGURE_ALARM_PILL_GRID_COLUMNS - 1)) / CONFIGURE_ALARM_PILL_GRID_COLUMNS)
                    .coerceAtLeast(48.dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(pillSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    for (rowPresets in CONFIGURE_ALARM_RADIUS_PRESET_ROWS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(pillSpacing, Alignment.CenterHorizontally),
                        ) {
                            for (preset in rowPresets) {
                                val isSelected = draft.radiusMeters == preset.meters
                                Box(
                                    modifier = Modifier
                                        .width(pillWidth)
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) colors.primary else colors.glassPill)
                                        .clickable(role = Role.RadioButton, enabled = !isOperationPending) {
                                            onDraftChange(draft.copy(radiusMeters = preset.meters))
                                        }
                                        .semantics {
                                            role = Role.RadioButton
                                            stateDescription = if (isSelected) {
                                                FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DAY_SELECTED, locale)
                                            } else {
                                                FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DAY_NOT_SELECTED, locale)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = formatDistance(preset.meters, locale),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = if (isSelected) colors.onPrimary else colors.textPrimary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Slider
            val formattedRadius = formatDistance(draft.radiusMeters, locale)
            val radiusDescription = FjalarStrings.get(FjalarStringKey.ACCESSIBLE_RADIUS_SLIDER, locale, formattedRadius)
            Slider(
                value = draft.radiusMeters.toFloat(),
                enabled = !isOperationPending,
                onValueChange = {
                    val clamped = it.roundToInt().coerceIn(TriggerRadius.MIN_METERS, RADIUS_SLIDER_MAX_METERS)
                    onDraftChange(draft.copy(radiusMeters = clamped))
                },
                valueRange = TriggerRadius.MIN_METERS.toFloat()..RADIUS_SLIDER_MAX_METERS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.textSecondary.copy(alpha = 0.2f),
                ),
                modifier = Modifier.semantics {
                    contentDescription = radiusDescription
                    stateDescription = formattedRadius
                },
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
                        .clickable(role = Role.Button, enabled = !isOperationPending) {
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
                        .clickable(role = Role.Button, enabled = !isOperationPending) {
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
                    EditorTimeSelection(
                        label = FjalarStrings.get(
                            FjalarStringKey.EDITOR_START_TIME,
                            locale,
                            formatMinuteOfDay(draft.startMinuteOfDay),
                        ),
                        enabled = !isOperationPending,
                        onClick = { editingTime = EditorTimeField.START },
                        modifier = Modifier.weight(1f).widthIn(min = 152.dp),
                    )
                    EditorTimeSelection(
                        label = FjalarStrings.get(
                            FjalarStringKey.EDITOR_END_TIME,
                            locale,
                            formatMinuteOfDay(draft.endMinuteOfDay),
                        ),
                        enabled = !isOperationPending,
                        onClick = { editingTime = EditorTimeField.END },
                        modifier = Modifier.weight(1f).widthIn(min = 152.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Day chips: Row 1 (Mo..Fr) and Row 2 (Sa..Su) with equal 5-column pill widths, center aligned.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val pillSpacing = 8.dp
                    val pillWidth = ((maxWidth - pillSpacing * (CONFIGURE_ALARM_PILL_GRID_COLUMNS - 1)) / CONFIGURE_ALARM_PILL_GRID_COLUMNS)
                        .coerceAtLeast(48.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(pillSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val dayLabels = dayChipLabels(locale)
                        for (rowIndices in CONFIGURE_ALARM_DAY_CHIP_ROWS) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(pillSpacing, Alignment.CenterHorizontally),
                            ) {
                                for (i in rowIndices) {
                                    val bit = 1 shl i
                                    val isDayActive = (draft.daysOfWeekMask and bit) != 0
                                    Box(
                                        modifier = Modifier
                                            .width(pillWidth)
                                            .heightIn(min = 48.dp)
                                            .clip(CircleShape)
                                            .background(if (isDayActive) colors.primary else colors.glassPill)
                                            .clickable(role = Role.Checkbox, enabled = !isOperationPending) {
                                                val toggled = draft.daysOfWeekMask xor bit
                                                if (toggled != 0) {
                                                    onDraftChange(draft.copy(daysOfWeekMask = toggled))
                                                }
                                            }
                                            .semantics {
                                                role = Role.Checkbox
                                                contentDescription = FjalarStrings.get(CONFIGURE_ALARM_DAY_NAME_KEYS[i], locale)
                                                stateDescription = if (isDayActive) {
                                                    FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DAY_SELECTED, locale)
                                                } else {
                                                    FjalarStrings.get(FjalarStringKey.ACCESSIBLE_DAY_NOT_SELECTED, locale)
                                                }
                                            }
                                            .padding(vertical = 8.dp),
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

    val selectedField = editingTime
    if (selectedField != null && !isOperationPending) {
        val initialMinute = if (selectedField == EditorTimeField.START) {
            draft.startMinuteOfDay
        } else {
            draft.endMinuteOfDay
        }
        key(selectedField) {
            EditorTimePickerDialog(
                title = FjalarStrings.get(
                    if (selectedField == EditorTimeField.START) {
                        FjalarStringKey.EDITOR_TIME_START_TITLE
                    } else {
                        FjalarStringKey.EDITOR_TIME_END_TITLE
                    },
                    locale,
                ),
                initialMinuteOfDay = initialMinute,
                locale = locale,
                onDismiss = { editingTime = null },
                onConfirm = { selectedMinute ->
                    editingTime = null
                    onDraftChange(
                        if (selectedField == EditorTimeField.START) {
                            draft.copy(startMinuteOfDay = selectedMinute)
                        } else {
                            draft.copy(endMinuteOfDay = selectedMinute)
                        },
                    )
                },
            )
        }
    }
}

private enum class EditorTimeField {
    START,
    END,
}

@Composable
private fun EditorTimeSelection(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidGlassTheme.colors
    Column(
        modifier = modifier
            .glassSurface(level = GlassLevel.PILL, shape = RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(12.dp),
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
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorTimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    locale: AppLocale,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = LiquidGlassTheme.colors
    val pickerState = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    var keyboardMode by remember { mutableStateOf(false) }
    val hourField = remember { EditorTimeFieldState((initialMinuteOfDay / 60).toString().padStart(2, '0')) }
    val minuteField = remember { EditorTimeFieldState((initialMinuteOfDay % 60).toString().padStart(2, '0')) }

    fun synchronizeDialFromInput() {
        hourField.text.toIntOrNull()?.takeIf { it in 0..23 }?.let { pickerState.hour = it }
        minuteField.text.toIntOrNull()?.takeIf { it in 0..59 }?.let { pickerState.minute = it }
    }

    fun synchronizeInputFromDial() {
        hourField.synchronize(pickerState.hour.toString().padStart(2, '0'))
        minuteField.synchronize(pickerState.minute.toString().padStart(2, '0'))
    }
    val pickerColors = TimePickerDefaults.colors(
        clockDialColor = colors.glassPill,
        clockDialSelectedContentColor = colors.onPrimary,
        clockDialUnselectedContentColor = colors.textPrimary,
        selectorColor = colors.primary,
        containerColor = Color.Transparent,
        periodSelectorBorderColor = colors.borderSubtle,
        periodSelectorSelectedContainerColor = colors.primary,
        periodSelectorUnselectedContainerColor = colors.glassPill,
        periodSelectorSelectedContentColor = colors.onPrimary,
        periodSelectorUnselectedContentColor = colors.textPrimary,
        timeSelectorSelectedContainerColor = colors.primary,
        timeSelectorUnselectedContainerColor = colors.glassPill,
        timeSelectorSelectedContentColor = colors.onPrimary,
        timeSelectorUnselectedContentColor = colors.textPrimary,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(WindowInsetsLayoutHelper.safeDrawingPaddingModifier())
                .imePadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < 360.dp || maxHeight < 500.dp || LocalDensity.current.fontScale > 1.3f
            val showKeyboard = compact || keyboardMode
            LaunchedEffect(showKeyboard) {
                if (showKeyboard) {
                    synchronizeInputFromDial()
                } else {
                    synchronizeDialFromInput()
                }
            }
            val selectedMinute = if (showKeyboard) {
                editorMinuteOfDay(hourField.text, minuteField.text)
            } else {
                pickerState.hour * 60 + pickerState.minute
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .glassSurface(level = GlassLevel.MODAL, shape = RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (showKeyboard) {
                    EditorTimeInput(
                        hourField = hourField,
                        minuteField = minuteField,
                        locale = locale,
                    )
                } else {
                    val typography = MaterialTheme.typography
                    MaterialTheme(
                        typography = typography.copy(
                            displayLarge = typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                            bodyLarge = typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        ),
                    ) {
                        TimePicker(
                            state = pickerState,
                            colors = pickerColors,
                            layoutType = TimePickerLayoutType.Vertical,
                        )
                    }
                }
                if (!compact) {
                    TextButton(
                        onClick = {
                            if (showKeyboard) synchronizeDialFromInput() else synchronizeInputFromDial()
                            keyboardMode = !keyboardMode
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            FjalarStrings.get(
                                if (keyboardMode) {
                                    FjalarStringKey.EDITOR_TIME_DIAL
                                } else {
                                    FjalarStringKey.EDITOR_TIME_KEYBOARD
                                },
                                locale,
                            ),
                            color = colors.primary,
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(FjalarStrings.get(FjalarStringKey.EDITOR_CANCEL, locale), color = colors.textPrimary)
                    }
                    Button(
                        onClick = {
                            // Read the authoritative state at activation, including an edit that
                            // arrived after the last composition of the button.
                            val confirmedMinute = if (showKeyboard) {
                                editorMinuteOfDay(hourField.text, minuteField.text)
                            } else {
                                pickerState.hour * 60 + pickerState.minute
                            }
                            confirmedMinute?.let(onConfirm)
                        },
                        enabled = selectedMinute != null,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                            disabledContainerColor = colors.textSecondary.copy(alpha = 0.2f),
                            disabledContentColor = colors.textPrimary,
                        ),
                    ) {
                        Text(FjalarStrings.get(FjalarStringKey.EDITOR_TIME_CONFIRM, locale))
                    }
                }
            }
        }
    }
}

/** A blank or invalid field never falls back to the picker's previous valid value. */
internal fun editorMinuteOfDay(hourText: String, minuteText: String): Int? {
    fun validNumber(text: String, maximum: Int): Int? {
        if (text.isEmpty() || text.length > 2 || text.any { it !in '0'..'9' }) return null
        return text.toIntOrNull()?.takeIf { it in 0..maximum }
    }
    val hour = validNumber(hourText, 23) ?: return null
    val minute = validNumber(minuteText, 59) ?: return null
    return hour * 60 + minute
}

@Composable
private fun EditorTimeInput(
    hourField: EditorTimeFieldState,
    minuteField: EditorTimeFieldState,
    locale: AppLocale,
) {
    val colors = LiquidGlassTheme.colors
    val focusManager = LocalFocusManager.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedContainerColor = colors.glassPill,
        unfocusedContainerColor = colors.glassPill,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.textSecondary,
        focusedLabelColor = colors.textPrimary,
        unfocusedLabelColor = colors.textPrimary,
        cursorColor = colors.primary,
        errorTextColor = colors.textPrimary,
        errorContainerColor = colors.glassPill,
        errorBorderColor = colors.danger,
        errorLabelColor = colors.textPrimary,
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                state = hourField.state,
                inputTransformation = hourField.inputTransformation,
                label = { Text(FjalarStrings.get(FjalarStringKey.EDITOR_TIME_HOUR, locale)) },
                isError = editorMinuteOfDay(hourField.text, "00") == null,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                onKeyboardAction = { focusManager.moveFocus(FocusDirection.Next) },
                colors = fieldColors,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .onFocusChanged { focus ->
                        hourField.onFocusChanged(focus.isFocused)
                    },
            )
            OutlinedTextField(
                state = minuteField.state,
                inputTransformation = minuteField.inputTransformation,
                label = { Text(FjalarStrings.get(FjalarStringKey.EDITOR_TIME_MINUTE, locale)) },
                isError = editorMinuteOfDay("00", minuteField.text) == null,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { focusManager.clearFocus() },
                colors = fieldColors,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .onFocusChanged { focus ->
                        minuteField.onFocusChanged(focus.isFocused)
                    },
            )
        }
        if (editorMinuteOfDay(hourField.text, minuteField.text) == null) {
            Text(
                text = FjalarStrings.get(FjalarStringKey.EDITOR_TIME_INVALID, locale),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
