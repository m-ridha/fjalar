package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosCLLocationAdapterTest {
    private class FakeGateway : IosLocationGateway {
        var starts = 0
        var stops = 0
        var background = false
        var fixListener: (com.muhammadridha.fjalar.domain.model.LocationFix) -> Unit = {}
        var availabilityListener: (com.muhammadridha.fjalar.domain.model.LocationAvailability) -> Unit = {}
        override fun setListeners(onFix: (com.muhammadridha.fjalar.domain.model.LocationFix) -> Unit, onAvailability: (com.muhammadridha.fjalar.domain.model.LocationAvailability) -> Unit) {
            fixListener = onFix
            availabilityListener = onAvailability
        }
        override fun setBackgroundEnabled(enabled: Boolean) { background = enabled }
        override fun start() { starts++ }
        override fun stop() { stops++ }
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
    fun trackingState_togglesAndIsIdempotent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCLLocationAdapter(
            coroutineDispatcher = testDispatcher,
            gateway = FakeGateway(),
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
            gateway = FakeGateway(),
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
            gateway = FakeGateway(),
        )

        val testCoordinates = GeoCoordinates(latitude = -6.1754, longitude = 106.8272)
        val testAccuracy = 10.0f

        adapter.dispatchLocationUpdate(testCoordinates, testAccuracy)

        assertEquals(1, fakePort.receivedCalls.size)
        assertEquals(testCoordinates, fakePort.receivedCalls.first().coordinates)
        assertEquals(testAccuracy, fakePort.receivedCalls.first().accuracy)

        adapter.close()
    }
    @Test
    fun ownersShareOneManagerAndOnlyAlarmsEnableBackground() = runTest {
        val gateway = FakeGateway()
        val adapter = IosCLLocationAdapter(gateway = gateway, coroutineDispatcher = StandardTestDispatcher(testScheduler))
        adapter.setForegroundMapTracking(true)
        assertEquals(1, gateway.starts)
        assertFalse(gateway.background)
        adapter.startTracking(10_000L)
        assertTrue(gateway.background)
        adapter.stopTracking()
        assertFalse(gateway.background)
        assertEquals(0, gateway.stops)
        adapter.setForegroundMapTracking(false)
        assertEquals(1, gateway.stops)
        adapter.startTracking(10_000L)
        adapter.setForegroundMapTracking(true)
        adapter.setForegroundMapTracking(false)
        assertTrue(adapter.isTracking)
        assertEquals(2, gateway.starts)
        adapter.close()
    }

    @Test
    fun nativeFailuresRemainVisibleAndNewerFixWins() = runTest {
        val gateway = FakeGateway()
        val adapter = IosCLLocationAdapter(gateway = gateway, coroutineDispatcher = StandardTestDispatcher(testScheduler))
        for (status in listOf(com.muhammadridha.fjalar.domain.model.LocationAvailability.PERMISSION_DENIED, com.muhammadridha.fjalar.domain.model.LocationAvailability.PROVIDER_UNAVAILABLE)) {
            gateway.availabilityListener(status)
            assertEquals(status, adapter.observeAvailability().value)
        }
        val now = kotlin.time.Instant.parse("2026-10-27T00:00:00Z")
        adapter.dispatchLocationUpdate(GeoCoordinates(1.0, 1.0), 10f, now)
        adapter.dispatchLocationUpdate(GeoCoordinates(2.0, 2.0), 10f, now - kotlin.time.Duration.parse("1s"))
        assertEquals(GeoCoordinates(2.0, 2.0), adapter.observeFixes().first().coordinates)
        adapter.close()
    }
}
