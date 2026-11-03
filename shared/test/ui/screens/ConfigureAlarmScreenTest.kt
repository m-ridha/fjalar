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
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Test
    fun newDraftUsesOneHundredMeterRadiusWithoutSelectingDestination() {
        val draft = ConfigureAlarmDraft()
        assertEquals(100, draft.radiusMeters)
        assertNull(draft.coordinates)
        assertNull(draft.copy(label = "New alarm").coordinates)
    }

    @Test
    fun unrelatedEditsPreserveSavedRadiusBeyondSliderRange() {
        val existing = sampleAlarm().copy(radius = TriggerRadius(5_000))
        val draft = ConfigureAlarmDraft.fromAlarm(existing).copy(label = "Updated label")

        assertEquals(5_000, draft.radiusMeters)
        assertEquals(5_000, draft.toLocationAlarm(existing).radius.meters)
        assertEquals(300, draft.copy(radiusMeters = 300).toLocationAlarm(existing).radius.meters)
    }

    @Test
    fun conversionPreservesExistingVolumeWhileApplyingEditableSoundFields() {
        val existing = sampleAlarm().copy(soundConfig = AlarmSoundConfig(volume = 0.35f))
        val draft = ConfigureAlarmDraft.fromAlarm(existing).copy(soundEnabled = false, vibrateEnabled = true)
        val result = draft.toLocationAlarm(existing)

        assertEquals(existing.soundConfig.volume, result.soundConfig.volume)
        assertFalse(result.soundConfig.soundEnabled)
        assertTrue(result.soundConfig.vibrateEnabled)
    }

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
    fun manuallyMovedPinDoesNotRestoreOldAddressOnSave() {
        val saved = sampleAlarm()
        val draft = ConfigureAlarmDraft.fromAlarm(saved).withNewCoordinates(GeoCoordinates(51.5, -0.1))
        assertNull(draft.toLocationAlarm(saved).addressLabel)
    }

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
        assertTrue(draft.enabled)
        assertNull(draft.enabledOverride)
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
            enabled = false,
            initialEnabled = true,
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
        assertNull(newDraft.addressLabel)
        assertEquals(ScheduleType.SCHEDULED, newDraft.scheduleType)
        assertEquals(8 * 60, newDraft.startMinuteOfDay)
        assertEquals(10 * 60, newDraft.endMinuteOfDay)
        assertEquals(AlarmSchedule.WEEKDAYS, newDraft.daysOfWeekMask)
        assertFalse(newDraft.enabled)
        assertEquals(false, newDraft.enabledOverride)
        assertTrue(newDraft.initialEnabled)
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
    fun keyboardTimeAcceptsExactMinutesAndRejectsBlankOrInvalidValues() {
        assertEquals(0, editorMinuteOfDay("00", "00"))
        assertEquals(1439, editorMinuteOfDay("23", "59"))
        assertEquals(7 * 60 + 13, editorMinuteOfDay("7", "13"))
        for ((hour, minute) in listOf("" to "15", "07" to "", "24" to "00", "23" to "60", "-1" to "00", "ab" to "10")) {
            assertNull(editorMinuteOfDay(hour, minute), "Invalid time $hour:$minute must not confirm")
        }
    }

    @Test
    fun unchangedEnableChoicePreservesLatestDismissalAndOffThenOnIsANoOp() {
        val original = sampleAlarm().copy(schedule = AlarmSchedule.ONE_TIME, state = AlarmState.ARMED_MONITORING)
        val latest = original.copy(state = AlarmState.DISABLED)
        val draft = ConfigureAlarmDraft.fromAlarm(original).copy(label = "New label")
        assertNull(draft.enabledOverride)
        assertEquals(AlarmState.DISABLED, draft.toLocationAlarm(latest).state)
        assertFalse(draft.displayedEnabled(latest))

        val restored = draft.withEnabled(false, true).withEnabled(true, false)
        assertNull(restored.enabledOverride)
        assertEquals(AlarmState.DISABLED, restored.toLocationAlarm(latest).state)

        // The persisted state can change while the editor is open. A new tap must
        // express intent relative to the switch currently displayed to the user.
        val explicitlyEnabled = draft.withEnabled(true, draft.displayedEnabled(latest))
        assertEquals(true, explicitlyEnabled.enabledOverride)
        assertEquals(AlarmState.ARMED_MONITORING, explicitlyEnabled.toLocationAlarm(latest).state)
    }

    @Test
    fun enableChoiceRoundTripsForBothModesAndNewDisabledDrafts() {
        for (type in ScheduleType.entries) {
            val existing = sampleAlarm().copy(state = AlarmState.DISABLED)
            val draft = ConfigureAlarmDraft.fromAlarm(existing).copy(scheduleType = type)
            assertFalse(draft.enabled)
            assertNull(draft.enabledOverride)
            assertEquals(AlarmState.DISABLED, draft.toLocationAlarm(existing).state)
            val enabled = draft.withEnabled(true, false)
            assertEquals(true, enabled.enabledOverride)
            assertTrue(enabled.toLocationAlarm(existing).isEnabled)

            val newDisabled = ConfigureAlarmDraft(
                label = "Later",
                coordinates = existing.coordinates,
                scheduleType = type,
                enabled = false,
            )
            assertEquals(false, newDisabled.enabledOverride)
            assertEquals(AlarmState.DISABLED, newDisabled.toLocationAlarm().state)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
                savedAlarm = alarm
                return Result.success(Unit)
            }
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> =
                saveAlarm(alarm)
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
        assertEquals("Edited Manggarai", savedAlarm.label)
        assertEquals(pickedCoords, savedAlarm.coordinates)
        assertEquals(2000, savedAlarm.radius.meters)
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

        assertEquals("Move the map to choose a destination", enPickerHint)
        assertEquals("Geser peta untuk memilih tujuan", idPickerHint)
        assertEquals("Choose destination", enPickerConfirm)
        assertEquals("Pilih tujuan", idPickerConfirm)
        assertEquals("Choose destination", enChangePin)
        assertEquals("Pilih tujuan", idChangePin)
        assertEquals("Destination", FjalarStrings.get(FjalarStringKey.PINNED_LOCATION, AppLocale.EN))
        assertEquals("Tujuan", FjalarStrings.get(FjalarStringKey.PINNED_LOCATION, AppLocale.ID))
        assertEquals("Scheduled", FjalarStrings.get(FjalarStringKey.EDITOR_MODE_SCHEDULED, AppLocale.EN))
        assertEquals("Terjadwal", FjalarStrings.get(FjalarStringKey.EDITOR_MODE_SCHEDULED, AppLocale.ID))

        assertEquals("Enable Alarm", FjalarStrings.get(FjalarStringKey.EDITOR_ENABLE_ALARM, AppLocale.EN))
        assertEquals("Aktifkan alarm", FjalarStrings.get(FjalarStringKey.EDITOR_ENABLE_ALARM, AppLocale.ID))
        assertEquals("Start time", FjalarStrings.get(FjalarStringKey.EDITOR_TIME_START_TITLE, AppLocale.EN))
        assertEquals("Waktu selesai", FjalarStrings.get(FjalarStringKey.EDITOR_TIME_END_TITLE, AppLocale.ID))
        assertEquals("Hour", FjalarStrings.get(FjalarStringKey.EDITOR_TIME_HOUR, AppLocale.EN))
        assertEquals("Menit", FjalarStrings.get(FjalarStringKey.EDITOR_TIME_MINUTE, AppLocale.ID))
        assertEquals("Confirm", FjalarStrings.get(FjalarStringKey.EDITOR_TIME_CONFIRM, AppLocale.EN))
        assertEquals("Konfirmasi", FjalarStrings.get(FjalarStringKey.EDITOR_TIME_CONFIRM, AppLocale.ID))

        // Configure Editor strings
        val enTitle = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, AppLocale.EN)
        val idTitle = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, AppLocale.ID)
        val enSave = FjalarStrings.get(FjalarStringKey.EDITOR_SAVE, AppLocale.EN)
        val idSave = FjalarStrings.get(FjalarStringKey.EDITOR_SAVE, AppLocale.ID)
        val enDelete = FjalarStrings.get(FjalarStringKey.EDITOR_DELETE_ALARM, AppLocale.EN)
        val idDelete = FjalarStrings.get(FjalarStringKey.EDITOR_DELETE_ALARM, AppLocale.ID)

        assertEquals("Configure Alarm", enTitle)
        assertEquals("Atur alarm", idTitle)
        assertEquals("Save", enSave)
        assertEquals("Simpan", idSave)
        assertEquals("Delete Alarm", enDelete)
        assertEquals("Hapus alarm", idDelete)
        assertEquals(
            "Changes are saved when you go back if all fields are valid.",
            FjalarStrings.get(FjalarStringKey.EDITOR_BACK_SAVES_CHANGES, AppLocale.EN),
        )
        assertEquals(
            "Perubahan disimpan saat kembali jika semua isian valid.",
            FjalarStrings.get(FjalarStringKey.EDITOR_BACK_SAVES_CHANGES, AppLocale.ID),
        )
        assertEquals(
            "This alarm no longer exists.",
            FjalarStrings.get(FjalarStringKey.EDITOR_ALARM_DELETED, AppLocale.EN),
        )
        assertEquals(
            "Alarm ini sudah tidak ada.",
            FjalarStrings.get(FjalarStringKey.EDITOR_ALARM_DELETED, AppLocale.ID),
        )
        assertEquals(
            "Could not finish updating the alarm. Try again.",
            FjalarStrings.get(FjalarStringKey.EDITOR_UPDATE_FAILED, AppLocale.EN),
        )
        assertEquals(
            "Alarm belum berhasil diperbarui. Coba lagi.",
            FjalarStrings.get(FjalarStringKey.EDITOR_UPDATE_FAILED, AppLocale.ID),
        )
    }
    @Test
    fun missingDestinationCannotBecomeSavedAlarm() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ConfigureAlarmDraft(label = "Choose location first").toLocationAlarm()
        }
    }

}

class DeleteInvalidDraftTest {
    @Test fun deletionUsesPersistedAlarmWithIncompleteDraft() {
        val saved = LocationAlarm("saved", "Office", GeoCoordinates(1.0, 2.0), TriggerRadius(500), AlarmSchedule.ONE_TIME)
        val draft = ConfigureAlarmDraft.fromAlarm(saved).copy(label = "", startMinuteOfDay = 420, endMinuteOfDay = 420)
        assertEquals(saved, draft.deletionTarget(listOf(saved)))
    }
}
