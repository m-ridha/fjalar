package com.muhammadridha.fjalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.components.ArmedStatusPill
import com.muhammadridha.fjalar.ui.components.FloatingHudBar
import com.muhammadridha.fjalar.ui.components.LocationPermissionBanner
import com.muhammadridha.fjalar.ui.components.PlatformMapView
import com.muhammadridha.fjalar.ui.components.RingingAlertOverlay
import com.muhammadridha.fjalar.ui.components.SearchPlacesSheet
import com.muhammadridha.fjalar.ui.navigation.AlarmsRoute
import com.muhammadridha.fjalar.ui.navigation.ConfigureRoute
import com.muhammadridha.fjalar.ui.navigation.FrostedBottomNavigationBar
import com.muhammadridha.fjalar.ui.navigation.MapPickerRoute
import com.muhammadridha.fjalar.ui.navigation.MapRoute
import com.muhammadridha.fjalar.ui.navigation.isBottomBarVisible
import com.muhammadridha.fjalar.ui.navigation.mapPickerDestination
import com.muhammadridha.fjalar.ui.screens.AlarmsScreen
import com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft
import com.muhammadridha.fjalar.ui.screens.ConfigureAlarmScreen
import com.muhammadridha.fjalar.ui.screens.MapPickerScreen
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme

/**
 * Main Composable representing the root Fjalar application.
 * Hosts multi-screen navigation using Jetpack Navigation Multiplatform:
 * - Map tab ([MapRoute]): Living map canvas, Floating HUD, and Search sheet
 * - Alarms tab ([AlarmsRoute]): Dedicated alarms list with delete and toggle flows
 * - Configure modal ([ConfigureRoute]): Full-screen alarm creation & editing
 * - Map picker modal ([MapPickerRoute]): Full-screen interactive pin refinement
 *
 * Guaranteed Safety Precedence (Invariant 3):
 * When an alarm transitions to [AlarmState.RINGING], [RingingAlertOverlay] is rendered
 * outside and above the [NavHost] occupying 100% of the viewport.
 */
