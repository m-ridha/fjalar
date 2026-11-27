package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SearchPlacesSheetTest {

    private fun sampleRecent(
        id: String = "dest-1",
        name: String = "Gambir",
        latitude: Double = -6.1767,
        longitude: Double = 106.8306,
        category: PlaceCategory = PlaceCategory.TRAIN_STATION,
    ): RecentDestination = RecentDestination(
        id = id,
        name = name,
        coordinates = GeoCoordinates(latitude, longitude),
        timestamp = Instant.fromEpochMilliseconds(1731000000000L),
        category = category,
    )

    @Test
    fun matchesCategoryFilter_unifiesRailAndMetroStationsOnly() {
        assertTrue(matchesCategoryFilter(PlaceCategory.TRAIN_STATION, null))
        assertTrue(matchesCategoryFilter(PlaceCategory.METRO_STATION, null))
        assertTrue(matchesCategoryFilter(PlaceCategory.BUS_STOP, null))

        assertTrue(matchesCategoryFilter(PlaceCategory.TRAIN_STATION, PlaceCategory.TRAIN_STATION))
        assertTrue(matchesCategoryFilter(PlaceCategory.METRO_STATION, PlaceCategory.TRAIN_STATION))
        assertTrue(matchesCategoryFilter(PlaceCategory.TRAIN_STATION, PlaceCategory.METRO_STATION))
        assertTrue(matchesCategoryFilter(PlaceCategory.METRO_STATION, PlaceCategory.METRO_STATION))
        assertFalse(matchesCategoryFilter(PlaceCategory.BUS_STOP, PlaceCategory.TRAIN_STATION))
        assertFalse(matchesCategoryFilter(PlaceCategory.TRANSIT_STOP, PlaceCategory.TRAIN_STATION))
        assertFalse(matchesCategoryFilter(PlaceCategory.LANDMARK, PlaceCategory.TRAIN_STATION))
        assertFalse(matchesCategoryFilter(PlaceCategory.GENERAL, PlaceCategory.TRAIN_STATION))

        assertTrue(matchesCategoryFilter(PlaceCategory.BUS_STOP, PlaceCategory.BUS_STOP))
        assertFalse(matchesCategoryFilter(PlaceCategory.TRAIN_STATION, PlaceCategory.BUS_STOP))
        assertTrue(matchesCategoryFilter(PlaceCategory.TRANSIT_STOP, PlaceCategory.TRANSIT_STOP))
        assertFalse(matchesCategoryFilter(PlaceCategory.BUS_STOP, PlaceCategory.TRANSIT_STOP))
    }

    @Test
    fun shouldShowQuickPicks_supportsShortQueriesAndBridgesUntilResultsSettle() {
        val recents = listOf(sampleRecent())
        val searchResult = SearchResultPlace(
            id = "krl_gmb",
            name = "Stasiun Gambir",
            coordinates = GeoCoordinates(-6.1767, 106.8306),
            category = PlaceCategory.TRAIN_STATION,
        )

        assertTrue(shouldShowQuickPicks("", recents))
        assertTrue(shouldShowQuickPicks("   ", recents))
        assertTrue(shouldShowQuickPicks("Ga", recents))
        assertTrue(shouldShowQuickPicks("Gam", recents, results = emptyList(), isSearchSettled = false))
        assertFalse(shouldShowQuickPicks("Gam", recents, results = emptyList(), isSearchSettled = true))
        assertFalse(shouldShowQuickPicks("Gam", recents, results = listOf(searchResult), isSearchSettled = false))
        assertFalse(shouldShowQuickPicks("Gam", recents, results = listOf(searchResult), isSearchSettled = true))
        assertFalse(shouldShowQuickPicks("", emptyList()))
        assertFalse(shouldShowQuickPicks("Ga", emptyList()))
    }

    @Test
    fun shouldShowEmptySearchResults_requiresAtLeastThreeNonWhitespaceCharsAndSettledSearch() {
        val searchResult = SearchResultPlace(
            id = "krl_mri",
            name = "Stasiun Manggarai",
            coordinates = GeoCoordinates(-6.20988, 106.85016),
            category = PlaceCategory.TRAIN_STATION,
        )

        assertFalse(shouldShowEmptySearchResults(emptyList(), "", isLoading = false, isSearchSettled = true))
        assertFalse(shouldShowEmptySearchResults(emptyList(), "M", isLoading = false, isSearchSettled = true))
        assertFalse(shouldShowEmptySearchResults(emptyList(), "Ma", isLoading = false, isSearchSettled = true))
        assertFalse(shouldShowEmptySearchResults(emptyList(), " Ma ", isLoading = false, isSearchSettled = true))
        assertFalse(shouldShowEmptySearchResults(emptyList(), "Man", isLoading = true, isSearchSettled = false))
        assertFalse(shouldShowEmptySearchResults(emptyList(), "Man", isLoading = false, isSearchSettled = false))
        assertFalse(shouldShowEmptySearchResults(listOf(searchResult), "Man", isLoading = false, isSearchSettled = true))
        assertTrue(shouldShowEmptySearchResults(emptyList(), "Man", isLoading = false, isSearchSettled = true))
    }

    @Test
    fun quickPickContentDescription_formatsCorrectlyInBothLocales() {
        val recent = sampleRecent(name = "Gambir")

        assertEquals(
            "Gambir, recent destination",
            quickPickContentDescription(recent, AppLocale.EN),
        )
        assertEquals(
            "Gambir, tujuan terakhir",
            quickPickContentDescription(recent, AppLocale.ID),
        )
    }

    @Test
    fun categoryEmojiMapsAllPlaceCategoriesCorrectly() {
        assertEquals("🚆", categoryEmoji(PlaceCategory.TRAIN_STATION))
        assertEquals("🚇", categoryEmoji(PlaceCategory.METRO_STATION))
        assertEquals("🚌", categoryEmoji(PlaceCategory.BUS_STOP))
        assertEquals("🚏", categoryEmoji(PlaceCategory.TRANSIT_STOP))
        assertEquals("🏛️", categoryEmoji(PlaceCategory.LANDMARK))
        assertEquals("📍", categoryEmoji(PlaceCategory.GENERAL))
    }

    @Test
    fun filterRecentDestinations_appliesUnifiedRailFilterAndLiveQuery() {
        val trainRecent = sampleRecent(id = "train-1", name = "Gambir", category = PlaceCategory.TRAIN_STATION)
        val metroRecent = sampleRecent(id = "metro-1", name = "Bundaran HI", category = PlaceCategory.METRO_STATION)
        val busRecent = sampleRecent(id = "bus-1", name = "Halte Harmoni", category = PlaceCategory.BUS_STOP)
        val recents = listOf(trainRecent, metroRecent, busRecent)

        assertEquals(recents, filterRecentDestinations(recents, null))
        assertEquals(listOf(trainRecent, metroRecent), filterRecentDestinations(recents, PlaceCategory.TRAIN_STATION))
        assertEquals(listOf(trainRecent, metroRecent), filterRecentDestinations(recents, PlaceCategory.METRO_STATION))
        assertEquals(listOf(busRecent), filterRecentDestinations(recents, PlaceCategory.BUS_STOP))

        // Live < 3 char filtering over recent destinations
        assertEquals(listOf(trainRecent), filterRecentDestinations(recents, null, "ga"))
        assertEquals(listOf(metroRecent), filterRecentDestinations(recents, PlaceCategory.TRAIN_STATION, "hi"))
        assertEquals(listOf(busRecent), filterRecentDestinations(recents, PlaceCategory.BUS_STOP, "ha"))
        assertTrue(filterRecentDestinations(recents, PlaceCategory.TRAIN_STATION, "ha").isEmpty())

        val nonMatching = filterRecentDestinations(recents, PlaceCategory.TRANSIT_STOP)
        assertTrue(nonMatching.isEmpty())
        assertFalse(shouldShowQuickPicks("", nonMatching))
        assertTrue(shouldShowEmptyFilteredRecents("", recents, nonMatching, PlaceCategory.TRANSIT_STOP))
        assertFalse(shouldShowEmptyFilteredRecents("", recents, recents, null))
        assertFalse(shouldShowEmptyFilteredRecents("", emptyList(), emptyList(), PlaceCategory.TRANSIT_STOP))
        assertFalse(shouldShowEmptyFilteredRecents("MRT", recents, nonMatching, PlaceCategory.TRANSIT_STOP))
    }

    @Test
    fun distanceFormattingForSearchResultMatchesHaversineCalculation() {
        val userLocation = GeoCoordinates(latitude = -6.2088, longitude = 106.8456) // Manggarai
        val place = SearchResultPlace(
            id = "station-sudirman",
            name = "Sudirman",
            coordinates = GeoCoordinates(latitude = -6.2024, longitude = 106.8236),
            category = PlaceCategory.TRAIN_STATION,
            detail = "Commuter Line • Cikarang Loop",
        )

        val distanceMeters = HaversineCalculator.calculateDistanceMeters(
            userLocation,
            place.coordinates,
        ).roundToInt()

        // Distance between Manggarai and Sudirman is ~2.5 km
        val formatted = formatDistance(distanceMeters, AppLocale.EN)
        assertEquals("2.5 km", formatted)
    }
}
