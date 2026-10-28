package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import platform.CoreLocation.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.time.Clock
import kotlin.time.Instant

/** The map and alarms own requests, while this adapter owns exactly one CLLocationManager. */
class IosCLLocationAdapter(
    private val updatePort: HandleLocationUpdatePort? = null,
    private val updatePortProvider: (() -> HandleLocationUpdatePort?)? = null,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val gateway: IosLocationGateway = NativeIosLocationGateway(),
) : LocationTrackerPort {
    private val adapterScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
    private val stream = LocationFixStream()
    private var alarmOwned = false
    private var mapOwned = false
    var isTracking: Boolean = false
        private set

    override fun observeFixes() = stream.fixes
    override fun observeAvailability() = stream.availability

    init {
        gateway.setListeners(
            { fix -> adapterScope.launch { dispatchLocationUpdate(fix.coordinates, fix.horizontalAccuracyMeters, fix.observedAt) } },
            stream::reportAvailability,
        )
    }

    override suspend fun startTracking(updateIntervalMillis: Long) = withContext(coroutineDispatcher) {
        alarmOwned = true
        reconcileTracking()
    }

    override suspend fun stopTracking() = withContext(coroutineDispatcher) {
        alarmOwned = false
        reconcileTracking()
    }

    override suspend fun setForegroundMapTracking(enabled: Boolean) = withContext(coroutineDispatcher) {
        mapOwned = enabled
        reconcileTracking()
    }

    private fun reconcileTracking() {
        gateway.setBackgroundEnabled(alarmOwned)
        if (alarmOwned || mapOwned) {
            if (!isTracking) {
                gateway.start()
                isTracking = true
            }
        } else if (isTracking) {
            gateway.stop()
            isTracking = false
            stream.reportAvailability(LocationAvailability.PROVIDER_UNAVAILABLE)
        }
    }

    suspend fun dispatchLocationUpdate(
        coordinates: GeoCoordinates,
        horizontalAccuracyMeters: Float,
        observedAt: Instant = Clock.System.now(),
    ) {
        if (!horizontalAccuracyMeters.isFinite() || horizontalAccuracyMeters < 0f) return
        if (!stream.publish(LocationFix(coordinates, horizontalAccuracyMeters, observedAt))) return
        val port = updatePort ?: updatePortProvider?.invoke()
        port?.handleLocationUpdate(coordinates, horizontalAccuracyMeters)
    }

    fun close() {
        gateway.stop()
        isTracking = false
        adapterScope.cancel()
    }
}

interface IosLocationGateway {
    fun setListeners(onFix: (LocationFix) -> Unit, onAvailability: (LocationAvailability) -> Unit)
    fun setBackgroundEnabled(enabled: Boolean)
    fun start()
    fun stop()
}

@OptIn(ExperimentalForeignApi::class)
private class NativeIosLocationGateway : IosLocationGateway {
    private val manager = CLLocationManager()
    private var onFix: (LocationFix) -> Unit = {}
    private var onAvailability: (LocationAvailability) -> Unit = {}
    private var requested = false
    private var running = false
    private var background = false
    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            if (!running) return
            for (location in didUpdateLocations.filterIsInstance<CLLocation>()) {
                val accuracy = location.horizontalAccuracy.toFloat()
                if (!accuracy.isFinite() || accuracy < 0f) continue
                // CoreLocation timestamps are wall-clock dates. Convert their age onto the
                // current monotonic clock before applying the same 30-second policy as Android.
                val ageMillis = ((NSDate().timeIntervalSince1970 - location.timestamp.timeIntervalSince1970) * 1000).toLong()
                val nowMillis = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
                if (!isFreshLocationFix(nowMillis - ageMillis, nowMillis)) continue
                val coordinates = location.coordinate.useContents {
                    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                        !longitude.isFinite() || longitude !in -180.0..180.0) null
                    else GeoCoordinates(latitude, longitude)
                } ?: continue
                onFix(LocationFix(coordinates, accuracy, Instant.fromEpochMilliseconds((location.timestamp.timeIntervalSince1970 * 1000).toLong())))
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            onAvailability(if (didFailWithError.code == kCLErrorDenied.toLong()) {
                LocationAvailability.PERMISSION_DENIED
            } else {
                LocationAvailability.PROVIDER_UNAVAILABLE
            })
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            reconcileAuthorization()
        }
    }

    override fun setListeners(onFix: (LocationFix) -> Unit, onAvailability: (LocationAvailability) -> Unit) {
        this.onFix = onFix
        this.onAvailability = onAvailability
        manager.delegate = delegate
    }

    override fun setBackgroundEnabled(enabled: Boolean) {
        val promote = enabled && !background
        background = enabled
        if (promote && requested) manager.requestAlwaysAuthorization()
        val modes = NSBundle.mainBundle.objectForInfoDictionaryKey("UIBackgroundModes") as? List<*>
        manager.allowsBackgroundLocationUpdates = enabled && modes?.contains("location") == true
    }

    override fun start() {
        requested = true
        // No distance filter: a stationary commuter still needs current accuracy/telemetry.
        // CoreLocation chooses cadence; unlike Android it has no 10-second interval setting.
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = false
        if (background) manager.requestAlwaysAuthorization() else manager.requestWhenInUseAuthorization()
        reconcileAuthorization()
    }

    private fun reconcileAuthorization() {
        if (!requested) return
        val status = manager.authorizationStatus
        if (status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted) {
            manager.stopUpdatingLocation()
            running = false
            onAvailability(LocationAvailability.PERMISSION_DENIED)
        } else if (!CLLocationManager.locationServicesEnabled()) {
            manager.stopUpdatingLocation()
            running = false
            onAvailability(LocationAvailability.PROVIDER_UNAVAILABLE)
        } else if (status != kCLAuthorizationStatusNotDetermined && !running) {
            manager.startUpdatingLocation()
            running = true
        }
    }

    override fun stop() {
        requested = false
        manager.stopUpdatingLocation()
        running = false
        manager.allowsBackgroundLocationUpdates = false
    }
}
