package com.muhammadridha.fjalar

import android.Manifest
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
import androidx.core.content.ContextCompat
import com.muhammadridha.fjalar.adapters.location.AndroidFusedLocationAdapter
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
 * - Service runs and polls location ONLY while alarms are actively armed in ARMED_MONITORING.
 * - Stops location updates and terminates immediately upon receiving [ACTION_STOP_TRACKING].
 * - Fixes are evaluated solely in-memory on-device and never transmitted across network boundaries.
 */
class FjalarLocationService : Service() {

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

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: AndroidFusedLocationAdapter.ACTION_START_TRACKING

        when (action) {
            AndroidFusedLocationAdapter.ACTION_STOP_TRACKING -> {
                stopTrackingAndSelf()
                return START_NOT_STICKY
            }
            AndroidFusedLocationAdapter.ACTION_START_TRACKING -> {
                val interval = intent?.getLongExtra(
                    AndroidFusedLocationAdapter.EXTRA_UPDATE_INTERVAL,
                    DEFAULT_INTERVAL_MILLIS,
                ) ?: DEFAULT_INTERVAL_MILLIS

                startForegroundTracking(interval)
                return START_STICKY
            }
            else -> {
                return START_NOT_STICKY
            }
        }
    }

    private fun startForegroundTracking(intervalMillis: Long) {
        createNotificationChannelIfNeeded()
        val notification = buildOngoingNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates(intervalMillis)
    }

    private fun startLocationUpdates(intervalMillis: Long) {
        if (isTracking) return

        val manager = locationManager ?: return

        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return
        }

        isTracking = true

        val looper = Looper.getMainLooper()

        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMillis,
                    0f,
                    locationListener,
                    looper,
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMillis,
                    0f,
                    locationListener,
                    looper,
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    private fun stopTrackingAndSelf() {
        stopLocationUpdates()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun stopLocationUpdates() {
        if (!isTracking) return
        isTracking = false
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }

    private fun handleFix(location: Location) {
        val coordinates = GeoCoordinates(
            latitude = location.latitude,
            longitude = location.longitude,
        )
        val accuracy = location.accuracy

        locationUpdateHook?.invoke(coordinates, accuracy)

        serviceScope.launch {
            try {
                val container = resolveContainer()
                val tracker = container?.locationTracker as? AndroidFusedLocationAdapter
                if (tracker != null) {
                    tracker.dispatchLocationUpdate(coordinates, accuracy)
                } else {
                    container?.alarmService?.handleLocationUpdate(coordinates, accuracy)
                }
            } catch (_: Exception) {}
        }
    }

    private fun resolveContainer(): FjalarAppContainer? {
        return containerOverride ?: (applicationContext as? FjalarApplication)?.container
    }

    private fun buildOngoingNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, launchIntent, flags)

        val title = try {
            getString(R.string.notif_monitoring_title)
        } catch (_: Throwable) {
            "Monitoring commute destination"
        }

        val body = try {
            getString(R.string.notif_monitoring_body)
        } catch (_: Throwable) {
            "Active destination monitoring"
        }

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
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "fjalar_monitoring_channel"
        const val CHANNEL_NAME = "Fjalar Location Monitoring"
        const val NOTIFICATION_ID = 1002
        const val DEFAULT_INTERVAL_MILLIS = 10_000L

        var containerOverride: FjalarAppContainer? = null
        var locationUpdateHook: ((GeoCoordinates, Float) -> Unit)? = null
    }
}
