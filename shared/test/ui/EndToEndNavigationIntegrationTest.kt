package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.ui.navigation.AlarmsRoute
import com.muhammadridha.fjalar.ui.navigation.ConfigureRoute
import com.muhammadridha.fjalar.ui.navigation.MapPickerRoute
import com.muhammadridha.fjalar.ui.navigation.MapRoute
import com.muhammadridha.fjalar.ui.navigation.NavigationTab
import com.muhammadridha.fjalar.ui.navigation.isAlarmsRoute
import com.muhammadridha.fjalar.ui.navigation.isBottomBarVisible
import com.muhammadridha.fjalar.ui.navigation.isMapRoute
import com.muhammadridha.fjalar.ui.navigation.resolveActiveTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for multi-screen navigation wiring,
 * draft coordination across screens, and Invariant 3 ringing alarm precedence.
 */
class EndToEndNavigationIntegrationTest {

    private class FakeObserveAlarmsPort(initialAlarms: List<LocationAlarm> = emptyList()) : ObserveAlarmsPort {
        val flow = MutableStateFlow(initialAlarms)
        override fun observeAlarms(): Flow<List<LocationAlarm>> = flow
    }

    private class FakeSaveAlarmPort : SaveAlarmPort {
        val savedAlarms = mutableListOf<LocationAlarm>()
        override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
            savedAlarms.add(alarm)
            return Result.success(Unit)
        }
    }

    private class FakeToggleAlarmPort : ToggleAlarmPort {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> {
            toggled.add(alarmId to enabled)
            return Result.success(Unit)
        }
    }

    private class FakeDismissAlarmPort : DismissAlarmPort {
        val dismissed = mutableListOf<String>()
        override suspend fun dismissAlarm(alarmId: String): Result<Unit> {
            dismissed.add(alarmId)
            return Result.success(Unit)
        }
    }

    private class FakeSkipTodayPort : SkipTodayPort {
        val skipped = mutableListOf<String>()
        override suspend fun skipToday(alarmId: String): Result<Unit> {
            skipped.add(alarmId)
            return Result.success(Unit)
        }
    }

    private class FakeDeleteAlarmPort : DeleteAlarmPort {
        val deleted = mutableListOf<String>()
        override suspend fun deleteAlarm(alarmId: String): Result<Unit> {
            deleted.add(alarmId)
            return Result.success(Unit)
        }
    }

    private class FakeMapRendererPort : MapRendererPort {
        val pins = mutableListOf<GeoCoordinates>()
        val geofences = mutableListOf<Pair<GeoCoordinates, TriggerRadius>>()
        var cleared = false

        override fun placePin(coordinates: GeoCoordinates) {
            pins.add(coordinates)
        }

        override fun showGeofence(center: GeoCoordinates, radius: TriggerRadius) {
            geofences.add(center to radius)
        }

        override fun clearOverlays() {
            cleared = true
        }

        override fun setViewport(center: GeoCoordinates, zoom: Double) {}
    }

    private fun sampleAlarm(
        id: String = "alarm-manggarai",
        state: AlarmState = AlarmState.ARMED_MONITORING,
    ): LocationAlarm = LocationAlarm(
        id = id,
        label = "Manggarai Station",
        coordinates = GeoCoordinates(-6.2088, 106.8456),
        radius = TriggerRadius(500),
        schedule = AlarmSchedule(
            type = ScheduleType.SCHEDULED,
            startMinuteOfDay = 7 * 60,
            endMinuteOfDay = 9 * 60,
            daysOfWeekMask = AlarmSchedule.WEEKDAYS,
        ),
        soundConfig = AlarmSoundConfig(soundEnabled = true, vibrateEnabled = true),
        state = state,
        addressLabel = "Manggarai Central",
    )

    @Test
    fun destinationResolution_and_routeDefinitions() {
        // MapRoute
        assertTrue(isMapRoute(MapRoute))
        assertTrue(isMapRoute("com.muhammadridha.fjalar.ui.navigation.MapRoute"))
        assertEquals(NavigationTab.MAP, resolveActiveTab(MapRoute))

        // AlarmsRoute
        assertTrue(isAlarmsRoute(AlarmsRoute))
        assertTrue(isAlarmsRoute("com.muhammadridha.fjalar.ui.navigation.AlarmsRoute"))
        assertEquals(NavigationTab.ALARMS, resolveActiveTab(AlarmsRoute))

        // ConfigureRoute
        val defaultConfigure = ConfigureRoute()
        assertNull(defaultConfigure.alarmId)
        assertFalse(isMapRoute(defaultConfigure))
        assertFalse(isAlarmsRoute(defaultConfigure))
        assertNull(resolveActiveTab(defaultConfigure))

        val editConfigure = ConfigureRoute(alarmId = "alarm-manggarai")
        assertEquals("alarm-manggarai", editConfigure.alarmId)
        assertFalse(isMapRoute(editConfigure))
        assertFalse(isAlarmsRoute(editConfigure))
        assertNull(resolveActiveTab(editConfigure))

        // MapPickerRoute
        val picker = MapPickerRoute(initialLat = -6.2088, initialLon = 106.8456)
        assertEquals(-6.2088, picker.initialLat)
        assertEquals(106.8456, picker.initialLon)
        assertFalse(isMapRoute(picker))
        assertFalse(isAlarmsRoute(picker))
        assertNull(resolveActiveTab(picker))
    }

    @Test
    fun bottomNavigationBar_visibilityLogic() {
        // Top-level tabs should display the bottom bar
        assertTrue(isBottomBarVisible(MapRoute))
        assertTrue(isBottomBarVisible("com.muhammadridha.fjalar.ui.navigation.MapRoute"))
        assertTrue(isBottomBarVisible(AlarmsRoute))
        assertTrue(isBottomBarVisible("com.muhammadridha.fjalar.ui.navigation.AlarmsRoute"))
        assertTrue(isBottomBarVisible(null)) // Start destination default

        // Sub-screens (full-screen modal flows) must hide bottom bar
        assertFalse(isBottomBarVisible(ConfigureRoute()))
        assertFalse(isBottomBarVisible(ConfigureRoute("alarm-1")))
        assertFalse(isBottomBarVisible("com.muhammadridha.fjalar.ui.navigation.ConfigureRoute/{alarmId}"))
        assertFalse(isBottomBarVisible("com.muhammadridha.fjalar.ui.navigation.ConfigureRoute?alarmId={alarmId}"))

        assertFalse(isBottomBarVisible(MapPickerRoute(-6.2088, 106.8456)))
        assertFalse(isBottomBarVisible("com.muhammadridha.fjalar.ui.navigation.MapPickerRoute/{initialLat}/{initialLon}"))
    }

    @Test
    fun draftCoordinationFlow_fromMap_toConfigure_toPicker_andSave() = runTest {
        val vmScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val saveAlarmPort = FakeSaveAlarmPort()
        val observeAlarmsPort = FakeObserveAlarmsPort()
        val mapRendererPort = FakeMapRendererPort()
        val prefs = PersistentPreferencesAdapter(InMemoryKeyValueStorage())

        val viewModel = FjalarViewModel(
            observeAlarmsPort = observeAlarmsPort,
            saveAlarmPort = saveAlarmPort,
            toggleAlarmPort = FakeToggleAlarmPort(),
            dismissAlarmPort = FakeDismissAlarmPort(),
            skipTodayPort = FakeSkipTodayPort(),
            preferencesPort = prefs,
            mapRendererPort = mapRendererPort,
            coroutineScope = vmScope,
        )

        try {
            // 1. Initial State on Map: no active draft
            assertNull(viewModel.uiState.value.activeDraft)

            // 2. User initiates new alarm configuration from Map or Bottom Bar (+)
            val defaultLocation = GeoCoordinates(-6.2088, 106.8456)
            viewModel.onStartConfiguringAlarm(alarmId = null, defaultCoordinates = defaultLocation)

            val initialDraft = viewModel.uiState.value.activeDraft
            assertNotNull(initialDraft)
            assertEquals(defaultLocation, initialDraft.coordinates)
            assertEquals(500, initialDraft.radiusMeters)

            // 3. User enters label in Configure screen
            val draftWithLabel = initialDraft.copy(label = "Sudirman MRT Hub")
            viewModel.onUpdateDraft(draftWithLabel)
            assertEquals("Sudirman MRT Hub", viewModel.uiState.value.activeDraft?.label)

            // 4. User navigates to MapPickerScreen to refine position
            val pickerRoute = MapPickerRoute(
                initialLat = initialDraft.coordinates!!.latitude,
                initialLon = initialDraft.coordinates!!.longitude,
            )
            assertEquals(-6.2088, pickerRoute.initialLat)
            assertEquals(106.8456, pickerRoute.initialLon)

            // 5. In MapPicker, user selects new coordinates:
            val refinedCoordinates = GeoCoordinates(-6.2000, 106.8228)
            viewModel.onDraftCoordinatesChanged(refinedCoordinates)

            // Verify uncommitted state is preserved: label is NOT wiped out!
            val updatedDraft = viewModel.uiState.value.activeDraft
            assertNotNull(updatedDraft)
            assertEquals(refinedCoordinates, updatedDraft.coordinates)
            assertEquals("Sudirman MRT Hub", updatedDraft.label)
            assertEquals(500, updatedDraft.radiusMeters)

            // 6. User modifies radius to 1000m
            viewModel.onUpdateDraft(updatedDraft.copy(radiusMeters = 1000))
            assertEquals(1000, viewModel.uiState.value.activeDraft?.radiusMeters)

            // 7. User saves the draft
            viewModel.onSaveDraftAlarm(viewModel.uiState.value.activeDraft!!)

            // Verify alarm was committed to port with exact fields
            assertEquals(1, saveAlarmPort.savedAlarms.size)
            val saved = saveAlarmPort.savedAlarms.first()
            assertEquals("Sudirman MRT Hub", saved.label)
            assertEquals(refinedCoordinates, saved.coordinates)
            assertEquals(1000, saved.radius.meters)
            assertEquals(AlarmState.SCHEDULED_IDLE, saved.state)

            // Verify active draft is reset after successful save
            assertNull(viewModel.uiState.value.activeDraft)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun draftCoordinationFlow_editExistingAlarm_preservesExistingIdAndSchedule() = runTest {
        val vmScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val existing = sampleAlarm(id = "existing-alarm-99")
        val saveAlarmPort = FakeSaveAlarmPort()
        val observeAlarmsPort = FakeObserveAlarmsPort(listOf(existing))
        val prefs = PersistentPreferencesAdapter(InMemoryKeyValueStorage())

        val viewModel = FjalarViewModel(
            observeAlarmsPort = observeAlarmsPort,
            saveAlarmPort = saveAlarmPort,
            toggleAlarmPort = FakeToggleAlarmPort(),
            dismissAlarmPort = FakeDismissAlarmPort(),
            skipTodayPort = FakeSkipTodayPort(),
            preferencesPort = prefs,
            coroutineScope = vmScope,
        )

        try {
            // User taps to edit existing alarm
            viewModel.onStartConfiguringAlarm(alarmId = "existing-alarm-99")
            val draft = viewModel.uiState.value.activeDraft
            assertNotNull(draft)
            assertEquals("existing-alarm-99", draft.alarmId)
            assertEquals("Manggarai Station", draft.label)
            assertEquals(500, draft.radiusMeters)

            // User updates label and saves
            viewModel.onUpdateDraft(draft.copy(label = "Manggarai Station Renovated"))
            viewModel.onSaveDraftAlarm(viewModel.uiState.value.activeDraft!!)

            assertEquals(1, saveAlarmPort.savedAlarms.size)
            val saved = saveAlarmPort.savedAlarms.first()
            assertEquals("existing-alarm-99", saved.id)
            assertEquals("Manggarai Station Renovated", saved.label)
            assertNull(viewModel.uiState.value.activeDraft)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun safetyInvariant3_ringingAlarmPrecedence_overAllScreens() = runTest {
        val vmScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val dismissAlarmPort = FakeDismissAlarmPort()
        val observeAlarmsPort = FakeObserveAlarmsPort()
        val prefs = PersistentPreferencesAdapter(InMemoryKeyValueStorage())

        val viewModel = FjalarViewModel(
            observeAlarmsPort = observeAlarmsPort,
            saveAlarmPort = FakeSaveAlarmPort(),
            toggleAlarmPort = FakeToggleAlarmPort(),
            dismissAlarmPort = dismissAlarmPort,
            skipTodayPort = FakeSkipTodayPort(),
            preferencesPort = prefs,
            coroutineScope = vmScope,
        )

        try {
            // 1. Initially no ringing alarm
            assertNull(viewModel.uiState.value.activeRingingAlarm)

            // 2. Alarm triggers -> state becomes RINGING
            val ringingAlarm = sampleAlarm(id = "ringing-alarm-1", state = AlarmState.RINGING)
            observeAlarmsPort.flow.value = listOf(ringingAlarm)

            // Invariant 3: activeRingingAlarm is immediately available to render overlay over ANY screen
            assertEquals(ringingAlarm, viewModel.uiState.value.activeRingingAlarm)

            // 3. Dismissal via oversized slide track dispatches to port
            viewModel.onDismissRingingAlarm("ringing-alarm-1")
            assertEquals(listOf("ringing-alarm-1"), dismissAlarmPort.dismissed)
        } finally {
            vmScope.cancel()
        }
    }
}
