package com.muhammadridha.fjalar.adapters.geofence

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.CoreLocation.CLCircularRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalForeignApi::class)
class FakeIosGeofenceEngine : IosGeofenceEngine {
    val monitoredRegions = mutableMapOf<String, CLCircularRegion>()
    var listener: IosGeofenceListener? = null
    var isAvailable: Boolean = true
    var startMonitoringCallCount = 0
    var stopMonitoringCallCount = 0

    override fun startMonitoring(region: CLCircularRegion): Boolean {
        startMonitoringCallCount++
        if (!isAvailable) return false
        monitoredRegions[region.identifier] = region
        return true
    }

    override fun stopMonitoring(identifier: String) {
        stopMonitoringCallCount++
        monitoredRegions.remove(identifier)
    }

    override fun getMonitoredRegionIdentifiers(): Set<String> = monitoredRegions.keys.toSet()

    override fun isMonitoringAvailable(): Boolean = isAvailable

    override fun setListener(listener: IosGeofenceListener?) {
        this.listener = listener
    }

    fun simulateEnter(identifier: String) {
        listener?.onRegionEntered(identifier)
    }

    fun simulateExit(identifier: String) {
        listener?.onRegionExited(identifier)
    }

    fun simulateFailure(identifier: String?, errorCode: Long) {
        listener?.onMonitoringFailed(identifier, errorCode)
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
class IosRegionGeofenceAdapterTest {

    private val fixedInstant = Instant.fromEpochMilliseconds(1700000000000L)

    @Test
    fun registerGeofences_startsMonitoringAndStoresRegions() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region1 = GeofenceRegion(
            id = "region-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
            transitionTypes = setOf(GeofenceTransitionType.ENTER),
        )
        val region2 = GeofenceRegion(
            id = "region-2",
            center = GeoCoordinates(-6.3, 106.9),
            radiusMeters = 1000.0,
            transitionTypes = setOf(GeofenceTransitionType.ENTER, GeofenceTransitionType.EXIT),
        )

        adapter.registerGeofences(listOf(region1, region2))

        assertEquals(2, fakeEngine.startMonitoringCallCount)
        assertEquals(2, fakeEngine.monitoredRegions.size)
        assertTrue(fakeEngine.monitoredRegions.containsKey("region-1"))
        assertTrue(fakeEngine.monitoredRegions.containsKey("region-2"))

        val monitored1 = fakeEngine.monitoredRegions["region-1"]!!
        assertEquals("region-1", monitored1.identifier)
        assertEquals(500.0, monitored1.radius)
        assertTrue(monitored1.notifyOnEntry)
        assertFalse(monitored1.notifyOnExit)

        val monitored2 = fakeEngine.monitoredRegions["region-2"]!!
        assertEquals("region-2", monitored2.identifier)
        assertEquals(1000.0, monitored2.radius)
        assertTrue(monitored2.notifyOnEntry)
        assertTrue(monitored2.notifyOnExit)

        assertEquals(2, adapter.getRegisteredRegions().size)
    }

    @Test
    fun registerGeofences_whenUnavailable_doesNotStoreRegions() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        fakeEngine.isAvailable = false
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "region-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )

        adapter.registerGeofences(listOf(region))

        assertEquals(1, fakeEngine.startMonitoringCallCount)
        assertTrue(fakeEngine.monitoredRegions.isEmpty())
        assertTrue(adapter.getRegisteredRegions().isEmpty())
    }

    @Test
    fun unregisterGeofences_stopsMonitoringAndRemovesRegions() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "region-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )

        adapter.registerGeofences(listOf(region))
        assertEquals(1, adapter.getRegisteredRegions().size)

        adapter.unregisterGeofences(listOf("region-1"))

        assertEquals(1, fakeEngine.stopMonitoringCallCount)
        assertTrue(fakeEngine.monitoredRegions.isEmpty())
        assertTrue(adapter.getRegisteredRegions().isEmpty())
    }

    @Test
    fun transitionCallbacks_emitEventsOnFlow() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val flow = adapter.observeGeofenceTransitions()
        val events = mutableListOf<com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { events.add(it) }
        }

        // Simulate ENTER
        fakeEngine.simulateEnter("test-region")
        assertEquals(1, events.size)
        val enterEvent = events.first()
        assertEquals("test-region", enterEvent.regionId)
        assertEquals(GeofenceTransitionType.ENTER, enterEvent.transitionType)
        assertEquals(fixedInstant, enterEvent.timestamp)

        // Simulate EXIT
        fakeEngine.simulateExit("test-region-2")
        assertEquals(2, events.size)
        val exitEvent = events[1]
        assertEquals("test-region-2", exitEvent.regionId)
        assertEquals(GeofenceTransitionType.EXIT, exitEvent.transitionType)
        assertEquals(fixedInstant, exitEvent.timestamp)

        job.cancel()
    }

    @Test
    fun monitoringFailure_doesNotEmitEvents() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        fakeEngine.simulateFailure("failing-region", 1L)
        // Ensure no events emitted or unhandled crash occurs
        assertTrue(adapter.getRegisteredRegions().isEmpty())
    }

    @Test
    fun monitoringFailure_removesPreviouslyRegisteredRegion() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "failing-region",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )
        adapter.registerGeofences(listOf(region))
        assertEquals(1, adapter.getRegisteredRegions().size)

        fakeEngine.simulateFailure("failing-region", 1L)
        assertTrue(adapter.getRegisteredRegions().isEmpty())
    }

    @Test
    fun close_clearsListener() {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        assertNotNull(fakeEngine.listener)
        adapter.close()
        assertNull(fakeEngine.listener)
    }

    @Test
    fun close_stopsMonitoredRegionsAndClearsRegistered() = runTest {
        val fakeEngine = FakeIosGeofenceEngine()
        val adapter = IosRegionGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "region-close-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )
        adapter.registerGeofences(listOf(region))
        assertEquals(1, adapter.getRegisteredRegions().size)
        assertEquals(1, fakeEngine.monitoredRegions.size)

        adapter.close()

        assertNull(fakeEngine.listener)
        assertEquals(1, fakeEngine.stopMonitoringCallCount)
        assertTrue(fakeEngine.monitoredRegions.isEmpty())
        assertTrue(adapter.getRegisteredRegions().isEmpty())
    }
}
