package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigureAlarmScreenTest {

    private fun sampleAlarm(): LocationAlarm = LocationAlarm(
        id = "test-alarm-123",
        label = "Manggarai Station Express",
        coordinates = GeoCoordinates(-6.2088, 106.8456),
        radius = TriggerRadius(750),
        schedule = AlarmSchedule(
            type = ScheduleType.SCHEDULED,
            startMinuteOfDay = 7 * 60 + 15,
            endMinuteOfDay = 9 * 60 + 45,
            daysOfWeekMask = AlarmSchedule.MONDAY or AlarmSchedule.FRIDAY,
        ),
        soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = false),
        state = AlarmState.SCHEDULED_IDLE,
        addressLabel = "Manggarai Central Platform",
    )

    @Test
    fun draft_fromAlarm_populatesAllFieldsAccurately() {
        val alarm = sampleAlarm()
        val draft = ConfigureAlarmDraft.fromAlarm(alarm)

        assertEquals("test-alarm-123", draft.alarmId)
        assertEquals("Manggarai Station Express", draft.label)
        assertEquals(750, draft.radiusMeters)
        assertEquals(GeoCoordinates(-6.2088, 106.8456), draft.coordinates)
        assertEquals("Manggarai Central Platform", draft.addressLabel)
        assertEquals(ScheduleType.SCHEDULED, draft.scheduleType)
        assertEquals(7 * 60 + 15, draft.startMinuteOfDay)
        assertEquals(9 * 60 + 45, draft.endMinuteOfDay)
        assertEquals(AlarmSchedule.MONDAY or AlarmSchedule.FRIDAY, draft.daysOfWeekMask)
        assertTrue(draft.soundEnabled)
        assertFalse(draft.vibrateEnabled)
    }

    @Test
    fun draft_withNewCoordinates_preservesAllUncommittedDraftFields() {
        val originalDraft = ConfigureAlarmDraft(
            alarmId = "draft-id-99",
            label = "Custom Work Commute",
            radiusMeters = 1200,
            coordinates = GeoCoordinates(-6.2000, 106.8000),
            addressLabel = "Old Location",
            scheduleType = ScheduleType.SCHEDULED,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 10 * 60,
            daysOfWeekMask = AlarmSchedule.WEEKDAYS,
            soundEnabled = false,
            vibrateEnabled = true,
        )

        val updatedCoords = GeoCoordinates(-6.1754, 106.8272)
        val newDraft = originalDraft.withNewCoordinates(updatedCoords)

        // Coordinates updated
        assertEquals(updatedCoords, newDraft.coordinates)
        // Everything else 100% preserved
        assertEquals("draft-id-99", newDraft.alarmId)
        assertEquals("Custom Work Commute", newDraft.label)
        assertEquals(1200, newDraft.radiusMeters)
        assertEquals("Old Location", newDraft.addressLabel)
        assertEquals(ScheduleType.SCHEDULED, newDraft.scheduleType)
        assertEquals(8 * 60, newDraft.startMinuteOfDay)
        assertEquals(10 * 60, newDraft.endMinuteOfDay)
        assertEquals(AlarmSchedule.WEEKDAYS, newDraft.daysOfWeekMask)
        assertFalse(newDraft.soundEnabled)
        assertTrue(newDraft.vibrateEnabled)
    }

    @Test
    fun draft_toLocationAlarm_convertsScheduledAndOneTimeCorrectly() {
        // Scheduled
        val scheduledDraft = ConfigureAlarmDraft(
            alarmId = "alarm-sched",
            label = "Morning Train",
            radiusMeters = 500,
            coordinates = GeoCoordinates(-6.2, 106.8),
            scheduleType = ScheduleType.SCHEDULED,
            startMinuteOfDay = 420,
            endMinuteOfDay = 540,
            daysOfWeekMask = AlarmSchedule.WEEKDAYS,
            soundEnabled = true,
            vibrateEnabled = true,
        )
        val scheduledAlarm = scheduledDraft.toLocationAlarm()
        assertEquals("alarm-sched", scheduledAlarm.id)
        assertEquals("Morning Train", scheduledAlarm.label)
        assertEquals(500, scheduledAlarm.radius.meters)
        assertEquals(ScheduleType.SCHEDULED, scheduledAlarm.schedule.type)
        assertEquals(420, scheduledAlarm.schedule.startMinuteOfDay)
        assertEquals(540, scheduledAlarm.schedule.endMinuteOfDay)
        assertEquals(AlarmSchedule.WEEKDAYS, scheduledAlarm.schedule.daysOfWeekMask)
        assertEquals(AlarmState.SCHEDULED_IDLE, scheduledAlarm.state)

        // One-time
        val oneTimeDraft = ConfigureAlarmDraft(
            alarmId = null,
            label = "Weekend Trip",
            radiusMeters = 1000,
            coordinates = GeoCoordinates(-6.3, 106.9),
            scheduleType = ScheduleType.ONE_TIME,
        )
        val oneTimeAlarm = oneTimeDraft.toLocationAlarm()
        assertNotNull(oneTimeAlarm.id)
        assertTrue(oneTimeAlarm.id.isNotBlank())
        assertEquals("Weekend Trip", oneTimeAlarm.label)
        assertEquals(ScheduleType.ONE_TIME, oneTimeAlarm.schedule.type)
        assertEquals(AlarmState.ARMED_MONITORING, oneTimeAlarm.state)
    }

    @Test
    fun draft_toLocationAlarm_withExistingAlarm_preservesStateAndSuppression() {
        val existing = sampleAlarm().copy(
            state = AlarmState.COMPLETED_FOR_WINDOW,
            suppressedUntil = kotlin.time.Instant.fromEpochMilliseconds(1700000000000L),
        )
        val draft = ConfigureAlarmDraft.fromAlarm(existing).copy(label = "Updated Name")
        val result = draft.toLocationAlarm(existing)

        assertEquals("test-alarm-123", result.id)
        assertEquals("Updated Name", result.label)
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, result.state)
        assertEquals(kotlin.time.Instant.fromEpochMilliseconds(1700000000000L), result.suppressedUntil)
    }

    @Test
    fun viewModel_draftCoordination_roundtripMapPickerPreservesDraftState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val alarm = sampleAlarm()
        val observePort = object : ObserveAlarmsPort {
            override fun observeAlarms(): Flow<List<LocationAlarm>> = MutableStateFlow(listOf(alarm))
        }
        var savedAlarm: LocationAlarm? = null
        val savePort = object : SaveAlarmPort {
            override suspend fun saveAlarm(a: LocationAlarm): Result<Unit> {
                savedAlarm = a
                return Result.success(Unit)
            }
        }
        val togglePort = object : ToggleAlarmPort {
            override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
        }
        val dismissPort = object : DismissAlarmPort {
            override suspend fun dismissAlarm(alarmId: String): Result<Unit> = Result.success(Unit)
        }
        val skipPort = object : SkipTodayPort {
            override suspend fun skipToday(alarmId: String): Result<Unit> = Result.success(Unit)
        }
        val prefsPort = object : PreferencesPort {
            val themeFlow = MutableStateFlow(ThemeMode.SYSTEM)
            val localeFlow = MutableStateFlow(AppLocale.EN)
            override fun observeThemeMode(): Flow<ThemeMode> = themeFlow
            override suspend fun setThemeMode(mode: ThemeMode) { themeFlow.value = mode }
            override fun observeLocale(): Flow<AppLocale> = localeFlow
            override suspend fun setLocale(locale: AppLocale) { localeFlow.value = locale }
        }

        val viewModel = FjalarViewModel(
            observeAlarmsPort = observePort,
            saveAlarmPort = savePort,
            toggleAlarmPort = togglePort,
            dismissAlarmPort = dismissPort,
            skipTodayPort = skipPort,
            preferencesPort = prefsPort,
            coroutineScope = testScope,
        )

        // 1. Start configuring existing alarm
        viewModel.onStartConfiguringAlarm(alarm.id)
        val initialDraft = viewModel.uiState.value.activeDraft
        assertNotNull(initialDraft)
        assertEquals(alarm.label, initialDraft.label)

        // 2. User edits draft in ConfigureAlarmScreen
        val editedDraft = initialDraft.copy(
            label = "Edited Manggarai",
            radiusMeters = 2000,
            soundEnabled = false,
            vibrateEnabled = true,
        )
        viewModel.onUpdateDraft(editedDraft)
        assertEquals("Edited Manggarai", viewModel.uiState.value.activeDraft?.label)
        assertEquals(2000, viewModel.uiState.value.activeDraft?.radiusMeters)

        // 3. User navigates to MapPickerScreen and confirms new pin location
        val pickedCoords = GeoCoordinates(-6.1234, 106.9876)
        viewModel.onDraftCoordinatesChanged(pickedCoords)

        // Verify coordinates changed BUT all uncommitted fields remain intact!
        val preservedDraft = viewModel.uiState.value.activeDraft
        assertNotNull(preservedDraft)
        assertEquals(pickedCoords, preservedDraft.coordinates)
        assertEquals("Edited Manggarai", preservedDraft.label)
        assertEquals(2000, preservedDraft.radiusMeters)
        assertFalse(preservedDraft.soundEnabled)
        assertTrue(preservedDraft.vibrateEnabled)

        // 4. Save draft
        viewModel.onSaveDraftAlarm(preservedDraft)
        assertNotNull(savedAlarm)
        assertEquals("Edited Manggarai", savedAlarm?.label)
        assertEquals(pickedCoords, savedAlarm?.coordinates)
        assertEquals(2000, savedAlarm?.radius?.meters)
        assertNull(viewModel.uiState.value.activeDraft)
    }

    @Test
    fun pickerAndEditorStrings_resolveCorrectlyBilingually() {
        // Map Picker strings
        val enPickerHint = FjalarStrings.get(FjalarStringKey.PICKER_HINT, AppLocale.EN)
        val idPickerHint = FjalarStrings.get(FjalarStringKey.PICKER_HINT, AppLocale.ID)
        val enPickerConfirm = FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, AppLocale.EN)
        val idPickerConfirm = FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, AppLocale.ID)
        val enChangePin = FjalarStrings.get(FjalarStringKey.PICKER_CHANGE_PIN, AppLocale.EN)
        val idChangePin = FjalarStrings.get(FjalarStringKey.PICKER_CHANGE_PIN, AppLocale.ID)

        assertEquals("Pan map and drag pin to destination", enPickerHint)
        assertEquals("Geser peta dan arahkan pin ke tujuan", idPickerHint)
        assertEquals("Set Location", enPickerConfirm)
        assertEquals("Tetapkan Lokasi", idPickerConfirm)
        assertEquals("Change Pin on Map", enChangePin)
        assertEquals("Ubah Titik di Peta", idChangePin)

        // Configure Editor strings
        val enTitle = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, AppLocale.EN)
        val idTitle = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, AppLocale.ID)
        val enSave = FjalarStrings.get(FjalarStringKey.EDITOR_SAVE, AppLocale.EN)
        val idSave = FjalarStrings.get(FjalarStringKey.EDITOR_SAVE, AppLocale.ID)
        val enDelete = FjalarStrings.get(FjalarStringKey.EDITOR_DELETE_ALARM, AppLocale.EN)
        val idDelete = FjalarStrings.get(FjalarStringKey.EDITOR_DELETE_ALARM, AppLocale.ID)

        assertEquals("Configure Alarm", enTitle)
        assertEquals("Atur Alarm", idTitle)
        assertEquals("Save", enSave)
        assertEquals("Simpan", idSave)
        assertEquals("Delete Alarm", enDelete)
        assertEquals("Hapus Alarm", idDelete)
    }
    @Test
    fun missingDestinationCannotBecomeSavedAlarm() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ConfigureAlarmDraft(label = "Choose location first").toLocationAlarm()
        }
    }

}
