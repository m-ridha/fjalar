package com.muhammadridha.fjalar.adapters.location

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
 * Implements 0% Runaway GPS Battery Drain (Safety Invariant 2) by bounding active GPS polling
 * strictly to active monitoring windows and stopping foreground location services immediately
 * when no alarms are in ARMED_MONITORING.
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

    private val adapterScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    @Volatile
    private var isTrackingActive: Boolean = false

    val isTracking: Boolean
        get() = isTrackingActive

    init {
        gateway.setLocationListener { coordinates, accuracy ->
            adapterScope.launch {
                dispatchLocationUpdate(coordinates, accuracy)
            }
        }
    }

    override suspend fun startTracking(updateIntervalMillis: Long) {
        if (isTrackingActive && gateway.isTracking()) {
            return
        }
        isTrackingActive = true
        gateway.startForegroundService(updateIntervalMillis)
    }

    override suspend fun stopTracking() {
        if (!isTrackingActive && !gateway.isTracking()) {
            return
        }
        isTrackingActive = false
        gateway.stopForegroundService()
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

    companion object {
        const val DEFAULT_SERVICE_CLASS_NAME = "com.muhammadridha.fjalar.FjalarLocationService"
        const val ACTION_START_TRACKING = "com.muhammadridha.fjalar.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.muhammadridha.fjalar.action.STOP_TRACKING"
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

    override fun startForegroundService(updateIntervalMillis: Long) {
        val intent = createServiceIntent(AndroidFusedLocationAdapter.ACTION_START_TRACKING).apply {
            putExtra(AndroidFusedLocationAdapter.EXTRA_UPDATE_INTERVAL, updateIntervalMillis)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            context.startService(intent)
        } catch (_: Exception) {
            // Defensive
        }
    }

    override fun isTracking(): Boolean {
        val serviceRunning = try {
            val clazz = serviceClass ?: AndroidFusedLocationAdapter.resolveDefaultService()
            if (clazz != null) {
                val field = clazz.getField("isServiceRunning")
                field.getBoolean(null)
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
        return tracking || serviceRunning
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
