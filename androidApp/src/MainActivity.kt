package com.muhammadridha.fjalar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.components.BackgroundLocationPermissionDialog
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.LightLiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.DarkLiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.resolveLiquidGlassColors

class MainActivity : ComponentActivity() {
    private val permissionPreferences by lazy {
        getSharedPreferences("location_permission_requests", Context.MODE_PRIVATE)
    }
    private var hasCompletedForegroundPermissionRequest: Boolean
        get() = permissionPreferences.getBoolean("foreground_request_completed", false)
        set(value) {
            permissionPreferences.edit().putBoolean("foreground_request_completed", value).apply()
        }

    private var hasCompletedBackgroundPermissionRequest: Boolean
        get() = permissionPreferences.getBoolean("background_request_completed", false)
        set(value) {
            permissionPreferences.edit().putBoolean("background_request_completed", value).apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        awakenScreenAndShowOverKeyguard()
        // Draw edge-to-edge behind system status and navigation bars; safe insets are handled via WindowInsetsLayoutHelper
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as? FjalarApplication
        val container = app?.container
        setContent {
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

            // Background access must be requested separately after foreground access.
            val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasCompletedBackgroundPermissionRequest = !granted
                refreshLocationPermissionState(viewModel)
                if (hasForegroundLocationPermission()) {
                    requestMapLocation(viewModel)
                }
            }
            val applicationSettingsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                refreshLocationPermissionState(viewModel)
                if (hasForegroundLocationPermission()) {
                    requestMapLocation(viewModel)
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
                    requestMapLocation(viewModel)
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
                    requestMapLocation(viewModel)
                }
            }

            DisposableEffect(viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.onAppForegroundChanged(true)
                        refreshLocationPermissionState(viewModel)
                    } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                        viewModel.onAppForegroundChanged(false)
                    }
                }
                lifecycle.addObserver(observer)
                viewModel.requestCurrentLocation = {
                    requestMapLocation(viewModel)
                }
                onDispose {
                    viewModel.onAppForegroundChanged(false)
                    lifecycle.removeObserver(observer)
                    viewModel.requestCurrentLocation = null
                }
            }

            FoldAwareControls(this@MainActivity) {
                App(
                    container = container,
                    viewModel = viewModel,
                    onRequestPermission = {
                        when (locationPermissionStep()) {
                            LocationPermissionStep.FOREGROUND -> requestForegroundPermission()
                            LocationPermissionStep.BACKGROUND_RUNTIME -> showBackgroundRationale = true
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
                                LocationPermissionStep.BACKGROUND_RUNTIME -> {
                                    if (isBackgroundLocationPermanentlyDenied()) {
                                        applicationSettingsLauncher.launch(applicationDetailsSettingsIntent())
                                    } else {
                                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                }
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

    private fun isBackgroundLocationPermanentlyDenied(): Boolean = shouldOpenBackgroundLocationSettings(
        backgroundGranted = hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        requestCompleted = hasCompletedBackgroundPermissionRequest,
        backgroundRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
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
        if (hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            hasCompletedBackgroundPermissionRequest = false
        }
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

    private fun requestMapLocation(
        viewModel: FjalarViewModel,
    ) {
        // Permission recovery/recenter refresh the existing owner, never open a second listener.
        viewModel.onAppForegroundChanged(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(keyValueStorage = remember { InMemoryKeyValueStorage() })
}
