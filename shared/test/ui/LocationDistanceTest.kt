package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.fakes.createTestViewModel
import com.muhammadridha.fjalar.domain.model.*
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class LocationDistanceTest {
    private class Tracker : LocationTrackerPort {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val availability = MutableStateFlow(LocationAvailability.AVAILABLE)
        override fun observeFixes() = fixes
        override fun observeAvailability() = availability
        override suspend fun startTracking(updateIntervalMillis: Long) {}
        override suspend fun stopTracking() {}
    }
    private fun alarm(id: String, longitude: Double) = LocationAlarm(
        id, id, GeoCoordinates(0.0, longitude), TriggerRadius(500), AlarmSchedule.ONE_TIME,
        state = AlarmState.ARMED_MONITORING,
    )
    private fun fix(time: TestTimeSource, accuracy: Float = 10f, longitude: Double = 0.0) = LocationFix(
        GeoCoordinates(0.0, longitude), accuracy, Instant.parse("2000-01-01T00:00:00Z"), time.markNow(),
    )

    @Test fun movementReordersArmedAlarmsAndNeverUsesRadiusAsDistance() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("west", 0.0))
        vm.onSaveAlarm(alarm("east", 1.0))
        runCurrent()
        val time = TestTimeSource()
        tracker.fixes.emit(fix(time))
        runCurrent()
        assertEquals("west", vm.uiState.value.nearestArmedAlarm?.id)
        assertEquals(0, vm.uiState.value.distanceMetersByAlarmId["west"])
        assertEquals(111_195, vm.uiState.value.distanceMetersByAlarmId["east"])
        val destination = GeoCoordinates(-33.9, 151.2)
        vm.onDraftCoordinatesChanged(destination)
        tracker.fixes.emit(fix(time, longitude = 1.0))
        runCurrent()
        assertEquals("east", vm.uiState.value.nearestArmedAlarm?.id)
        assertEquals(destination, vm.uiState.value.selectedCoordinates)
        assertEquals(TriggerRadius.DEFAULT, vm.uiState.value.selectedRadius)
    }

    @Test fun unknownDistancesUseStableIdAndExcludeNonArmedAlarms() = runTest {
        val vm = createTestViewModel(InMemoryKeyValueStorage(), coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("z", 0.0))
        vm.onSaveAlarm(alarm("a", 1.0))
        vm.onSaveAlarm(alarm("0", 0.0).copy(state = AlarmState.DISABLED))
        runCurrent()
        assertEquals("a", vm.uiState.value.nearestArmedAlarm?.id)
        assertNull(vm.uiState.value.distanceMetersByAlarmId["a"])
    }

    @Test fun equalDistancesUseStableAlarmId() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("z", 1.0))
        vm.onSaveAlarm(alarm("a", -1.0))
        tracker.fixes.emit(fix(TestTimeSource()))
        runCurrent()
        assertEquals("a", vm.uiState.value.nearestArmedAlarm?.id)
    }

    @Test fun nearestUsesUnroundedDistanceBeforeIdTieBreak() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("a", 0.001001))
        vm.onSaveAlarm(alarm("z", 0.001000))
        tracker.fixes.emit(fix(TestTimeSource()))
        runCurrent()
        assertEquals(111, vm.uiState.value.distanceMetersByAlarmId["a"])
        assertEquals(111, vm.uiState.value.distanceMetersByAlarmId["z"])
        assertEquals("z", vm.uiState.value.nearestArmedAlarm?.id)
    }

    @Test fun accuracyCutoffsKeepApproximateDotSeparateFromNumericDistance() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("a", 1.0))
        val time = TestTimeSource()
        for ((accuracy, hasDistance, hasDot) in listOf(
            Triple(100f, true, true), Triple(100.01f, false, true),
            Triple(1000f, false, true), Triple(1000.01f, false, false),
        )) {
            tracker.fixes.emit(fix(time, accuracy))
            runCurrent()
            assertEquals(hasDistance, vm.uiState.value.distanceMetersByAlarmId["a"] != null)
            assertEquals(hasDot, vm.uiState.value.userLocation != null)
        }
    }

    @Test fun fixExpiresAfterThirtySecondsWithoutAnotherCallbackOrWallClockUse() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("a", 1.0))
        val time = TestTimeSource()
        tracker.fixes.emit(fix(time))
        runCurrent()
        time += 30_000.milliseconds
        advanceTimeBy(30_000)
        runCurrent()
        assertNotNull(vm.uiState.value.distanceMetersByAlarmId["a"])
        time += 1.milliseconds
        advanceTimeBy(1)
        runCurrent()
        assertNull(vm.uiState.value.distanceMetersByAlarmId["a"])
        assertNull(vm.uiState.value.userLocation)
    }

    @Test fun viewModelRecreationCannotResetCachedFixAge() = runTest {
        val tracker = Tracker()
        val time = TestTimeSource()
        tracker.fixes.emit(fix(time))
        time += 30_001.milliseconds
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        runCurrent()
        assertNull(vm.uiState.value.userLocation)
    }

    @Test fun recenterWaitsForNewSharedFixWithoutMovingTheSelectedDestination() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        val destination = GeoCoordinates(-33.9, 151.2)
        vm.onDraftCoordinatesChanged(destination)
        vm.onRecenterMap()
        assertEquals(destination, vm.uiState.value.mapViewportCenter)
        tracker.fixes.emit(fix(TestTimeSource(), longitude = 1.0))
        runCurrent()
        assertEquals(GeoCoordinates(0.0, 1.0), vm.uiState.value.mapViewportCenter)
        assertEquals(destination, vm.uiState.value.selectedCoordinates)
    }

    @Test fun availabilityArrivingAfterFirstFixStillCentersTheInitialViewport() = runTest {
        val tracker = Tracker()
        tracker.availability.value = LocationAvailability.PROVIDER_UNAVAILABLE
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        tracker.fixes.emit(fix(TestTimeSource(), longitude = -179.9))
        runCurrent()
        assertNull(vm.uiState.value.userLocation)
        tracker.availability.value = LocationAvailability.AVAILABLE
        runCurrent()
        assertEquals(GeoCoordinates(0.0, -179.9), vm.uiState.value.mapViewportCenter)
    }

    @Test fun deniedProviderRemovesDistanceAndFutureObservationCannotRestoreIt() = runTest {
        val tracker = Tracker()
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        vm.onSaveAlarm(alarm("a", 1.0))
        val time = TestTimeSource()
        tracker.fixes.emit(fix(time))
        runCurrent()
        assertNotNull(vm.uiState.value.distanceMetersByAlarmId["a"])
        tracker.availability.value = LocationAvailability.PERMISSION_DENIED
        runCurrent()
        assertNull(vm.uiState.value.distanceMetersByAlarmId["a"])
        assertNull(vm.uiState.value.userLocation)
        val futureFix = fix(time).copy(monotonicObservation = time.markNow() + 1.milliseconds)
        tracker.availability.value = LocationAvailability.AVAILABLE
        tracker.fixes.emit(futureFix)
        runCurrent()
        assertNull(vm.uiState.value.distanceMetersByAlarmId["a"])
    }

    @Test fun partiallyAgedReplayExpiresAtOriginalDeadline() = runTest {
        val tracker = Tracker()
        val time = TestTimeSource()
        tracker.fixes.emit(fix(time))
        time += 20_000.milliseconds
        val vm = createTestViewModel(InMemoryKeyValueStorage(), tracker = tracker, coroutineScope = backgroundScope)
        runCurrent()
        assertNotNull(vm.uiState.value.userLocation)
        time += 10_001.milliseconds
        advanceTimeBy(10_001)
        runCurrent()
        assertNull(vm.uiState.value.userLocation)
    }
}
