package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.content.Intent
import android.os.Build
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.ports.inbound.HandleLocationUpdatePort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android implementation of [LocationTrackerPort] coordinating with [com.muhammadridha.fjalar.FjalarLocationService]
 * via [LocationPlatformGateway].
 *
 * Alarm requests may keep the service in the foreground for background monitoring.
 * The independent map owner may keep its regular service alive only while the map UI is foregrounded.
 */
class AndroidFusedLocationAdapter(
    private val context: Context? = null,
    private val updatePort: HandleLocationUpdatePort? = null,
    private val updatePortProvider: (() -> HandleLocationUpdatePort?)? = null,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val gateway: LocationPlatformGateway = DefaultAndroidLocationPlatformGateway(
        context = requireNotNull(context) { "Context is required when gateway is not provided" },
    ),
) : LocationTrackerPort {

    private val stream = LocationFixStream()
    private val ownershipMutex = Mutex()
    private var alarmOwned = false
    private var mapOwned = false
    override fun observeFixes() = stream.fixes
    override fun observeAvailability() = stream.availability
    fun reportAvailability(value: LocationAvailability) = stream.reportAvailability(value)

    private val adapterScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    @Volatile
    private var isTrackingActive: Boolean = false

    val isTracking: Boolean
        get() = isTrackingActive && gateway.isTracking()

    init {
        gateway.setLocationListener { coordinates, accuracy ->
            submitLocationUpdate(coordinates, accuracy)
        }
    }

    override suspend fun startTracking(updateIntervalMillis: Long) = ownershipMutex.withLock {
        if (!alarmOwned) stream.invalidateQueuedFixes()
        alarmOwned = true
        gateway.setAlarmOwnership(true)
        reconcileTracking(updateIntervalMillis)
    }

    override suspend fun stopTracking() = ownershipMutex.withLock {
        if (alarmOwned) stream.invalidateQueuedFixes()
        alarmOwned = false
        gateway.setAlarmOwnership(false)
        reconcileTracking(10_000L)
    }

    override suspend fun setForegroundMapTracking(enabled: Boolean) = ownershipMutex.withLock {
        if (mapOwned != enabled) stream.invalidateQueuedFixes()
        mapOwned = enabled
        reconcileTracking(10_000L)
    }

    private fun reconcileTracking(interval: Long) {
        if (alarmOwned || mapOwned) {
            if (!isTrackingActive || !gateway.isTracking()) gateway.startForegroundService(interval)
            isTrackingActive = true
        } else {
            if (isTrackingActive || gateway.isTracking()) gateway.stopForegroundService()
            isTrackingActive = false
            stream.reportAvailability(LocationAvailability.PROVIDER_UNAVAILABLE)
        }
    }

    /** Publish on the native callback before queued alarm handling can be overtaken by failure. */
    fun submitLocationUpdate(
        coordinates: GeoCoordinates,
        horizontalAccuracyMeters: Float,
        observedAt: Instant = Clock.System.now(),
    ) {
        if (!horizontalAccuracyMeters.isFinite() || horizontalAccuracyMeters < 0f) return
        stream.publish(LocationFix(coordinates, horizontalAccuracyMeters, observedAt))
        val generation = stream.currentGeneration()
        adapterScope.launch {
            if (stream.isCurrent(generation)) forwardLocationUpdate(coordinates, horizontalAccuracyMeters)
        }
    }

    suspend fun dispatchLocationUpdate(
        coordinates: GeoCoordinates,
        horizontalAccuracyMeters: Float,
        observedAt: Instant = Clock.System.now(),
    ) {
        if (!horizontalAccuracyMeters.isFinite() || horizontalAccuracyMeters < 0f) return
        if (!stream.publish(LocationFix(coordinates, horizontalAccuracyMeters, observedAt))) return
        forwardLocationUpdate(coordinates, horizontalAccuracyMeters)
    }

    private suspend fun forwardLocationUpdate(coordinates: GeoCoordinates, accuracy: Float) {
        val port = updatePort ?: updatePortProvider?.invoke()
        port?.handleLocationUpdate(coordinates, accuracy)
    }

    fun close() {
        stream.invalidateQueuedFixes()
        gateway.stopForegroundService()
        gateway.setLocationListener(null)
        adapterScope.cancel()
    }

    companion object {
        const val DEFAULT_SERVICE_CLASS_NAME = "com.muhammadridha.fjalar.FjalarLocationService"
        const val ACTION_START_TRACKING = "com.muhammadridha.fjalar.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.muhammadridha.fjalar.action.STOP_TRACKING"
        const val EXTRA_ALARM_OWNED = "com.muhammadridha.fjalar.extra.ALARM_OWNED"
        const val EXTRA_UPDATE_INTERVAL = "com.muhammadridha.fjalar.extra.UPDATE_INTERVAL"

        fun resolveDefaultService(): Class<*>? {
            return try {
                Class.forName(DEFAULT_SERVICE_CLASS_NAME)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
    }
}

/**
 * SPI boundary for starting/stopping the Android location foreground service
 * and observing location updates.
 */
interface LocationPlatformGateway {
    fun setAlarmOwnership(enabled: Boolean) {}
    fun startForegroundService(updateIntervalMillis: Long)
    fun stopForegroundService()
    fun isTracking(): Boolean
    fun setLocationListener(listener: ((coordinates: GeoCoordinates, horizontalAccuracyMeters: Float) -> Unit)?)
}

/**
 * Production implementation of [LocationPlatformGateway] dispatching to Android foreground service.
 */
open class DefaultAndroidLocationPlatformGateway(
    private val context: Context,
    private val serviceClass: Class<*>? = AndroidFusedLocationAdapter.resolveDefaultService(),
) : LocationPlatformGateway {

    @Volatile
    private var tracking: Boolean = false

    @Volatile
    private var locationListener: ((GeoCoordinates, Float) -> Unit)? = null

    private var alarmOwned = false
    private var intervalMillis = 10_000L

    override fun setAlarmOwnership(enabled: Boolean) {
        if (alarmOwned == enabled) return
        alarmOwned = enabled
        if (tracking) startForegroundService(intervalMillis)
    }

    override fun startForegroundService(updateIntervalMillis: Long) {
        intervalMillis = updateIntervalMillis
        val intent = createServiceIntent(AndroidFusedLocationAdapter.ACTION_START_TRACKING).apply {
            putExtra(AndroidFusedLocationAdapter.EXTRA_UPDATE_INTERVAL, updateIntervalMillis)
            putExtra(AndroidFusedLocationAdapter.EXTRA_ALARM_OWNED, alarmOwned)
        }
        try {
            if (alarmOwned && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            tracking = true
        } catch (_: Exception) {
            // Defensive: handles background start restrictions if applicable
            tracking = false
        }
    }

    override fun stopForegroundService() {
        tracking = false
        val intent = createServiceIntent(AndroidFusedLocationAdapter.ACTION_STOP_TRACKING)
        try {
            context.stopService(intent)
        } catch (_: Exception) {
            // Defensive
        }
    }

    override fun isTracking(): Boolean {
        val serviceRunning = resolveServiceRunningState()
        return if (serviceRunning != null) {
            // When serviceClass with isServiceRunning is available, it is the true lifecycle source.
            // Clear stale tracking flag if the service has stopped.
            if (!serviceRunning && tracking) {
                tracking = false
            }
            serviceRunning
        } else {
            tracking
        }
    }

    private fun resolveServiceRunningState(): Boolean? {
        return try {
            val clazz = serviceClass ?: AndroidFusedLocationAdapter.resolveDefaultService()
            if (clazz != null) {
                val field = clazz.getField("isServiceRunning")
                field.getBoolean(null)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun setLocationListener(listener: ((coordinates: GeoCoordinates, horizontalAccuracyMeters: Float) -> Unit)?) {
        locationListener = listener
    }

    private fun createServiceIntent(action: String): Intent {
        return if (serviceClass != null) {
            Intent(context, serviceClass)
        } else {
            Intent(action).setPackage(context.packageName)
        }.apply {
            this.action = action
        }
    }
}
