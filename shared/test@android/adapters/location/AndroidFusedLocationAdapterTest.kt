package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
}
