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
        searchPlacesPort: SearchPlacesPort? = null,
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
            searchPlacesPort = searchPlacesPort,
            deleteAlarmPort = deleteAlarmPort,
            coroutineScope = vmScope,
        )
        return Triple(viewModel, preferencesPort, vmScope)
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
    fun onMapClicked_updatesSelectedCoordinatesAndMapRenderer() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2000, 106.8166)
            viewModel.onMapClicked(coords)

            assertEquals(coords, viewModel.uiState.value.selectedCoordinates)
            assertEquals(coords, fakeMapRenderer.lastPin)
            assertEquals(coords to TriggerRadius.DEFAULT, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onPinDragged_updatesSelectedCoordinates() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val initialCoords = GeoCoordinates(-6.2000, 106.8166)
            val draggedCoords = GeoCoordinates(-6.2100, 106.8200)

            viewModel.onMapClicked(initialCoords)
            viewModel.onPinDragged(draggedCoords)

            assertEquals(draggedCoords, viewModel.uiState.value.selectedCoordinates)
            assertEquals(draggedCoords, fakeMapRenderer.lastPin)
            assertEquals(draggedCoords to TriggerRadius.DEFAULT, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRadiusChanged_updatesSelectedRadiusAndGeofenceOverlay() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2000, 106.8166)
            val newRadius = TriggerRadius(1000)

            viewModel.onMapClicked(coords)
            viewModel.onRadiusChanged(newRadius)

            assertEquals(newRadius, viewModel.uiState.value.selectedRadius)
            assertEquals(coords to newRadius, fakeMapRenderer.lastGeofence)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_centersOnUserLocationWhenAvailable() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val userLocation = GeoCoordinates(-6.1754, 106.8272)
            viewModel.onUserLocationChanged(userLocation)

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

    @Test
    fun onOpenNewAlarm_preservesSelectedMapCoordinates() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val coords = GeoCoordinates(-6.2088, 106.8456)
            viewModel.onMapClicked(coords)
            assertEquals(coords, viewModel.uiState.value.selectedCoordinates)

            viewModel.onOpenNewAlarm()

            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
            assertEquals(coords, viewModel.uiState.value.selectedCoordinates)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onOpenNewAlarm_defaultsToUserLocationWhenSelectedCoordinatesNull() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val userLocation = GeoCoordinates(-6.1754, 106.8272)
            viewModel.onUserLocationChanged(userLocation)
            assertNull(viewModel.uiState.value.selectedCoordinates)

            viewModel.onOpenNewAlarm()

            val state = viewModel.uiState.value
            assertTrue(state.isConfigureSheetVisible)
            assertNull(state.selectedAlarmForEdit)
            assertEquals(userLocation, state.selectedCoordinates)
            assertEquals(TriggerRadius.DEFAULT, state.selectedRadius)
            assertEquals(userLocation, fakeMapRenderer.lastPin)
            assertEquals(userLocation to TriggerRadius.DEFAULT, fakeMapRenderer.lastGeofence)
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
    fun editMode_mapClickUpdatesSelectedCoordinatesPreservingEditedRadius() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            val alarm = manggaraiAlarm().copy(
                coordinates = GeoCoordinates(-6.2098, 106.8501),
                radius = TriggerRadius(600),
            )
            viewModel.onOpenEditAlarm(alarm)

            val newRadius = TriggerRadius(1200)
            viewModel.onRadiusChanged(newRadius)

            val newCoordinates = GeoCoordinates(-6.2100, 106.8600)
            viewModel.onMapClicked(newCoordinates)

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
            viewModel.onMapClicked(coords)
            viewModel.onRadiusChanged(TriggerRadius(1500))

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
            viewModel.onMapClicked(coords)
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
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            viewModel.onUserLocationChanged(GeoCoordinates(51.5, -0.1))
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
    fun testSelectPlace() = runTest {
        val fakeMapRenderer = FakeMapRendererPort()
        val place = SearchResultPlace(
            id = "krl_mri",
            name = "Stasiun Manggarai",
            coordinates = GeoCoordinates(-6.20988, 106.85016),
            category = PlaceCategory.TRAIN_STATION,
        )
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeMapRenderer)
        try {
            viewModel.onOpenSearch()
            assertTrue(viewModel.uiState.value.isSearchSheetVisible)

            viewModel.onSelectPlace(place)

            val state = viewModel.uiState.value
            assertFalse(state.isSearchSheetVisible)
            assertTrue(state.isConfigureSheetVisible)
            assertNull(state.selectedAlarmForEdit)
            assertEquals("Stasiun Manggarai", state.initialAlarmLabel)
            assertEquals(place.coordinates, state.selectedCoordinates)
            assertEquals(TriggerRadius.DEFAULT, state.selectedRadius)
            assertEquals(place.coordinates, state.mapViewportCenter)
            assertEquals(15.0, state.mapViewportZoom)
            assertEquals(1L, state.viewportRequestId)

            assertEquals(place.coordinates, fakeMapRenderer.lastPin)
            assertEquals(place.coordinates to TriggerRadius.DEFAULT, fakeMapRenderer.lastGeofence)
            assertEquals(place.coordinates to 15.0, fakeMapRenderer.lastViewport)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun testInitialAlarmLabelLifecycle() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            val place = SearchResultPlace(
                id = "krl_mri",
                name = "Stasiun Manggarai",
                coordinates = GeoCoordinates(-6.20988, 106.85016),
                category = PlaceCategory.TRAIN_STATION,
            )
            viewModel.onSelectPlace(place)
            assertEquals("Stasiun Manggarai", viewModel.uiState.value.initialAlarmLabel)

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
        val (vm, _, scope) = createTestViewModel(mapRendererPort = renderer)
        try {
            vm.onRecenterMap()
            assertEquals(GeoCoordinates(0.0, 0.0), vm.uiState.value.mapViewportCenter)
            assertEquals(GeoCoordinates(0.0, 0.0) to 2.0, renderer.lastViewport)
            vm.onStartConfiguringAlarm(null)
            assertNull(vm.uiState.value.activeDraft?.coordinates)
        } finally { scope.cancel() }
    }

    @Test
    fun selectingSearchResultCreatesNamedDraft() = runTest {
        val (vm, _, scope) = createTestViewModel()
        try {
            val place = SearchResultPlace(
                id = "london", name = "London", coordinates = GeoCoordinates(51.5, -0.1),
                category = PlaceCategory.TRAIN_STATION,
            )
            vm.onSelectPlace(place)
            assertEquals(place.name, vm.uiState.value.activeDraft?.label)
            assertEquals(place.coordinates, vm.uiState.value.activeDraft?.coordinates)
        } finally { scope.cancel() }
    }

    @Test
    fun saveCompletionOnlyNavigatesAfterSuccessfulPersistence() = runTest {
        val save = FakeSaveAlarmPort(shouldFail = true)
        val (vm, _, scope) = createTestViewModel(savePort = save)
        try {
            vm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = vm.uiState.value.activeDraft!!.copy(label = "London")
            vm.onUpdateDraft(draft)
            var completed = 0
            vm.onSaveDraftAlarm(draft) { completed++ }
            assertEquals(0, completed)
            assertEquals(draft, vm.uiState.value.activeDraft)
            assertTrue(vm.uiState.value.hasOperationFailure)
            save.shouldFail = false
            vm.onSaveDraftAlarm(draft) { completed++ }
            assertEquals(1, completed)
            assertNull(vm.uiState.value.activeDraft)
            assertFalse(vm.uiState.value.hasOperationFailure)
        } finally { scope.cancel() }
    }

    @Test
    fun missingDeletePortDoesNotSignalSuccess() = runTest {
        val (vm, _, scope) = createTestViewModel()
        try {
            var completed = false
            vm.onDeleteAlarm("absent") { completed = true }
            assertFalse(completed)
        } finally { scope.cancel() }
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
        val (vm, _, scope) = createTestViewModel(savePort = port)
        try {
            vm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val draft = vm.uiState.value.activeDraft!!.copy(label = "London")
            vm.onSaveDraftAlarm(draft)
            assertTrue(vm.uiState.value.isOperationPending)
            vm.onSaveDraftAlarm(draft)
            assertEquals(1, writes)
            release.complete(Unit)
            assertFalse(vm.uiState.value.isOperationPending)
        } finally { scope.cancel() }
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
        val (vm, _, scope) = createTestViewModel(savePort = port, mapRendererPort = renderer)
        try {
            vm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val originalDraft = vm.uiState.value.activeDraft!!.copy(label = "London")
            vm.onUpdateDraft(originalDraft)
            vm.onSaveDraftAlarm(originalDraft)
            vm.onClearDraft()
            vm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val newerDraft = vm.uiState.value.activeDraft!!.copy(label = "London")
            vm.onUpdateDraft(newerDraft)
            release.complete(Unit)
            assertEquals(newerDraft, vm.uiState.value.activeDraft)
            assertEquals(GeoCoordinates(51.5, -0.1), vm.uiState.value.selectedCoordinates)
            assertFalse(renderer.overlaysCleared)
        } finally {
            scope.cancel()
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
        val (vm, _, scope) = createTestViewModel(deleteAlarmPort = port)
        try {
            vm.onDeleteAlarm("old-alarm")
            vm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val newerDraft = vm.uiState.value.activeDraft!!.copy(label = "London")
            vm.onUpdateDraft(newerDraft)
            release.complete(Unit)
            assertEquals(newerDraft, vm.uiState.value.activeDraft)
            assertEquals(GeoCoordinates(51.5, -0.1), vm.uiState.value.selectedCoordinates)
        } finally {
            scope.cancel()
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
        val (vm, _, scope) = createTestViewModel(savePort = port)
        try {
            vm.onStartConfiguringAlarm(null, GeoCoordinates(51.5, -0.1))
            val submittedDraft = vm.uiState.value.activeDraft!!.copy(label = "London")
            vm.onUpdateDraft(submittedDraft)
            vm.onSaveDraftAlarm(submittedDraft)
            val editedDraft = submittedDraft.copy(label = "London Bridge", radiusMeters = 1000)
            vm.onUpdateDraft(editedDraft)
            vm.onDraftCoordinatesChanged(GeoCoordinates(51.505, -0.09))
            release.complete(Unit)
            assertEquals("London Bridge", vm.uiState.value.activeDraft?.label)
            assertEquals(1000, vm.uiState.value.activeDraft?.radiusMeters)
            assertEquals(GeoCoordinates(51.505, -0.09), vm.uiState.value.activeDraft?.coordinates)
        } finally {
            scope.cancel()
        }
    }

}
