package com.muhammadridha.fjalar

import android.Manifest
import org.koin.android.ext.android.inject
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import androidx.core.content.ContextCompat
import com.muhammadridha.fjalar.adapters.location.AndroidFusedLocationAdapter
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import kotlin.time.Instant
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service managing real-time GPS location updates for armed commute alarms.
 *
 * Implements 0% Runaway GPS Battery Drain (Safety Invariant 2) and 100% On-Device Privacy (Safety Invariant 1):
 * - Background polling is limited to armed alarms; a foreground map may own the same listener.
 * - Map-only requests are non-sticky and never promote this service to a foreground service.
 * - Stops location updates and terminates immediately upon receiving [ACTION_STOP_TRACKING].
 * - Fixes are evaluated solely in-memory on-device and never transmitted across network boundaries.
 */
class FjalarLocationService : Service() {

    private val tracker: AndroidFusedLocationAdapter by inject()
    private val syncSchedules: SyncSchedulesPort by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    private val locationManager: LocationManager? by lazy {
        getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val notificationManager: NotificationManager? by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    @Volatile
    private var isTracking = false

    @Volatile
    private var latestStartId: Int = 0

    @Volatile
    private var currentIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS

    private val trackingLock = Any()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleFix(location)
        }

        override fun onLocationChanged(locations: MutableList<Location>) {
            for (location in locations) {
                handleFix(location)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {
            synchronized(trackingLock) {
                if (isTracking && (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER)) {
                    reportProviderAvailability()
                }
            }
        }

        override fun onProviderDisabled(provider: String) { reportProviderAvailability() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent == null) {
            // Service restarted by system (e.g. START_STICKY) with null intent.
            // Reconcile with active alarms first; stop only if none are armed and no newer start command arrived.
            serviceScope.launch {
                val alarms = syncSchedules.syncSchedules()
                if (alarms.none { it.state == com.muhammadridha.fjalar.domain.model.AlarmState.ARMED_MONITORING }) {
                    if (latestStartId == startId) {
                        stopTrackingAndSelf(startId)
                    }
                }
            }
            if (!hasLocationPermission()) {
                stopTrackingAndSelf(startId)
                return START_NOT_STICKY
            }
            val started = startForegroundTracking(DEFAULT_INTERVAL_MILLIS)
            return if (started) START_STICKY else START_NOT_STICKY
        }

        val action = intent.action ?: AndroidFusedLocationAdapter.ACTION_START_TRACKING

        when (action) {
            AndroidFusedLocationAdapter.ACTION_STOP_TRACKING -> {
                stopTrackingAndSelf(startId)
                return START_NOT_STICKY
            }
            AndroidFusedLocationAdapter.ACTION_START_TRACKING -> {
                val interval = intent.getLongExtra(
                    AndroidFusedLocationAdapter.EXTRA_UPDATE_INTERVAL,
                    DEFAULT_INTERVAL_MILLIS,
                )

                val alarmOwned = intent.getBooleanExtra(AndroidFusedLocationAdapter.EXTRA_ALARM_OWNED, true)
                val started = if (alarmOwned) {
                    startForegroundTracking(interval)
                } else {
                    // Map-only work is a regular service, bounded by the foreground UI owner.
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (hasLocationPermission()) {
                        isServiceRunning = true
                        startLocationUpdates(interval)
                        true
                    } else {
                        reportAvailability(LocationAvailability.PERMISSION_DENIED)
                        stopTrackingAndSelf(startId)
                        false
                    }
                }
                return if (started && alarmOwned) START_STICKY else START_NOT_STICKY
            }
            else -> {
                return START_NOT_STICKY
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun startForegroundTracking(intervalMillis: Long): Boolean {
        if (!hasLocationPermission()) {
            reportAvailability(LocationAvailability.PERMISSION_DENIED)
            stopTrackingAndSelf()
            return false
        }

        createNotificationChannelIfNeeded()
        val notification = buildOngoingNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopTrackingAndSelf()
            return false
        }

        isServiceRunning = true
        startLocationUpdates(intervalMillis)
        return true
    }

    private fun startLocationUpdates(intervalMillis: Long) {
        if (!hasLocationPermission()) {
            return
        }

        synchronized(trackingLock) {
            if (isTracking) return
            currentIntervalMillis = intervalMillis
            isTracking = true

            registerProvider(LocationManager.GPS_PROVIDER, intervalMillis)
            registerProvider(LocationManager.NETWORK_PROVIDER, intervalMillis)
            reportProviderAvailability()
        }
    }

    private fun registerProvider(provider: String, intervalMillis: Long) {
        val manager = locationManager ?: return
        if (!hasLocationPermission()) return

        synchronized(trackingLock) {
            if (!isTracking) return
            val looper = Looper.getMainLooper()
            try {
                // Register listener regardless of whether provider is currently enabled.
                // Android LocationManager safely accepts registrations for disabled providers
                // and automatically begins delivering fixes and onProviderEnabled callbacks once enabled.
                manager.requestLocationUpdates(
                    provider,
                    intervalMillis,
                    // Preserve the 10-second alarm cadence and zero displacement filter: a
                    // stationary commuter still needs fresh accuracy updates near a destination.
                    0f,
                    locationListener,
                    looper,
                )
            } catch (_: SecurityException) {
                reportAvailability(LocationAvailability.PERMISSION_DENIED)
            } catch (_: Exception) {
                reportProviderAvailability()
            }
        }
    }

    private fun stopTrackingAndSelf(startId: Int? = null) {
        if (startId != null && latestStartId != startId) {
            // A newer start request arrived; do not tear down service.
            return
        }
        isServiceRunning = false
        stopLocationUpdates()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (startId != null) {
            stopSelfResult(startId)
        } else {
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        synchronized(trackingLock) {
            if (!isTracking) return
            isTracking = false
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (_: Exception) {}
        }
    }

    private fun handleFix(location: Location) {
        if (!isTracking) return
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        val observationElapsedMillis = location.elapsedRealtimeNanos / 1_000_000L
        val isFresh = isFreshLocationFix(observationElapsedMillis, nowElapsedMillis)
        if (!isFresh) return

        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy < 0f) return
        if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
            !location.longitude.isFinite() || location.longitude !in -180.0..180.0) return
        val coordinates = GeoCoordinates(
            latitude = location.latitude,
            longitude = location.longitude,
        )
        val accuracy = location.accuracy

        locationUpdateHook?.invoke(coordinates, accuracy)

        tracker.submitLocationUpdate(
            coordinates,
            accuracy,
            Instant.fromEpochMilliseconds(location.time),
            observationAgeMillis = nowElapsedMillis - observationElapsedMillis,
        )
    }

    private fun reportAvailability(value: LocationAvailability) {
        tracker.reportAvailability(value)
    }

    private fun reportProviderAvailability() {
        if (!hasLocationPermission()) {
            reportAvailability(LocationAvailability.PERMISSION_DENIED)
            return
        }
        val enabled = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).any {
            try { locationManager?.isProviderEnabled(it) == true } catch (_: Exception) { false }
        }
        if (!enabled) reportAvailability(LocationAvailability.PROVIDER_UNAVAILABLE)
    }

    private fun buildOngoingNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, launchIntent, flags)

        val title = getString(R.string.notif_monitoring_title)
        val body = getString(R.string.notif_monitoring_body)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_LOW)
        }

        return builder.build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = notificationManager ?: return
            try {
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channelName = try {
                        getString(R.string.notification_channel_monitoring_name)
                    } catch (_: Throwable) {
                        CHANNEL_NAME
                    }
                    val channelDesc = try {
                        getString(R.string.notification_channel_monitoring_desc)
                    } catch (_: Throwable) {
                        "Ongoing location monitoring for armed commute alarms"
                    }
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = channelDesc
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    manager.createNotificationChannel(channel)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "fjalar_monitoring_channel"
        const val CHANNEL_NAME = "Fjalar Location Monitoring"
        const val NOTIFICATION_ID = 1002
        const val DEFAULT_INTERVAL_MILLIS = 10_000L

        @JvmField
        @Volatile
        var isServiceRunning: Boolean = false

        var locationUpdateHook: ((GeoCoordinates, Float) -> Unit)? = null
    }
}
