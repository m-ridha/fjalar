package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRecenterTest {

    private class FakeObserveAlarmsPort(initialAlarms: List<LocationAlarm> = emptyList()) : ObserveAlarmsPort {
        val flow = MutableStateFlow(initialAlarms)
        override fun observeAlarms(): Flow<List<LocationAlarm>> = flow
    }

    private class FakeSaveAlarmPort : SaveAlarmPort {
        override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
    }

    private class FakeToggleAlarmPort : ToggleAlarmPort {
        override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    }

    private class FakeDismissAlarmPort : DismissAlarmPort {
        override suspend fun dismissAlarm(alarmId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeSkipTodayPort : SkipTodayPort {
        override suspend fun skipToday(alarmId: String): Result<Unit> = Result.success(Unit)
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

    private fun TestScope.createTestViewModel(
        initialAlarms: List<LocationAlarm> = emptyList(),
        mapRendererPort: MapRendererPort? = null,
    ): Triple<FjalarViewModel, FakeMapRendererPort?, CoroutineScope> {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        val fakeMapRenderer = mapRendererPort as? FakeMapRendererPort
        val viewModel = FjalarViewModel(
            observeAlarmsPort = FakeObserveAlarmsPort(initialAlarms),
            saveAlarmPort = FakeSaveAlarmPort(),
            toggleAlarmPort = FakeToggleAlarmPort(),
            dismissAlarmPort = FakeDismissAlarmPort(),
            skipTodayPort = FakeSkipTodayPort(),
            preferencesPort = PersistentPreferencesAdapter(InMemoryKeyValueStorage()),
            mapRendererPort = mapRendererPort,
            coroutineScope = vmScope,
        )
        return Triple(viewModel, fakeMapRenderer, vmScope)
    }

    @Test
    fun onPermissionResult_updatesHasLocationPermission() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            assertTrue(viewModel.uiState.value.hasLocationPermission)

            viewModel.onPermissionResult(false)
            assertFalse(viewModel.uiState.value.hasLocationPermission)

            viewModel.onPermissionResult(true)
            assertTrue(viewModel.uiState.value.hasLocationPermission)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onUserLocationChanged_updatesUserLocationAndInitialMapViewport() = runTest {
        val fakeRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeRenderer)
        try {
            assertNull(viewModel.uiState.value.userLocation)
            assertNull(viewModel.uiState.value.mapViewportCenter)

            val initialLocation = GeoCoordinates(-6.1754, 106.8272)
            viewModel.onUserLocationChanged(initialLocation)

            assertEquals(initialLocation, viewModel.uiState.value.userLocation)
            assertEquals(initialLocation, viewModel.uiState.value.mapViewportCenter)

            // When mapViewportCenter is already initialized, subsequent user location changes
            // update userLocation without resetting mapViewportCenter
            val userMovedViewport = GeoCoordinates(-6.2000, 106.8000)
            viewModel.onRecenterMap() // or user pans; let's explicitly change center via onRecenter
            val secondLocation = GeoCoordinates(-6.1800, 106.8300)
            viewModel.onUserLocationChanged(secondLocation)

            assertEquals(secondLocation, viewModel.uiState.value.userLocation)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_centersOnUserLocationWhenAvailable() = runTest {
        val fakeRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(mapRendererPort = fakeRenderer)
        try {
            val userLocation = GeoCoordinates(-6.1754, 106.8272)
            viewModel.onUserLocationChanged(userLocation)

            viewModel.onRecenterMap()

            assertEquals(userLocation, viewModel.uiState.value.mapViewportCenter)
            assertEquals(15.0, viewModel.uiState.value.mapViewportZoom)
            assertEquals(userLocation to 15.0, fakeRenderer.lastViewport)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_fallsBackInPriorityOrder() = runTest {
        val fakeRenderer = FakeMapRendererPort()
        val savedAlarm = manggaraiAlarm()
        val (viewModel, _, vmScope) = createTestViewModel(
            initialAlarms = listOf(savedAlarm),
            mapRendererPort = fakeRenderer,
        )
        try {
            // Priority 4 fallback: userLocation == null, selectedCoordinates == null, no alarms
            // But here we have an alarm, so Priority 3 applies:
            assertNull(viewModel.uiState.value.userLocation)
            assertNull(viewModel.uiState.value.selectedCoordinates)

            viewModel.onRecenterMap()
            assertEquals(savedAlarm.coordinates, viewModel.uiState.value.mapViewportCenter)
            assertEquals(15.0, viewModel.uiState.value.mapViewportZoom)
            assertEquals(savedAlarm.coordinates to 15.0, fakeRenderer.lastViewport)

            // Priority 2: selectedCoordinates overrides alarms
            val selectedCoords = GeoCoordinates(-6.2024, 106.8232)
            viewModel.onMapClicked(selectedCoords)
            viewModel.onRecenterMap()
            assertEquals(selectedCoords, viewModel.uiState.value.mapViewportCenter)
            assertEquals(selectedCoords to 15.0, fakeRenderer.lastViewport)

            // Priority 1: userLocation overrides selectedCoordinates and alarms
            val liveGpsCoords = GeoCoordinates(-6.1754, 106.8272)
            viewModel.onUserLocationChanged(liveGpsCoords)
            viewModel.onRecenterMap()
            assertEquals(liveGpsCoords, viewModel.uiState.value.mapViewportCenter)
            assertEquals(liveGpsCoords to 15.0, fakeRenderer.lastViewport)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun onRecenterMap_usesNeutralOverviewWhenAllLocationsUnavailable() = runTest {
        val fakeRenderer = FakeMapRendererPort()
        val (viewModel, _, vmScope) = createTestViewModel(
            initialAlarms = emptyList(),
            mapRendererPort = fakeRenderer,
        )
        try {
            assertNull(viewModel.uiState.value.userLocation)
            assertNull(viewModel.uiState.value.selectedCoordinates)
            assertTrue(viewModel.uiState.value.alarms.isEmpty())

            viewModel.onRecenterMap()

            assertEquals(GeoCoordinates(0.0, 0.0), viewModel.uiState.value.mapViewportCenter)
            assertEquals(2.0, viewModel.uiState.value.mapViewportZoom)
            assertEquals(GeoCoordinates(0.0, 0.0) to 2.0, fakeRenderer.lastViewport)
        } finally {
            vmScope.cancel()
        }
    }
    @Test
    fun firstLocationRequestsNativeCameraMoveButLaterFixesPreserveUserPanning() = runTest {
        val (vm, _, scope) = createTestViewModel()
        try {
            vm.onUserLocationChanged(GeoCoordinates(51.5, -0.1))
            assertEquals(1L, vm.uiState.value.viewportRequestId)
            assertEquals(GeoCoordinates(51.5, -0.1), vm.uiState.value.mapViewportCenter)
            vm.onUserLocationChanged(GeoCoordinates(51.6, -0.2))
            assertEquals(1L, vm.uiState.value.viewportRequestId)
            assertEquals(GeoCoordinates(51.5, -0.1), vm.uiState.value.mapViewportCenter)
        } finally { scope.cancel() }
    }

}
