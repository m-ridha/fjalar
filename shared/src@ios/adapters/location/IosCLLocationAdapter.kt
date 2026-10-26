package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS implementation of [LocationTrackerPort] using [CLLocationManager]
 * with background location updates enabled and pausesLocationUpdatesAutomatically disabled.
 *
 * Implements 0% Runaway GPS Battery Drain (Safety Invariant 2) by bounding active GPS polling
 * strictly to active monitoring windows and stopping location updates immediately
 * when no alarms are in ARMED_MONITORING.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCLLocationAdapter(
    private val updatePort: HandleLocationUpdatePort? = null,
    private val updatePortProvider: (() -> HandleLocationUpdatePort?)? = null,
    private val locationManager: CLLocationManager = CLLocationManager(),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : LocationTrackerPort {

    private val adapterScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    @kotlin.concurrent.Volatile
    private var isTrackingActive: Boolean = false

    val isTracking: Boolean
        get() = isTrackingActive

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val locations = didUpdateLocations.filterIsInstance<CLLocation>()
            for (location in locations) {
                val accuracy = location.horizontalAccuracy.toFloat()
                // In iOS CoreLocation, negative accuracy indicates an invalid fix
                if (accuracy >= 0f) {
                    val coordinates = location.coordinate.useContents {
                        GeoCoordinates(latitude, longitude)
                    }
                    adapterScope.launch {
                        try {
                            dispatchLocationUpdate(coordinates, accuracy)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            // Defensive: drop this fix without terminating processing for future fixes
                        }
                    }
                }
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            // Defensive: keep tracking active even if temporary satellite lock failure occurs
        }
    }

    override suspend fun startTracking(updateIntervalMillis: Long) {
        if (isTrackingActive) return
        isTrackingActive = true

        try {
            locationManager.delegate = delegate
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.pausesLocationUpdatesAutomatically = false
            try {
                locationManager.requestAlwaysAuthorization()
            } catch (_: Throwable) {
                // Defensive degradation if authorization prompt not permitted
            }
            try {
                val backgroundModes = NSBundle.mainBundle.objectForInfoDictionaryKey("UIBackgroundModes") as? List<*>
                if (backgroundModes?.contains("location") == true) {
                    locationManager.allowsBackgroundLocationUpdates = true
                }
            } catch (_: Throwable) {
                // Defensive degradation if background location entitlement not configured
            }
            locationManager.startUpdatingLocation()
        } catch (_: Throwable) {
            // Defensive degradation
        }
    }

    override suspend fun stopTracking() {
        if (!isTrackingActive) return
        isTrackingActive = false

        try {
            locationManager.stopUpdatingLocation()
            locationManager.delegate = null
        } catch (_: Throwable) {
            // Defensive degradation
        }
    }

    suspend fun dispatchLocationUpdate(
        coordinates: GeoCoordinates,
        horizontalAccuracyMeters: Float,
    ) {
        val port = updatePort ?: updatePortProvider?.invoke()
        port?.handleLocationUpdate(coordinates, horizontalAccuracyMeters)
    }

    fun close() {
        adapterScope.cancel()
    }
}
