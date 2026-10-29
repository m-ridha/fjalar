package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.createDefaultViewModel
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LocationTrackingLifecycleTest {
    private class Tracker : LocationTrackerPort {
        var mapRequested = false
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val availability = MutableStateFlow(LocationAvailability.PROVIDER_UNAVAILABLE)
        override fun observeFixes() = fixes
        override fun observeAvailability() = availability
        override suspend fun setForegroundMapTracking(enabled: Boolean) { mapRequested = enabled }
        override suspend fun startTracking(updateIntervalMillis: Long) {}
        override suspend fun stopTracking() {}
    }

    @Test fun mapRequestRequiresBothVisibleMapAndForegroundApp() = runTest {
        val tracker = Tracker()
        val vm = createDefaultViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onMapVisibilityChanged(true)
        runCurrent()
        assertFalse(tracker.mapRequested)
        vm.onAppForegroundChanged(true)
        runCurrent()
        assertTrue(tracker.mapRequested)
        vm.onMapVisibilityChanged(false)
        runCurrent()
        assertFalse(tracker.mapRequested)
        vm.onMapVisibilityChanged(true)
        runCurrent()
        assertTrue(tracker.mapRequested)
        vm.onAppForegroundChanged(false)
        runCurrent()
        assertFalse(tracker.mapRequested)
    }

    @Test fun sharedFixAndAvailabilityReachUiWithoutOverwritingDestination() = runTest {
        val tracker = Tracker()
        val vm = createDefaultViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        val destination = vm.uiState.value.selectedCoordinates
        val fix = LocationFix(GeoCoordinates(-33.0, 151.0), 12f, Instant.parse("2026-10-27T00:00:00Z"))
        tracker.fixes.emit(fix)
        tracker.availability.value = LocationAvailability.PERMISSION_DENIED
        runCurrent()
        assertEquals(fix, vm.uiState.value.locationFix)
        assertEquals(destination, vm.uiState.value.selectedCoordinates)
        assertEquals(LocationAvailability.PERMISSION_DENIED, vm.uiState.value.locationAvailability)
    }
    @Test fun noLocationNewAlarmRequiresASelectedDestination() = runTest {
        val vm = createDefaultViewModel(InMemoryKeyValueStorage(), coroutineScope = backgroundScope)
        vm.onOpenNewAlarm()
        assertTrue(vm.uiState.value.isConfigureSheetVisible)
        assertNull(vm.uiState.value.selectedCoordinates)
        vm.onStartConfiguringAlarm(null)
        assertNull(vm.uiState.value.activeDraft?.coordinates)
    }

    @Test fun permissionDenialRemovesUserPositionWithoutChangingDestination() = runTest {
        val tracker = Tracker()
        val vm = createDefaultViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        val destination = GeoCoordinates(51.5, -0.1)
        vm.onMapClicked(destination)
        tracker.availability.value = LocationAvailability.AVAILABLE
        tracker.fixes.emit(LocationFix(GeoCoordinates(-33.0, 151.0), 12f, Instant.parse("2026-10-27T00:00:00Z")))
        runCurrent()
        assertNotNull(vm.uiState.value.userLocation)
        tracker.availability.value = LocationAvailability.PERMISSION_DENIED
        runCurrent()
        assertNull(vm.uiState.value.userLocation)
        assertEquals(destination, vm.uiState.value.selectedCoordinates)
    }
}
