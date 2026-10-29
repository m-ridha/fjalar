package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPlacesSheetTest {

    @Test
    fun categoryEmojiMapsAllPlaceCategoriesCorrectly() {
        assertEquals("🚆", categoryEmoji(PlaceCategory.TRAIN_STATION))
        assertEquals("🚇", categoryEmoji(PlaceCategory.METRO_STATION))
        assertEquals("🚏", categoryEmoji(PlaceCategory.TRANSIT_STOP))
        assertEquals("🏛️", categoryEmoji(PlaceCategory.LANDMARK))
        assertEquals("📍", categoryEmoji(PlaceCategory.GENERAL))
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
