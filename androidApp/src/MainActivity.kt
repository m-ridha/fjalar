package com.muhammadridha.fjalar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.ui.FjalarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
                    queryAndDispatchLastKnownLocation(
                        context = this@MainActivity,
                        viewModel = viewModel,
                        alarmService = container?.alarmService,
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
                    queryAndDispatchLastKnownLocation(
                        context = this@MainActivity,
                        viewModel = viewModel,
                        alarmService = container?.alarmService,
                        scope = scope,
                    )
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

    private fun queryAndDispatchLastKnownLocation(
        context: Context,
        viewModel: FjalarViewModel,
        alarmService: AlarmService?,
        scope: CoroutineScope,
    ) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                        bestLocation = loc
                    }
                }
            } catch (_: SecurityException) {
                // Permission revoked or not granted
            } catch (_: IllegalArgumentException) {
                // Provider not supported
            }
        }
        bestLocation?.let { loc ->
            val coordinates = GeoCoordinates(loc.latitude, loc.longitude)
            viewModel.onUserLocationChanged(coordinates)
            alarmService?.let { service ->
                scope.launch {
                    service.handleLocationUpdate(coordinates, loc.accuracy)
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(keyValueStorage = remember { InMemoryKeyValueStorage() })
}
