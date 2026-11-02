package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
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
        override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
            if (shouldFail) {
                return Result.failure(IllegalStateException("Simulated save failure"))
            }
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
            assertNull(state.selectedAlarmForEdit)
            assertFalse(state.isConfigureSheetVisible)
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
    fun saveAlarmDispatchesToPortAndClosesConfigureSheet() = runTest {
        val savePort = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(savePort = savePort)
        try {
            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)

            val newAlarm = manggaraiAlarm()
            viewModel.onSaveAlarm(newAlarm)

            assertEquals(listOf(newAlarm), savePort.savedAlarms)
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun deleteAlarmDispatchesToPortAndClosesConfigureSheetIfMatching() = runTest {
        val deletePort = FakeDeleteAlarmPort()
        val alarm = manggaraiAlarm()
        val (viewModel, _, vmScope) = createTestViewModel(deleteAlarmPort = deletePort)
        try {
            viewModel.onOpenEditAlarm(alarm)
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertEquals(alarm, viewModel.uiState.value.selectedAlarmForEdit)

            viewModel.onDeleteAlarm(alarm.id)

            assertEquals(listOf(alarm.id), deletePort.deleted)
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
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
    fun sheetVisibilityTransitions() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)

            val alarm = manggaraiAlarm()
            viewModel.onOpenEditAlarm(alarm)
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertEquals(alarm, viewModel.uiState.value.selectedAlarmForEdit)

            viewModel.onDismissConfigureSheet()
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)

            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
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
    fun saveAlarmFailureKeepsConfigureSheetOpen() = runTest {
        val savePort = FakeSaveAlarmPort(shouldFail = true)
        val (viewModel, _, vmScope) = createTestViewModel(savePort = savePort)
        try {
            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)

            val alarm = manggaraiAlarm()
            viewModel.onSaveAlarm(alarm)

            // Sheet should remain open on failure so user doesn't lose inputs
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
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
                if (nextContext == null) {
                    viewModel.onDismissSearch()
                } else {
                    viewModel.onOpenSearch(nextContext)
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
    fun onOpenNewAlarmClearsPriorDestinationSelection() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2088, 106.8456)
            viewModel.onDraftCoordinatesChanged(coords)
            assertEquals(coords, viewModel.uiState.value.selectedCoordinates)

            viewModel.onOpenNewAlarm()

            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
            assertNull(viewModel.uiState.value.selectedCoordinates)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onOpenNewAlarmDoesNotSelectUserLocation() = runTest {
        val tracker = UiLocationTracker()
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(locationTrackerPort = tracker, mapRendererPort = fakeMapRenderer)
        try {
            val userLocation = GeoCoordinates(-6.1754, 106.8272)
            tracker.emit(userLocation)
            assertNull(viewModel.uiState.value.selectedCoordinates)

            viewModel.onOpenNewAlarm()

            val state = viewModel.uiState.value
            assertTrue(state.isConfigureSheetVisible)
            assertNull(state.selectedAlarmForEdit)
            assertNull(state.selectedCoordinates)
            assertEquals(TriggerRadius.DEFAULT, state.selectedRadius)
            assertNull(fakeMapRenderer.lastPin)
            assertNull(fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onOpenEditAlarm_syncsSelectedCoordinatesAndRadiusAndCentersViewport() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val alarm = manggaraiAlarm().copy(
                coordinates = GeoCoordinates(-6.2098, 106.8501),
                radius = TriggerRadius(750),
            )

            viewModel.onOpenEditAlarm(alarm)

            val state = viewModel.uiState.value
            assertTrue(state.isConfigureSheetVisible)
            assertEquals(alarm, state.selectedAlarmForEdit)
            assertEquals(alarm.coordinates, state.selectedCoordinates)
            assertEquals(alarm.radius, state.selectedRadius)
            assertEquals(alarm.coordinates, state.mapViewportCenter)
            assertEquals(15.0, state.mapViewportZoom)
            assertEquals(alarm.coordinates, fakeMapRenderer.lastPin)
            assertEquals(alarm.coordinates to alarm.radius, fakeMapRenderer.lastGeofence)
            assertEquals(alarm.coordinates to 15.0, fakeMapRenderer.lastViewport)
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
            viewModel.onOpenEditAlarm(alarm)
            viewModel.onStartConfiguringAlarm(alarm.id)

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
    fun onSaveAlarm_clearsSelectedCoordinatesAndRadiusAndOverlays() = runTest {
        val savePort = FakeSaveAlarmPort()
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(savePort = savePort, mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2088, 106.8456)
            viewModel.onDraftCoordinatesChanged(coords)
            viewModel.onUpdateDraft(viewModel.uiState.value.activeDraft!!.copy(radiusMeters = 1500))

            val alarm = manggaraiAlarm()
            viewModel.onSaveAlarm(alarm)

            val state = viewModel.uiState.value
            assertFalse(state.isConfigureSheetVisible)
            assertNull(state.selectedAlarmForEdit)
            assertNull(state.selectedCoordinates)
            assertEquals(TriggerRadius.DEFAULT, state.selectedRadius)
            assertTrue(fakeMapRenderer.overlaysCleared)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onDismissConfigureSheet_clearsSelectedCoordinatesAndRadiusAndOverlays() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2088, 106.8456)
            viewModel.onDraftCoordinatesChanged(coords)
            viewModel.onOpenNewAlarm()

            viewModel.onDismissConfigureSheet()

            val state = viewModel.uiState.value
            assertFalse(state.isConfigureSheetVisible)
            assertNull(state.selectedCoordinates)
            assertEquals(TriggerRadius.DEFAULT, state.selectedRadius)
            assertTrue(fakeMapRenderer.overlaysCleared)
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

            // Open configure sheet first to verify onOpenSearch dismisses it
            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)

            viewModel.onOpenSearch()
            assertTrue(viewModel.uiState.value.isSearchSheetVisible)
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible, "Opening search must dismiss configure sheet")
            assertEquals(1, fakeSearchPort.searchCallCount)
            assertEquals("", fakeSearchPort.lastQuery)
            assertEquals(1, viewModel.uiState.value.searchResults.size)

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
        )
        val metro = SearchResultPlace(
            id = "mrt_bundaran_hi",
            name = "Bundaran HI",
            coordinates = GeoCoordinates(-6.19277, 106.82302),
            category = PlaceCategory.METRO_STATION,
        )
        val transit = SearchResultPlace(
            id = "tj_harmoni",
            name = "Harmoni Central Busway",
            coordinates = GeoCoordinates(-6.16611, 106.82056),
            category = PlaceCategory.TRANSIT_STOP,
        )
        val fakeSearchPort = FakeSearchPlacesPort(places = listOf(train, metro, transit))
        val (viewModel, _, vmScope) = createTestViewModel(searchPlacesPort = fakeSearchPort)
        try {
            viewModel.onSearchQueryChanged("")
            assertEquals(3, viewModel.uiState.value.searchResults.size)

            viewModel.onSelectCategoryFilter(PlaceCategory.TRAIN_STATION)
            assertEquals(PlaceCategory.TRAIN_STATION, viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("krl_mri", viewModel.uiState.value.searchResults.first().id)

            viewModel.onSelectCategoryFilter(PlaceCategory.METRO_STATION)
            assertEquals(PlaceCategory.METRO_STATION, viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("mrt_bundaran_hi", viewModel.uiState.value.searchResults.first().id)

            viewModel.onSelectCategoryFilter(null)
            assertNull(viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(3, viewModel.uiState.value.searchResults.size)
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
            assertFalse(state.isConfigureSheetVisible)
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
    fun testInitialAlarmLabelLifecycle() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            // Open new alarm resets initialAlarmLabel to null
            viewModel.onOpenNewAlarm()
            assertNull(viewModel.uiState.value.initialAlarmLabel)

            // Open edit alarm sets initialAlarmLabel to alarm.label
            val alarm = manggaraiAlarm()
            viewModel.onOpenEditAlarm(alarm)
            assertEquals(alarm.label, viewModel.uiState.value.initialAlarmLabel)

            // Dismiss configure sheet resets initialAlarmLabel to null
            viewModel.onDismissConfigureSheet()
            assertNull(viewModel.uiState.value.initialAlarmLabel)
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
            viewModel.onSaveDraftAlarm(draft) {}
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
            viewModel.onSaveDraftAlarm(draft) { completed++ }
            assertEquals(0, completed)
            assertEquals(draft, viewModel.uiState.value.activeDraft)
            assertTrue(viewModel.uiState.value.hasOperationFailure)
            save.shouldFail = false
            viewModel.onSaveDraftAlarm(draft) { completed++ }
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
            viewModel.onSaveDraftAlarm(draft) { completed = true }
            assertFalse(completed)
            assertEquals(draft, viewModel.uiState.value.activeDraft)
            assertTrue(viewModel.uiState.value.alarms.single().isEnabled)
            assertTrue(viewModel.uiState.value.hasOperationFailure)
            save.shouldFail = false
            viewModel.onSaveDraftAlarm(draft) { completed = true }
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
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onSaveDraftAlarm(draft)
            assertTrue(viewModel.uiState.value.isOperationPending)
            viewModel.onSaveDraftAlarm(draft)
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
        }
        val renderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port, mapRendererPort = renderer)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val originalDraft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(originalDraft)
            viewModel.onSaveDraftAlarm(originalDraft)
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
    fun delayedSaveAndResaveOfNewerEditsReuseOneIdentity() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val writes = mutableListOf<LocationAlarm>()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
                writes += alarm
                release.await()
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val submitted = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            assertNotNull(submitted.alarmId)
            viewModel.onUpdateDraft(submitted)
            viewModel.onSaveDraftAlarm(submitted)
            viewModel.onUpdateDraft(submitted.copy(label = "London Bridge"))
            release.complete(Unit)
            val revised = viewModel.uiState.value.activeDraft!!
            viewModel.onSaveDraftAlarm(revised)
            assertEquals(2, writes.size)
            assertEquals(writes.first().id, writes.last().id)
            assertEquals("London Bridge", writes.last().label)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun delayedSaveDoesNotClearNewerEditsInTheSameEditor() = runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val port = object : SaveAlarmPort {
            override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
                release.await()
                return Result.success(Unit)
            }
        }
        val (viewModel, _, vmScope) = createTestViewModel(savePort = port)
        try {
            viewModel.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val submittedDraft = viewModel.uiState.value.activeDraft!!.copy(label = "London")
            viewModel.onUpdateDraft(submittedDraft)
            viewModel.onSaveDraftAlarm(submittedDraft)
            val editedDraft = submittedDraft.copy(label = "London Bridge", radiusMeters = 1000)
            viewModel.onUpdateDraft(editedDraft)
            viewModel.onDraftCoordinatesChanged(GeoCoordinates(51.505, -0.09))
            release.complete(Unit)
            assertEquals("London Bridge", viewModel.uiState.value.activeDraft?.label)
            assertEquals(1000, viewModel.uiState.value.activeDraft?.radiusMeters)
            assertEquals(GeoCoordinates(51.505, -0.09), viewModel.uiState.value.activeDraft?.coordinates)
        } finally {
            vmScope.cancel()
        }
    }

}
