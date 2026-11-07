package com.muhammadridha.fjalar.adapters.geofence

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FakeAndroidGeofenceEngine : AndroidGeofenceEngine {
    val registeredRegions = mutableMapOf<String, GeofenceRegion>()
    var activeListener: AndroidGeofenceListener? = null
    var available: Boolean = true
    var registerCallCount = 0
    var unregisterCallCount = 0

    override fun registerRegion(region: GeofenceRegion): Boolean {
        registerCallCount++
        if (!available) return false
        registeredRegions[region.id] = region
        return true
    }

    override fun unregisterRegion(regionId: String) {
        unregisterCallCount++
        registeredRegions.remove(regionId)
    }

    override fun getRegisteredRegionIds(): Set<String> = registeredRegions.keys.toSet()

    override fun isAvailable(): Boolean = available

    override fun setListener(listener: AndroidGeofenceListener?) {
        this.activeListener = listener
    }

    fun simulateTransition(regionId: String, transitionType: GeofenceTransitionType) {
        activeListener?.onTransition(regionId, transitionType)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidGeofenceAdapterTest {

    private val fixedInstant = Instant.fromEpochMilliseconds(1700000000000L)

    @Test
    fun registerGeofences_delegatesToEngineAndStoresActiveRegions() = runTest {
        val fakeEngine = FakeAndroidGeofenceEngine()
        val adapter = AndroidGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region1 = GeofenceRegion(
            id = "android-region-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )
        val region2 = GeofenceRegion(
            id = "android-region-2",
            center = GeoCoordinates(-6.3, 106.9),
            radiusMeters = 1200.0,
            transitionTypes = setOf(GeofenceTransitionType.ENTER, GeofenceTransitionType.EXIT),
        )

        adapter.registerGeofences(listOf(region1, region2))

        assertEquals(2, fakeEngine.registerCallCount)
        assertEquals(2, fakeEngine.registeredRegions.size)
        assertEquals(2, adapter.getActiveRegions().size)
        assertTrue(adapter.getActiveRegions().containsKey("android-region-1"))
        assertTrue(adapter.getActiveRegions().containsKey("android-region-2"))
    }

    @Test
    fun registerGeofences_whenEngineUnavailable_doesNotStoreActiveRegions() = runTest {
        val fakeEngine = FakeAndroidGeofenceEngine()
        fakeEngine.available = false
        val adapter = AndroidGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "android-region-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )

        adapter.registerGeofences(listOf(region))

        assertEquals(1, fakeEngine.registerCallCount)
        assertTrue(fakeEngine.registeredRegions.isEmpty())
        assertTrue(adapter.getActiveRegions().isEmpty())
    }

    @Test
    fun unregisterGeofences_delegatesToEngineAndRemovesRegions() = runTest {
        val fakeEngine = FakeAndroidGeofenceEngine()
        val adapter = AndroidGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "android-region-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )

        adapter.registerGeofences(listOf(region))
        assertEquals(1, adapter.getActiveRegions().size)

        adapter.unregisterGeofences(listOf("android-region-1"))

        assertEquals(1, fakeEngine.unregisterCallCount)
        assertTrue(fakeEngine.registeredRegions.isEmpty())
        assertTrue(adapter.getActiveRegions().isEmpty())
    }

    @Test
    fun transitionListener_emitsEventsToFlow() = runTest {
        val fakeEngine = FakeAndroidGeofenceEngine()
        val adapter = AndroidGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val flow = adapter.observeGeofenceTransitions()
        val events = mutableListOf<GeofenceTransitionEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { events.add(it) }
        }

        // Simulate ENTER
        fakeEngine.simulateTransition("android-region-1", GeofenceTransitionType.ENTER)
        assertEquals(1, events.size)
        assertEquals("android-region-1", events[0].regionId)
        assertEquals(GeofenceTransitionType.ENTER, events[0].transitionType)
        assertEquals(fixedInstant, events[0].timestamp)

        // Simulate EXIT
        fakeEngine.simulateTransition("android-region-2", GeofenceTransitionType.EXIT)
        assertEquals(2, events.size)
        assertEquals("android-region-2", events[1].regionId)
        assertEquals(GeofenceTransitionType.EXIT, events[1].transitionType)
        assertEquals(fixedInstant, events[1].timestamp)

        job.cancel()
    }

    @Test
    fun close_clearsListener() {
        val fakeEngine = FakeAndroidGeofenceEngine()
        val adapter = AndroidGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        assertNotNull(fakeEngine.activeListener)
        adapter.close()
        assertNull(fakeEngine.activeListener)
    }

    @Test
    fun close_unregistersRegionsAndClearsActive() = runTest {
        val fakeEngine = FakeAndroidGeofenceEngine()
        val adapter = AndroidGeofenceAdapter(
            engine = fakeEngine,
            clock = { fixedInstant },
        )

        val region = GeofenceRegion(
            id = "android-close-1",
            center = GeoCoordinates(-6.2, 106.8),
            radiusMeters = 500.0,
        )
        adapter.registerGeofences(listOf(region))
        assertEquals(1, adapter.getActiveRegions().size)
        assertEquals(1, fakeEngine.registeredRegions.size)

        adapter.close()

        assertNull(fakeEngine.activeListener)
        assertEquals(1, fakeEngine.unregisterCallCount)
        assertTrue(fakeEngine.registeredRegions.isEmpty())
        assertTrue(adapter.getActiveRegions().isEmpty())
    }
}
