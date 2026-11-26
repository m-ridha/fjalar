package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.AlarmConfigurationMissingException
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FjalarViewModelTest {

    private class FakeObserveAlarmsPort(initialAlarms: List<LocationAlarm> = emptyList()) : ObserveAlarmsPort {
        val flow = MutableStateFlow(initialAlarms)
        override fun observeAlarms(): Flow<List<LocationAlarm>> = flow
    }

    private class FakeSaveAlarmPort(
        var shouldFail: Boolean = false,
    ) : SaveAlarmPort {
        val savedAlarms = mutableListOf<LocationAlarm>()
        var configurationCalls = 0
        override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
            if (shouldFail) {
                return Result.failure(IllegalStateException("Simulated save failure"))
            }
            savedAlarms.add(alarm)
            return Result.success(Unit)
        }
        override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
            configurationCalls++
            return saveAlarm(alarm)
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

    private class FakeSyncSchedulesPort : SyncSchedulesPort {
        var syncCount = 0
        override suspend fun syncSchedules(): List<LocationAlarm> {
            syncCount++
            return emptyList()
        }
    }

    private class FakeMapRendererPort : MapRendererPort {
        var lastViewport: Pair<GeoCoordinates, Double>? = null
        var lastPin: GeoCoordinates? = null
        var lastGeofence: Pair<GeoCoordinates, TriggerRadius>? = null
        var overlaysCleared: Boolean = false

        override fun setViewport(center: GeoCoordinates, zoom: Double) {
            lastViewport = center to zoom
        }

        override fun placePin(coordinates: GeoCoordinates) {
            lastPin = coordinates
        }

        override fun showGeofence(center: GeoCoordinates, radius: TriggerRadius) {
            lastGeofence = center to radius
        }

        override fun clearOverlays() {
            overlaysCleared = true
            lastPin = null
            lastGeofence = null
        }
    }

    private class FakeSearchPlacesPort(
        var places: List<SearchResultPlace> = emptyList(),
    ) : SearchPlacesPort {
        var lastQuery: String? = null
        var lastProximity: GeoCoordinates? = null
        var searchCallCount: Int = 0

        override suspend fun searchPlaces(
            query: String,
            locale: AppLocale,
            proximity: GeoCoordinates?,
        ): List<SearchResultPlace> {
            searchCallCount++
            lastQuery = query
            lastProximity = proximity
            return places.filter { it.matchesQuery(query) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.createTestViewModel(
        observePort: ObserveAlarmsPort = FakeObserveAlarmsPort(),
        savePort: SaveAlarmPort = FakeSaveAlarmPort(),
        togglePort: ToggleAlarmPort = FakeToggleAlarmPort(),
        dismissPort: DismissAlarmPort = FakeDismissAlarmPort(),
        skipTodayPort: SkipTodayPort = FakeSkipTodayPort(),
        deleteAlarmPort: DeleteAlarmPort? = null,
        syncSchedulesPort: SyncSchedulesPort? = null,
        mapRendererPort: MapRendererPort? = null,
        locationTrackerPort: LocationTrackerPort? = null,
        searchPlacesPort: SearchPlacesPort? = null,
        snoozePort: com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort? = null,
        recentDestinationsPort: RecentDestinationsPort? = null,
        synchronizeAlarmsUseCase: com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase? = null,
        clock: kotlin.time.Clock = kotlin.time.Clock.System,
        storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage(),
    ): Triple<FjalarViewModel, PersistentPreferencesAdapter, CoroutineScope> {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        val preferencesPort = PersistentPreferencesAdapter(storage)
        val viewModel = FjalarViewModel(
            observeAlarmsPort = observePort,
            saveAlarmPort = savePort,
            toggleAlarmPort = togglePort,
            dismissAlarmPort = dismissPort,
            skipTodayPort = skipTodayPort,
            preferencesPort = preferencesPort,
            syncSchedulesPort = syncSchedulesPort,
            mapRendererPort = mapRendererPort,
            locationTrackerPort = locationTrackerPort,
            searchPlacesPort = searchPlacesPort,
            deleteAlarmPort = deleteAlarmPort,
            coroutineScope = vmScope,
            snoozeAlarmPort = snoozePort,
            clock = clock,
            recentDestinationsPort = recentDestinationsPort,
            synchronizeAlarmsUseCase = synchronizeAlarmsUseCase,
        )
        return Triple(viewModel, preferencesPort, vmScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun foregroundSnoozeDeadlineSynchronizesWithoutLocationAndCancelsInBackground() = runTest {
        val clock = com.muhammadridha.fjalar.fakes.FakeClock(kotlin.time.Clock.System.now())
        val deadline = clock.now + kotlin.time.Duration.parse("2m")
        val alarm = manggaraiAlarm().copy(
            state = AlarmState.SNOOZED,
            snoozedUntil = deadline,
        )
        val observe = FakeObserveAlarmsPort(listOf(alarm))
        val sync = FakeSyncSchedulesPort()
        val (vm, _, scope) = createTestViewModel(observePort = observe, syncSchedulesPort = sync, clock = clock)
        try {
            vm.onAppForegroundChanged(true)
            testScheduler.advanceTimeBy(60_000)
            vm.onAppForegroundChanged(false)
            testScheduler.advanceTimeBy(120_000)
            assertEquals(0, sync.syncCount)
            vm.onAppForegroundChanged(true)
            // A backward wall-clock change at the first wake must re-arm the timer.
            clock.now = deadline - kotlin.time.Duration.parse("1.0005s")
            testScheduler.advanceTimeBy(120_001)
            testScheduler.runCurrent()
            assertEquals(0, sync.syncCount)
            clock.now = deadline
            testScheduler.advanceTimeBy(1_002)
            testScheduler.runCurrent()
            assertEquals(1, sync.syncCount)
            // A synchronization which saw an earlier clock emits no state change.
            testScheduler.advanceTimeBy(1_001)
            testScheduler.runCurrent()
            assertEquals(2, sync.syncCount)
            observe.flow.value = listOf(alarm.copy(state = AlarmState.RINGING, snoozedUntil = null))
            testScheduler.advanceTimeBy(2_000)
            assertEquals(2, sync.syncCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ringingOwnerUsesSameIdOrderingAsPlaybackAndAdvances() = runTest {
        val a = manggaraiAlarm().copy(id = "a", state = AlarmState.RINGING)
        val b = a.copy(id = "b")
        val observe = FakeObserveAlarmsPort(listOf(b, a))
        val (vm, _, scope) = createTestViewModel(observePort = observe)
        try {
            assertEquals("a", vm.uiState.value.activeRingingAlarm?.id)
            observe.flow.value = listOf(b, a.copy(state = AlarmState.SNOOZED))
            assertEquals("b", vm.uiState.value.activeRingingAlarm?.id)
        } finally { scope.cancel() }
    }

    @Test
    fun notificationDenialExposesSnoozeSettingsGuidance() = runTest {
        val port = object : com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort {
            override suspend fun snoozeAlarm(alarmId: String): Result<Unit> = Result.failure(
                com.muhammadridha.fjalar.domain.ports.outbound.SnoozeDeliveryUnavailableException()
            )
        }
        val (vm, _, scope) = createTestViewModel(snoozePort = port)
        try {
            vm.onSnoozeRingingAlarm("a")
            assertTrue(vm.uiState.value.hasOperationFailure)
            assertTrue(vm.uiState.value.snoozeDeliveryUnavailable)
        } finally { scope.cancel() }
    }

    @Test
    fun snoozeShowsPendingThenFailureAndAllowsRetry() = runTest {
        val completion = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
        var calls = 0
        val port = object : com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort {
            override suspend fun snoozeAlarm(alarmId: String): Result<Unit> {
                calls++
                return if (calls == 1) completion.await() else Result.success(Unit)
            }
        }
        val (vm, _, scope) = createTestViewModel(snoozePort = port)
        try {
            vm.onSnoozeRingingAlarm("a")
            assertTrue(vm.uiState.value.isOperationPending)
            vm.onSnoozeRingingAlarm("a")
            assertEquals(1, calls)
            completion.complete(Result.failure(IllegalStateException("stop failed")))
            assertTrue(vm.uiState.value.hasOperationFailure)
            assertFalse(vm.uiState.value.isOperationPending)
            vm.onSnoozeRingingAlarm("a")
            assertEquals(2, calls)
            assertFalse(vm.uiState.value.hasOperationFailure)
        } finally { scope.cancel() }
    }

    @Test
    fun failedStopIsRetryableAndSuccessWaitsForServiceState() = runTest {
        val alarm = manggaraiAlarm().copy(state = AlarmState.RINGING)
        val observe = FakeObserveAlarmsPort(listOf(alarm))
        var calls = 0
        val port = object : DismissAlarmPort {
            override suspend fun dismissAlarm(alarmId: String): Result<Unit> {
                calls++
                return if (calls <= 2) Result.failure(IllegalStateException("stop failed"))
                else Result.success(Unit)
            }
        }
        val (vm, _, scope) = createTestViewModel(observePort = observe, dismissPort = port)
        try {
            vm.onDismissRingingAlarm(alarm.id)
            assertEquals(1L, vm.uiState.value.ringingOperationFailureId)
            assertEquals(alarm, vm.uiState.value.activeRingingAlarm)
            vm.onDismissRingingAlarm(alarm.id)
            assertEquals(2L, vm.uiState.value.ringingOperationFailureId)
            vm.onDismissRingingAlarm(alarm.id)
            assertFalse(vm.uiState.value.hasOperationFailure)
            assertEquals(alarm, vm.uiState.value.activeRingingAlarm)
            observe.flow.value = listOf(alarm.copy(state = AlarmState.COMPLETED_FOR_WINDOW))
            assertNull(vm.uiState.value.activeRingingAlarm)
        } finally { scope.cancel() }
    }

    @Test
    fun initialStateLoadsAlarmsAndPreferences() = runTest {
        val initialAlarm = manggaraiAlarm()
        val observePort = FakeObserveAlarmsPort(listOf(initialAlarm))
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.DARK.name)
            putString(PersistentPreferencesAdapter.KEY_LOCALE, AppLocale.ID.name)
        }

        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = observePort,
            storage = storage,
        )
        try {
            val state = viewModel.uiState.value
            assertEquals(listOf(initialAlarm), state.alarms)
            assertEquals(ThemeMode.DARK, state.themeMode)
            assertEquals(AppLocale.ID, state.appLocale)
            assertNull(state.activeRingingAlarm)
            assertNull(state.activeDraft)
            assertEquals("", state.searchQuery)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleAlarmDispatchesToPort() = runTest {
        val togglePort = FakeToggleAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(togglePort = togglePort)
        try {
            viewModel.onToggleAlarm("manggarai", false)
            assertEquals(listOf("manggarai" to false), togglePort.toggled)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun saveAlarmEditorDispatchesToPortAndClearsActiveDraft() = runTest {
        val savePort = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(savePort = savePort)
        try {
            val newAlarm = manggaraiAlarm()
            viewModel.onStartConfiguringAlarm(null, newAlarm.coordinates)
            assertNotNull(viewModel.uiState.value.activeDraft)
            viewModel.onUpdateDraft(com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft.fromAlarm(newAlarm))

            var exited = false
            viewModel.onSaveAlarmEditor { exited = true }

            assertTrue(exited)
            assertEquals(1, savePort.savedAlarms.size)
            assertEquals(newAlarm.label, savePort.savedAlarms.single().label)
            assertNull(viewModel.uiState.value.activeDraft)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun deleteAlarmDispatchesToPortAndClearsMatchingDraft() = runTest {
        val deletePort = FakeDeleteAlarmPort()
        val alarm = manggaraiAlarm()
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(alarm)),
            deleteAlarmPort = deletePort,
        )
        try {
            viewModel.onStartConfiguringAlarm(alarm.id)
            assertNotNull(viewModel.uiState.value.activeDraft)

            viewModel.onDeleteAlarm(alarm.id)

            assertEquals(listOf(alarm.id), deletePort.deleted)
            assertNull(viewModel.uiState.value.activeDraft)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleThemeSwitchesBetweenModes() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.LIGHT.name)
        }
        val (viewModel, _, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme()
            assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme()
            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleLocaleSwitchesBetweenEnAndId() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_LOCALE, AppLocale.EN.name)
        }
        val (viewModel, _, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(AppLocale.EN, viewModel.uiState.value.appLocale)

            viewModel.onToggleLocale()
            assertEquals(AppLocale.ID, viewModel.uiState.value.appLocale)

            viewModel.onToggleLocale()
            assertEquals(AppLocale.EN, viewModel.uiState.value.appLocale)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onSelectLocale_updatesPreferencesAndUiState() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_LOCALE, AppLocale.EN.name)
        }
        val (viewModel, _, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(AppLocale.EN, viewModel.uiState.value.appLocale)

            viewModel.onSelectLocale(AppLocale.ID)
            assertEquals(AppLocale.ID, viewModel.uiState.value.appLocale)

            viewModel.onSelectLocale(AppLocale.EN)
            assertEquals(AppLocale.EN, viewModel.uiState.value.appLocale)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun localeChangesRefreshScheduledNotificationContentAndReportFailures() = runTest {
        val sync = FakeSyncSchedulesPort()
        val (viewModel, _, vmScope) = createTestViewModel(syncSchedulesPort = sync)
        try {
            val initial = sync.syncCount
            viewModel.onSelectLocale(AppLocale.ID)
            assertEquals(initial + 1, sync.syncCount)
            viewModel.onSelectLocale(AppLocale.ID)
            assertEquals(initial + 1, sync.syncCount)
            viewModel.onToggleLocale()
            assertEquals(initial + 2, sync.syncCount)
        } finally {
            vmScope.cancel()
        }

        val failingSync = object : SyncSchedulesPort {
            override suspend fun syncSchedules(): List<LocationAlarm> = error("Reschedule failed")
        }
        val (failingViewModel, _, failingScope) = createTestViewModel(syncSchedulesPort = failingSync)
        try {
            failingViewModel.onSelectLocale(AppLocale.ID)
            assertEquals(AppLocale.ID, failingViewModel.uiState.value.appLocale)
            assertTrue(failingViewModel.uiState.value.hasOperationFailure)
        } finally {
            failingScope.cancel()
        }
    }

    @Test
    fun ringingAlarmDetectionExtractsFirstRingingAlarm() = runTest {
        val alarm1 = manggaraiAlarm().copy(id = "1", state = AlarmState.ARMED_MONITORING)
        val alarm2 = manggaraiAlarm().copy(id = "2", state = AlarmState.RINGING)
        val observePort = FakeObserveAlarmsPort(listOf(alarm1))

        val (viewModel, _, vmScope) = createTestViewModel(observePort = observePort)
        try {
            assertNull(viewModel.uiState.value.activeRingingAlarm)

            // Alarm 2 starts ringing
            observePort.flow.value = listOf(alarm1, alarm2)
            assertEquals(alarm2, viewModel.uiState.value.activeRingingAlarm)

            // Alarm 2 silenced
            observePort.flow.value = listOf(alarm1, alarm2.copy(state = AlarmState.COMPLETED_FOR_WINDOW))
            assertNull(viewModel.uiState.value.activeRingingAlarm)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun dismissRingingAlarmDispatchesToPort() = runTest {
        val dismissPort = FakeDismissAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(dismissPort = dismissPort)
        try {
            viewModel.onDismissRingingAlarm("alarm-ring-123")
            assertEquals(listOf("alarm-ring-123"), dismissPort.dismissed)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun skipTodayDispatchesToPort() = runTest {
        val skipPort = FakeSkipTodayPort()
        val (viewModel, _, vmScope) = createTestViewModel(skipTodayPort = skipPort)
        try {
            viewModel.onSkipToday("manggarai")
            assertEquals(listOf("manggarai"), skipPort.skipped)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun searchQueryUpdatesState() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            viewModel.onSearchQueryChanged("Sudirman")
            assertEquals("Sudirman", viewModel.uiState.value.searchQuery)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleThemeFromSystemSwitchesToDark() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        }
        val (viewModel, preferences, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme()
            assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
            assertEquals(ThemeMode.DARK, preferences.observeThemeMode().first())
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleThemeFromSystemWhenOsIsDarkSwitchesToLight() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        }
        val (viewModel, preferences, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme(currentlyDark = true)
            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
            assertEquals(ThemeMode.LIGHT, preferences.observeThemeMode().first())
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun syncSchedules_invokesSyncSchedulesPort() = runTest {
        val fakeSyncPort = FakeSyncSchedulesPort()
        val (viewModel, _, vmScope) = createTestViewModel(syncSchedulesPort = fakeSyncPort)
        try {
            viewModel.syncSchedules().join()
            assertEquals(1, fakeSyncPort.syncCount)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun draftDestinationSelectionUpdatesCoordinatesAndMapRenderer() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2000, 106.8166)
            viewModel.onDraftCoordinatesChanged(coords)

            assertEquals(coords, viewModel.uiState.value.selectedCoordinates)
            assertEquals(coords, fakeMapRenderer.lastPin)
            assertEquals(coords to TriggerRadius.DEFAULT, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun draftDestinationRefinementUpdatesCoordinates() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val initialCoords = GeoCoordinates(-6.2000, 106.8166)
            val draggedCoords = GeoCoordinates(-6.2100, 106.8200)

            viewModel.onDraftCoordinatesChanged(initialCoords)
            viewModel.onDraftCoordinatesChanged(draggedCoords)

            assertEquals(draggedCoords, viewModel.uiState.value.selectedCoordinates)
            assertEquals(draggedCoords, fakeMapRenderer.lastPin)
            assertEquals(draggedCoords to TriggerRadius.DEFAULT, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun draftRadiusChangeUpdatesSelectedRadiusAndGeofenceOverlay() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2000, 106.8166)
            val newRadius = TriggerRadius(1000)

            viewModel.onDraftCoordinatesChanged(coords)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(radiusMeters = newRadius.meters))

            assertEquals(newRadius, viewModel.uiState.value.selectedRadius)
            assertEquals(coords to newRadius, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_centersOnUserLocationWhenAvailable() = runTest {
        val tracker = UiLocationTracker()
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(locationTrackerPort = tracker, mapRendererPort = fakeMapRenderer)
        try {
            val userLocation = GeoCoordinates(-6.1754, 106.8272)
            tracker.emit(userLocation)

            viewModel.onRecenterMap()

            assertEquals(userLocation, viewModel.uiState.value.mapViewportCenter)
            assertEquals(15.0, viewModel.uiState.value.mapViewportZoom)
            assertEquals(userLocation to 15.0, fakeMapRenderer.lastViewport)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_usesNeutralOverviewWhenUserLocationUnavailable() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            assertNull(viewModel.uiState.value.userLocation)
            assertNull(viewModel.uiState.value.selectedCoordinates)

            viewModel.onRecenterMap()

            assertEquals(GeoCoordinates(0.0, 0.0), viewModel.uiState.value.mapViewportCenter)
            assertEquals(2.0, viewModel.uiState.value.mapViewportZoom)
            assertEquals(GeoCoordinates(0.0, 0.0) to 2.0, fakeMapRenderer.lastViewport)
        } finally {
            vmScope.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closedOrReplacedSearchIgnoresResponseThatOutlivesCancellation() = runTest {
        for (nextContext in listOf(null, PlaceSearchContext.BROWSE)) {
            val response = kotlinx.coroutines.CompletableDeferred<List<SearchResultPlace>>()
            val oldPlace = SearchResultPlace(
                id = "old", name = "Old picker result",
                coordinates = GeoCoordinates(51.5, -0.1), category = PlaceCategory.GENERAL,
            )
            val currentPlace = oldPlace.copy(id = "current", name = "Current browse result")
            val port = object : SearchPlacesPort {
                var calls = 0
                override suspend fun searchPlaces(
                    query: String, locale: AppLocale, proximity: GeoCoordinates?,
                ): List<SearchResultPlace> {
                    calls++
                    return if (calls == 1) {
                        // Native/remote callbacks can finish even after their caller cancels.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { response.await() }
                    } else listOf(currentPlace)
                }
            }
            val (viewModel, _, vmScope) = createTestViewModel(searchPlacesPort = port)
            try {
                viewModel.onStartConfiguringAlarm(null)
                val originalDraft = viewModel.uiState.value.activeDraft
                viewModel.onOpenSearch(PlaceSearchContext.PICKER)
                viewModel.onSearchQueryChanged("Old")
                testScheduler.advanceTimeBy(FjalarViewModel.SEARCH_DEBOUNCE_MILLIS)
                testScheduler.runCurrent()
                if (nextContext == null) {
                    viewModel.onDismissSearch()
                } else {
                    viewModel.onOpenSearch(nextContext)
                    viewModel.onSearchQueryChanged("Current")
                    testScheduler.advanceUntilIdle()
                }
                response.complete(listOf(oldPlace))
                testScheduler.runCurrent()

                val state = viewModel.uiState.value
                assertEquals(nextContext, state.searchContext)
                assertEquals(nextContext != null, state.isSearchSheetVisible)
                assertEquals(if (nextContext == null) emptyList() else listOf(currentPlace), state.searchResults)
                assertFalse(state.isSearching)
                assertEquals(originalDraft, state.activeDraft)
                assertNull(state.selectedCoordinates)
                assertNull(state.mapViewportCenter)
                viewModel.onSelectCategoryFilter(PlaceCategory.GENERAL)
                assertEquals(state.searchResults, viewModel.uiState.value.searchResults)
            } finally {
                response.complete(emptyList())
                vmScope.cancel()
            }
        }
    }

    @Test
    fun newAlarmDoesNotInheritUserOrBrowseCoordinates() = runTest {
        val tracker = UiLocationTracker()
        val savedAlarm = manggaraiAlarm()
        val (viewModel, _, vmScope) = createTestViewModel(
            locationTrackerPort = tracker,
            observePort = FakeObserveAlarmsPort(listOf(savedAlarm)),
        )
        try {
            tracker.emit(GeoCoordinates(-6.1754, 106.8272))
            viewModel.onOpenSearch()
            viewModel.onBrowsePlaceSelected(SearchResultPlace(
                id = "browse", name = "Browse place",
                coordinates = GeoCoordinates(51.5, -0.1), category = PlaceCategory.GENERAL,
            ))
            viewModel.onStartConfiguringAlarm(null)
            assertNull(viewModel.uiState.value.activeDraft?.coordinates)
            assertNull(viewModel.uiState.value.selectedCoordinates)

            viewModel.onStartConfiguringAlarm(savedAlarm.id)
            assertEquals(savedAlarm.coordinates, viewModel.uiState.value.activeDraft?.coordinates)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun editorDestinationChangePreservesEditedRadius() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val alarm = manggaraiAlarm().copy(
                coordinates = GeoCoordinates(-6.2098, 106.8501),
                radius = TriggerRadius(600),
            )
            viewModel.onStartConfiguringAlarm(alarm.id, alarm.coordinates)

            val newRadius = TriggerRadius(1200)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(radiusMeters = newRadius.meters))

            val newCoordinates = GeoCoordinates(-6.2100, 106.8600)
            viewModel.onDraftCoordinatesChanged(newCoordinates)

            val state = viewModel.uiState.value
            assertEquals(newCoordinates, state.selectedCoordinates)
            assertEquals(newRadius, state.selectedRadius)
            assertEquals(newCoordinates, fakeMapRenderer.lastPin)
            assertEquals(newCoordinates to newRadius, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_incrementsViewportRequestId() = runTest {
        val tracker = UiLocationTracker()
        val (viewModel, _, vmScope) = createTestViewModel(locationTrackerPort = tracker)
        try {
            tracker.emit(GeoCoordinates(51.5, -0.1))
            assertEquals(1L, viewModel.uiState.value.viewportRequestId)

            viewModel.onRecenterMap()
            assertEquals(2L, viewModel.uiState.value.viewportRequestId)

            viewModel.onRecenterMap()
            assertEquals(3L, viewModel.uiState.value.viewportRequestId)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun testOpenAndDismissSearch() = runTest {
        val fakeSearchPort = FakeSearchPlacesPort(
            places = listOf(
                SearchResultPlace(
                    id = "station_1",
                    name = "Manggarai",
                    coordinates = GeoCoordinates(-6.20988, 106.85016),
                    category = PlaceCategory.TRAIN_STATION,
                ),
            ),
        )
        val (viewModel, _, vmScope) = createTestViewModel(searchPlacesPort = fakeSearchPort)
        try {
            assertFalse(viewModel.uiState.value.isSearchSheetVisible)

            viewModel.onOpenSearch()
            assertTrue(viewModel.uiState.value.isSearchSheetVisible)
            assertFalse(viewModel.uiState.value.isSearching)
            assertEquals(0, fakeSearchPort.searchCallCount)
            assertTrue(viewModel.uiState.value.searchResults.isEmpty())

            viewModel.onDismissSearch()
            assertFalse(viewModel.uiState.value.isSearchSheetVisible)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun testSearchQueryChanged() = runTest {
        val mri = SearchResultPlace(
            id = "krl_mri",
            name = "Stasiun Manggarai",
            coordinates = GeoCoordinates(-6.20988, 106.85016),
            category = PlaceCategory.TRAIN_STATION,
            aliases = listOf("MRI", "Manggarai"),
        )
        val sud = SearchResultPlace(
            id = "krl_sud",
            name = "Stasiun Sudirman",
            coordinates = GeoCoordinates(-6.20235, 106.82365),
            category = PlaceCategory.TRAIN_STATION,
            aliases = listOf("SUD", "Sudirman"),
        )
        val fakeSearchPort = FakeSearchPlacesPort(places = listOf(mri, sud))
        val (viewModel, _, vmScope) = createTestViewModel(searchPlacesPort = fakeSearchPort)
        try {
            viewModel.onSearchQueryChanged("Sudirman")
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Sudirman", state.searchQuery)
            assertFalse(state.isSearching)
            assertEquals(1, state.searchResults.size)
            assertEquals("krl_sud", state.searchResults.first().id)
            assertEquals("Sudirman", fakeSearchPort.lastQuery)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun testSelectCategoryFilter() = runTest {
        val train = SearchResultPlace(
            id = "krl_mri",
            name = "Stasiun Manggarai",
            coordinates = GeoCoordinates(-6.20988, 106.85016),
            category = PlaceCategory.TRAIN_STATION,
            aliases = listOf("jakarta"),
        )
        val metro = SearchResultPlace(
            id = "mrt_bundaran_hi",
            name = "Bundaran HI",
            coordinates = GeoCoordinates(-6.19277, 106.82302),
            category = PlaceCategory.METRO_STATION,
            aliases = listOf("jakarta"),
        )
        val bus = SearchResultPlace(
            id = "tj_harmoni",
            name = "Halte TransJakarta Harmoni",
            coordinates = GeoCoordinates(-6.16611, 106.82056),
            category = PlaceCategory.BUS_STOP,
            aliases = listOf("jakarta"),
        )
        val transit = SearchResultPlace(
            id = "ferry_muara_angke",
            name = "Muara Angke Ferry Terminal",
            coordinates = GeoCoordinates(-6.10320, 106.77470),
            category = PlaceCategory.TRANSIT_STOP,
            aliases = listOf("jakarta"),
        )
        val fakeSearchPort = FakeSearchPlacesPort(places = listOf(train, metro, bus, transit))
        val (viewModel, _, vmScope) = createTestViewModel(searchPlacesPort = fakeSearchPort)
        try {
            viewModel.onSearchQueryChanged("jakarta")
            testScheduler.advanceUntilIdle()
            assertEquals(4, viewModel.uiState.value.searchResults.size)

            viewModel.onSelectCategoryFilter(PlaceCategory.TRAIN_STATION)
            assertEquals(PlaceCategory.TRAIN_STATION, viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("krl_mri", viewModel.uiState.value.searchResults.first().id)

            viewModel.onSelectCategoryFilter(PlaceCategory.METRO_STATION)
            assertEquals(PlaceCategory.METRO_STATION, viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("mrt_bundaran_hi", viewModel.uiState.value.searchResults.first().id)

            viewModel.onSelectCategoryFilter(PlaceCategory.BUS_STOP)
            assertEquals(PlaceCategory.BUS_STOP, viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("tj_harmoni", viewModel.uiState.value.searchResults.first().id)

            viewModel.onSelectCategoryFilter(null)
            assertNull(viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(4, viewModel.uiState.value.searchResults.size)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun shortSearchQueriesDoNotStartSearchingOrCallPort() = runTest {
        val fakeSearchPort = FakeSearchPlacesPort(
            places = listOf(
                SearchResultPlace(
                    id = "krl_sud",
                    name = "Stasiun Sudirman",
                    coordinates = GeoCoordinates(-6.20235, 106.82365),
                    category = PlaceCategory.TRAIN_STATION,
                ),
            ),
        )
        val (viewModel, _, vmScope) = createTestViewModel(searchPlacesPort = fakeSearchPort)
        try {
            viewModel.onSearchQueryChanged("Sud")
            testScheduler.advanceUntilIdle()
            assertEquals(1, fakeSearchPort.searchCallCount)
            assertEquals(1, viewModel.uiState.value.searchResults.size)

            for (shortQuery in listOf("", "  ", "S", "Su", " Su ")) {
                viewModel.onSearchQueryChanged(shortQuery)
                assertFalse(viewModel.uiState.value.isSearching)
                assertTrue(viewModel.uiState.value.searchResults.isEmpty())
                testScheduler.advanceUntilIdle()
                assertEquals(1, fakeSearchPort.searchCallCount)
            }
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun searchUsesMapViewportCenterAsProximityWhenAvailable() = runTest {
        val tracker = UiLocationTracker()
        val fakeSearchPort = FakeSearchPlacesPort()
        val (viewModel, _, vmScope) = createTestViewModel(
            locationTrackerPort = tracker,
            searchPlacesPort = fakeSearchPort,
        )
        try {
            tracker.emit(GeoCoordinates(-6.2088, 106.8456))
            val pannedCenter = GeoCoordinates(51.5072, -0.1276)
            viewModel.onMapViewportBrowsed(pannedCenter, zoom = 14.0)

            viewModel.onSearchQueryChanged("Paddington")
            testScheduler.advanceUntilIdle()

            assertEquals(pannedCenter, fakeSearchPort.lastProximity)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun browseSelectionMovesCameraWithoutCreatingDraftOrReplacingAlarm() = runTest {
        val alarm = manggaraiAlarm().copy(state = AlarmState.ARMED_MONITORING)
        val renderer = FakeMapRendererPort()
        val saves = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(alarm)),
            savePort = saves,
            mapRendererPort = renderer,
        )
        try {
            val place = SearchResultPlace(
                id = "browse-london",
                name = "London",
                coordinates = GeoCoordinates(51.5, -0.1),
                category = PlaceCategory.TRAIN_STATION,
            )
            viewModel.onOpenSearch()
            val requestId = viewModel.uiState.value.viewportRequestId
            viewModel.onBrowsePlaceSelected(place)

            val state = viewModel.uiState.value
            assertNull(state.activeDraft)
            assertNull(state.selectedCoordinates)
            assertFalse(state.isSearchSheetVisible)
            assertEquals(listOf(alarm), state.alarms)
            assertEquals(alarm.coordinates, state.nearestArmedAlarm?.coordinates)
            assertEquals(alarm.radius, state.nearestArmedAlarm?.radius)
            assertEquals(place.coordinates, state.mapViewportCenter)
            assertEquals(15.0, state.mapViewportZoom)
            assertEquals(requestId + 1L, state.viewportRequestId)
            assertEquals(place.coordinates to 15.0, renderer.lastViewport)
            assertNull(renderer.lastPin)
            assertNull(renderer.lastGeofence)
            assertTrue(saves.savedAlarms.isEmpty())
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun recenterWithoutLocationUsesOverviewButDoesNotInventAlarmDestination() = runTest {
        val renderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = renderer)
        try {
            viewModel.onRecenterMap()
            assertEquals(GeoCoordinates(0.0, 0.0), viewModel.uiState.value.mapViewportCenter)
            assertEquals(GeoCoordinates(0.0, 0.0) to 2.0, renderer.lastViewport)
            viewModel.onStartConfiguringAlarm(null)
            val draft = assertNotNull(viewModel.uiState.value.activeDraft)
            assertNull(draft.coordinates)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun leavingFailedEditorClearsItsFailure() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel(savePort = FakeSaveAlarmPort(shouldFail = true))
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(draft)
            viewModel.onSaveAlarmEditor {}
            assertTrue(viewModel.uiState.value.hasOperationFailure)
            viewModel.onClearDraft()
            assertFalse(viewModel.uiState.value.hasOperationFailure)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun saveCompletionOnlyNavigatesAfterSuccessfulPersistence() = runTest {
        val save = FakeSaveAlarmPort(shouldFail = true)
        val (viewModel, _, vmScope) = createTestViewModel(savePort = save)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(draft)
            var completed = 0
            viewModel.onSaveAlarmEditor { completed++ }
            assertEquals(0, completed)
            assertEquals(draft, viewModel.uiState.value.activeDraft)
            assertTrue(viewModel.uiState.value.hasOperationFailure)
            save.shouldFail = false
            viewModel.onSaveAlarmEditor { completed++ }
            assertEquals(1, completed)
            assertNull(viewModel.uiState.value.activeDraft)
            assertFalse(viewModel.uiState.value.hasOperationFailure)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun failedEditorDisableKeepsDraftAndPersistedStateUntilSaveSucceeds() = runTest {
        val save = FakeSaveAlarmPort(shouldFail = true)
        val alarm = manggaraiAlarm().copy(state = AlarmState.ARMED_MONITORING)
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(alarm)),
            savePort = save,
        )
        try {
            viewModel.onStartConfiguringAlarm(alarm.id)
            val draft = viewModel.uiState.value.activeDraft!!.withEnabled(false, true)
            viewModel.onUpdateDraft(draft)
            var completed = false
            viewModel.onSaveAlarmEditor { completed = true }
            assertFalse(completed)
            assertEquals(draft, viewModel.uiState.value.activeDraft)
            assertTrue(viewModel.uiState.value.alarms.single().isEnabled)
            assertTrue(viewModel.uiState.value.hasOperationFailure)
            save.shouldFail = false
            viewModel.onSaveAlarmEditor { completed = true }
            assertTrue(completed)
            assertEquals(AlarmState.DISABLED, save.savedAlarms.single().state)
            assertNull(viewModel.uiState.value.activeDraft)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun missingDeletePortDoesNotSignalSuccess() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            var completed = false
            viewModel.onDeleteAlarm("absent") { completed = true }
            assertFalse(completed)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun pendingSavePreventsDuplicateAlarmWrites() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var writes = 0
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
                writes++
                release.await()
                return Result.success(Unit)
            }
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> =
                saveAlarm(alarm)
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(draft)
            viewModel.onSaveAlarmEditor {}
            assertTrue(viewModel.uiState.value.isOperationPending)
            viewModel.onSaveAlarmEditor {}
            assertEquals(1, writes)
            release.complete(Unit)
            assertFalse(viewModel.uiState.value.isOperationPending)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun delayedSaveDoesNotClearAnIdenticalDraftFromANewerEditor() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
                release.await()
                return Result.success(Unit)
            }
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> =
                saveAlarm(alarm)
        }
        val renderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port, mapRendererPort = renderer)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val originalDraft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(originalDraft)
            viewModel.onSaveAlarmEditor {}
            viewModel.onClearDraft()
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val newerDraft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(newerDraft)
            release.complete(Unit)
            assertEquals(newerDraft, viewModel.uiState.value.activeDraft)
            assertEquals(GeoCoordinates(51.5, -0.1), viewModel.uiState.value.selectedCoordinates)
            assertFalse(renderer.overlaysCleared)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun delayedDeleteDoesNotClearDraftOpenedFromTheList() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val port = object : DeleteAlarmPort {
            override suspend fun deleteAlarm(alarmId: String): Result<Unit> {
                release.await()
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(deleteAlarmPort = port)
        try {
            viewModel.onDeleteAlarm("old-alarm")
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val newerDraft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(newerDraft)
            release.complete(Unit)
            assertEquals(newerDraft, viewModel.uiState.value.activeDraft)
            assertEquals(GeoCoordinates(51.5, -0.1), viewModel.uiState.value.selectedCoordinates)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun invalidEditorExitDiscardsNewAndExistingDraftsWithoutSaving() = runTest {
        val existing = manggaraiAlarm()
        val save = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(existing)), savePort = save,
        )
        try {
            var exits = 0
            viewModel.onStartConfiguringAlarm(null)
            val draft = viewModel.uiState.value.activeDraft
            viewModel.onExitAlarmEditor(discardInvalid = false) { exits++ }
            assertEquals(0, exits)
            assertEquals(draft, viewModel.uiState.value.activeDraft)
            assertEquals(AlarmEditorNotice.NO_DESTINATION, viewModel.uiState.value.pendingEditorNotice)
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(1, exits)
            assertNull(viewModel.uiState.value.activeDraft)
            assertEquals(AlarmEditorNotice.NO_DESTINATION, viewModel.uiState.value.pendingEditorNotice)
            viewModel.onEditorNoticeShown()
            assertNull(viewModel.uiState.value.pendingEditorNotice)

            viewModel.onStartConfiguringAlarm(existing.id)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(label = " "))
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(2, exits)
            assertNull(viewModel.uiState.value.activeDraft)
            assertEquals(AlarmEditorNotice.NAME_REQUIRED, viewModel.uiState.value.pendingEditorNotice)
            assertEquals(0, save.configurationCalls)
            assertEquals(existing, viewModel.uiState.value.alarms.single())

            viewModel.onStartConfiguringAlarm(existing.id)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(radiusMeters = 10_001))
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(3, exits)
            assertEquals(AlarmEditorNotice.RADIUS_INVALID, viewModel.uiState.value.pendingEditorNotice)
            assertEquals(0, save.configurationCalls)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun unchangedExistingDraftAndMissingExistingRecordExitWithoutWrites() = runTest {
        val existing = manggaraiAlarm().copy(
            radius = TriggerRadius(5_000), soundConfig = AlarmSoundConfig(volume = 0.4f),
        )
        val save = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(existing)), savePort = save,
        )
        try {
            var exits = 0
            viewModel.onStartConfiguringAlarm(existing.id)
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(1, exits)
            assertEquals(0, save.configurationCalls)
            assertNull(viewModel.uiState.value.pendingEditorNotice)

            viewModel.onStartConfiguringAlarm("deleted-id")
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(
                coordinates = GeoCoordinates(51.5, -0.1), label = "Deleted",
            ))
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(2, exits)
            assertEquals(AlarmEditorNotice.ALARM_DELETED, viewModel.uiState.value.pendingEditorNotice)
            assertEquals(0, save.configurationCalls)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun repeatedBackWaitsForOneSuccessfulSaveAndOneExit() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val writes = mutableListOf<LocationAlarm>()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
                writes += alarm
                release.await()
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(label = "London"))
            var exits = 0
            viewModel.onExitAlarmEditor { exits++ }
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(1, writes.size)
            assertEquals(0, exits)
            assertTrue(viewModel.uiState.value.isOperationPending)
            release.complete(Unit)
            assertEquals(1, exits)
            assertNull(viewModel.uiState.value.activeDraft)
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(1, exits)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun changedExistingExitUsesPersistedProvenanceAndNetEnableChoice() = runTest {
        val existing = manggaraiAlarm().copy(state = AlarmState.ARMED_MONITORING)
        val calls = mutableListOf<Pair<Boolean?, Boolean>>()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
                assertEquals(existing.id, alarm.id)
                assertEquals("Changed", alarm.label)
                calls += enabled to requireExisting
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(existing)), savePort = port,
        )
        try {
            viewModel.onStartConfiguringAlarm(existing.id)
            val changed = viewModel.uiState.value.activeDraft!!.copy(label = "Changed")
                .withEnabled(false, displayedEnabled = true)
            viewModel.onUpdateDraft(changed)
            var exits = 0
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(listOf<Pair<Boolean?, Boolean>>(false to true), calls)
            assertEquals(1, exits)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun failedSaveAfterStoreKeepsEditableIdentityAndRetry() = runTest {
        val writes = mutableListOf<LocationAlarm>()
        var fail = true
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
                writes += alarm
                return if (fail) Result.failure(IllegalStateException("stored before failure")) else Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(draft)
            var exits = 0
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(0, exits)
            assertEquals(draft, viewModel.uiState.value.activeDraft)
            assertEquals(AlarmEditorNotice.UPDATE_FAILED, viewModel.uiState.value.pendingEditorNotice)
            fail = false
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(1, exits)
            assertEquals(2, writes.size)
            assertEquals(writes.first().id, writes.last().id)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun revertingToOriginalDraftAfterFailedSaveRetriesPersistence() = runTest {
        for (persistBeforeFailure in listOf(false, true)) {
            val original = manggaraiAlarm()
            val observe = FakeObserveAlarmsPort(listOf(original))
            var stored = original
            var attempts = 0
            val port = object : SaveAlarmPort {
                override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)

                override suspend fun saveAlarmConfiguration(
                    alarm: LocationAlarm,
                    enabled: Boolean?,
                    requireExisting: Boolean,
                ): Result<Unit> {
                    assertTrue(requireExisting)
                    attempts++
                    if (attempts > 1 || persistBeforeFailure) {
                        stored = alarm
                        observe.flow.value = listOf(stored)
                    }
                    return if (attempts == 1) {
                        Result.failure(IllegalStateException("Save did not finish"))
                    } else {
                        Result.success(Unit)
                    }
                }
            }
            val (viewModel, _, vmScope) = createTestViewModel(observePort = observe, savePort = port)
            try {
                viewModel.onStartConfiguringAlarm(original.id)
                val baseline = viewModel.uiState.value.activeDraft!!
                viewModel.onUpdateDraft(baseline.copy(
                    label = "Changed",
                    coordinates = GeoCoordinates(51.5, -0.1),
                    radiusMeters = 200,
                    startMinuteOfDay = 8 * 60,
                ))
                var exits = 0
                viewModel.onExitAlarmEditor { exits++ }
                assertEquals(0, exits)
                assertEquals(AlarmEditorNotice.UPDATE_FAILED, viewModel.uiState.value.pendingEditorNotice)
                assertEquals(if (persistBeforeFailure) "Changed" else original.label, stored.label)

                viewModel.onUpdateDraft(baseline)
                viewModel.onExitAlarmEditor { exits++ }

                assertEquals(2, attempts)
                assertEquals(1, exits)
                assertNull(viewModel.uiState.value.activeDraft)
                assertEquals(original.label, stored.label)
                assertEquals(original.coordinates, stored.coordinates)
                assertEquals(original.radius, stored.radius)
                assertEquals(original.schedule, stored.schedule)
            } finally {
                vmScope.cancel()
            }
        }
    }

    @Test
    fun saveReportingDeletedRecordExitsWithDeletedNotice() = runTest {
        val existing = manggaraiAlarm()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> =
                Result.failure(AlarmConfigurationMissingException(alarm.id))
        }
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(existing)), savePort = port,
        )
        try {
            viewModel.onStartConfiguringAlarm(existing.id)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(label = "Changed"))
            var exits = 0
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(1, exits)
            assertNull(viewModel.uiState.value.activeDraft)
            assertEquals(AlarmEditorNotice.ALARM_DELETED, viewModel.uiState.value.pendingEditorNotice)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun ringingAndPendingMutationBlockEditorExit() = runTest {
        val alarm = manggaraiAlarm().copy(state = AlarmState.RINGING)
        val save = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(alarm)), savePort = save,
        )
        try {
            viewModel.onStartConfiguringAlarm(alarm.id)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(label = "Edited"))
            var exits = 0
            viewModel.onExitAlarmEditor { exits++ }
            assertEquals(0, exits)
            assertNotNull(viewModel.uiState.value.activeDraft)
            assertEquals(0, save.configurationCalls)
        } finally {
            vmScope.cancel()
        }

        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val heldPort = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
                release.await()
                return Result.success(Unit)
            }
        }
        val (pendingVm, _, pendingScope) = createTestViewModel(savePort = heldPort)
        try {
            pendingVm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            pendingVm.onUpdateDraft(pendingVm.uiState.value.activeDraft!!.copy(label = "London"))
            var exits = 0
            pendingVm.onExitAlarmEditor { exits++ }
            pendingVm.onExitAlarmEditor { exits++ }
            assertEquals(0, exits)
            release.complete(Unit)
            assertEquals(1, exits)
        } finally {
            pendingScope.cancel()
        }
    }

    @Test
    fun pendingEditorSaveRejectsSameSessionChangesOnSuccessAndFailure() = runTest {
        for (succeeds in listOf(true, false)) {
            val completion = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
            val writes = mutableListOf<LocationAlarm>()
            val port = object : SaveAlarmPort {
                override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
                override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
                    writes += alarm
                    return completion.await()
                }
            }
            val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
            try {
                viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
                val original = viewModel.uiState.value.activeDraft!!.copy(label = "London")
                viewModel.onUpdateDraft(original)
                var exits = 0
                viewModel.onExitAlarmEditor { exits++ }
                viewModel.onUpdateDraft(original.copy(label = "Changed"))
                viewModel.onUpdateDraft(original.copy(radiusMeters = 500))
                viewModel.onUpdateDraft(original.copy(scheduleType = ScheduleType.ONE_TIME))
                viewModel.onUpdateDraft(original.copy(daysOfWeekMask = 1))
                viewModel.onDraftCoordinatesChanged(GeoCoordinates(-6.2, 106.8))
                viewModel.onExitAlarmEditor { exits++ }
                assertEquals(original, viewModel.uiState.value.activeDraft)
                assertEquals(listOf(original.toLocationAlarm(null)), writes)
                assertEquals(0, exits)
                completion.complete(if (succeeds) Result.success(Unit) else Result.failure(IllegalStateException("failed")))
                assertFalse(viewModel.uiState.value.isOperationPending)
                if (succeeds) {
                    assertEquals(1, exits)
                    assertNull(viewModel.uiState.value.activeDraft)
                } else {
                    assertEquals(0, exits)
                    assertEquals(original, viewModel.uiState.value.activeDraft)
                    assertEquals(AlarmEditorNotice.UPDATE_FAILED, viewModel.uiState.value.pendingEditorNotice)
                    val edited = original.copy(label = "Retry")
                    viewModel.onUpdateDraft(edited)
                    assertEquals(edited, viewModel.uiState.value.activeDraft)
                }
            } finally {
                vmScope.cancel()
            }
        }
    }

    @Test
    fun staleExitSaveCompletionNeverClearsNewerEditor() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(alarm: LocationAlarm, enabled: Boolean?, requireExisting: Boolean): Result<Unit> {
                release.await()
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(label = "First"))
            var exits = 0
            viewModel.onExitAlarmEditor { exits++ }
            viewModel.onClearDraft()
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(-6.2, 106.8))
            val newer = viewModel.uiState.value.activeDraft!!.copy(label = "Second")
            viewModel.onUpdateDraft(newer)
            release.complete(Unit)
            assertEquals(0, exits)
            assertEquals(newer, viewModel.uiState.value.activeDraft)
            assertFalse(viewModel.uiState.value.isOperationPending)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun recentDestinations_areObservedAndConfirmedDestinationsAreRecorded() = runTest {
        val fakeRecentsPort = com.muhammadridha.fjalar.fakes.FakeRecentDestinationsAdapter()
        val (viewModel, _, vmScope) = createTestViewModel(recentDestinationsPort = fakeRecentsPort)
        try {
            assertTrue(viewModel.uiState.value.recentDestinations.isEmpty())

            val targetCoords = GeoCoordinates(latitude = -6.1767, longitude = 106.8306)
            viewModel.onConfirmDestination(
                coordinates = targetCoords,
                label = "Gambir Station",
                category = PlaceCategory.TRAIN_STATION,
            )

            val recents = viewModel.uiState.value.recentDestinations
            assertEquals(1, recents.size)
            assertEquals("Gambir Station", recents.first().name)
            assertEquals(targetCoords, recents.first().coordinates)
            assertEquals(PlaceCategory.TRAIN_STATION, recents.first().category)

            val unlabeledCoords = GeoCoordinates(latitude = -6.2000, longitude = 106.8000)
            viewModel.onConfirmDestination(coordinates = unlabeledCoords)
            val updatedRecents = viewModel.uiState.value.recentDestinations
            assertEquals(1, updatedRecents.size)
            assertEquals("Gambir Station", updatedRecents.first().name)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun browseAndPickerPlaceSelectionsRecordIntoRecentDestinationsInLruOrder() = runTest {
        val fakeRecentsPort = com.muhammadridha.fjalar.fakes.FakeRecentDestinationsAdapter()
        val (viewModel, _, vmScope) = createTestViewModel(recentDestinationsPort = fakeRecentsPort)
        try {
            val manggarai = SearchResultPlace(
                id = "osm_manggarai",
                name = "Stasiun Manggarai",
                coordinates = GeoCoordinates(-6.20988, 106.85016),
                category = PlaceCategory.TRAIN_STATION,
            )
            val shibuya = SearchResultPlace(
                id = "mk_shibuya",
                name = "Shibuya Station",
                coordinates = GeoCoordinates(35.65858, 139.70144),
                category = PlaceCategory.TRAIN_STATION,
            )

            // Guard check: browse handler does nothing outside BROWSE context
            viewModel.onOpenSearch(PlaceSearchContext.PICKER)
            viewModel.onBrowsePlaceSelected(manggarai)
            assertTrue(viewModel.uiState.value.recentDestinations.isEmpty())

            // Picker search selection records directly
            viewModel.onPickerPlaceSelected(manggarai)
            assertEquals(listOf("osm_manggarai"), viewModel.uiState.value.recentDestinations.map { it.id })

            // Main map browse selection records and promotes most recent first
            viewModel.onOpenSearch(PlaceSearchContext.BROWSE)
            viewModel.onBrowsePlaceSelected(shibuya)
            assertEquals(
                listOf("mk_shibuya", "osm_manggarai"),
                viewModel.uiState.value.recentDestinations.map { it.id },
            )

            // Selecting an earlier place again moves it to the top of the LRU list
            viewModel.onOpenSearch(PlaceSearchContext.BROWSE)
            viewModel.onBrowsePlaceSelected(manggarai)
            assertEquals(
                listOf("osm_manggarai", "mk_shibuya"),
                viewModel.uiState.value.recentDestinations.map { it.id },
            )
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun syncSchedulesAndMutationsReconcileBackgroundSyncViaUseCase() = runTest {
        val repository = com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository()
        val tracker = com.muhammadridha.fjalar.fakes.FakeLocationTracker()
        val scheduler = com.muhammadridha.fjalar.fakes.FakeAlarmScheduler()
        val ringer = com.muhammadridha.fjalar.fakes.FakeAlarmRinger()
        val backgroundSync = com.muhammadridha.fjalar.fakes.FakeBackgroundSyncAdapter()
        val clock = com.muhammadridha.fjalar.fakes.FakeClock(com.muhammadridha.fjalar.domain.jakarta(2026, 10, 26, 7))
        val service = com.muhammadridha.fjalar.domain.service.AlarmService(
            repository = repository,
            tracker = tracker,
            scheduler = scheduler,
            ringer = ringer,
            scheduleEvaluator = com.muhammadridha.fjalar.domain.service.ScheduleEvaluator(com.muhammadridha.fjalar.domain.JAKARTA),
            clock = clock,
        )
        val useCase = com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase(
            syncSchedulesPort = service,
            backgroundSyncPort = backgroundSync,
        )
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = service,
            savePort = service,
            togglePort = service,
            dismissPort = service,
            skipTodayPort = service,
            deleteAlarmPort = service,
            syncSchedulesPort = service,
            snoozePort = service,
            synchronizeAlarmsUseCase = useCase,
            clock = clock,
        )
        try {
            // Initially no alarms -> sync cancels background refresh
            viewModel.syncSchedules().join()
            assertTrue(backgroundSync.isCancelled)
            assertNull(backgroundSync.scheduledDelayMinutes)

            // Saving an enabled scheduled alarm schedules background refresh
            val scheduledAlarm = manggaraiAlarm(
                schedule = com.muhammadridha.fjalar.domain.WEEKDAY_COMMUTE,
            ).copy(id = "commute-sync", state = AlarmState.SCHEDULED_IDLE)
            viewModel.onStartConfiguringAlarm(null, scheduledAlarm.coordinates)
            viewModel.onUpdateDraft(com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft.fromAlarm(scheduledAlarm))
            viewModel.onSaveAlarmEditor {}
            assertEquals(
                com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase.DEFAULT_NEXT_SYNC_MINUTES,
                backgroundSync.scheduledDelayMinutes,
            )
            assertFalse(backgroundSync.isCancelled)

            // Disabling the only scheduled alarm cancels background refresh
            viewModel.onToggleAlarm("commute-sync", enabled = false)
            assertTrue(backgroundSync.isCancelled)
            assertNull(backgroundSync.scheduledDelayMinutes)

            // Re-enabling schedules again; deleting cancels again
            viewModel.onToggleAlarm("commute-sync", enabled = true)
            assertEquals(
                com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase.DEFAULT_NEXT_SYNC_MINUTES,
                backgroundSync.scheduledDelayMinutes,
            )
            viewModel.onDeleteAlarm("commute-sync")
            assertTrue(backgroundSync.isCancelled)
            assertNull(backgroundSync.scheduledDelayMinutes)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun mutationsReconcileBackgroundSyncWithoutRedundantSyncSchedulesOrDuplicateSnoozeWakeup() = runTest {
        val repository = com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository()
        val tracker = com.muhammadridha.fjalar.fakes.FakeLocationTracker()
        var snoozeScheduleCount = 0
        val countingScheduler = object : com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort {
            override suspend fun scheduleWakeup(alarmId: String, at: kotlin.time.Instant) {}
            override suspend fun scheduleSnoozeWakeup(alarm: LocationAlarm, at: kotlin.time.Instant) {
                snoozeScheduleCount++
            }
            override suspend fun cancelWakeup(alarmId: String) {}
        }
        val ringer = com.muhammadridha.fjalar.fakes.FakeAlarmRinger()
        val backgroundSync = com.muhammadridha.fjalar.fakes.FakeBackgroundSyncAdapter()
        val clock = com.muhammadridha.fjalar.fakes.FakeClock(com.muhammadridha.fjalar.domain.jakarta(2026, 10, 26, 7))
        val service = com.muhammadridha.fjalar.domain.service.AlarmService(
            repository = repository,
            tracker = tracker,
            scheduler = countingScheduler,
            ringer = ringer,
            scheduleEvaluator = com.muhammadridha.fjalar.domain.service.ScheduleEvaluator(com.muhammadridha.fjalar.domain.JAKARTA),
            clock = clock,
        )
        var syncSchedulesCallCount = 0
        val countingSyncPort = object : SyncSchedulesPort {
            override suspend fun syncSchedules(): List<LocationAlarm> {
                syncSchedulesCallCount++
                return service.syncSchedules()
            }
        }
        val useCase = com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase(
            syncSchedulesPort = countingSyncPort,
            backgroundSyncPort = backgroundSync,
        )
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = service,
            savePort = service,
            togglePort = service,
            dismissPort = service,
            skipTodayPort = service,
            deleteAlarmPort = service,
            syncSchedulesPort = countingSyncPort,
            snoozePort = service,
            synchronizeAlarmsUseCase = useCase,
            clock = clock,
        )
        try {
            // Save a one-time alarm and trigger it into RINGING
            val oneTime = manggaraiAlarm(
                schedule = com.muhammadridha.fjalar.domain.model.AlarmSchedule.ONE_TIME,
            ).copy(
                id = "snooze-test",
                state = AlarmState.ARMED_MONITORING,
            )
            viewModel.onStartConfiguringAlarm(null, oneTime.coordinates)
            viewModel.onUpdateDraft(
                com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft.fromAlarm(oneTime)
            )
            viewModel.onSaveAlarmEditor {}
            val savedId = repository.getAlarms().single().id
            assertEquals(0, syncSchedulesCallCount)
            assertTrue(backgroundSync.isCancelled)

            service.handleLocationUpdate(com.muhammadridha.fjalar.domain.MANGGARAI, 10f)
            assertEquals(AlarmState.RINGING, repository.getAlarm(savedId)?.state)

            // Snoozing schedules background refresh and calls scheduleSnoozeWakeup exactly once without syncSchedules()
            viewModel.onSnoozeRingingAlarm(savedId)
            assertEquals(0, syncSchedulesCallCount)
            assertEquals(1, snoozeScheduleCount)
            assertEquals(
                com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase.DEFAULT_NEXT_SYNC_MINUTES,
                backgroundSync.scheduledDelayMinutes,
            )
            assertFalse(backgroundSync.isCancelled)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun hasUnsavedEditorChangesAndDiscardAndExplicitSaveBehaviors() = runTest {
        val existing = manggaraiAlarm(
            schedule = com.muhammadridha.fjalar.domain.model.AlarmSchedule.ONE_TIME,
        ).copy(id = "existing-1")
        val observePort = FakeObserveAlarmsPort(listOf(existing))
        var shouldFailSave = false
        val savedConfigs = mutableListOf<LocationAlarm>()
        val savePort = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
            override suspend fun saveAlarmConfiguration(
                alarm: LocationAlarm,
                enabled: Boolean?,
                requireExisting: Boolean,
            ): Result<Unit> {
                if (shouldFailSave) return Result.failure(IllegalStateException("Disk full"))
                savedConfigs += alarm
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = observePort,
            savePort = savePort,
        )
        try {
            // Untouched new alarm is clean
            viewModel.onStartConfiguringAlarm(null)
            assertFalse(viewModel.hasUnsavedEditorChanges())

            // Modifying new alarm marks it dirty; invalid Save keeps editor open with notice
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(label = "No pin yet"))
            assertTrue(viewModel.hasUnsavedEditorChanges())
            var exits = 0
            viewModel.onSaveAlarmEditor { exits++ }
            assertEquals(0, exits)
            assertEquals(AlarmEditorNotice.NO_DESTINATION, viewModel.uiState.value.pendingEditorNotice)
            assertTrue(viewModel.hasUnsavedEditorChanges())

            // Explicit discard clears draft without saving
            viewModel.onDiscardAlarmEditor { exits++ }
            assertEquals(1, exits)
            assertNull(viewModel.uiState.value.activeDraft)
            assertFalse(viewModel.hasUnsavedEditorChanges())
            assertTrue(savedConfigs.isEmpty())

            // Untouched existing alarm is clean and explicit Save exits without redundant write
            viewModel.onStartConfiguringAlarm("existing-1")
            assertFalse(viewModel.hasUnsavedEditorChanges())
            viewModel.onSaveAlarmEditor { exits++ }
            assertEquals(2, exits)
            assertTrue(savedConfigs.isEmpty())

            // Failed save keeps editor open and hasUnsavedEditorChanges() remains true even if reverted
            viewModel.onStartConfiguringAlarm("existing-1")
            val originalDraft = viewModel.uiState.value.activeDraft!!
            viewModel.onUpdateDraft(originalDraft.copy(label = "Updated"))
            assertTrue(viewModel.hasUnsavedEditorChanges())
            shouldFailSave = true
            viewModel.onSaveAlarmEditor { exits++ }
            assertEquals(2, exits)
            assertTrue(viewModel.uiState.value.hasOperationFailure)
            assertTrue(viewModel.hasUnsavedEditorChanges())

            // Retrying Save after failure succeeds and exits
            shouldFailSave = false
            viewModel.onSaveAlarmEditor { exits++ }
            assertEquals(3, exits)
            assertEquals(listOf("Updated"), savedConfigs.map { it.label })
            assertNull(viewModel.uiState.value.activeDraft)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun draftCoordinatesChangedAutoFillsBlankLabelAndClearsValidationNotice() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            viewModel.onStartConfiguringAlarm(alarmId = null, defaultCoordinates = null)
            viewModel.onSaveAlarmEditor {}
            assertEquals(AlarmEditorNotice.NO_DESTINATION, viewModel.uiState.value.pendingEditorNotice)

            val stationCoords = GeoCoordinates(-6.20988, 106.85016)
            viewModel.onDraftCoordinatesChanged(stationCoords, label = "  Stasiun Manggarai  ")
            val updated = viewModel.uiState.value.activeDraft
            assertNotNull(updated)
            assertEquals("Stasiun Manggarai", updated.label)
            assertEquals("Stasiun Manggarai", updated.addressLabel)
            assertNull(viewModel.uiState.value.pendingEditorNotice)

            // Non-blank user label is preserved when coordinates change later
            viewModel.onUpdateDraft(updated.copy(label = "My Morning Stop"))
            viewModel.onDraftCoordinatesChanged(GeoCoordinates(-6.1767, 106.8306), label = "Gambir")
            val kept = viewModel.uiState.value.activeDraft!!
            assertEquals("My Morning Stop", kept.label)
            assertEquals("Gambir", kept.addressLabel)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleAndSkipFailuresEmitTransientUpdateFailedNotice() = runTest {
        val alarm = manggaraiAlarm()
        val failingToggle = object : ToggleAlarmPort {
            override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> =
                Result.failure(IllegalStateException("toggle failed"))
        }
        val failingSkip = object : SkipTodayPort {
            override suspend fun skipToday(alarmId: String): Result<Unit> =
                Result.failure(IllegalStateException("skip failed"))
        }
        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = FakeObserveAlarmsPort(listOf(alarm)),
            togglePort = failingToggle,
            skipTodayPort = failingSkip,
        )
        try {
            viewModel.onToggleAlarm(alarm.id, enabled = false)
            val toggleNoticeId = viewModel.uiState.value.pendingEditorNoticeId
            assertEquals(AlarmEditorNotice.UPDATE_FAILED, viewModel.uiState.value.pendingEditorNotice)
            viewModel.onEditorNoticeShown(toggleNoticeId)
            assertNull(viewModel.uiState.value.pendingEditorNotice)

            viewModel.onSkipToday(alarm.id)
            assertEquals(AlarmEditorNotice.UPDATE_FAILED, viewModel.uiState.value.pendingEditorNotice)
            assertTrue(viewModel.uiState.value.pendingEditorNoticeId > toggleNoticeId)
        } finally {
            vmScope.cancel()
        }
    }

}
