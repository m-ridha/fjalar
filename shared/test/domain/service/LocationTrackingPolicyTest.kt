package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.JAKARTA
import com.muhammadridha.fjalar.domain.MANGGARAI
import com.muhammadridha.fjalar.domain.SUDIRMAN
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TrackingTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationTrackingPolicyTest {

    @Test
    fun evaluateTier_initialTierEvaluation() {
        assertEquals(TrackingTier.FAR, LocationTrackingPolicy.evaluateTier(20_000.0, TrackingTier.MEDIUM))
        assertEquals(TrackingTier.MEDIUM, LocationTrackingPolicy.evaluateTier(3_500.0, TrackingTier.MEDIUM))
        assertEquals(TrackingTier.APPROACH, LocationTrackingPolicy.evaluateTier(1_200.0, TrackingTier.MEDIUM))
    }

    @Test
    fun evaluateTier_steppingUpToFinerTiers() {
        // Commuter traveling inward from Far (>5km) to Medium (<=5km)
        assertEquals(TrackingTier.MEDIUM, LocationTrackingPolicy.evaluateTier(5_000.0, TrackingTier.FAR))
        assertEquals(TrackingTier.MEDIUM, LocationTrackingPolicy.evaluateTier(4_999.0, TrackingTier.FAR))

        // Commuter traveling inward from Medium (2km-5km) to Approach (<2km)
        assertEquals(TrackingTier.APPROACH, LocationTrackingPolicy.evaluateTier(1_999.0, TrackingTier.MEDIUM))
        assertEquals(TrackingTier.APPROACH, LocationTrackingPolicy.evaluateTier(500.0, TrackingTier.MEDIUM))
    }

    @Test
    fun evaluateTier_hysteresisPreventsPingPongingAtBoundaries() {
        // Approach -> Medium requires distance > 2,000m + 100m = 2,100m
        assertEquals(TrackingTier.APPROACH, LocationTrackingPolicy.evaluateTier(2_050.0, TrackingTier.APPROACH))
        assertEquals(TrackingTier.APPROACH, LocationTrackingPolicy.evaluateTier(2_100.0, TrackingTier.APPROACH))
        assertEquals(TrackingTier.MEDIUM, LocationTrackingPolicy.evaluateTier(2_101.0, TrackingTier.APPROACH))

        // Medium -> Far requires distance > 5,000m + 100m = 5,100m
        assertEquals(TrackingTier.MEDIUM, LocationTrackingPolicy.evaluateTier(5_050.0, TrackingTier.MEDIUM))
        assertEquals(TrackingTier.MEDIUM, LocationTrackingPolicy.evaluateTier(5_100.0, TrackingTier.MEDIUM))
        assertEquals(TrackingTier.FAR, LocationTrackingPolicy.evaluateTier(5_101.0, TrackingTier.MEDIUM))
    }

    @Test
    fun evaluateTier_jumpDirectlyFromFarToApproach() {
        assertEquals(TrackingTier.APPROACH, LocationTrackingPolicy.evaluateTier(800.0, TrackingTier.FAR))
    }

    @Test
    fun minimumDistanceMeters_findsClosestDestinationAmongMultipleArmedAlarms() {
        val alarm1 = manggaraiAlarm().copy(id = "manggarai", coordinates = MANGGARAI)
        val alarm2 = manggaraiAlarm().copy(id = "sudirman", coordinates = SUDIRMAN)

        // Sudirman is at SUDIRMAN, distance to alarm2 is 0m, to alarm1 is ~3.1km
        val minDistance = LocationTrackingPolicy.minimumDistanceMeters(SUDIRMAN, listOf(alarm1, alarm2))
        assertEquals(0.0, minDistance)
    }

    @Test
    fun minimumDistanceMeters_returnsNullWhenArmedListIsEmpty() {
        assertNull(LocationTrackingPolicy.minimumDistanceMeters(MANGGARAI, emptyList()))
    }
}
