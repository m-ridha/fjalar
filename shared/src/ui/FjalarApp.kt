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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.muhammadridha.fjalar.ui.accessibility.isReduceTransparencyActive
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.components.ArmedStatusPill
import com.muhammadridha.fjalar.ui.components.FloatingHudBar
import com.muhammadridha.fjalar.ui.components.LocationPermissionBanner
import com.muhammadridha.fjalar.ui.components.PlatformMapView
import com.muhammadridha.fjalar.ui.components.RingingAlertOverlay
import com.muhammadridha.fjalar.ui.components.StartupPlaceholder
import com.muhammadridha.fjalar.ui.components.SearchPlacesOverlay
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
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import kotlinx.coroutines.launch

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
    reducedTransparency: Boolean = isReduceTransparencyActive(),
    suppressPermissionBanner: Boolean = false,
    onRequestPermission: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController(),
) {
    var dockHeight by remember { mutableStateOf(100.dp) }
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val editorSnackbarHostState = remember { SnackbarHostState() }
    val editorNoticeScope = rememberCoroutineScope()
    var lastQueuedEditorNoticeId by remember(viewModel) { mutableStateOf(0L) }
    androidx.compose.runtime.DisposableEffect(currentRoute, viewModel) {
        viewModel.onMapVisibilityChanged(
            currentRoute == null || com.muhammadridha.fjalar.ui.navigation.isMapRoute(currentRoute) ||
                navBackStackEntry?.destination?.hasRoute<MapPickerRoute>() == true,
        )
        onDispose { viewModel.onMapVisibilityChanged(false) }
    }


    LiquidGlassTheme(
        themeMode = uiState.themeMode,
        locale = uiState.appLocale,
        reducedTransparency = reducedTransparency,
    ) {
        val colors = LiquidGlassTheme.colors

        val isDarkTheme = uiState.themeMode == ThemeMode.DARK ||
            (uiState.themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

        val activeAlarm = uiState.nearestArmedAlarm

        val pinLocation = activeAlarm?.coordinates
        val geofenceCenter = activeAlarm?.coordinates
        val geofenceRadius = activeAlarm?.radius

        val center = uiState.mapViewportCenter
            ?: uiState.userLocation
            ?: GeoCoordinates(0.0, 0.0)

        var permissionBannerDismissed by rememberSaveable { mutableStateOf(false) }

        val isMapPickerRoute = navBackStackEntry?.destination?.hasRoute<MapPickerRoute>() == true
        LaunchedEffect(isMapPickerRoute) {
            if (isMapPickerRoute) {
                editorSnackbarHostState.currentSnackbarData?.dismiss()
            }
        }

        val editorNotice = uiState.pendingEditorNotice
        val editorNoticeId = uiState.pendingEditorNoticeId
        val canShowEditorNotice = shouldDisplayEditorNotice(
            notice = editorNotice,
            isMapPickerRoute = isMapPickerRoute,
            isBottomBarVisible = isBottomBarVisible(currentRoute),
            hasActiveDraft = uiState.activeDraft != null,
        )
        LaunchedEffect(editorNoticeId, editorNotice, canShowEditorNotice, uiState.appLocale) {
            if (editorNotice != null && canShowEditorNotice && editorNoticeId > lastQueuedEditorNoticeId) {
                lastQueuedEditorNoticeId = editorNoticeId
                val message = FjalarStrings.get(editorNoticeStringKey(editorNotice), uiState.appLocale)
                // Keep delivery alive if the pending value clears when another editor opens.
                editorNoticeScope.launch {
                    editorSnackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Long,
                    )
                    viewModel.onEditorNoticeShown(editorNoticeId)
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.canvas),
        ) {
            // Root Multiplatform Navigation Host
            // The map (PlatformMapView) is never mounted before the first viewport is known.
            if (uiState.isInitialViewportReady) NavHost(
                navController = navController,
                startDestination = MapRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Screen 1: Living Map Canvas
                composable<MapRoute> {
                    SearchBackHandler(enabled = uiState.isSearchSheetVisible && uiState.activeRingingAlarm == null) {
                        viewModel.onDismissSearch()
                    }
                    var mapBearing by remember { mutableStateOf(0.0) }
                    var northRequestId by remember { mutableStateOf(0L) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlatformMapView(
                            center = center,
                            zoom = uiState.mapViewportZoom,
                            pinLocation = pinLocation,
                            userPosition = uiState.mapUserPosition(),
                            onBearingChanged = { mapBearing = it },
                            northRequestId = northRequestId,
                            geofenceCenter = geofenceCenter,
                            geofenceRadius = geofenceRadius,
                            isDarkTheme = isDarkTheme,
                            onMapClick = {},
                            onPinDrag = {},
                            destinationEditingEnabled = false,
                            onUserCameraIdle = viewModel::onMapViewportBrowsed,
                            viewportRequestId = uiState.viewportRequestId,
                            bottomOverlayClearance = dockHeight,
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (!uiState.isSearchSheetVisible) {
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
                                    onSearchClick = { viewModel.onOpenSearch(PlaceSearchContext.BROWSE) },
                                    onToggleLocale = viewModel::onToggleLocale,
                                    onSelectLocale = viewModel::onSelectLocale,
                                    onToggleTheme = { viewModel.onToggleTheme(isDarkTheme) },
                                    onRecenter = viewModel::onRecenterMap,
                                    bearing = mapBearing,
                                    onResetNorth = { northRequestId++ },
                                    modifier = Modifier,
                                    locale = uiState.appLocale,
                                    themeMode = uiState.themeMode,
                                    armedStatus = activeAlarm?.let { alarm ->
                                        {
                                            ArmedStatusPill(
                                                alarm = alarm,
                                                distanceMeters = uiState.distanceMetersByAlarmId[alarm.id],
                                                onClick = {
                                                    viewModel.onStartConfiguringAlarm(
                                                        alarmId = alarm.id,
                                                        defaultCoordinates = null,
                                                    )
                                                    navController.navigate(ConfigureRoute(alarm.id))
                                                },
                                            )
                                        }
                                    },
                                )

                                if (shouldShowLocationPermissionBanner(
                                        hasLocationPermission = uiState.hasLocationPermission,
                                        permissionBannerDismissed = permissionBannerDismissed,
                                        suppressPermissionBanner = suppressPermissionBanner,
                                    )
                                ) {
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
                            }
                        }

                        // Search Places Modal Overlay
                        SearchPlacesOverlay(
                            visible = uiState.isSearchSheetVisible && uiState.searchContext == PlaceSearchContext.BROWSE,
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            results = uiState.searchResults,
                            selectedCategory = uiState.selectedCategoryFilter,
                            onCategorySelect = viewModel::onSelectCategoryFilter,
                            onPlaceSelect = viewModel::onBrowsePlaceSelected,
                            onDismiss = viewModel::onDismissSearch,
                            recentDestinations = uiState.recentDestinations,
                            onRecentSelect = { recent ->
                                viewModel.onBrowsePlaceSelected(
                                    SearchResultPlace(
                                        id = recent.id,
                                        name = recent.name,
                                        coordinates = recent.coordinates,
                                        category = recent.category,
                                    )
                                )
                            },
                            isLoading = uiState.isSearching,
                            userLocation = uiState.userLocation ?: uiState.mapViewportCenter,
                            locale = uiState.appLocale,
                        )
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
                            ConfigureAlarmDraft()
                        }
                    }

                    var showDiscardDialog by remember(backStackEntry) { mutableStateOf(false) }
                    val isCurrentEditor = { navController.currentBackStackEntry == backStackEntry }
                    val handleBack: () -> Unit = {
                        handleEditorBackIfCurrent(
                            viewModel = viewModel,
                            isCurrentEditor = isCurrentEditor,
                            requestDiscardConfirmation = { showDiscardDialog = true },
                            popEditor = { navController.popBackStack() },
                        )
                    }
                    val handleSave: () -> Unit = {
                        saveEditorIfCurrent(
                            viewModel = viewModel,
                            isCurrentEditor = isCurrentEditor,
                            popEditor = { navController.popBackStack() },
                        )
                    }

                    SearchBackHandler(
                        enabled = navBackStackEntry == backStackEntry && !showDiscardDialog,
                        onBack = handleBack,
                    )

                    ConfigureAlarmScreen(
                        draft = draft,
                        persistedAlarm = draft.deletionTarget(uiState.alarms),
                        isOperationPending = uiState.isOperationPending,
                        hasOperationFailure = uiState.hasOperationFailure,
                        validationNotice = uiState.pendingEditorNotice,
                        onDraftChange = viewModel::onUpdateDraft,
                        onBack = handleBack,
                        onSave = handleSave,
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
                                viewModel.onClearDraft()
                                if (navController.currentBackStackEntry == backStackEntry) {
                                    navController.popBackStack()
                                }
                            }
                        },
                        locale = uiState.appLocale,
                    )

                    if (showDiscardDialog) {
                        val colors = LiquidGlassTheme.colors
                        AlertDialog(
                            onDismissRequest = { showDiscardDialog = false },
                            containerColor = colors.glassModal,
                            titleContentColor = colors.textPrimary,
                            textContentColor = colors.textSecondary,
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Text(
                                    text = FjalarStrings.get(FjalarStringKey.EDITOR_DISCARD_TITLE, uiState.appLocale),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                )
                            },
                            text = {
                                Text(
                                    text = FjalarStrings.get(FjalarStringKey.EDITOR_DISCARD_MESSAGE, uiState.appLocale),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showDiscardDialog = false },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = FjalarStrings.get(
                                            FjalarStringKey.EDITOR_DISCARD_KEEP_EDITING,
                                            uiState.appLocale,
                                        ),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = colors.textSecondary,
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDiscardDialog = false
                                        viewModel.onDiscardAlarmEditor {
                                            if (isCurrentEditor()) navController.popBackStack()
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.danger,
                                        contentColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = FjalarStrings.get(
                                            FjalarStringKey.EDITOR_DISCARD_CONFIRM,
                                            uiState.appLocale,
                                        ),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                            },
                        )
                    }
                }

                // Screen 4: Map Location Picker Full-Screen Modal
                mapPickerDestination { backStackEntry ->
                    val route = backStackEntry.toRoute<MapPickerRoute>()
                    // Freeze initial framing for this visit so new GPS fixes cannot
                    // reset a local, unconfirmed destination selection.
                    val initialState = remember(backStackEntry) { viewModel.uiState.value }
                    val destination = initialState.activeDraft?.coordinates
                    val location = initialState.userLocation
                    val initialCenter = destination ?: location ?: initialState.mapViewportCenter
                        ?: GeoCoordinates(route.initialLat, route.initialLon)
                    val initialZoom = when {
                        destination != null || location != null -> 15.0
                        initialState.mapViewportCenter != null -> initialState.mapViewportZoom
                        else -> 2.0
                    }
                    androidx.compose.runtime.DisposableEffect(backStackEntry) {
                        onDispose {
                            if (viewModel.uiState.value.searchContext == PlaceSearchContext.PICKER) {
                                viewModel.onDismissSearch()
                            }
                        }
                    }
                    MapPickerScreen(
                        initialCoordinates = initialCenter,
                        initialZoom = initialZoom,
                        awaitInitialLocation = destination == null && location == null,
                        hasInitialSelection = uiState.activeDraft?.coordinates != null,
                        userPosition = uiState.mapUserPosition(),
                        geofenceRadius = uiState.activeDraft
                            ?.let { TriggerRadius(it.radiusMeters) }
                            ?: TriggerRadius.DEFAULT,
                        isSearchVisible = uiState.isSearchSheetVisible && uiState.searchContext == PlaceSearchContext.PICKER,
                        searchQuery = uiState.searchQuery,
                        searchResults = uiState.searchResults,
                        selectedCategory = uiState.selectedCategoryFilter,
                        isSearching = uiState.isSearching,
                        onOpenSearch = { viewModel.onOpenSearch(PlaceSearchContext.PICKER) },
                        onSearchQueryChange = viewModel::onSearchQueryChanged,
                        onSelectCategory = viewModel::onSelectCategoryFilter,
                        onDismissSearch = viewModel::onDismissSearch,
                        recentDestinations = uiState.recentDestinations,
                        initialPlaceName = uiState.activeDraft?.addressLabel,
                        onConfirmSelection = { coords, name, category, placeId ->
                            viewModel.onDismissSearch()
                            viewModel.onDraftCoordinatesChanged(coords, name)
                            viewModel.onConfirmDestination(coords, name, category, placeId)
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        locale = uiState.appLocale,
                    )
                }
            }

            val showBottomBar = uiState.isInitialViewportReady &&
                !uiState.isSearchSheetVisible && (
                    navBackStackEntry?.destination?.hasRoute<MapRoute>() == true ||
                        navBackStackEntry?.destination?.hasRoute<AlarmsRoute>() == true
                    )

            SnackbarHost(
                hostState = editorSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(WindowInsetsLayoutHelper.sheetBottomPaddingModifier())
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (showBottomBar) dockHeight + 8.dp else 16.dp),
            )

            // Anchored Frosted Bottom Navigation Dock (Visible on top-level tabs)
            if (showBottomBar) {
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
                            defaultCoordinates = null,
                        )
                        navController.navigate(ConfigureRoute())
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDockHeightChanged = { dockHeight = it },
                )
            }

            StartupPlaceholder(
                visible = !uiState.isInitialViewportReady && uiState.activeRingingAlarm == null,
                reducedTransparency = reducedTransparency,
            )

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
                            distanceMeters = uiState.distanceMetersByAlarmId[ringingAlarm.id],
                            onDismiss = viewModel::onDismissRingingAlarm,
                            onSnooze = viewModel::onSnoozeRingingAlarm,
                            isOperationPending = uiState.isOperationPending,
                            hasOperationFailure = uiState.hasOperationFailure,
                            snoozeDeliveryUnavailable = uiState.snoozeDeliveryUnavailable,
                            operationFailureId = uiState.ringingOperationFailureId,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** Ignore stale callbacks both before a save starts and after its asynchronous completion. */
