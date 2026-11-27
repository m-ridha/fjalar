package com.muhammadridha.fjalar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muhammadridha.fjalar.adapters.location.isFreshLocationFix
import com.muhammadridha.fjalar.adapters.search.PlaceSearchAdapter
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.SnoozeDeliveryUnavailableException
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DeleteAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.AlarmConfigurationMissingException
import com.muhammadridha.fjalar.domain.ports.inbound.AlarmConfigurationRingingException
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import com.muhammadridha.fjalar.ui.components.matchesCategoryFilter
import com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

enum class PlaceSearchContext { BROWSE, PICKER }

/**
 * Observable UI state representation for the entire Fjalar application.
 */
data class FjalarUiState(
    val alarms: List<LocationAlarm> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLocale: AppLocale = AppLocale.EN,
    val activeRingingAlarm: LocationAlarm? = null,
    val searchQuery: String = "",
    val isSearchSheetVisible: Boolean = false,
    val searchContext: PlaceSearchContext? = null,
    val searchResults: List<SearchResultPlace> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchSettled: Boolean = false,
    val selectedCategoryFilter: PlaceCategory? = null,
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
    val pendingEditorNotice: AlarmEditorNotice? = null,
    val pendingEditorNoticeId: Long = 0L,
    // Access required by the platform for background alarm monitoring.
    val hasLocationPermission: Boolean = true,
    val hasOperationFailure: Boolean = false,
    val isOperationPending: Boolean = false,
    val ringingOperationFailureId: Long = 0L,
    val snoozeDeliveryUnavailable: Boolean = false,
    val recentDestinations: List<RecentDestination> = emptyList(),
    /**
     * False until the first map viewport is known, so the map never renders at the neutral
     * `0,0` fallback. Starts true when a previously observed position was restored from disk.
     * See [FjalarViewModel.onLocationAccessChecked] and the startup timeout.
     */
    val isInitialViewportReady: Boolean = false,
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
    private val recentDestinationsPort: RecentDestinationsPort? = null,
    private val synchronizeAlarmsUseCase: SynchronizeAlarmsUseCase? = null,
    private val startupViewportTimeoutMillis: Long = STARTUP_VIEWPORT_TIMEOUT_MILLIS,
) : ViewModel() {

    internal val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private var draftRevision: Long = 0L
    private var pendingEditorRevision: Long? = null
    private var editorBaseline: ConfigureAlarmDraft? = null
    private var editorPersistedId: String? = null
    private var editorNoticeSequence: Long = 0L

    private val storedViewportCenter: GeoCoordinates? = preferencesPort.peekLastKnownLocation()

    private val _uiState = MutableStateFlow(
        FjalarUiState(
            themeMode = preferencesPort.peekThemeMode() ?: ThemeMode.SYSTEM,
            mapViewportCenter = storedViewportCenter,
            mapViewportZoom = if (storedViewportCenter != null) 15.0 else 2.0,
            isInitialViewportReady = storedViewportCenter != null,
        ),
    )
    val uiState: StateFlow<FjalarUiState> = _uiState.asStateFlow()

    private var snoozeDeadlineJob: Job? = null
    private var fixExpiryJob: Job? = null
    private var searchJob: Job? = null
    private var searchSessionToken: Long = 0L
    private var unfilteredSearchResults: List<SearchResultPlace> = emptyList()

    private val mapTrackingMutex = kotlinx.coroutines.sync.Mutex()
    private var appForeground = false
    private var mapVisible = false
    private var pendingRecenter = false
    private var alarmsLoaded = false
    private var startupTimedOut = false
    private var locationKnownUnavailable = false

    /**
     * True while the camera still shows the position restored from disk and no live fix or user
     * gesture has claimed it yet. The first real fix then follows the user exactly once.
     */
    private var viewportFromStoredLocation = storedViewportCenter != null
    private var lastPersistedLocation: GeoCoordinates? = storedViewportCenter

    fun onAppForegroundChanged(foreground: Boolean) {
        appForeground = foreground
        updateSnoozeDeadline(_uiState.value.alarms)
        updateMapTracking()
    }

    private fun updateSnoozeDeadline(alarms: List<LocationAlarm>) {
        snoozeDeadlineJob?.cancel()
        if (!appForeground || (synchronizeAlarmsUseCase == null && syncSchedulesPort == null)) return
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
                if (availability == LocationAvailability.PERMISSION_DENIED) {
                    onLocationAccessChecked(canLocate = false)
                }
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
                alarmsLoaded = true
                resolveStartupReadiness()
            }
        }

        scope.launch {
            delay(startupViewportTimeoutMillis)
            startupTimedOut = true
            resolveStartupReadiness()
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

        scope.launch {
            recentDestinationsPort?.observeRecentDestinations()?.collect { recents ->
                _uiState.update { it.copy(recentDestinations = recents) }
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
        rememberLastKnownLocation(fix.coordinates)
        fixExpiryJob = scope.launch {
            // The boundary itself remains valid. Expire at the first millisecond after it.
            delay((30.seconds - age).inWholeMilliseconds + 1)
            updateLocationDisplay { it.copy(locationFix = null) }
        }
    }

    /**
     * Persists the newest fix only after meaningful movement, so startup has a real viewport
     * without writing storage on every GPS tick.
     */
    private fun rememberLastKnownLocation(coordinates: GeoCoordinates) {
        val previous = lastPersistedLocation
        val movedEnough = previous == null ||
            HaversineCalculator.distanceMeters(previous, coordinates) >= LAST_KNOWN_LOCATION_MOVE_METERS
        if (!movedEnough) return
        lastPersistedLocation = coordinates
        preferencesPort.rememberLastKnownLocation(coordinates)
    }

    private fun updateLocationDisplay(transform: (FjalarUiState) -> FjalarUiState) {
        val followUserLocation = pendingRecenter || viewportFromStoredLocation
        _uiState.update { current ->
            val updated = transform(current).withLocationDistances()
            if (updated.userLocation != null && (current.mapViewportCenter == null || followUserLocation)) {
                updated.copy(
                    mapViewportCenter = updated.userLocation,
                    mapViewportZoom = 15.0,
                    viewportRequestId = current.viewportRequestId + 1,
                )
            } else updated
        }
        if (followUserLocation && _uiState.value.userLocation != null) {
            clearViewportFollow()
            _uiState.value.userLocation?.let { mapRendererPort?.setViewport(it, zoom = 15.0) }
        }
        resolveStartupReadiness()
    }

    /** Stops following the user's position; a user gesture or a camera command claimed the map. */
    private fun clearViewportFollow() {
        pendingRecenter = false
        viewportFromStoredLocation = false
    }

    /**
     * Platform report of whether this app can obtain a location right now (foreground permission
     * granted and location services enabled). `false` lets startup finish without waiting for a fix.
     */
    fun onLocationAccessChecked(canLocate: Boolean) {
        locationKnownUnavailable = !canLocate
        resolveStartupReadiness()
    }

    /**
     * Opens the startup gate on the first of: a known viewport (restored from disk or a first fix),
     * a ringing alarm, known-unavailable location once alarms have loaded, or the startup timeout.
     * Without a fix, the viewport falls back to a selected or saved alarm before the neutral default.
     */
    private fun resolveStartupReadiness() {
        _uiState.update { current ->
            if (current.isInitialViewportReady) return@update current
            val viewportKnown = current.mapViewportCenter != null
            val ringing = current.activeRingingAlarm != null
            val noFixExpected = locationKnownUnavailable && alarmsLoaded
            if (!viewportKnown && !ringing && !noFixExpected && !startupTimedOut) return@update current
            withStartupFallbackViewport(current).copy(isInitialViewportReady = true)
        }
    }

    private fun withStartupFallbackViewport(state: FjalarUiState): FjalarUiState {
        if (state.mapViewportCenter != null || state.userLocation != null) return state
        val fallback = state.selectedCoordinates
            ?: state.nearestArmedAlarm?.coordinates
            ?: state.alarms.firstOrNull()?.coordinates
            ?: return state
        return state.copy(
            mapViewportCenter = fallback,
            mapViewportZoom = 15.0,
            viewportRequestId = state.viewportRequestId + 1L,
        )
    }

    fun onOpenSearch(context: PlaceSearchContext = PlaceSearchContext.BROWSE) {
        invalidateSearch()
        _uiState.update {
            it.copy(
                searchContext = context,
                isSearchSheetVisible = true,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                isSearchSettled = false,
                selectedCategoryFilter = null,
            )
        }
        onSearchQueryChanged("")
    }

    private fun invalidateSearch() {
        searchSessionToken++
        searchJob?.cancel()
        searchJob = null
        unfilteredSearchResults = emptyList()
    }

    fun onDismissSearch() {
        invalidateSearch()
        _uiState.update {
            it.copy(
                isSearchSheetVisible = false,
                searchContext = null,
                searchResults = emptyList(),
                isSearching = false,
                isSearchSettled = false,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        // A cancelled platform callback may still finish. Only the latest request
        // in the current search session can publish results or filter input.
        searchSessionToken++
        val requestToken = searchSessionToken
        searchJob?.cancel()
        searchJob = null
        if (query.trim().length < PlaceSearchAdapter.MIN_QUERY_LENGTH) {
            unfilteredSearchResults = emptyList()
            _uiState.update {
                it.copy(
                    searchQuery = query,
                    searchResults = emptyList(),
                    isSearching = false,
                    isSearchSettled = false,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearching = false,
                isSearchSettled = it.isSearchSettled &&
                    it.searchQuery.trim().length >= PlaceSearchAdapter.MIN_QUERY_LENGTH,
            )
        }
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (requestToken != searchSessionToken) return@launch
            _uiState.update { it.copy(isSearching = true) }
            val locale = _uiState.value.appLocale
            val proximity = _uiState.value.mapViewportCenter
                ?: _uiState.value.userLocation
                ?: _uiState.value.selectedCoordinates
            val places = searchPlacesPort?.searchPlaces(query, locale, proximity) ?: emptyList()
            if (requestToken != searchSessionToken) return@launch
            unfilteredSearchResults = places
            val filtered = applyCategoryFilter(places, _uiState.value.selectedCategoryFilter)
            _uiState.update {
                it.copy(
                    searchResults = filtered,
                    isSearching = false,
                    isSearchSettled = true,
                )
            }
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

    fun onBrowsePlaceSelected(place: SearchResultPlace) {
        if (_uiState.value.searchContext != PlaceSearchContext.BROWSE) return
        recordSearchSelection(place)
        clearViewportFollow()
        onDismissSearch()
        _uiState.update { current ->
            current.copy(
                isSearchSheetVisible = false,
                mapViewportCenter = place.coordinates,
                mapViewportZoom = 15.0,
                viewportRequestId = current.viewportRequestId + 1L,
            )
        }
        mapRendererPort?.setViewport(place.coordinates, zoom = 15.0)
    }

    /** Records a user-selected search result into on-device recent destinations without storing raw queries. */
    fun onPickerPlaceSelected(place: SearchResultPlace) {
        recordSearchSelection(place)
    }

    private fun recordSearchSelection(place: SearchResultPlace) {
        onConfirmDestination(
            coordinates = place.coordinates,
            label = place.name,
            category = place.category,
            destinationId = place.id,
        )
    }

    private fun applyCategoryFilter(
        places: List<SearchResultPlace>,
        category: PlaceCategory?,
    ): List<SearchResultPlace> {
        return places.filter { matchesCategoryFilter(it.category, category) }
    }

    fun onToggleTheme(currentlyDark: Boolean = false) {
        val currentMode = _uiState.value.themeMode
        val nextMode = when (currentMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        }
        scope.launch {
            preferencesPort.setThemeMode(nextMode)
        }
    }

    fun onToggleLocale() {
        onSelectLocale(if (_uiState.value.appLocale == AppLocale.EN) AppLocale.ID else AppLocale.EN)
    }

    fun onSelectLocale(locale: AppLocale) {
        if (locale == _uiState.value.appLocale) return
        scope.launch {
            preferencesPort.setLocale(locale)
            // Rebuild pending user-facing notifications with the same deadlines.
            try {
                if (synchronizeAlarmsUseCase != null) {
                    synchronizeAlarmsUseCase.invoke()
                } else {
                    syncSchedulesPort?.syncSchedules()
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                emitTransientFailureNotice(setOperationFailure = true)
            }
        }
    }

    fun onToggleAlarm(alarmId: String, enabled: Boolean) {
        scope.launch {
            if (toggleAlarmPort.toggleAlarm(alarmId, enabled).isSuccess) {
                refreshBackgroundSyncAfterMutation()
            } else {
                emitTransientFailureNotice()
            }
        }
    }

    fun onSkipToday(alarmId: String) {
        scope.launch {
            if (skipTodayPort.skipToday(alarmId).isSuccess) {
                refreshBackgroundSyncAfterMutation()
            } else {
                emitTransientFailureNotice()
            }
        }
    }

    fun onDeleteAlarm(alarmId: String, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isOperationPending) return
        val operationDraftRevision = draftRevision
        _uiState.update { it.copy(isOperationPending = true, hasOperationFailure = false) }
        scope.launch {
            val result = deleteAlarmPort?.deleteAlarm(alarmId)
            if (result?.isSuccess == true) {
                refreshBackgroundSyncAfterMutation()
            }
            val ownsDraft = operationDraftRevision == draftRevision
            val failed = result?.isSuccess != true
            val noticeId = if (failed) ++editorNoticeSequence else _uiState.value.pendingEditorNoticeId
            _uiState.update {
                it.copy(
                    isOperationPending = false,
                    hasOperationFailure = ownsDraft && failed,
                    pendingEditorNotice = if (failed) AlarmEditorNotice.UPDATE_FAILED else it.pendingEditorNotice,
                    pendingEditorNoticeId = noticeId,
                )
            }
            if (result?.isSuccess == true && ownsDraft) {
                if (editorPersistedId == alarmId || _uiState.value.activeDraft?.alarmId == alarmId) {
                    discardEditor(null)
                } else {
                    _uiState.update { it.copy(activeDraft = null, hasOperationFailure = false) }
                }
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
                if (result.isSuccess) {
                    refreshBackgroundSyncAfterMutation()
                }
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

    fun onStartConfiguringAlarm(alarmId: String?, defaultCoordinates: GeoCoordinates? = null) {
        onDismissSearch()
        // The editor owns the camera from here: a late first fix must not move it.
        clearViewportFollow()
        draftRevision++
        editorPersistedId = alarmId
        val draft = if (alarmId != null) {
            val alarm = _uiState.value.alarms.firstOrNull { it.id == alarmId }
            alarm?.let { ConfigureAlarmDraft.fromAlarm(it) }
                ?: ConfigureAlarmDraft(
                    alarmId = alarmId,
                    coordinates = defaultCoordinates,
                )
        } else {
            ConfigureAlarmDraft(
                alarmId = newDraftIdentity(),
                coordinates = defaultCoordinates,
            )
        }
        editorBaseline = draft
        _uiState.update { current ->
            current.copy(
                activeDraft = draft,
                hasOperationFailure = false,
                pendingEditorNotice = null,
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
        if (pendingEditorRevision == draftRevision) return
        draftRevision++
        _uiState.update { current ->
            current.copy(
                activeDraft = draft,
                selectedCoordinates = draft.coordinates,
                selectedRadius = validDraftRadius(draft) ?: current.selectedRadius,
                pendingEditorNotice = null,
            )
        }
        draft.coordinates?.let { coords ->
            mapRendererPort?.placePin(coords)
            validDraftRadius(draft)?.let { radius -> mapRendererPort?.showGeofence(coords, radius) }
        }
    }

    private fun validDraftRadius(draft: ConfigureAlarmDraft): TriggerRadius? =
        draft.radiusMeters.takeIf { it in TriggerRadius.MIN_METERS..TriggerRadius.MAX_METERS }
            ?.let(::TriggerRadius)

    fun onDraftCoordinatesChanged(coordinates: GeoCoordinates, label: String? = null) {
        if (pendingEditorRevision == draftRevision) return
        draftRevision++
        _uiState.update { current ->
            val trimmedLabel = label?.trim()?.takeIf { it.isNotBlank() }
            val existingLabel = trimmedLabel ?: current.activeDraft?.takeIf { it.coordinates == coordinates }?.addressLabel
            val baseDraft = current.activeDraft?.withNewCoordinates(coordinates)
                ?: ConfigureAlarmDraft(alarmId = newDraftIdentity(), coordinates = coordinates)
            val withAddress = if (existingLabel != null) {
                baseDraft.copy(addressLabel = existingLabel)
            } else {
                baseDraft
            }
            val updatedDraft = if (trimmedLabel != null && withAddress.label.isBlank()) {
                withAddress.copy(label = trimmedLabel)
            } else {
                withAddress
            }
            current.copy(
                activeDraft = updatedDraft,
                selectedCoordinates = coordinates,
                pendingEditorNotice = null,
            )
        }
        val radius = _uiState.value.activeDraft?.let { TriggerRadius(it.radiusMeters) } ?: TriggerRadius.DEFAULT
        mapRendererPort?.placePin(coordinates)
        mapRendererPort?.showGeofence(coordinates, radius)
    }

    fun onConfirmDestination(
        coordinates: GeoCoordinates,
        label: String? = null,
        category: PlaceCategory = PlaceCategory.GENERAL,
        destinationId: String? = null,
    ) {
        val name = label?.trim()?.takeIf { it.isNotBlank() } ?: return
        val id = destinationId?.takeIf { it.isNotBlank() }
            ?: "dest-${coordinates.latitude}-${coordinates.longitude}"
        val recent = RecentDestination(
            id = id,
            name = name,
            coordinates = coordinates,
            timestamp = clock.now(),
            category = category,
        )
        scope.launch {
            recentDestinationsPort?.recordDestination(recent)
        }
    }

    fun hasUnsavedEditorChanges(): Boolean {
        val draft = _uiState.value.activeDraft ?: return false
        return draft != editorBaseline
    }

    fun onDiscardAlarmEditor(onExit: () -> Unit = {}) {
        val current = _uiState.value
        if (current.isOperationPending || pendingEditorRevision == draftRevision) return
        discardEditor(null)
        onExit()
    }

    fun onSaveAlarmEditor(onExit: () -> Unit) {
        onExitAlarmEditor(discardInvalid = false, onExit = onExit)
    }

    /** Validate and persist editor changes. */
    fun onExitAlarmEditor(discardInvalid: Boolean = true, onExit: () -> Unit) {
        val current = _uiState.value
        val draft = current.activeDraft ?: return
        if (current.isOperationPending) return
        if (current.activeRingingAlarm != null ||
            current.alarms.any { it.state == AlarmState.RINGING }
        ) return

        val persistedId = editorPersistedId
        val existing = persistedId?.let { id -> current.alarms.firstOrNull { it.id == id } }
        if (persistedId != null && existing == null) {
            discardEditor(AlarmEditorNotice.ALARM_DELETED)
            onExit()
            return
        }
        val notice = draft.validationNotice()
        if (notice != null) {
            if (discardInvalid) {
                discardEditor(notice)
                onExit()
            } else {
                val noticeId = ++editorNoticeSequence
                _uiState.update {
                    it.copy(pendingEditorNotice = notice, pendingEditorNoticeId = noticeId)
                }
            }
            return
        }
        if (persistedId != null && draft == editorBaseline) {
            discardEditor(null)
            onExit()
            return
        }

        val alarm = try {
            draft.toLocationAlarm(existing)
        } catch (_: IllegalArgumentException) {
            val noticeId = ++editorNoticeSequence
            _uiState.update {
                it.copy(pendingEditorNotice = AlarmEditorNotice.UPDATE_FAILED, pendingEditorNoticeId = noticeId)
            }
            return
        }
        val operationDraftRevision = draftRevision
        pendingEditorRevision = operationDraftRevision
        _uiState.update {
            it.copy(isOperationPending = true, hasOperationFailure = false, pendingEditorNotice = null)
        }
        scope.launch {
            val result = saveAlarmPort.saveAlarmConfiguration(
                alarm = alarm,
                enabled = draft.enabledOverride,
                requireExisting = persistedId != null,
            )
            if (result.isSuccess) {
                refreshBackgroundSyncAfterMutation()
            }
            val ownsDraft = operationDraftRevision == draftRevision && _uiState.value.activeDraft == draft
            if (pendingEditorRevision == operationDraftRevision) pendingEditorRevision = null
            _uiState.update { state -> state.copy(isOperationPending = false) }
            if (!ownsDraft) return@launch
            when {
                result.isSuccess -> {
                    discardEditor(null)
                    onExit()
                }
                result.exceptionOrNull() is AlarmConfigurationMissingException -> {
                    discardEditor(AlarmEditorNotice.ALARM_DELETED)
                    onExit()
                }
                result.exceptionOrNull() is AlarmConfigurationRingingException -> Unit
                else -> {
                    // Persistence may have succeeded before a later side effect failed.
                    // Even a return to the opening values now requires a confirmed save.
                    editorBaseline = null
                    val noticeId = ++editorNoticeSequence
                    _uiState.update {
                        it.copy(
                            hasOperationFailure = true,
                            pendingEditorNotice = AlarmEditorNotice.UPDATE_FAILED,
                            pendingEditorNoticeId = noticeId,
                        )
                    }
                }
            }
        }
    }

    fun onEditorNoticeShown(noticeId: Long = _uiState.value.pendingEditorNoticeId) {
        _uiState.update { current ->
            if (current.pendingEditorNoticeId == noticeId) current.copy(pendingEditorNotice = null) else current
        }
    }

    private fun emitTransientFailureNotice(setOperationFailure: Boolean = false) {
        val noticeId = ++editorNoticeSequence
        _uiState.update { current ->
            current.copy(
                hasOperationFailure = if (setOperationFailure) true else current.hasOperationFailure,
                pendingEditorNotice = AlarmEditorNotice.UPDATE_FAILED,
                pendingEditorNoticeId = noticeId,
            )
        }
    }

    private fun discardEditor(notice: AlarmEditorNotice?) {
        draftRevision++
        editorBaseline = null
        editorPersistedId = null
        val noticeId = if (notice != null) ++editorNoticeSequence else _uiState.value.pendingEditorNoticeId
        _uiState.update { current ->
            current.copy(
                activeDraft = null,
                selectedCoordinates = null,
                selectedRadius = TriggerRadius.DEFAULT,
                hasOperationFailure = false,
                pendingEditorNotice = notice,
                pendingEditorNoticeId = noticeId,
            )
        }
        mapRendererPort?.clearOverlays()
    }

    fun onClearDraft() {
        draftRevision++
        editorBaseline = null
        editorPersistedId = null
        _uiState.update { it.copy(activeDraft = null, hasOperationFailure = false) }
    }

    /**
     * Re-synchronizes all saved alarms against current time to re-arm commute windows
     * and transition states. Invoked during foreground application entry and system events.
     */
    fun syncSchedules(): Job = scope.launch {
        if (synchronizeAlarmsUseCase != null) {
            synchronizeAlarmsUseCase.invoke()
        } else {
            syncSchedulesPort?.syncSchedules()
        }
    }

    private suspend fun refreshBackgroundSyncAfterMutation() {
        val useCase = synchronizeAlarmsUseCase ?: return
        try {
            val latestAlarms = observeAlarmsPort.observeAlarms().first()
            useCase.reconcileBackgroundSync(latestAlarms)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Post-mutation background refresh failure does not affect completed alarm mutations.
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    // Platform foreground UI request; does not start the alarm tracking port.
    var requestCurrentLocation: (() -> Unit)? = null

    /**
     * Keeps the area the user browsed on the main map so the destination picker can open there.
     * Deliberately leaves [FjalarUiState.viewportRequestId] alone: this is a report, not a camera command.
     */
    fun onMapViewportBrowsed(center: GeoCoordinates, zoom: Double) {
        clearViewportFollow()
        _uiState.update { it.copy(mapViewportCenter = center, mapViewportZoom = zoom) }
    }

    fun onRecenterMap() {
        pendingRecenter = true
        requestCurrentLocation?.invoke()
        recenterOnKnownLocation()
    }

    private fun recenterOnKnownLocation() {
        val current = _uiState.value
        val knownTarget = current.userLocation
            ?: current.selectedCoordinates
            ?: current.mapViewportCenter
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

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300L

        /** Longest the startup gate waits for a first fix before showing the map anyway. */
        const val STARTUP_VIEWPORT_TIMEOUT_MILLIS: Long = 2_500L

        /**
         * Minimum movement before the last known position is written again, so nearby GPS jitter
         * never turns into a storage write on every fix.
         */
        const val LAST_KNOWN_LOCATION_MOVE_METERS: Double = 100.0
    }
}
