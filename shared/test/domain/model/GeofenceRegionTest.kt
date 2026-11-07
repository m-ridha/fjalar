package com.muhammadridha.fjalar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class GeofenceRegionTest {

    private val defaultCenter = GeoCoordinates(latitude = -6.2088, longitude = 106.8456)

    @Test
    fun validGeofenceRegionCreation_withDefaultTransitionTypes() {
        val region = GeofenceRegion(
            id = "region-1",
            center = defaultCenter,
            radiusMeters = 500.0,
        )

        assertEquals("region-1", region.id)
        assertEquals(defaultCenter, region.center)
        assertEquals(500.0, region.radiusMeters)
        assertEquals(setOf(GeofenceTransitionType.ENTER), region.transitionTypes)
    }

    @Test
    fun validGeofenceRegionCreation_withCustomTransitionTypes() {
        val transitionTypes = setOf(GeofenceTransitionType.ENTER, GeofenceTransitionType.EXIT)
        val region = GeofenceRegion(
            id = "region-custom",
            center = defaultCenter,
            radiusMeters = 1000.0,
            transitionTypes = transitionTypes,
        )

        assertEquals(transitionTypes, region.transitionTypes)
    }

    @Test
    fun validGeofenceRegion_atRadiusBoundaries() {
        val minRegion = GeofenceRegion(
            id = "min-region",
            center = defaultCenter,
            radiusMeters = GeofenceRegion.MIN_RADIUS_METERS,
        )
        assertEquals(50.0, minRegion.radiusMeters)

        val maxRegion = GeofenceRegion(
            id = "max-region",
            center = defaultCenter,
            radiusMeters = GeofenceRegion.MAX_RADIUS_METERS,
        )
        assertEquals(10_000.0, maxRegion.radiusMeters)
    }

    @Test
    fun blankIdThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "",
                center = defaultCenter,
                radiusMeters = 100.0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "   \t\n",
                center = defaultCenter,
                radiusMeters = 100.0,
            )
        }
    }

    @Test
    fun radiusBelowMinimumThrowsIllegalArgumentException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "too-small",
                center = defaultCenter,
                radiusMeters = 49.99,
            )
        }
        assertTrue(exception.message?.contains("radiusMeters must be within") == true)
    }

    @Test
    fun radiusAboveMaximumThrowsIllegalArgumentException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "too-large",
                center = defaultCenter,
                radiusMeters = 10_000.01,
            )
        }
        assertTrue(exception.message?.contains("radiusMeters must be within") == true)
    }

    @Test
    fun negativeOrNonFiniteRadiusThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "negative",
                center = defaultCenter,
                radiusMeters = -1.0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "nan",
                center = defaultCenter,
                radiusMeters = Double.NaN,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "infinite",
                center = defaultCenter,
                radiusMeters = Double.POSITIVE_INFINITY,
            )
        }
    }

    @Test
    fun emptyTransitionTypesThrowsIllegalArgumentException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            GeofenceRegion(
                id = "empty-transitions",
                center = defaultCenter,
                radiusMeters = 100.0,
                transitionTypes = emptySet(),
            )
        }
        assertTrue(exception.message?.contains("transitionTypes cannot be empty") == true)
    }

    @Test
    fun geofenceTransitionEventCreation() {
        val now = Instant.fromEpochMilliseconds(1731000000000L)
        val event = GeofenceTransitionEvent(
            regionId = "region-event-1",
            transitionType = GeofenceTransitionType.ENTER,
            timestamp = now,
        )

        assertEquals("region-event-1", event.regionId)
        assertEquals(GeofenceTransitionType.ENTER, event.transitionType)
        assertEquals(now, event.timestamp)
    }

    @Test
    fun geofenceTransitionEventBlankRegionIdThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            GeofenceTransitionEvent(
                regionId = "",
                transitionType = GeofenceTransitionType.ENTER,
                timestamp = Instant.fromEpochMilliseconds(1731000000000L),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            GeofenceTransitionEvent(
                regionId = "   ",
                transitionType = GeofenceTransitionType.EXIT,
                timestamp = Instant.fromEpochMilliseconds(1731000000000L),
            )
        }
    }
}