@Composable
fun FjalarApp(
    viewModel: FjalarViewModel,
    modifier: Modifier = Modifier,
    reducedTransparency: Boolean = false,
    onRequestPermission: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController(),
) {
    var dockHeight by remember { mutableStateOf(100.dp) }
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // System back can pop Configure without invoking its toolbar callback. Clear
    // abandoned editor state when a top-level destination becomes current again.
    // Returning from the picker keeps Configure current and preserves its draft.
    LaunchedEffect(navBackStackEntry) {
        if (isBottomBarVisible(currentRoute) && viewModel.uiState.value.activeDraft != null) {
            viewModel.onClearDraft()
        }
    }

    LiquidGlassTheme(
        themeMode = uiState.themeMode,
        locale = uiState.appLocale,
        reducedTransparency = reducedTransparency,
    ) {
        val colors = LiquidGlassTheme.colors

        val isDarkTheme = uiState.themeMode == ThemeMode.DARK ||
            (uiState.themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

        val activeAlarm = uiState.alarms.firstOrNull { it.state == AlarmState.ARMED_MONITORING }

        val pinLocation = activeAlarm?.coordinates ?: uiState.selectedCoordinates
        val geofenceCenter = pinLocation
        val geofenceRadius = activeAlarm?.radius ?: uiState.selectedRadius

        val center = uiState.mapViewportCenter
            ?: uiState.userLocation
            ?: uiState.selectedCoordinates
            ?: GeoCoordinates(0.0, 0.0)

        var permissionBannerDismissed by remember { mutableStateOf(false) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.canvas),
        ) {
            // Root Multiplatform Navigation Host
            NavHost(
                navController = navController,
                startDestination = MapRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Screen 1: Living Map Canvas
                composable<MapRoute> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlatformMapView(
                            center = center,
                            zoom = uiState.mapViewportZoom,
                            pinLocation = pinLocation,
                            geofenceCenter = geofenceCenter,
                            geofenceRadius = geofenceRadius,
                            isDarkTheme = isDarkTheme,
                            onMapClick = viewModel::onMapClicked,
                            onPinDrag = viewModel::onPinDragged,
                            viewportRequestId = uiState.viewportRequestId,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Top Floating HUD & Armed Status Pill
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .then(WindowInsetsLayoutHelper.safeDrawingPaddingModifier())
                                .padding(bottom = dockHeight)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FloatingHudBar(
                                onSearchClick = viewModel::onOpenSearch,
                                onToggleLocale = viewModel::onToggleLocale,
                                onSelectLocale = viewModel::onSelectLocale,
                                onToggleTheme = viewModel::onToggleTheme,
                                onRecenter = viewModel::onRecenterMap,
                                modifier = Modifier,
                                locale = uiState.appLocale,
                                themeMode = uiState.themeMode,
                            )

                            if (!uiState.hasLocationPermission && !permissionBannerDismissed) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LocationPermissionBanner(
                                    onRequestPermission = { onRequestPermission?.invoke() },
                                    onDismiss = { permissionBannerDismissed = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(WindowInsetsLayoutHelper.horizontalSafeDrawingPaddingModifier())
                                        .padding(horizontal = 16.dp),
                                )
                            }

                            if (activeAlarm != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                ArmedStatusPill(
                                    alarm = activeAlarm,
                                    onClick = {
                                        viewModel.onStartConfiguringAlarm(
                                            alarmId = activeAlarm.id,
                                            defaultCoordinates = null,
                                        )
                                        navController.navigate(ConfigureRoute(activeAlarm.id))
                                    },
                                    modifier = WindowInsetsLayoutHelper.horizontalSafeDrawingPaddingModifier()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                        }

                        // Search Places Modal Sheet
                        AnimatedVisibility(
                            visible = uiState.isSearchSheetVisible,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                        ) {
                            SearchPlacesSheet(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::onSearchQueryChanged,
                                results = uiState.searchResults,
                                selectedCategory = uiState.selectedCategoryFilter,
                                onCategorySelect = viewModel::onSelectCategoryFilter,
                                onPlaceSelect = { place ->
                                    viewModel.onSelectPlace(place)
                                    navController.navigate(ConfigureRoute())
                                },
                                onDismiss = viewModel::onDismissSearch,
                                isLoading = uiState.isSearching,
                                userLocation = uiState.userLocation,
                                locale = uiState.appLocale,
                                modifier = WindowInsetsLayoutHelper.safeDrawingPaddingModifier()
                                    .imePadding()
                                    .padding(bottom = 100.dp),
                            )
                        }
                    }
                }

                // Screen 2: Dedicated Alarms Tab
                composable<AlarmsRoute> {
                    AlarmsScreen(
                        alarms = uiState.alarms,
                        bottomDockClearance = dockHeight,
                        hasOperationFailure = uiState.hasOperationFailure,
                        onToggleAlarm = viewModel::onToggleAlarm,
                        onSkipToday = viewModel::onSkipToday,
                        onDeleteAlarm = { viewModel.onDeleteAlarm(it) },
                        onNavigateToConfigure = { alarmId ->
                            viewModel.onStartConfiguringAlarm(alarmId, defaultCoordinates = null)
                            navController.navigate(ConfigureRoute(alarmId))
                        },
                        locale = uiState.appLocale,
                    )
                }

                // Screen 3: Configure Alarm Full-Screen Modal
                composable<ConfigureRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<ConfigureRoute>()
                    LaunchedEffect(backStackEntry) {
                        if (viewModel.uiState.value.activeDraft == null) {
                            viewModel.onStartConfiguringAlarm(route.alarmId)
                        }
                    }
                    val draft = uiState.activeDraft ?: run {
                        val existing = route.alarmId?.let { id ->
                            uiState.alarms.firstOrNull { it.id == id }
                        }
                        if (existing != null) {
                            ConfigureAlarmDraft.fromAlarm(existing)
                        } else {
                            ConfigureAlarmDraft(
                                coordinates = uiState.selectedCoordinates
                                    ?: uiState.userLocation,
                            )
                        }
                    }

                    val handleBack: () -> Unit = {
                        viewModel.onClearDraft()
                        navController.popBackStack()
                    }

                    ConfigureAlarmScreen(
                        draft = draft,
                        persistedAlarm = draft.deletionTarget(uiState.alarms),
                        isOperationPending = uiState.isOperationPending,
                        hasOperationFailure = uiState.hasOperationFailure,
                        onDraftChange = viewModel::onUpdateDraft,
                        onSave = { savedDraft ->
                            viewModel.onSaveDraftAlarm(savedDraft) {
                                if (navController.currentBackStackEntry == backStackEntry) {
                                    navController.popBackStack()
                                }
                            }
                        },
                        onBack = handleBack,
                        onNavigateToPicker = { coords ->
                            if (!viewModel.uiState.value.isOperationPending) {
                                navController.navigate(
                                    MapPickerRoute(
                                        initialLat = coords.latitude,
                                        initialLon = coords.longitude,
                                    )
                                )
                            }
                        },
                        onDelete = { id ->
                            viewModel.onDeleteAlarm(id) {
                                if (navController.currentBackStackEntry == backStackEntry) {
                                    navController.popBackStack()
                                }
                            }
                        },
                        locale = uiState.appLocale,
                    )
                }

                // Screen 4: Map Location Picker Full-Screen Modal
                mapPickerDestination { backStackEntry ->
                    val route = backStackEntry.toRoute<MapPickerRoute>()
                    MapPickerScreen(
                        initialCoordinates = GeoCoordinates(route.initialLat, route.initialLon),
                        initialZoom = if (uiState.activeDraft?.coordinates == null) 2.0 else 15.0,
                        geofenceRadius = uiState.activeDraft
                            ?.let { TriggerRadius(it.radiusMeters) }
                            ?: TriggerRadius.DEFAULT,
                        onConfirmCoordinates = { coords ->
                            viewModel.onDraftCoordinatesChanged(coords)
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        locale = uiState.appLocale,
                    )
                }
            }

            // Anchored Frosted Bottom Navigation Dock (Visible on top-level tabs)
            if (isBottomBarVisible(currentRoute)) {
                FrostedBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigateToMap = {
                        navController.navigate(MapRoute) {
                            popUpTo<MapRoute> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAlarms = {
                        navController.navigate(AlarmsRoute) {
                            popUpTo<MapRoute> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddAlarmClick = {
                        viewModel.onStartConfiguringAlarm(
                            alarmId = null,
                            defaultCoordinates = uiState.selectedCoordinates ?: uiState.userLocation,
                        )
                        navController.navigate(ConfigureRoute())
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDockHeightChanged = { dockHeight = it },
                )
            }

            // Topmost Safety Layer: Invariant 3 (Sleepy-Eyes Dismissal Safety)
            val ringingAlarm = uiState.activeRingingAlarm
            if (ringingAlarm != null) {
                key(ringingAlarm.id) {
                    // A modal window also blocks system back and existing confirmation dialogs.
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false,
                        ),
                    ) {
                        RingingAlertOverlay(
                            alarm = ringingAlarm,
                            onDismiss = viewModel::onDismissRingingAlarm,
                            // Snooze remains inert until a dedicated use case is available.
                            onSnooze = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
