package com.muhammadridha.fjalar.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State representation for the standalone map destination picker and quick-pick search.
 */
data class MapPickerUiState(
    val selection: MapPickerSelection = MapPickerSelection(null),
    val selectedPlaceName: String? = null,
    val selectedPlaceCategory: PlaceCategory = PlaceCategory.GENERAL,
    val recentDestinations: List<RecentDestination> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResultPlace> = emptyList(),
    val selectedCategoryFilter: PlaceCategory? = null,
    val isSearching: Boolean = false,
    val isSearchVisible: Boolean = false,
    val cameraTarget: GeoCoordinates = GeoCoordinates(0.0, 0.0),
    val cameraZoom: Double = 15.0,
    val preserveZoom: Boolean = true,
    val viewportRequestId: Long = 0L,
    val mapBearing: Double = 0.0,
    val northRequestId: Long = 0L,
) {
    val canConfirm: Boolean get() = selection.coordinates != null
}

/**
 * ViewModel managing destination selection, camera framing, recent destinations quick-pick,
 * and search autocomplete in the map picker.
 */
class MapPickerViewModel(
    private val recentDestinationsPort: RecentDestinationsPort,
    private val searchPlacesPort: SearchPlacesPort? = null,
    coroutineScope: CoroutineScope? = null,
    private val clock: Clock = Clock.System,
    initialCoordinates: GeoCoordinates? = null,
    hasInitialSelection: Boolean = false,
) : ViewModel() {

    internal val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private var searchJob: Job? = null
    private var searchSessionToken: Long = 0L
    private var unfilteredSearchResults: List<SearchResultPlace> = emptyList()
    private var selectedPlaceId: String? = null

    private val _uiState = MutableStateFlow(
        MapPickerUiState(
            selection = MapPickerSelection(if (hasInitialSelection) initialCoordinates else null),
            cameraTarget = initialCoordinates ?: GeoCoordinates(0.0, 0.0),
        )
    )
    val uiState: StateFlow<MapPickerUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            recentDestinationsPort.observeRecentDestinations().collect { recents ->
                _uiState.update { it.copy(recentDestinations = recents) }
            }
        }
    }

    fun selectCoordinates(
        coords: GeoCoordinates,
        name: String? = null,
        category: PlaceCategory = PlaceCategory.GENERAL,
    ) {
        selectedPlaceId = null
        _uiState.update { current ->
            current.copy(
                selection = current.selection.select(coords),
                selectedPlaceName = name,
                selectedPlaceCategory = category,
                cameraTarget = coords,
                viewportRequestId = current.viewportRequestId + 1L,
                preserveZoom = true,
            )
        }
    }

    fun selectPlace(place: SearchResultPlace) {
        selectedPlaceId = place.id
        invalidateSearch()
        _uiState.update { current ->
            current.copy(
                selection = current.selection.select(place),
                selectedPlaceName = place.name,
                selectedPlaceCategory = place.category,
                cameraTarget = place.coordinates,
                cameraZoom = 15.0,
                preserveZoom = false,
                viewportRequestId = current.viewportRequestId + 1L,
                isSearchVisible = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
            )
        }
    }

    fun selectRecentDestination(destination: RecentDestination) {
        selectedPlaceId = destination.id
        invalidateSearch()
        _uiState.update { current ->
            current.copy(
                selection = current.selection.select(destination.coordinates),
                selectedPlaceName = destination.name,
                selectedPlaceCategory = destination.category,
                cameraTarget = destination.coordinates,
                cameraZoom = 15.0,
                preserveZoom = false,
                viewportRequestId = current.viewportRequestId + 1L,
                isSearchVisible = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
            )
        }
    }

    fun onCameraCenterChanged(center: GeoCoordinates, isUserGesture: Boolean = false) {
        _uiState.update { current ->
            val updatedSelection = current.selection.onCameraCenterChanged(center, isUserGesture)
            if (updatedSelection != current.selection) {
                selectedPlaceId = null
                current.copy(
                    selection = updatedSelection,
                    cameraTarget = center,
                    selectedPlaceName = null,
                    selectedPlaceCategory = PlaceCategory.GENERAL,
                )
            } else {
                current.copy(cameraTarget = center)
            }
        }
    }

    fun onMapBearingChanged(bearing: Double) {
        _uiState.update { it.copy(mapBearing = bearing) }
    }

    fun resetNorth() {
        _uiState.update { it.copy(northRequestId = it.northRequestId + 1L) }
    }

    fun recenterOnUser(userCoords: GeoCoordinates?) {
        val fix = userCoords
        val currentSelection = _uiState.value.selection.coordinates
        val target = fix ?: currentSelection ?: GeoCoordinates(0.0, 0.0)
        val zoom = if (fix != null || currentSelection != null) 15.0 else 2.0
        _uiState.update { current ->
            current.copy(
                cameraTarget = target,
                cameraZoom = zoom,
                preserveZoom = false,
                viewportRequestId = current.viewportRequestId + 1L,
            )
        }
    }

    fun openSearch() {
        invalidateSearch()
        _uiState.update {
            it.copy(
                isSearchVisible = true,
                searchQuery = "",
                searchResults = emptyList(),
                selectedCategoryFilter = null,
                isSearching = false,
            )
        }
    }

    fun dismissSearch() {
        invalidateSearch()
        _uiState.update {
            it.copy(
                isSearchVisible = false,
                searchQuery = "",
                searchResults = emptyList(),
                selectedCategoryFilter = null,
                isSearching = false,
            )
        }
    }

    fun onSearchQueryChanged(
        query: String,
        locale: AppLocale = AppLocale.EN,
        proximity: GeoCoordinates? = null,
    ) {
        searchSessionToken++
        val requestToken = searchSessionToken
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        searchJob = scope.launch {
            if (query.isNotBlank()) {
                delay(SEARCH_DEBOUNCE_MILLIS)
            }
            val effectiveProximity = proximity ?: _uiState.value.selection.coordinates ?: _uiState.value.cameraTarget
            val places = searchPlacesPort?.searchPlaces(query, locale, effectiveProximity) ?: emptyList()
            if (requestToken != searchSessionToken) return@launch
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

    fun confirmSelection(onConfirmed: (GeoCoordinates) -> Unit = {}): GeoCoordinates? {
        val coordinates = _uiState.value.selection.coordinates ?: return null
        val placeName = _uiState.value.selectedPlaceName?.takeIf { it.isNotBlank() } ?: "Location"
        val category = _uiState.value.selectedPlaceCategory
        val id = selectedPlaceId?.takeIf { it.isNotBlank() }
            ?: "dest-${coordinates.latitude}-${coordinates.longitude}"

        val recent = RecentDestination(
            id = id,
            name = placeName,
            coordinates = coordinates,
            timestamp = clock.now(),
            category = category,
        )
        scope.launch {
            recentDestinationsPort.recordDestination(recent)
        }
        onConfirmed(coordinates)
        return coordinates
    }

    fun clearRecentDestinations() {
        scope.launch {
            recentDestinationsPort.clearRecentDestinations()
        }
    }

    private fun invalidateSearch() {
        searchSessionToken++
        searchJob?.cancel()
        searchJob = null
        unfilteredSearchResults = emptyList()
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

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300L
    }
}