internal fun saveEditorIfCurrent(
    viewModel: FjalarViewModel,
    isCurrentEditor: () -> Boolean,
    popEditor: () -> Unit,
) {
    if (!isCurrentEditor()) return
    viewModel.onSaveAlarmEditor {
        if (isCurrentEditor()) popEditor()
    }
}

/**
 * Handle toolbar and system Back from Configure Alarm:
 * - Ignore while stale or while a save/delete operation is pending.
 * - If there are unsaved changes, prompt with [requestDiscardConfirmation].
 * - Otherwise clear editor state and pop immediately without a popup.
 */
internal fun handleEditorBackIfCurrent(
    viewModel: FjalarViewModel,
    isCurrentEditor: () -> Boolean,
    requestDiscardConfirmation: () -> Unit,
    popEditor: () -> Unit,
) {
    if (!isCurrentEditor()) return
    if (viewModel.uiState.value.isOperationPending) return
    if (viewModel.hasUnsavedEditorChanges()) {
        requestDiscardConfirmation()
    } else {
        viewModel.onDiscardAlarmEditor {
            if (isCurrentEditor()) popEditor()
        }
    }
}

/** Ignore stale callbacks both before a save starts and after its asynchronous completion. */
internal fun exitEditorIfCurrent(
    viewModel: FjalarViewModel,
    isCurrentEditor: () -> Boolean,
    popEditor: () -> Unit,
) = saveEditorIfCurrent(viewModel, isCurrentEditor, popEditor)

