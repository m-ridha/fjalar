package com.muhammadridha.fjalar.adapters.geofence

import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionType
import com.muhammadridha.fjalar.domain.ports.outbound.GeofencePort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.cValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.time.Clock

interface IosGeofenceListener {
    fun onRegionEntered(identifier: String)
    fun onRegionExited(identifier: String)
    fun onMonitoringFailed(identifier: String?, errorCode: Long)
}

@OptIn(ExperimentalForeignApi::class)
interface IosGeofenceEngine {
    fun startMonitoring(region: CLCircularRegion): Boolean
    fun stopMonitoring(identifier: String)
    fun getMonitoredRegionIdentifiers(): Set<String>
    fun isMonitoringAvailable(): Boolean
    fun setListener(listener: IosGeofenceListener?)
}

@OptIn(ExperimentalForeignApi::class)
class DefaultIosGeofenceEngine(
    private val managerProvider: () -> CLLocationManager = { CLLocationManager() },
) : IosGeofenceEngine {
    private val manager by lazy { managerProvider() }
    private var currentListener: IosGeofenceListener? = null

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        @ObjCSignatureOverride
        override fun locationManager(manager: CLLocationManager, didEnterRegion: CLRegion) {
            currentListener?.onRegionEntered(didEnterRegion.identifier)
        }

        @ObjCSignatureOverride
        override fun locationManager(manager: CLLocationManager, didExitRegion: CLRegion) {
            currentListener?.onRegionExited(didExitRegion.identifier)
        }

        override fun locationManager(manager: CLLocationManager, monitoringDidFailForRegion: CLRegion?, withError: NSError) {
            currentListener?.onMonitoringFailed(monitoringDidFailForRegion?.identifier, withError.code)
        }
    }

    override fun startMonitoring(region: CLCircularRegion): Boolean {
        if (!isMonitoringAvailable()) return false
        manager.startMonitoringForRegion(region)
        return true
    }

    override fun stopMonitoring(identifier: String) {
        val matching = manager.monitoredRegions.filterIsInstance<CLRegion>().firstOrNull { it.identifier == identifier }
        if (matching != null) {
            manager.stopMonitoringForRegion(matching)
        }
    }

    override fun getMonitoredRegionIdentifiers(): Set<String> {
        return manager.monitoredRegions.filterIsInstance<CLRegion>().map { it.identifier }.toSet()
    }

    override fun isMonitoringAvailable(): Boolean {
        return CLLocationManager.isMonitoringAvailableForClass(CLCircularRegion)
    }

    override fun setListener(listener: IosGeofenceListener?) {
        currentListener = listener
        manager.delegate = if (listener != null) delegate else null
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosRegionGeofenceAdapter(
    private val engine: IosGeofenceEngine = DefaultIosGeofenceEngine(),
    private val clock: () -> kotlin.time.Instant = { Clock.System.now() },
) : GeofencePort {

    private val transitionsFlow = MutableSharedFlow<GeofenceTransitionEvent>(extraBufferCapacity = 64)
    private val registered = mutableMapOf<String, GeofenceRegion>()
    private val mutex = Mutex()

    init {
        engine.setListener(object : IosGeofenceListener {
            override fun onRegionEntered(identifier: String) {
                transitionsFlow.tryEmit(
                    GeofenceTransitionEvent(
                        regionId = identifier,
                        transitionType = GeofenceTransitionType.ENTER,
                        timestamp = clock(),
                    ),
                )
            }

            override fun onRegionExited(identifier: String) {
                transitionsFlow.tryEmit(
                    GeofenceTransitionEvent(
                        regionId = identifier,
                        transitionType = GeofenceTransitionType.EXIT,
                        timestamp = clock(),
                    ),
                )
            }

            override fun onMonitoringFailed(identifier: String?, errorCode: Long) {
                if (identifier != null) {
                    runBlocking {
                        mutex.withLock {
                            registered.remove(identifier)
                        }
                    }
                }
            }
        })
    }

    override suspend fun registerGeofences(regions: List<GeofenceRegion>) {
        mutex.withLock {
            for (region in regions) {
                val centerCoord = cValue<CLLocationCoordinate2D> {
                    latitude = region.center.latitude
                    longitude = region.center.longitude
                }
                val circularRegion = CLCircularRegion(
                    center = centerCoord,
                    radius = region.radiusMeters,
                    identifier = region.id,
                )
                circularRegion.notifyOnEntry = region.transitionTypes.contains(GeofenceTransitionType.ENTER)
                circularRegion.notifyOnExit = region.transitionTypes.contains(GeofenceTransitionType.EXIT)

                val success = engine.startMonitoring(circularRegion)
                if (success) {
                    registered[region.id] = region
                }
            }
        }
    }

    override suspend fun unregisterGeofences(regionIds: List<String>) {
        mutex.withLock {
            for (id in regionIds) {
                engine.stopMonitoring(id)
                registered.remove(id)
            }
        }
    }

    override fun observeGeofenceTransitions(): Flow<GeofenceTransitionEvent> = transitionsFlow.asSharedFlow()

    suspend fun getRegisteredRegions(): Map<String, GeofenceRegion> = mutex.withLock { registered.toMap() }

    fun close() = runBlocking {
        mutex.withLock {
            engine.setListener(null)
            val regionIds = registered.keys.toList()
            for (id in regionIds) {
                engine.stopMonitoring(id)
            }
            registered.clear()
        }
    }
}
