package com.muhammadridha.fjalar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.provider.Settings
import android.location.LocationManager
import android.location.LocationListener
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.components.BackgroundLocationPermissionDialog
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.LightLiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.DarkLiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.resolveLiquidGlassColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var currentLocationJob: Job? = null
    private val permissionPreferences by lazy {
        getSharedPreferences("location_permission_requests", Context.MODE_PRIVATE)
    }
    private var hasCompletedForegroundPermissionRequest: Boolean
        get() = permissionPreferences.getBoolean("foreground_request_completed", false)
        set(value) {
            permissionPreferences.edit().putBoolean("foreground_request_completed", value).apply()
        }

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

            val permissionUiState by viewModel.uiState.collectAsState()
            val isDarkTheme = resolveLiquidGlassColors(
                themeMode = permissionUiState.themeMode,
                systemInDark = isSystemInDarkTheme(),
            ).isDark
            SideEffect {
                // Update icons and the pre-29 navigation scrim together. Newer versions
                // retain transparent gesture navigation and platform contrast protection.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightLiquidGlassColors.canvas.toArgb(),
                        DarkLiquidGlassColors.canvas.toArgb(),
                    ) { isDarkTheme },
                )
            }
            var showBackgroundRationale by remember { mutableStateOf(false) }
            val requiredPermissions = remember {
                foregroundPermissions(Build.VERSION.SDK_INT)
            }

            // Android 10 offers "Allow all the time" in a separate runtime request.
            val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                refreshLocationPermissionState(viewModel)
                if (hasForegroundLocationPermission()) {
                    requestMapLocation(this@MainActivity, viewModel, scope)
                }
            }
            val applicationSettingsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                refreshLocationPermissionState(viewModel)
                if (hasForegroundLocationPermission()) {
                    requestMapLocation(this@MainActivity, viewModel, scope)
                }
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                val foregroundGranted = hasForegroundLocationPermission()
                // A one-time grant can expire; only denials should retain the settings-recovery marker.
                hasCompletedForegroundPermissionRequest = !foregroundGranted
                refreshLocationPermissionState(viewModel)
                if (foregroundGranted) {
                    requestMapLocation(this@MainActivity, viewModel, scope)
                    showBackgroundRationale = locationPermissionStep() != LocationPermissionStep.GRANTED
                }
            }
            val requestForegroundPermission = {
                if (isForegroundLocationPermanentlyDenied()) {
                    applicationSettingsLauncher.launch(applicationDetailsSettingsIntent())
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            }

            LaunchedEffect(Unit) {
                refreshLocationPermissionState(viewModel)
                if (!hasForegroundLocationPermission()) {
                    // Settings recovery is user initiated through the permission banner.
                    if (!isForegroundLocationPermanentlyDenied()) {
                        requestForegroundPermission()
                    }
                } else {
                    requestMapLocation(this@MainActivity, viewModel, scope)
                }
            }

            DisposableEffect(viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshLocationPermissionState(viewModel)
                    }
                }
                lifecycle.addObserver(observer)
                viewModel.requestCurrentLocation = {
                    requestMapLocation(this@MainActivity, viewModel, scope)
                }
                onDispose {
                    lifecycle.removeObserver(observer)
                    viewModel.requestCurrentLocation = null
                    currentLocationJob?.cancel()
                }
            }

            FoldAwareControls(this@MainActivity) {
                App(
                    container = container,
                    viewModel = viewModel,
                    onRequestPermission = {
                        when (locationPermissionStep()) {
                            LocationPermissionStep.FOREGROUND -> requestForegroundPermission()
                            LocationPermissionStep.BACKGROUND_RUNTIME,
                            LocationPermissionStep.BACKGROUND_SETTINGS -> showBackgroundRationale = true
                            LocationPermissionStep.GRANTED -> refreshLocationPermissionState(viewModel)
                        }
                    },
                )
            }

            // An educational dialog must never cover the active wake-up alarm.
            if (showBackgroundRationale && permissionUiState.activeRingingAlarm == null) {
                LiquidGlassTheme(
                    themeMode = permissionUiState.themeMode,
                    locale = permissionUiState.appLocale,
                ) {
                    BackgroundLocationPermissionDialog(
                        settingsOptionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            packageManager.backgroundPermissionOptionLabel.toString()
                        } else {
                            null
                        },
                        onDismiss = { showBackgroundRationale = false },
                        onGrant = {
                            showBackgroundRationale = false
                            when (locationPermissionStep()) {
                                LocationPermissionStep.FOREGROUND -> requestForegroundPermission()
                                LocationPermissionStep.BACKGROUND_RUNTIME -> backgroundPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                )
                                LocationPermissionStep.BACKGROUND_SETTINGS -> applicationSettingsLauncher.launch(
                                    applicationDetailsSettingsIntent(),
                                )
                                LocationPermissionStep.GRANTED -> refreshLocationPermissionState(viewModel)
                            }
                        },
                    )
                }
            }
        }
    }

    private fun hasForegroundLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isForegroundLocationPermanentlyDenied(): Boolean = shouldOpenForegroundLocationSettings(
        foregroundGranted = hasForegroundLocationPermission(),
        requestCompleted = hasCompletedForegroundPermissionRequest,
        fineLocationRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocationRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION),
    )

    private fun applicationDetailsSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )

    private fun locationPermissionStep(): LocationPermissionStep = nextLocationPermissionStep(
        apiLevel = Build.VERSION.SDK_INT,
        foregroundGranted = hasForegroundLocationPermission(),
        backgroundGranted = hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
    )

    private fun refreshLocationPermissionState(viewModel: FjalarViewModel) {
        val granted = locationPermissionStep() == LocationPermissionStep.GRANTED
        val previouslyGranted = viewModel.uiState.value.hasLocationPermission
        // The alarm permission banner stays visible until both foreground and
        // background access are ready. Map requests need only foreground access.
        viewModel.onPermissionResult(granted)
        if (granted && !previouslyGranted) {
            viewModel.syncSchedules()
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
