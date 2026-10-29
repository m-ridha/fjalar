package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidFusedLocationAdapterTest {

    private class FakeLocationPlatformGateway : LocationPlatformGateway {
        var startCount = 0
        var stopCount = 0
        var lastIntervalMillis: Long? = null
        var tracking = false
        var listener: ((GeoCoordinates, Float) -> Unit)? = null

        override fun startForegroundService(updateIntervalMillis: Long) {
            startCount++
            lastIntervalMillis = updateIntervalMillis
            tracking = true
        }

        override fun stopForegroundService() {
            stopCount++
            tracking = false
        }

        override fun isTracking(): Boolean = tracking

        override fun setLocationListener(listener: ((coordinates: GeoCoordinates, horizontalAccuracyMeters: Float) -> Unit)?) {
            this.listener = listener
        }

        fun emitLocation(coordinates: GeoCoordinates, horizontalAccuracyMeters: Float) {
            listener?.invoke(coordinates, horizontalAccuracyMeters)
        }
    }

    private class FakeHandleLocationUpdatePort : HandleLocationUpdatePort {
        data class UpdateCall(
            val coordinates: GeoCoordinates,
            val accuracy: Float,
        )

        val receivedCalls = mutableListOf<UpdateCall>()

        override suspend fun handleLocationUpdate(
            coordinates: GeoCoordinates,
            horizontalAccuracyMeters: Float,
        ): List<LocationAlarm> {
            receivedCalls.add(UpdateCall(coordinates, horizontalAccuracyMeters))
            return emptyList()
        }
    }

    @Test
    fun nativeObservationAgeSurvivesPublicationInsteadOfRestartingAtZero() = runTest {
        val adapter = AndroidFusedLocationAdapter(
            coroutineDispatcher = StandardTestDispatcher(testScheduler),
            gateway = FakeLocationPlatformGateway(),
        )
        try {
            adapter.submitLocationUpdate(GeoCoordinates(51.5, -0.1), 10f, observationAgeMillis = 29_000L)
            val published = adapter.observeFixes().first()
            assertTrue(published.monotonicObservation.elapsedNow() >= 29.seconds)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun startTracking_invokesGatewayWithConfiguredInterval() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        val adapter = AndroidFusedLocationAdapter(
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        adapter.startTracking(10_000L)

        assertTrue(adapter.isTracking)
        assertTrue(fakeGateway.tracking)
        assertEquals(1, fakeGateway.startCount)
        assertEquals(10_000L, fakeGateway.lastIntervalMillis)
    }

    @Test
    fun stopTracking_stopsForegroundServiceAndEnforcesZeroRunawayBatteryDrain() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        val adapter = AndroidFusedLocationAdapter(
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        adapter.startTracking(5_000L)
        assertTrue(adapter.isTracking)

        adapter.stopTracking()

        assertFalse(adapter.isTracking, "Adapter must be inactive after stopTracking (Safety Invariant 2)")
        assertFalse(fakeGateway.tracking, "Gateway must not be tracking after stopTracking (Safety Invariant 2)")
        assertEquals(1, fakeGateway.stopCount)
    }

    @Test
    fun trackingMethods_areIdempotentOnRepeatedCalls() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        val adapter = AndroidFusedLocationAdapter(
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        // Initial repeated stops when already stopped
        adapter.stopTracking()
        adapter.stopTracking()
        assertEquals(0, fakeGateway.stopCount, "Repeated stops while stopped must be no-op")

        // Start tracking multiple times
        adapter.startTracking(10_000L)
        adapter.startTracking(10_000L)
        adapter.startTracking(15_000L)
        assertEquals(1, fakeGateway.startCount, "Repeated start while active must be idempotent")

        // Stop tracking multiple times
        adapter.stopTracking()
        adapter.stopTracking()
        assertEquals(1, fakeGateway.stopCount, "Repeated stop calls must execute once")
    }

    @Test
    fun locationFix_dispatchesToUpdatePortViaGatewayListener() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        val fakePort = FakeHandleLocationUpdatePort()

        val adapter = AndroidFusedLocationAdapter(
            updatePort = fakePort,
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        adapter.startTracking(10_000L)
        val testCoordinates = GeoCoordinates(latitude = -6.2088, longitude = 106.8456)
        val testAccuracy = 12.5f

        fakeGateway.emitLocation(testCoordinates, testAccuracy)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakePort.receivedCalls.size)
        val received = fakePort.receivedCalls.first()
        assertEquals(testCoordinates, received.coordinates)
        assertEquals(testAccuracy, received.accuracy)
    }

    @Test
    fun locationFix_dispatchesViaPortProviderLambda() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        val fakePort = FakeHandleLocationUpdatePort()

        val adapter = AndroidFusedLocationAdapter(
            updatePortProvider = { fakePort },
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        val testCoordinates = GeoCoordinates(latitude = -6.1754, longitude = 106.8272)
        adapter.dispatchLocationUpdate(testCoordinates, 8.0f)

        assertEquals(1, fakePort.receivedCalls.size)
        assertEquals(testCoordinates, fakePort.receivedCalls.first().coordinates)
        assertEquals(8.0f, fakePort.receivedCalls.first().accuracy)
    }

    @Test
    fun stopTracking_stopsServiceWhenGatewayReportsTrackingEvenIfAdapterWasNotActive() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        fakeGateway.tracking = true // e.g. service was restarted by OS after process death

        val adapter = AndroidFusedLocationAdapter(
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        assertFalse(adapter.isTracking)
        adapter.stopTracking()

        assertEquals(1, fakeGateway.stopCount, "Stop must reach gateway when gateway is active")
        assertFalse(fakeGateway.tracking)
    }

    @Test
    fun startTracking_retriesStartForegroundService_whenUnderlyingServiceStopped() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeGateway = FakeLocationPlatformGateway()
        val adapter = AndroidFusedLocationAdapter(
            coroutineDispatcher = testDispatcher,
            gateway = fakeGateway,
        )

        // First start succeeds
        adapter.startTracking(10_000L)
        assertTrue(adapter.isTracking)
        assertEquals(1, fakeGateway.startCount)

        // Simulate service abortion or stopSelf from missing permissions / OS
        fakeGateway.tracking = false
        assertFalse(adapter.isTracking, "Adapter must not report tracking when gateway reports stopped")

        // Subsequent startTracking must retry and not be blocked by stale adapter state (Safety Invariant 2)
        adapter.startTracking(10_000L)
        assertTrue(adapter.isTracking)
        assertEquals(2, fakeGateway.startCount)
    }
    @Test
    fun foregroundMapAndAlarmShareOneStream() = runTest {
        val gateway = FakeLocationPlatformGateway()
        val adapter = AndroidFusedLocationAdapter(gateway = gateway)
        adapter.setForegroundMapTracking(true)
        assertEquals(1, gateway.startCount)
        adapter.startTracking(10_000L)
        adapter.stopTracking()
        assertTrue(gateway.tracking, "Stopping alarms must preserve map ownership")
        adapter.setForegroundMapTracking(false)
        assertFalse(gateway.tracking)
        assertEquals(1, gateway.stopCount)
        adapter.close()
    }

    @Test
    fun hiddenMapPreservesArmedAlarm() = runTest {
        val gateway = FakeLocationPlatformGateway()
        val adapter = AndroidFusedLocationAdapter(gateway = gateway)
        adapter.startTracking(10_000L)
        adapter.setForegroundMapTracking(true)
        adapter.setForegroundMapTracking(false)
        assertTrue(gateway.tracking)
        assertEquals(1, gateway.startCount)
        adapter.stopTracking()
        assertFalse(gateway.tracking)
        adapter.close()
    }
    @Test
    fun availabilityAndLatestFixAreObservedWithoutAnotherListener() = runTest {
        val adapter = AndroidFusedLocationAdapter(gateway = FakeLocationPlatformGateway())
        for (status in listOf(com.muhammadridha.fjalar.domain.model.LocationAvailability.PERMISSION_DENIED, com.muhammadridha.fjalar.domain.model.LocationAvailability.PROVIDER_UNAVAILABLE)) {
            adapter.reportAvailability(status)
            assertEquals(status, adapter.observeAvailability().value)
        }
        val now = kotlin.time.Instant.parse("2026-10-27T00:00:00Z")
        adapter.dispatchLocationUpdate(GeoCoordinates(1.0, 1.0), 10f, now)
        adapter.dispatchLocationUpdate(GeoCoordinates(2.0, 2.0), 10f, now - kotlin.time.Duration.parse("1s"))
        assertEquals(GeoCoordinates(2.0, 2.0), adapter.observeFixes().first().coordinates)
        adapter.dispatchLocationUpdate(GeoCoordinates(3.0, 3.0), 5f, now + kotlin.time.Duration.parse("1s"))
        assertEquals(GeoCoordinates(3.0, 3.0), adapter.observeFixes().first().coordinates)
        adapter.close()
    }
    @Test
    fun queuedFixCannotOverrideFailureOrReachAlarmAfterOwnershipEnds() = runTest {
        val gateway = FakeLocationPlatformGateway()
        val port = FakeHandleLocationUpdatePort()
        val adapter = AndroidFusedLocationAdapter(gateway = gateway, updatePort = port, coroutineDispatcher = StandardTestDispatcher(testScheduler))
        adapter.setForegroundMapTracking(true)
        for (status in listOf(com.muhammadridha.fjalar.domain.model.LocationAvailability.PERMISSION_DENIED, com.muhammadridha.fjalar.domain.model.LocationAvailability.PROVIDER_UNAVAILABLE)) {
            gateway.emitLocation(GeoCoordinates(1.0, 2.0), 10f)
            adapter.reportAvailability(status)
            testScheduler.runCurrent()
            assertEquals(status, adapter.observeAvailability().value)
            assertTrue(port.receivedCalls.isEmpty())
        }
        gateway.emitLocation(GeoCoordinates(1.0, 2.0), 10f)
        adapter.setForegroundMapTracking(false)
        testScheduler.runCurrent()
        assertTrue(port.receivedCalls.isEmpty())
        adapter.close()
    }

}
