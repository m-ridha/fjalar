package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Startup gate: the map must not show before the initial viewport is known (never `0,0`). */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupGateTest {

    private class FakeObserveAlarmsPort(initialAlarms: List<LocationAlarm>) : ObserveAlarmsPort {
        val flow = MutableStateFlow(initialAlarms)
        override fun observeAlarms(): Flow<List<LocationAlarm>> = flow
    }

    private class NoOpSaveAlarmPort : SaveAlarmPort {
        override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> = Result.success(Unit)
        override suspend fun saveAlarmConfiguration(
            alarm: LocationAlarm,
            enabled: Boolean?,
            requireExisting: Boolean,
        ): Result<Unit> = Result.success(Unit)
    }

    private class NoOpToggleAlarmPort : ToggleAlarmPort {
        override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    }

    private class NoOpDismissAlarmPort : DismissAlarmPort {
        override suspend fun dismissAlarm(alarmId: String): Result<Unit> = Result.success(Unit)
    }

    private class NoOpSkipTodayPort : SkipTodayPort {
        override suspend fun skipToday(alarmId: String): Result<Unit> = Result.success(Unit)
    }

    private class Harness(val viewModel: FjalarViewModel, val scope: CoroutineScope)

    private fun seededStorage(coordinates: GeoCoordinates): InMemoryKeyValueStorage =
        InMemoryKeyValueStorage().apply {
            putString(
                PersistentPreferencesAdapter.KEY_LAST_KNOWN_LOCATION,
                "${coordinates.latitude},${coordinates.longitude}",
            )
        }

    private fun TestScope.harness(
        alarms: List<LocationAlarm> = emptyList(),
        tracker: LocationTrackerPort? = UiLocationTracker(),
        storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage(),
        timeoutMillis: Long = FjalarViewModel.STARTUP_VIEWPORT_TIMEOUT_MILLIS,
        eager: Boolean = true,
    ): Harness {
        val dispatcher = if (eager) UnconfinedTestDispatcher(testScheduler) else StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val viewModel = FjalarViewModel(
            observeAlarmsPort = FakeObserveAlarmsPort(alarms),
            saveAlarmPort = NoOpSaveAlarmPort(),
            toggleAlarmPort = NoOpToggleAlarmPort(),
            dismissAlarmPort = NoOpDismissAlarmPort(),
            skipTodayPort = NoOpSkipTodayPort(),
            preferencesPort = PersistentPreferencesAdapter(storage),
            coroutineScope = scope,
            locationTrackerPort = tracker,
            startupViewportTimeoutMillis = timeoutMillis,
        )
        return Harness(viewModel, scope)
    }

    @Test
    fun notReadyBeforeAnyStartupSignalAndNoViewportIsShown() = runTest {
        val h = harness()
        try {
            val state = h.viewModel.uiState.value
            assertFalse(state.isInitialViewportReady)
            assertNull(state.mapViewportCenter)
            assertNull(state.userLocation)
        } finally { h.scope.cancel() }
    }

    @Test
    fun firstFixOpensTheGateAtTheUserLocation() = runTest {
        val tracker = UiLocationTracker()
        val h = harness(tracker = tracker)
        try {
            val fix = GeoCoordinates(-6.2, 106.8)
            tracker.emit(fix)
            val state = h.viewModel.uiState.value
            assertTrue(state.isInitialViewportReady)
            assertEquals(fix, state.mapViewportCenter)
            assertEquals(15.0, state.mapViewportZoom)
        } finally { h.scope.cancel() }
    }

    @Test
    fun locationUnavailableOpensTheGateOnceAlarmsHaveLoaded() = runTest {
        val h = harness()
        try {
            h.viewModel.onLocationAccessChecked(canLocate = false)
            val state = h.viewModel.uiState.value
            assertTrue(state.isInitialViewportReady)
            assertNull(state.userLocation)
        } finally { h.scope.cancel() }
    }

    @Test
    fun locationAvailableDoesNotOpenTheGateByItself() = runTest {
        val h = harness()
        try {
            h.viewModel.onLocationAccessChecked(canLocate = true)
            assertFalse(h.viewModel.uiState.value.isInitialViewportReady)
        } finally { h.scope.cancel() }
    }

    @Test
    fun timeoutOpensTheGateWithVirtualTime() = runTest {
        val h = harness()
        try {
            advanceTimeBy(FjalarViewModel.STARTUP_VIEWPORT_TIMEOUT_MILLIS - 1)
            runCurrent()
            assertFalse(h.viewModel.uiState.value.isInitialViewportReady)
            advanceTimeBy(1)
            runCurrent()
            val state = h.viewModel.uiState.value
            assertTrue(state.isInitialViewportReady)
            assertNull(state.mapViewportCenter)
        } finally { h.scope.cancel() }
    }

    @Test
    fun timeoutIsInjectable() = runTest {
        val h = harness(timeoutMillis = 100L)
        try {
            advanceTimeBy(100)
            runCurrent()
            assertTrue(h.viewModel.uiState.value.isInitialViewportReady)
        } finally { h.scope.cancel() }
    }

    @Test
    fun timeoutWithoutFixFallsBackToSavedAlarmInsteadOfNeutralOrigin() = runTest {
        val alarm = manggaraiAlarm()
        val h = harness(alarms = listOf(alarm))
        try {
            val requestIdBefore = h.viewModel.uiState.value.viewportRequestId
            advanceTimeBy(FjalarViewModel.STARTUP_VIEWPORT_TIMEOUT_MILLIS)
            runCurrent()
            val state = h.viewModel.uiState.value
            assertTrue(state.isInitialViewportReady)
            assertEquals(alarm.coordinates, state.mapViewportCenter)
            assertEquals(15.0, state.mapViewportZoom)
            assertEquals(requestIdBefore + 1L, state.viewportRequestId)
        } finally { h.scope.cancel() }
    }

    @Test
    fun deniedLocationFallsBackToSavedAlarm() = runTest {
        val alarm = manggaraiAlarm()
        val h = harness(alarms = listOf(alarm))
        try {
            h.viewModel.onLocationAccessChecked(canLocate = false)
            val state = h.viewModel.uiState.value
            assertTrue(state.isInitialViewportReady)
            assertEquals(alarm.coordinates, state.mapViewportCenter)
        } finally { h.scope.cancel() }
    }

    @Test
    fun lateFixAfterTimeoutStillMovesNothingBecauseViewportIsAlreadyKnown() = runTest {
        val tracker = UiLocationTracker()
        val alarm = manggaraiAlarm()
        val h = harness(alarms = listOf(alarm), tracker = tracker)
        try {
            advanceTimeBy(FjalarViewModel.STARTUP_VIEWPORT_TIMEOUT_MILLIS)
            runCurrent()
            tracker.emit(GeoCoordinates(51.5, -0.12))
            // Existing contract: a fix moves the camera only when no viewport is known or a recenter is pending.
            assertEquals(alarm.coordinates, h.viewModel.uiState.value.mapViewportCenter)
            assertEquals(GeoCoordinates(51.5, -0.12), h.viewModel.uiState.value.userLocation)
        } finally { h.scope.cancel() }
    }

    @Test
    fun restoredLocationOpensTheGateBeforeAnyCollectorRuns() = runTest {
        val storage = seededStorage(GeoCoordinates(-6.2, 106.8))
        val h = harness(storage = storage, eager = false)
        try {
            // StandardTestDispatcher: no coroutine has run yet.
            val state = h.viewModel.uiState.value
            assertTrue(state.isInitialViewportReady)
            assertEquals(GeoCoordinates(-6.2, 106.8), state.mapViewportCenter)
            assertEquals(15.0, state.mapViewportZoom)
        } finally { h.scope.cancel() }
    }

    @Test
    fun firstFixAfterRestoredLocationFollowsTheUserExactlyOnce() = runTest {
        val tracker = UiLocationTracker()
        val h = harness(tracker = tracker, storage = seededStorage(GeoCoordinates(-6.2, 106.8)))
        try {
            val firstFix = GeoCoordinates(51.5, -0.12)
            tracker.emit(firstFix)
            val afterFirst = h.viewModel.uiState.value
            assertEquals(firstFix, afterFirst.mapViewportCenter)
            assertEquals(15.0, afterFirst.mapViewportZoom)

            tracker.emit(GeoCoordinates(51.6, -0.13))
            // Later fixes update the dot, not the camera: the restored viewport was only a head start.
            assertEquals(firstFix, h.viewModel.uiState.value.mapViewportCenter)
        } finally { h.scope.cancel() }
    }

    @Test
    fun browsingBeforeTheFirstFixKeepsTheBrowsedViewport() = runTest {
        val tracker = UiLocationTracker()
        val h = harness(tracker = tracker, storage = seededStorage(GeoCoordinates(-6.2, 106.8)))
        try {
            val browsed = GeoCoordinates(-6.9, 107.6)
            h.viewModel.onMapViewportBrowsed(browsed, zoom = 13.0)
            tracker.emit(GeoCoordinates(51.5, -0.12))
            assertEquals(browsed, h.viewModel.uiState.value.mapViewportCenter)
            assertEquals(13.0, h.viewModel.uiState.value.mapViewportZoom)
        } finally { h.scope.cancel() }
    }

    @Test
    fun openingTheEditorBeforeTheFirstFixStopsTheCameraFollowingTheFix() = runTest {
        val tracker = UiLocationTracker()
        val storedCenter = GeoCoordinates(-6.2, 106.8)
        val h = harness(tracker = tracker, storage = seededStorage(storedCenter))
        try {
            h.viewModel.onStartConfiguringAlarm(
                alarmId = null,
                defaultCoordinates = GeoCoordinates(-6.9, 107.6),
            )
            tracker.emit(GeoCoordinates(51.5, -0.12))
            // The editor owns the camera; the late fix only updates the dot.
            assertEquals(storedCenter, h.viewModel.uiState.value.mapViewportCenter)
            assertEquals(GeoCoordinates(51.5, -0.12), h.viewModel.uiState.value.userLocation)
        } finally { h.scope.cancel() }
    }

    @Test
    fun freshFixesAreStoredOnlyAfterMeaningfulMovement() = runTest {
        val storage = InMemoryKeyValueStorage()
        val tracker = UiLocationTracker()
        val h = harness(tracker = tracker, storage = storage)
        try {
            tracker.emit(GeoCoordinates(-6.2, 106.8))
            val first = storage.getString(PersistentPreferencesAdapter.KEY_LAST_KNOWN_LOCATION)
            assertNotNull(first)

            tracker.emit(GeoCoordinates(-6.2001, 106.8)) // ~11 m: jitter, not travel.
            assertEquals(first, storage.getString(PersistentPreferencesAdapter.KEY_LAST_KNOWN_LOCATION))

            tracker.emit(GeoCoordinates(-6.2045, 106.8)) // ~500 m.
            val moved = storage.getString(PersistentPreferencesAdapter.KEY_LAST_KNOWN_LOCATION)
            assertNotNull(moved)
            assertTrue(moved != first)
        } finally { h.scope.cancel() }
    }

    @Test
    fun unusableStoredLocationIsIgnored() {
        for (raw in listOf("", "-6.2", "-6.2,", "north,east", "91.0,0.0", "-6.2,181.0")) {
            val storage = InMemoryKeyValueStorage()
            storage.putString(PersistentPreferencesAdapter.KEY_LAST_KNOWN_LOCATION, raw)
            assertNull(
                PersistentPreferencesAdapter.readLastKnownLocation(storage),
                "Stored value '$raw' must not produce a viewport",
            )
        }
    }

    @Test
    fun savedThemeIsInTheFirstStateBeforeAnyCollectorRuns() = runTest {
        val storage = InMemoryKeyValueStorage()
        storage.putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.DARK.name)
        val h = harness(storage = storage, eager = false)
        try {
            // StandardTestDispatcher: no coroutine has run yet.
            assertEquals(ThemeMode.DARK, h.viewModel.uiState.value.themeMode)
        } finally { h.scope.cancel() }
    }

    @Test
    fun storedThemeModeIsResolvedSynchronouslyForEveryChoice() {
        val storage = InMemoryKeyValueStorage()
        assertEquals(ThemeMode.SYSTEM, PersistentPreferencesAdapter.readThemeMode(storage))
        for (mode in ThemeMode.entries) {
            storage.putString(PersistentPreferencesAdapter.KEY_THEME_MODE, mode.name)
            assertEquals(mode, PersistentPreferencesAdapter.readThemeMode(storage))
        }
        storage.putString(PersistentPreferencesAdapter.KEY_THEME_MODE, "NOT_A_MODE")
        assertEquals(ThemeMode.SYSTEM, PersistentPreferencesAdapter.readThemeMode(storage))
    }
}
