package com.muhammadridha.fjalar.adapters.geofence

import android.content.Context
import android.location.LocationManager
import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionType
import com.muhammadridha.fjalar.domain.ports.outbound.GeofencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

interface AndroidGeofenceListener {
    fun onTransition(regionId: String, transitionType: GeofenceTransitionType)
}

interface AndroidGeofenceEngine {
    fun registerRegion(region: GeofenceRegion): Boolean
    fun unregisterRegion(regionId: String)
    fun getRegisteredRegionIds(): Set<String>
    fun isAvailable(): Boolean
    fun setListener(listener: AndroidGeofenceListener?)
}

class DefaultAndroidGeofenceEngine(
    private val context: Context? = null,
    private val locationManagerProvider: () -> LocationManager? = {
        context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    },
) : AndroidGeofenceEngine {
    private val registeredRegions = mutableMapOf<String, GeofenceRegion>()
    private var currentListener: AndroidGeofenceListener? = null

    override fun registerRegion(region: GeofenceRegion): Boolean {
        if (!isAvailable()) return false
        registeredRegions[region.id] = region
        return true
    }

    override fun unregisterRegion(regionId: String) {
        registeredRegions.remove(regionId)
    }

    override fun getRegisteredRegionIds(): Set<String> = registeredRegions.keys.toSet()

    override fun isAvailable(): Boolean {
        val manager = locationManagerProvider() ?: return false
        return manager.allProviders.isNotEmpty()
    }

    override fun setListener(listener: AndroidGeofenceListener?) {
        currentListener = listener
    }
}

class AndroidGeofenceAdapter(
    private val context: Context? = null,
    private val engine: AndroidGeofenceEngine = DefaultAndroidGeofenceEngine(context),
    private val clock: () -> kotlin.time.Instant = { Clock.System.now() },
) : GeofencePort {

    private val transitionsFlow = MutableSharedFlow<GeofenceTransitionEvent>(extraBufferCapacity = 64)
    private val activeRegions = mutableMapOf<String, GeofenceRegion>()
    private val mutex = Mutex()

    init {
        engine.setListener(object : AndroidGeofenceListener {
            override fun onTransition(regionId: String, transitionType: GeofenceTransitionType) {
                transitionsFlow.tryEmit(
                    GeofenceTransitionEvent(
                        regionId = regionId,
                        transitionType = transitionType,
                        timestamp = clock(),
                    ),
                )
            }
        })
    }

    override suspend fun registerGeofences(regions: List<GeofenceRegion>) {
        mutex.withLock {
            for (region in regions) {
                val success = engine.registerRegion(region)
                if (success) {
                    activeRegions[region.id] = region
                }
            }
        }
    }

    override suspend fun unregisterGeofences(regionIds: List<String>) {
        mutex.withLock {
            for (id in regionIds) {
                engine.unregisterRegion(id)
                activeRegions.remove(id)
            }
        }
    }

    override fun observeGeofenceTransitions(): Flow<GeofenceTransitionEvent> = transitionsFlow.asSharedFlow()

    suspend fun getActiveRegions(): Map<String, GeofenceRegion> = mutex.withLock { activeRegions.toMap() }

    fun close() {
        engine.setListener(null)
        val regionIds = activeRegions.keys.toList()
        for (id in regionIds) {
            engine.unregisterRegion(id)
        }
        activeRegions.clear()
    }
}
