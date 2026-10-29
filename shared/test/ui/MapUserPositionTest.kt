package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapUserPositionTest {
    @Test
    fun firstLoadAtWorldOverviewHasNeitherDotNorDestination() {
        val state = FjalarUiState()
        assertNull(state.mapUserPosition())
        assertNull(state.selectedCoordinates)
    }

    @Test
    fun inaccurateOrMismatchedFixCannotSupplyAnAccuracyHalo() {
        val fix = LocationFix(GeoCoordinates(51.5, -0.1), 1_001f, kotlin.time.Instant.fromEpochMilliseconds(0))
        val state = FjalarUiState(locationFix = fix, userLocation = fix.coordinates)
        assertNull(state.mapUserPosition())
        assertNull(state.copy(locationFix = fix.copy(horizontalAccuracyMeters = 20f), userLocation = GeoCoordinates(0.0, 0.0)).mapUserPosition())
    }

    @Test
    fun retainedRawFixWithoutEligibleUserLocationNeverRendersADot() {
        val fix = LocationFix(GeoCoordinates(51.5, -0.1), 20f, kotlin.time.Instant.fromEpochMilliseconds(0))
        assertNull(FjalarUiState(locationFix = fix, userLocation = null).mapUserPosition())
    }

    @Test
    fun eligibleDotUsesMatchingFixAccuracyAndLeavesDestinationAlone() {
        val fix = LocationFix(GeoCoordinates(51.5, -0.1), 500f, kotlin.time.Instant.fromEpochMilliseconds(0))
        val destination = GeoCoordinates(-33.86, 151.2)
        val state = FjalarUiState(locationFix = fix, userLocation = fix.coordinates, selectedCoordinates = destination)
        assertEquals(fix.coordinates, state.mapUserPosition()?.coordinates)
        assertEquals(500.0, state.mapUserPosition()?.accuracyMeters)
        assertEquals(destination, state.selectedCoordinates)
        assertNull(state.copy(userLocation = null).mapUserPosition())
    }
}