internal fun editorNoticeStringKey(notice: AlarmEditorNotice): FjalarStringKey = when (notice) {
    AlarmEditorNotice.NO_DESTINATION -> FjalarStringKey.EDITOR_NOT_SAVED_NO_DESTINATION
    AlarmEditorNotice.NAME_REQUIRED -> FjalarStringKey.EDITOR_NOT_SAVED_NAME_REQUIRED
    AlarmEditorNotice.TIME_INVALID -> FjalarStringKey.EDITOR_NOT_SAVED_TIME_INVALID
    AlarmEditorNotice.DIFFERENT_TIMES_REQUIRED -> FjalarStringKey.EDITOR_NOT_SAVED_DIFFERENT_TIMES
    AlarmEditorNotice.DAYS_REQUIRED -> FjalarStringKey.EDITOR_NOT_SAVED_DAYS_REQUIRED
    AlarmEditorNotice.DAYS_INVALID -> FjalarStringKey.EDITOR_NOT_SAVED_DAYS_INVALID
    AlarmEditorNotice.RADIUS_INVALID -> FjalarStringKey.EDITOR_NOT_SAVED_RADIUS_INVALID
    AlarmEditorNotice.ALARM_DELETED -> FjalarStringKey.EDITOR_ALARM_DELETED
    AlarmEditorNotice.UPDATE_FAILED -> FjalarStringKey.EDITOR_UPDATE_FAILED
}

internal fun shouldDisplayEditorNotice(
    notice: AlarmEditorNotice?,
    isMapPickerRoute: Boolean,
    isBottomBarVisible: Boolean,
    hasActiveDraft: Boolean,
): Boolean {
    if (notice == null || isMapPickerRoute) return false
    return notice == AlarmEditorNotice.UPDATE_FAILED || isBottomBarVisible || hasActiveDraft
}

internal fun shouldShowLocationPermissionBanner(
    hasLocationPermission: Boolean,
    permissionBannerDismissed: Boolean,
    suppressPermissionBanner: Boolean,
): Boolean = !hasLocationPermission && !permissionBannerDismissed && !suppressPermissionBanner
