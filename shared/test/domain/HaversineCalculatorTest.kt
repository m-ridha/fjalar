package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HaversineCalculatorTest {

    @Test
    fun samePointIsZeroMeters() {
        assertEquals(0.0, HaversineCalculator.distanceMeters(SUDIRMAN, SUDIRMAN))
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111km() {
        val d = HaversineCalculator.distanceMeters(GeoCoordinates(0.0, 0.0), GeoCoordinates(1.0, 0.0))
        assertEquals(111_195.08, d, 0.01)
    }

    @Test
    fun sudirmanToManggaraiIsAbout3_1km() {
        // Reference value cross-checked with an independent equirectangular computation.
        assertEquals(3_110.92, HaversineCalculator.distanceMeters(SUDIRMAN, MANGGARAI), 0.5)
    }

    @Test
    fun distanceIsSymmetric() {
        assertEquals(
            HaversineCalculator.distanceMeters(SUDIRMAN, MANGGARAI),
            HaversineCalculator.distanceMeters(MANGGARAI, SUDIRMAN),
            1e-9,
        )
    }

    @Test
    fun antipodalPointsAreHalfTheEarthApart() {
        val d = HaversineCalculator.distanceMeters(GeoCoordinates(0.0, 0.0), GeoCoordinates(0.0, 180.0))
        assertEquals(20_015_115.0, d, 1.0)
    }

    @Test fun antimeridianUsesShortCrossing() {
        assertEquals(22_239.016, HaversineCalculator.distanceMeters(
            GeoCoordinates(0.0, 179.9), GeoCoordinates(0.0, -179.9)), 0.01)
    }

    @Test fun highLatitudeRemainsFinite() {
        assertEquals(157_249.6, HaversineCalculator.distanceMeters(
            GeoCoordinates(89.0, 0.0), GeoCoordinates(89.0, 90.0)), 0.1)
    }

    @Test
    fun coordinatesOutOfBoundsAreRejected() {
        assertFailsWith<IllegalArgumentException> { GeoCoordinates(90.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoCoordinates(0.0, -180.1) }
        assertFailsWith<IllegalArgumentException> { GeoCoordinates(Double.NaN, 0.0) }
    }

    @Test
    fun triggerRadiusBoundsAndPresets() {
        assertEquals(listOf(50, 100, 200, 300, 500, 1_000), TriggerRadius.PRESETS.map { it.meters })
        TriggerRadius(50)
        TriggerRadius(10_000)
        assertFailsWith<IllegalArgumentException> { TriggerRadius(49) }
        assertFailsWith<IllegalArgumentException> { TriggerRadius(10_001) }
    }
}
