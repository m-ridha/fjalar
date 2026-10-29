package com.muhammadridha.fjalar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.SnoozeDeliveryUnavailableException
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Observable UI state representation for the entire Fjalar application.
 */
data class FjalarUiState(
    val alarms: List<LocationAlarm> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLocale: AppLocale = AppLocale.EN,
    val activeRingingAlarm: LocationAlarm? = null,
    val selectedAlarmForEdit: LocationAlarm? = null,
    val isConfigureSheetVisible: Boolean = false,
    val searchQuery: String = "",
    val isSearchSheetVisible: Boolean = false,
    val searchResults: List<SearchResultPlace> = emptyList(),
    val isSearching: Boolean = false,
    val selectedCategoryFilter: PlaceCategory? = null,
    val initialAlarmLabel: String? = null,
    val locationFix: LocationFix? = null,
    val locationAvailability: LocationAvailability = LocationAvailability.PROVIDER_UNAVAILABLE,
    val userLocation: GeoCoordinates? = null,
    val distanceMetersByAlarmId: Map<String, Int?> = emptyMap(),
    val nearestArmedAlarm: LocationAlarm? = null,
    val selectedCoordinates: GeoCoordinates? = null,
    val mapViewportCenter: GeoCoordinates? = null,
    val mapViewportZoom: Double = 2.0,
    val selectedRadius: TriggerRadius = TriggerRadius.DEFAULT,
    val viewportRequestId: Long = 0L,
    val activeDraft: ConfigureAlarmDraft? = null,
    // Access required by the platform for background alarm monitoring.
    val hasLocationPermission: Boolean = true,
    val hasOperationFailure: Boolean = false,
    val isOperationPending: Boolean = false,
    val ringingOperationFailureId: Long = 0L,
    val snoozeDeliveryUnavailable: Boolean = false,
)

/**
 * Driving adapter (ViewModel) coordinating user interactions, inbound domain ports,
 * and outbound user preferences.
 */
