package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosCLLocationAdapterTest {

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
    fun trackingState_togglesAndIsIdempotent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCLLocationAdapter(
            coroutineDispatcher = testDispatcher,
        )

        assertFalse(adapter.isTracking)

        // Stop while stopped is no-op
        adapter.stopTracking()
        assertFalse(adapter.isTracking)

        // Start tracking
        adapter.startTracking(10_000L)
        assertTrue(adapter.isTracking)

        // Repeated start is idempotent
        adapter.startTracking(10_000L)
        assertTrue(adapter.isTracking)

        // Stop tracking
        adapter.stopTracking()
        assertFalse(adapter.isTracking)

        // Repeated stop is idempotent
        adapter.stopTracking()
        assertFalse(adapter.isTracking)

        adapter.close()
    }

    @Test
    fun locationFix_dispatchesToUpdatePort() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakePort = FakeHandleLocationUpdatePort()
        val adapter = IosCLLocationAdapter(
            updatePort = fakePort,
            coroutineDispatcher = testDispatcher,
        )

        val testCoordinates = GeoCoordinates(latitude = -6.2088, longitude = 106.8456)
        val testAccuracy = 15.0f

        adapter.dispatchLocationUpdate(testCoordinates, testAccuracy)

        assertEquals(1, fakePort.receivedCalls.size)
        val call = fakePort.receivedCalls.first()
        assertEquals(testCoordinates, call.coordinates)
        assertEquals(testAccuracy, call.accuracy)

        adapter.close()
    }

    @Test
    fun locationFix_dispatchesViaPortProviderLambda() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakePort = FakeHandleLocationUpdatePort()
        val adapter = IosCLLocationAdapter(
            updatePortProvider = { fakePort },
            coroutineDispatcher = testDispatcher,
        )

        val testCoordinates = GeoCoordinates(latitude = -6.1754, longitude = 106.8272)
        val testAccuracy = 10.0f

        adapter.dispatchLocationUpdate(testCoordinates, testAccuracy)

        assertEquals(1, fakePort.receivedCalls.size)
        assertEquals(testCoordinates, fakePort.receivedCalls.first().coordinates)
        assertEquals(testAccuracy, fakePort.receivedCalls.first().accuracy)

        adapter.close()
    }
}
