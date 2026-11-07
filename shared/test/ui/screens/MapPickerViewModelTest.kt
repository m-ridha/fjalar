package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentRecentDestinationsAdapter
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.fakes.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MapPickerViewModelTest {

    private class FakeSearchPlacesPort(
        var places: List<SearchResultPlace> = emptyList(),
    ) : SearchPlacesPort {
        var callCount: Int = 0
        var lastQuery: String? = null
        var lastProximity: GeoCoordinates? = null

        override suspend fun searchPlaces(
            query: String,
            locale: AppLocale,
            proximity: GeoCoordinates?,
        ): List<SearchResultPlace> {
            callCount++
            lastQuery = query
            lastProximity = proximity
            return places.filter { it.matchesQuery(query) }
        }
    }

    private val fixedInstant = Instant.fromEpochMilliseconds(1731000000000L)

    private fun sampleRecent(
        id: String = "dest-1",
        name: String = "Manggarai Station",
        latitude: Double = -6.2098,
        longitude: Double = 106.8501,
        epochMillis: Long = 1731000000000L,
        category: PlaceCategory = PlaceCategory.TRAIN_STATION,
    ): RecentDestination = RecentDestination(
        id = id,
        name = name,
        coordinates = GeoCoordinates(latitude, longitude),
        timestamp = Instant.fromEpochMilliseconds(epochMillis),
        category = category,
    )

    private fun samplePlace(
        id: String = "place-mri",
        name: String = "Manggarai Station",
        latitude: Double = -6.2098,
        longitude: Double = 106.8501,
        category: PlaceCategory = PlaceCategory.TRAIN_STATION,
    ): SearchResultPlace = SearchResultPlace(
        id = id,
        name = name,
        coordinates = GeoCoordinates(latitude, longitude),
        category = category,
    )

    @Test
    fun initialState_withoutInitialSelection_hasNullCoordinatesAndFramesDefault() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val initialCoords = GeoCoordinates(-6.2000, 106.8166)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
                initialCoordinates = initialCoords,
                hasInitialSelection = false,
            )

            val state = viewModel.uiState.value
            assertNull(state.selection.coordinates)
            assertFalse(state.canConfirm)
            assertEquals(initialCoords, state.cameraTarget)
            assertEquals(15.0, state.cameraZoom)
            assertTrue(state.recentDestinations.isEmpty())
            assertFalse(state.isSearchVisible)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun initialState_withInitialSelection_hasSelectionCoordinatesAndCanConfirm() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val initialCoords = GeoCoordinates(-6.2000, 106.8166)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
                initialCoordinates = initialCoords,
                hasInitialSelection = true,
            )

            val state = viewModel.uiState.value
            assertEquals(initialCoords, state.selection.coordinates)
            assertTrue(state.canConfirm)
            assertEquals(initialCoords, state.cameraTarget)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun initialState_collectsExistingRecentDestinationsFromPort() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val sample = sampleRecent(id = "preloaded-1", name = "Gambir")
            recentPort.recordDestination(sample)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
            )

            val state = viewModel.uiState.value
            assertEquals(1, state.recentDestinations.size)
            assertEquals("Gambir", state.recentDestinations.first().name)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun selectingPlace_updatesSelectionCoordinatesNameCategoryAndCamera() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
            )

            val place = samplePlace(
                id = "place-sudirman",
                name = "Sudirman Station",
                latitude = -6.2024,
                longitude = 106.8236,
                category = PlaceCategory.TRAIN_STATION,
            )

            viewModel.openSearch()
            assertTrue(viewModel.uiState.value.isSearchVisible)

            viewModel.selectPlace(place)

            val state = viewModel.uiState.value
            assertEquals(place.coordinates, state.selection.coordinates)
            assertEquals("Sudirman Station", state.selectedPlaceName)
            assertEquals(PlaceCategory.TRAIN_STATION, state.selectedPlaceCategory)
            assertEquals(place.coordinates, state.cameraTarget)
            assertEquals(15.0, state.cameraZoom)
            assertFalse(state.preserveZoom)
            assertEquals(1L, state.viewportRequestId)
            assertFalse(state.isSearchVisible)
            assertTrue(state.canConfirm)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun selectingRecentDestination_updatesSelectionCoordinatesCategoryAndCentersCamera() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val destination = sampleRecent(
                id = "dest-tanah-abang",
                name = "Tanah Abang Station",
                latitude = -6.1856,
                longitude = 106.8110,
                category = PlaceCategory.TRAIN_STATION,
            )
            recentPort.recordDestination(destination)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
            )

            viewModel.selectRecentDestination(destination)

            val state = viewModel.uiState.value
            assertEquals(destination.coordinates, state.selection.coordinates)
            assertEquals("Tanah Abang Station", state.selectedPlaceName)
            assertEquals(PlaceCategory.TRAIN_STATION, state.selectedPlaceCategory)
            assertEquals(destination.coordinates, state.cameraTarget)
            assertEquals(15.0, state.cameraZoom)
            assertFalse(state.preserveZoom)
            assertEquals(1L, state.viewportRequestId)
            assertTrue(state.canConfirm)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun confirmingSelection_recordsDestinationWithCurrentTimestampAndPlaceDetails() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val clock = FakeClock(fixedInstant)
            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
                clock = clock,
            )

            // When no selection exists, confirmSelection returns null
            assertNull(viewModel.confirmSelection())

            // Select a place
            val place = samplePlace(
                id = "place-mri",
                name = "Manggarai Station",
                latitude = -6.2098,
                longitude = 106.8501,
                category = PlaceCategory.TRAIN_STATION,
            )
            viewModel.selectPlace(place)

            var confirmedCoords: GeoCoordinates? = null
            val returnedCoords = viewModel.confirmSelection { confirmedCoords = it }

            assertEquals(place.coordinates, returnedCoords)
            assertEquals(place.coordinates, confirmedCoords)

            advanceUntilIdle()

            val recorded = recentPort.getRecentDestinations()
            assertEquals(1, recorded.size)
            val recent = recorded.first()
            assertEquals("place-mri", recent.id)
            assertEquals("Manggarai Station", recent.name)
            assertEquals(place.coordinates, recent.coordinates)
            assertEquals(fixedInstant, recent.timestamp)
            assertEquals(PlaceCategory.TRAIN_STATION, recent.category)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun confirmingSelection_fallbackToCoordinatesWhenNoPlaceSelected() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val clock = FakeClock(fixedInstant)
            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
                clock = clock,
            )

            val customCoords = GeoCoordinates(-6.1234, 106.5678)
            viewModel.selectCoordinates(customCoords)

            val returnedCoords = viewModel.confirmSelection()
            assertEquals(customCoords, returnedCoords)

            advanceUntilIdle()

            val recorded = recentPort.getRecentDestinations()
            assertEquals(1, recorded.size)
            val recent = recorded.first()
            assertEquals("dest--6.1234-106.5678", recent.id)
            assertEquals("${customCoords.latitude}, ${customCoords.longitude}", recent.name)
            assertEquals(customCoords, recent.coordinates)
            assertEquals(fixedInstant, recent.timestamp)
            assertEquals(PlaceCategory.GENERAL, recent.category)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun confirmingMultipleDestinations_maintainsLruOrderingAndMaxCapacity() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            var currentTime = fixedInstant
            val clock = FakeClock(currentTime)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
                clock = clock,
            )

            // 6 distinct places (spaced far enough to avoid proximity deduplication)
            val places = (1..6).map { index ->
                samplePlace(
                    id = "station-$index",
                    name = "Station $index",
                    latitude = -6.0 + (index * 0.1),
                    longitude = 106.0 + (index * 0.1),
                    category = PlaceCategory.TRAIN_STATION,
                )
            }

            for (place in places) {
                currentTime += 1.minutes
                clock.now = currentTime
                viewModel.selectPlace(place)
                viewModel.confirmSelection()
                advanceUntilIdle()
            }

            val recents = recentPort.getRecentDestinations()
            assertEquals(5, recents.size, "Should be capped at MAX_CAPACITY (5)")

            // LRU order: Station 6 (most recent) at index 0, Station 2 at index 4; Station 1 evicted
            assertEquals("Station 6", recents[0].name)
            assertEquals("Station 5", recents[1].name)
            assertEquals("Station 4", recents[2].name)
            assertEquals("Station 3", recents[3].name)
            assertEquals("Station 2", recents[4].name)

            // UiState should also match
            val stateRecents = viewModel.uiState.value.recentDestinations
            assertEquals(5, stateRecents.size)
            assertEquals("Station 6", stateRecents[0].name)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun searchQuery_debouncesQueriesPortAndFiltersCategories() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val mri = samplePlace(
                id = "p-1",
                name = "Manggarai Station",
                latitude = -6.2098,
                longitude = 106.8501,
                category = PlaceCategory.TRAIN_STATION,
            )
            val landmark = samplePlace(
                id = "p-2",
                name = "Manggarai Landmark",
                latitude = -6.2090,
                longitude = 106.8510,
                category = PlaceCategory.LANDMARK,
            )
            val searchPort = FakeSearchPlacesPort(places = listOf(mri, landmark))
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                searchPlacesPort = searchPort,
                coroutineScope = vmScope,
            )

            viewModel.onSearchQueryChanged("Manggarai")
            assertTrue(viewModel.uiState.value.isSearching)
            assertEquals(0, searchPort.callCount)

            // Advance before debounce threshold: still waiting
            advanceTimeBy(150)
            assertEquals(0, searchPort.callCount)

            // Advance past debounce (300ms total)
            advanceTimeBy(160)
            advanceUntilIdle()

            assertEquals(1, searchPort.callCount)
            assertFalse(viewModel.uiState.value.isSearching)
            assertEquals(2, viewModel.uiState.value.searchResults.size)

            // Apply category filter
            viewModel.onSelectCategoryFilter(PlaceCategory.TRAIN_STATION)
            assertEquals(PlaceCategory.TRAIN_STATION, viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("Manggarai Station", viewModel.uiState.value.searchResults.first().name)

            // Clear category filter
            viewModel.onSelectCategoryFilter(null)
            assertNull(viewModel.uiState.value.selectedCategoryFilter)
            assertEquals(2, viewModel.uiState.value.searchResults.size)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun searchQuery_cancellationGuardDiscardsOutdatedRequests() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val slowPort = object : SearchPlacesPort {
                override suspend fun searchPlaces(
                    query: String,
                    locale: AppLocale,
                    proximity: GeoCoordinates?,
                ): List<SearchResultPlace> {
                    kotlinx.coroutines.delay(200)
                    return listOf(
                        samplePlace(id = query, name = "Result for $query")
                    )
                }
            }
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                searchPlacesPort = slowPort,
                coroutineScope = vmScope,
            )

            viewModel.onSearchQueryChanged("first")
            advanceTimeBy(300) // triggers launch and starts delay(200)

            // Fire second query before first finishes
            viewModel.onSearchQueryChanged("second")
            advanceTimeBy(300) // triggers launch for second
            advanceTimeBy(250) // finishes second
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("Result for second", viewModel.uiState.value.searchResults.first().name)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun dismissSearch_resetsQueryAndHidesSearchSheet() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
            )

            viewModel.openSearch()
            assertTrue(viewModel.uiState.value.isSearchVisible)

            viewModel.onSearchQueryChanged("test")
            advanceTimeBy(350)
            advanceUntilIdle()

            viewModel.dismissSearch()

            val state = viewModel.uiState.value
            assertFalse(state.isSearchVisible)
            assertEquals("", state.searchQuery)
            assertTrue(state.searchResults.isEmpty())
            assertFalse(state.isSearching)
            assertNull(state.selectedCategoryFilter)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun clearRecentDestinations_callsPortAndUpdatesStateToEmptyList() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            recentPort.recordDestination(sampleRecent(id = "dest-1", name = "Station 1", latitude = -6.2098, longitude = 106.8501))
            recentPort.recordDestination(sampleRecent(id = "dest-2", name = "Station 2", latitude = -6.1856, longitude = 106.8110))

            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
            )

            assertEquals(2, viewModel.uiState.value.recentDestinations.size)

            viewModel.clearRecentDestinations()
            advanceUntilIdle()

            assertTrue(recentPort.getRecentDestinations().isEmpty())
            assertTrue(viewModel.uiState.value.recentDestinations.isEmpty())
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun cameraControls_gestureBearingAndNorthReset() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val storage = InMemoryKeyValueStorage()
            val recentPort = PersistentRecentDestinationsAdapter(storage)
            val viewModel = MapPickerViewModel(
                recentDestinationsPort = recentPort,
                coroutineScope = vmScope,
            )

            // Bearing
            viewModel.onMapBearingChanged(45.0)
            assertEquals(45.0, viewModel.uiState.value.mapBearing)

            // Reset north
            assertEquals(0L, viewModel.uiState.value.northRequestId)
            viewModel.resetNorth()
            assertEquals(1L, viewModel.uiState.value.northRequestId)

            // Camera center changed via programmatic / non-gesture
            val nonGestureCenter = GeoCoordinates(-6.1, 106.1)
            viewModel.onCameraCenterChanged(nonGestureCenter, isUserGesture = false)
            assertEquals(nonGestureCenter, viewModel.uiState.value.cameraTarget)
            assertNull(viewModel.uiState.value.selection.coordinates)

            // Camera center changed via user gesture updates selection
            val gestureCenter = GeoCoordinates(-6.2, 106.2)
            viewModel.onCameraCenterChanged(gestureCenter, isUserGesture = true)
            assertEquals(gestureCenter, viewModel.uiState.value.cameraTarget)
            assertEquals(gestureCenter, viewModel.uiState.value.selection.coordinates)

            // Recenter on user
            val userCoords = GeoCoordinates(-6.3, 106.3)
            viewModel.recenterOnUser(userCoords)
            assertEquals(userCoords, viewModel.uiState.value.cameraTarget)
            assertEquals(15.0, viewModel.uiState.value.cameraZoom)
            assertFalse(viewModel.uiState.value.preserveZoom)
        } finally {
            vmScope.cancel()
        }
    }
}