class FjalarViewModel(
    private val observeAlarmsPort: ObserveAlarmsPort,
    private val saveAlarmPort: SaveAlarmPort,
    private val toggleAlarmPort: ToggleAlarmPort,
    private val dismissAlarmPort: DismissAlarmPort,
    private val skipTodayPort: SkipTodayPort,
    private val preferencesPort: PreferencesPort,
    private val syncSchedulesPort: SyncSchedulesPort? = null,
    private val mapRendererPort: MapRendererPort? = null,
    private val searchPlacesPort: SearchPlacesPort? = null,
    private val deleteAlarmPort: DeleteAlarmPort? = null,
    coroutineScope: CoroutineScope? = null,
    private val locationTrackerPort: LocationTrackerPort? = null,
    private val snoozeAlarmPort: SnoozeAlarmPort? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    internal val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private var draftRevision: Long = 0L

    private val _uiState = MutableStateFlow(FjalarUiState())
    val uiState: StateFlow<FjalarUiState> = _uiState.asStateFlow()

    private var snoozeDeadlineJob: Job? = null
    private var fixExpiryJob: Job? = null
    private var searchJob: Job? = null
    private var unfilteredSearchResults: List<SearchResultPlace> = emptyList()

    private val mapTrackingMutex = kotlinx.coroutines.sync.Mutex()
    private var appForeground = false
    private var mapVisible = false
    private var pendingRecenter = false

    fun onAppForegroundChanged(foreground: Boolean) {
        appForeground = foreground
        updateSnoozeDeadline(_uiState.value.alarms)
        updateMapTracking()
    }

    private fun updateSnoozeDeadline(alarms: List<LocationAlarm>) {
        snoozeDeadlineJob?.cancel()
        if (!appForeground || syncSchedulesPort == null) return
        val deadline = alarms.filter { it.state == AlarmState.SNOOZED }
            .mapNotNull { alarm ->
                alarm.snoozedUntil?.let { deadline ->
                    alarm.suppressedUntil?.let { minOf(deadline, it) } ?: deadline
                }
            }.minOrNull() ?: return
        snoozeDeadlineJob = scope.launch {
            while (true) {
                val remaining = deadline - clock.now()
                if (remaining > kotlin.time.Duration.ZERO) {
                    val wholeMillis = remaining.inWholeMilliseconds
                    val waitMillis = wholeMillis + if (remaining > wholeMillis.milliseconds) 1 else 0
                    delay(waitMillis.coerceAtLeast(1))
                    continue
                }
                // Synchronization can observe an earlier clock than this timer. If it
                // leaves the same snooze intact, retry without depending on a new emission.
                syncSchedules().join()
                val stillPending = _uiState.value.alarms.any { alarm ->
                    alarm.state == AlarmState.SNOOZED &&
                        (alarm.snoozedUntil == deadline || alarm.suppressedUntil == deadline)
                }
                if (!stillPending) return@launch
                delay(1_000)
            }
        }
    }

    fun onMapVisibilityChanged(visible: Boolean) {
        mapVisible = visible
        updateMapTracking()
    }

    private fun updateMapTracking() {
        scope.launch {
            mapTrackingMutex.lock()
            try {
                locationTrackerPort?.setForegroundMapTracking(appForeground && mapVisible)
            } finally {
                mapTrackingMutex.unlock()
            }
        }
    }

    init {
        scope.launch {
            locationTrackerPort?.observeFixes()?.collect { fix ->
                acceptLocationFix(fix)
            }
        }
        scope.launch {
            locationTrackerPort?.observeAvailability()?.collect { availability ->
                updateLocationDisplay { it.copy(locationAvailability = availability) }
            }
        }
        scope.launch {
            observeAlarmsPort.observeAlarms().collect { alarmsList ->
                updateSnoozeDeadline(alarmsList)
                val ringing = alarmsList.filter { it.state == AlarmState.RINGING }.minByOrNull { it.id }
                _uiState.update { current ->
                    current.copy(
                        alarms = alarmsList,
                        activeRingingAlarm = ringing,
                    ).withLocationDistances()
                }
            }
        }

        scope.launch {
            preferencesPort.observeThemeMode().collect { mode ->
                _uiState.update { current ->
                    current.copy(themeMode = mode)
                }
            }
        }

        scope.launch {
            preferencesPort.observeLocale().collect { locale ->
                _uiState.update { current ->
                    current.copy(appLocale = locale)
                }
            }
        }
    }

    private fun acceptLocationFix(fix: LocationFix) {
        fixExpiryJob?.cancel()
        val age = fix.monotonicObservation.elapsedNow()
        if (!isFreshLocationFix(age)) {
            updateLocationDisplay { it.copy(locationFix = null) }
            return
        }
        updateLocationDisplay { it.copy(locationFix = fix) }
        fixExpiryJob = scope.launch {
            // The boundary itself remains valid. Expire at the first millisecond after it.
            delay((30.seconds - age).inWholeMilliseconds + 1)
            updateLocationDisplay { it.copy(locationFix = null) }
        }
    }

    private fun updateLocationDisplay(transform: (FjalarUiState) -> FjalarUiState) {
        val recenterRequested = pendingRecenter
        _uiState.update { current ->
            val updated = transform(current).withLocationDistances()
            if (updated.userLocation != null && (current.mapViewportCenter == null || recenterRequested)) {
                updated.copy(
                    mapViewportCenter = updated.userLocation,
                    mapViewportZoom = 15.0,
                    viewportRequestId = current.viewportRequestId + 1,
                )
            } else updated
        }
        if (recenterRequested && _uiState.value.userLocation != null) {
            pendingRecenter = false
            _uiState.value.userLocation?.let { mapRendererPort?.setViewport(it, zoom = 15.0) }
        }
    }

    fun onOpenSearch() {
        _uiState.update { it.copy(isSearchSheetVisible = true, isConfigureSheetVisible = false) }
        if (_uiState.value.searchResults.isEmpty() && _uiState.value.searchQuery.isBlank()) {
            onSearchQueryChanged("")
        }
    }

    fun onDismissSearch() {
        _uiState.update { it.copy(isSearchSheetVisible = false) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        searchJob?.cancel()
        searchJob = scope.launch {
            if (query.isNotBlank()) {
                delay(SEARCH_DEBOUNCE_MILLIS)
            }
            val locale = _uiState.value.appLocale
            val proximity = _uiState.value.userLocation ?: _uiState.value.selectedCoordinates
            val places = searchPlacesPort?.searchPlaces(query, locale, proximity) ?: emptyList()
            unfilteredSearchResults = places
            val filtered = applyCategoryFilter(places, _uiState.value.selectedCategoryFilter)
            _uiState.update { it.copy(searchResults = filtered, isSearching = false) }
        }
    }

    fun onSelectCategoryFilter(category: PlaceCategory?) {
        val filtered = applyCategoryFilter(unfilteredSearchResults, category)
        _uiState.update { current ->
            current.copy(
                selectedCategoryFilter = category,
                searchResults = filtered,
            )
        }
    }

    private fun newDraftIdentity(): String =
        "alarm-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong().toString(16)}"

    fun onSelectPlace(place: SearchResultPlace) {
        draftRevision++
        _uiState.update { current ->
            current.copy(
                isSearchSheetVisible = false,
                isConfigureSheetVisible = true,
                selectedAlarmForEdit = null,
                initialAlarmLabel = place.name,
                activeDraft = ConfigureAlarmDraft(
                    alarmId = newDraftIdentity(),
                    label = place.name,
                    coordinates = place.coordinates,
                    addressLabel = place.detail,
                ),
                selectedCoordinates = place.coordinates,
                selectedRadius = TriggerRadius.DEFAULT,
                mapViewportCenter = place.coordinates,
                mapViewportZoom = 15.0,
                viewportRequestId = current.viewportRequestId + 1L,
            )
        }
        mapRendererPort?.placePin(place.coordinates)
        mapRendererPort?.showGeofence(place.coordinates, TriggerRadius.DEFAULT)
        mapRendererPort?.setViewport(place.coordinates, zoom = 15.0)
    }

    private fun applyCategoryFilter(
        places: List<SearchResultPlace>,
        category: PlaceCategory?,
    ): List<SearchResultPlace> {
        return if (category == null) {
            places
        } else {
            places.filter { it.category == category }
        }
    }

    fun onToggleTheme() {
        val currentMode = _uiState.value.themeMode
        val nextMode = when (currentMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        scope.launch {
            preferencesPort.setThemeMode(nextMode)
        }
    }

    fun onToggleLocale() {
        val currentLocale = _uiState.value.appLocale
        val nextLocale = if (currentLocale == AppLocale.EN) {
            AppLocale.ID
        } else {
            AppLocale.EN
        }
        scope.launch {
            preferencesPort.setLocale(nextLocale)
        }
    }

    fun onSelectLocale(locale: AppLocale) {
        scope.launch {
            preferencesPort.setLocale(locale)
        }
    }

    fun onToggleAlarm(alarmId: String, enabled: Boolean) {
        scope.launch {
            toggleAlarmPort.toggleAlarm(alarmId, enabled)
        }
    }

    fun onSkipToday(alarmId: String) {
        scope.launch {
            skipTodayPort.skipToday(alarmId)
        }
    }

    fun onSaveAlarm(alarm: LocationAlarm) {
        scope.launch {
            val result = saveAlarmPort.saveAlarm(alarm)
            if (result.isSuccess) {
                _uiState.update { current ->
                    current.copy(
                        isConfigureSheetVisible = false,
                        selectedAlarmForEdit = null,
                        initialAlarmLabel = null,
                        selectedCoordinates = null,
                        selectedRadius = TriggerRadius.DEFAULT,
                    )
                }
                mapRendererPort?.clearOverlays()
            }
        }
    }

    fun onDeleteAlarm(alarmId: String, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isOperationPending) return
        val operationDraftRevision = draftRevision
        _uiState.update { it.copy(isOperationPending = true, hasOperationFailure = false) }
        scope.launch {
            val result = deleteAlarmPort?.deleteAlarm(alarmId)
            val ownsDraft = operationDraftRevision == draftRevision
            _uiState.update {
                it.copy(
                    isOperationPending = false,
                    hasOperationFailure = ownsDraft && result?.isSuccess != true,
                )
            }
            if (result?.isSuccess == true && ownsDraft) {
                if (_uiState.value.selectedAlarmForEdit?.id == alarmId) {
                    _uiState.update { current ->
                        current.copy(
                            isConfigureSheetVisible = false,
                            selectedAlarmForEdit = null,
                            initialAlarmLabel = null,
                            selectedCoordinates = null,
                            selectedRadius = TriggerRadius.DEFAULT,
                        )
                    }
                    mapRendererPort?.clearOverlays()
                }
                _uiState.update { it.copy(activeDraft = null, hasOperationFailure = false) }
                onSuccess()
            }
        }
    }

    fun onDismissRingingAlarm(alarmId: String) {
        performRingingOperation { dismissAlarmPort.dismissAlarm(alarmId) }
    }

    fun onSnoozeRingingAlarm(alarmId: String) {
        performRingingOperation {
            snoozeAlarmPort?.snoozeAlarm(alarmId)
                ?: Result.failure(IllegalStateException("Snooze is unavailable"))
        }
    }

    private fun performRingingOperation(operation: suspend () -> Result<Unit>) {
        if (_uiState.value.isOperationPending) return
        _uiState.update { it.copy(isOperationPending = true, hasOperationFailure = false, snoozeDeliveryUnavailable = false) }
        scope.launch {
            try {
                val result = operation()
                _uiState.update {
                    it.copy(
                        hasOperationFailure = result.isFailure,
                        snoozeDeliveryUnavailable = result.exceptionOrNull() is SnoozeDeliveryUnavailableException,
                        ringingOperationFailureId = it.ringingOperationFailureId + if (result.isFailure) 1 else 0,
                    )
                }
            } finally {
                _uiState.update { it.copy(isOperationPending = false) }
            }
        }
    }

    fun onOpenNewAlarm() {
        val target = _uiState.value.selectedCoordinates
            ?: _uiState.value.userLocation
        _uiState.update { current ->
            current.copy(
                selectedAlarmForEdit = null,
                initialAlarmLabel = null,
                isConfigureSheetVisible = true,
                selectedCoordinates = target,
                selectedRadius = TriggerRadius.DEFAULT,
            )
        }
        if (target != null) {
            mapRendererPort?.placePin(target)
            mapRendererPort?.showGeofence(target, TriggerRadius.DEFAULT)
        }
    }

    fun onOpenEditAlarm(alarm: LocationAlarm) {
        _uiState.update { current ->
            current.copy(
                selectedAlarmForEdit = alarm,
                initialAlarmLabel = alarm.label,
                isConfigureSheetVisible = true,
                selectedCoordinates = alarm.coordinates,
                selectedRadius = alarm.radius,
                mapViewportCenter = alarm.coordinates,
                mapViewportZoom = 15.0,
                viewportRequestId = current.viewportRequestId + 1L,
            )
        }
        mapRendererPort?.placePin(alarm.coordinates)
        mapRendererPort?.showGeofence(alarm.coordinates, alarm.radius)
        mapRendererPort?.setViewport(alarm.coordinates, zoom = 15.0)
    }

    fun onDismissConfigureSheet() {
        _uiState.update { current ->
            current.copy(
                isConfigureSheetVisible = false,
                selectedAlarmForEdit = null,
                initialAlarmLabel = null,
                selectedCoordinates = null,
                selectedRadius = TriggerRadius.DEFAULT,
            )
        }
        mapRendererPort?.clearOverlays()
    }

    fun onStartConfiguringAlarm(alarmId: String?, defaultCoordinates: GeoCoordinates? = null) {
        draftRevision++
        val draft = if (alarmId != null) {
            val alarm = _uiState.value.alarms.firstOrNull { it.id == alarmId }
            alarm?.let { ConfigureAlarmDraft.fromAlarm(it) }
                ?: ConfigureAlarmDraft(
                    alarmId = alarmId,
                    coordinates = defaultCoordinates
                        ?: _uiState.value.selectedCoordinates
                        ?: _uiState.value.userLocation,
                )
        } else {
            ConfigureAlarmDraft(
                alarmId = newDraftIdentity(),
                coordinates = defaultCoordinates
                    ?: _uiState.value.selectedCoordinates
                    ?: _uiState.value.userLocation,
            )
        }
        _uiState.update { current ->
            current.copy(
                activeDraft = draft,
                hasOperationFailure = false,
                selectedAlarmForEdit = _uiState.value.alarms.firstOrNull { it.id == alarmId },
                selectedCoordinates = draft.coordinates,
                selectedRadius = TriggerRadius(draft.radiusMeters),
            )
        }
        draft.coordinates?.let { coords ->
            mapRendererPort?.placePin(coords)
            mapRendererPort?.showGeofence(coords, TriggerRadius(draft.radiusMeters))
        }
    }

    fun onUpdateDraft(draft: ConfigureAlarmDraft) {
        draftRevision++
        _uiState.update { current ->
            current.copy(
                activeDraft = draft,
                selectedCoordinates = draft.coordinates,
                selectedRadius = TriggerRadius(draft.radiusMeters),
            )
        }
        draft.coordinates?.let { coords ->
            mapRendererPort?.placePin(coords)
            mapRendererPort?.showGeofence(coords, TriggerRadius(draft.radiusMeters))
        }
    }

    fun onDraftCoordinatesChanged(coordinates: GeoCoordinates) {
        draftRevision++
        _uiState.update { current ->
            val updatedDraft = current.activeDraft?.withNewCoordinates(coordinates)
                ?: ConfigureAlarmDraft(alarmId = newDraftIdentity(), coordinates = coordinates)
            current.copy(
                activeDraft = updatedDraft,
                selectedCoordinates = coordinates,
            )
        }
        val radius = _uiState.value.activeDraft?.let { TriggerRadius(it.radiusMeters) } ?: TriggerRadius.DEFAULT
        mapRendererPort?.placePin(coordinates)
        mapRendererPort?.showGeofence(coordinates, radius)
    }

    fun onSaveDraftAlarm(draft: ConfigureAlarmDraft, onSuccess: () -> Unit = {}) {
        val existing = draft.alarmId?.let { id ->
            _uiState.value.alarms.firstOrNull { it.id == id }
        }
        if (_uiState.value.isOperationPending) return
        val alarm = draft.toLocationAlarm(existing)
        val operationDraftRevision = draftRevision
        _uiState.update { it.copy(isOperationPending = true, hasOperationFailure = false) }
        scope.launch {
            val result = saveAlarmPort.saveAlarm(alarm, draft.enabledOverride)
            val ownsDraft = operationDraftRevision == draftRevision
            _uiState.update {
                it.copy(isOperationPending = false, hasOperationFailure = ownsDraft && result.isFailure)
            }
            if (result.isSuccess && ownsDraft) {
                _uiState.update { current ->
                    current.copy(
                        activeDraft = null,
                        selectedAlarmForEdit = null,
                        initialAlarmLabel = null,
                        selectedCoordinates = null,
                        selectedRadius = TriggerRadius.DEFAULT,
                    )
                }
                mapRendererPort?.clearOverlays()
                onSuccess()
            }
        }
    }

    fun onClearDraft() {
        draftRevision++
        _uiState.update { it.copy(activeDraft = null, hasOperationFailure = false) }
    }

    /**
     * Re-synchronizes all saved alarms against current time to re-arm commute windows
     * and transition states. Invoked during foreground application entry and system events.
     */
    fun syncSchedules(): Job = scope.launch {
        syncSchedulesPort?.syncSchedules()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    // Platform foreground UI request; does not start the alarm tracking port.
    var requestCurrentLocation: (() -> Unit)? = null

    fun onRecenterMap() {
        pendingRecenter = true
        requestCurrentLocation?.invoke()
        recenterOnKnownLocation()
    }

    private fun recenterOnKnownLocation() {
        val current = _uiState.value
        val knownTarget = current.userLocation
            ?: current.selectedCoordinates
        val target = knownTarget ?: GeoCoordinates(0.0, 0.0)
        val zoom = if (knownTarget == null) 2.0 else 15.0
        _uiState.update { state ->
            state.copy(
                mapViewportCenter = target,
                mapViewportZoom = zoom,
                viewportRequestId = state.viewportRequestId + 1L,
            )
        }
        mapRendererPort?.setViewport(target, zoom = zoom)
    }

    fun onMapClicked(coordinates: GeoCoordinates) {
        _uiState.update { it.copy(selectedCoordinates = coordinates) }
        mapRendererPort?.placePin(coordinates)
        mapRendererPort?.showGeofence(coordinates, _uiState.value.selectedRadius)
    }

    fun onPinDragged(coordinates: GeoCoordinates) {
        _uiState.update { it.copy(selectedCoordinates = coordinates) }
        mapRendererPort?.placePin(coordinates)
        mapRendererPort?.showGeofence(coordinates, _uiState.value.selectedRadius)
    }

    fun onRadiusChanged(radius: TriggerRadius) {
        _uiState.update { it.copy(selectedRadius = radius) }
        val coords = _uiState.value.selectedCoordinates
        if (coords != null) {
            mapRendererPort?.showGeofence(coords, radius)
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300L
    }
}
