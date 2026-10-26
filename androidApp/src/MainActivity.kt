package com.muhammadridha.fjalar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import com.muhammadridha.fjalar.ui.FjalarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var currentLocationJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        awakenScreenAndShowOverKeyguard()
        // Draw edge-to-edge behind system status and navigation bars; safe insets are handled via WindowInsetsLayoutHelper
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as? FjalarApplication
        val container = app?.container
        setContent {
            val scope = rememberCoroutineScope()
            val viewModel: FjalarViewModel = viewModel {
                if (container != null) {
                    createDefaultViewModel(
                        keyValueStorage = container.keyValueStorage,
                        tracker = container.locationTracker,
                        scheduler = container.alarmScheduler,
                        ringer = container.alarmRinger,
                        alarmService = container.alarmService,
                        preferencesPort = container.preferences,
                        syncSchedulesPort = container.syncSchedulesPort,
                        mapRendererPort = container.mapRendererPort,
                        searchPlacesPort = container.searchPlacesPort,
                    )
                } else {
                    createDefaultViewModel(
                        keyValueStorage = SharedPreferencesKeyValueStorage(applicationContext),
                    )
                }
            }

            val requiredPermissions = remember {
                buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { permissionsMap ->
                val fineGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarseGranted = permissionsMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                val locationGranted = fineGranted || coarseGranted

                viewModel.onPermissionResult(locationGranted)
                if (locationGranted) {
                    requestMapLocation(
                        context = this@MainActivity,
                        viewModel = viewModel,
                        scope = scope,
                    )
                }
            }

            LaunchedEffect(Unit) {
                val fineCheck = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                val coarseCheck = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                val alreadyGranted = fineCheck || coarseCheck

                if (!alreadyGranted) {
                    viewModel.onPermissionResult(false)
                    permissionLauncher.launch(requiredPermissions)
                } else {
                    viewModel.onPermissionResult(true)
                    requestMapLocation(
                        context = this@MainActivity,
                        viewModel = viewModel,
                        scope = scope,
                    )
                }
            }

            DisposableEffect(viewModel) {
                viewModel.requestCurrentLocation = {
                    requestMapLocation(this@MainActivity, viewModel, scope)
                }
                onDispose {
                    viewModel.requestCurrentLocation = null
                    currentLocationJob?.cancel()
                }
            }

            App(
                container = container,
                viewModel = viewModel,
                onRequestPermission = {
                    permissionLauncher.launch(requiredPermissions)
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        awakenScreenAndShowOverKeyguard()
    }

    private fun awakenScreenAndShowOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onStop() {
        currentLocationJob?.cancel()
        super.onStop()
    }

    private fun requestMapLocation(
        context: Context,
        viewModel: FjalarViewModel,
        scope: CoroutineScope,
    ) {
        currentLocationJob?.cancel()
        currentLocationJob = scope.launch {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@launch
            // A bounded foreground request is exclusively for map positioning. Never evaluate
            // cached or UI-requested fixes against an armed alarm.
            val location = withTimeoutOrNull(15_000L) {
                suspendCancellableCoroutine<Location> { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val isFresh = isFreshLocationFix(
                                location.elapsedRealtimeNanos / 1_000_000L,
                                SystemClock.elapsedRealtime(),
                            )
                            if (!isFresh) return
                            if (continuation.isActive) continuation.resume(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                        try {
                            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        } catch (_: SecurityException) {
                            // Permission may have been revoked while the screen was open.
                        } catch (_: IllegalArgumentException) {
                            // This device may not expose both providers.
                        }
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    continuation.context[Job]?.invokeOnCompletion { manager.removeUpdates(listener) }
                }
            }
            if (location != null) {
                viewModel.onCurrentLocationReceived(GeoCoordinates(location.latitude, location.longitude))
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(keyValueStorage = remember { InMemoryKeyValueStorage() })
}
