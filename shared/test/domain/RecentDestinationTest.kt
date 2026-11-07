package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.toRecentDestination
import com.muhammadridha.fjalar.domain.service.DistanceCalculator
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecentDestinationTest {

    private val coordsA = GeoCoordinates(-6.200000, 106.800000)
    private val coordsB = GeoCoordinates(-6.205000, 106.805000) // ~760 meters away
    private val time0 = Instant.fromEpochMilliseconds(1_000_000L)
    private val time1 = Instant.fromEpochMilliseconds(2_000_000L)
    private val time2 = Instant.fromEpochMilliseconds(3_000_000L)

    @Test
    fun instantiationWithValidPropertiesSucceeds() {
        val destination = RecentDestination(
            id = "dest_1",
            name = "Stasiun Gambir",
            coordinates = coordsA,
            timestamp = time0,
            category = PlaceCategory.TRAIN_STATION,
        )

        assertEquals("dest_1", destination.id)
        assertEquals("Stasiun Gambir", destination.name)
        assertEquals(coordsA, destination.coordinates)
        assertEquals(time0, destination.timestamp)
        assertEquals(PlaceCategory.TRAIN_STATION, destination.category)
    }

    @Test
    fun defaultCategoryIsGeneral() {
        val destination = RecentDestination(
            id = "dest_1",
            name = "Office",
            coordinates = coordsA,
            timestamp = time0,
        )

        assertEquals(PlaceCategory.GENERAL, destination.category)
    }

    @Test
    fun blankIdIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            RecentDestination(
                id = "",
                name = "Office",
                coordinates = coordsA,
                timestamp = time0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            RecentDestination(
                id = "   ",
                name = "Office",
                coordinates = coordsA,
                timestamp = time0,
            )
        }
    }

    @Test
    fun blankNameIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            RecentDestination(
                id = "dest_1",
                name = "",
                coordinates = coordsA,
                timestamp = time0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            RecentDestination(
                id = "dest_1",
                name = "   ",
                coordinates = coordsA,
                timestamp = time0,
            )
        }
    }

    @Test
    fun searchResultPlaceToRecentDestinationMapsCorrectly() {
        val place = SearchResultPlace(
            id = "station_mri",
            name = "Stasiun Manggarai",
            coordinates = coordsA,
            category = PlaceCategory.TRAIN_STATION,
            detail = "Commuter Line",
            aliases = listOf("MRI"),
        )

        val destination = place.toRecentDestination(timestamp = time1)

        assertEquals("station_mri", destination.id)
        assertEquals("Stasiun Manggarai", destination.name)
        assertEquals(coordsA, destination.coordinates)
        assertEquals(time1, destination.timestamp)
        assertEquals(PlaceCategory.TRAIN_STATION, destination.category)
    }

    @Test
    fun updateRecentsPrependsNewDestinationToFront() {
        val existing = listOf(
            RecentDestination(id = "1", name = "Place 1", coordinates = coordsA, timestamp = time0),
        )
        val latest = RecentDestination(id = "2", name = "Place 2", coordinates = coordsB, timestamp = time1)

        val updated = RecentDestination.updateRecents(current = existing, latest = latest)

        assertEquals(2, updated.size)
        assertEquals(latest, updated[0])
        assertEquals(existing[0], updated[1])
    }

    @Test
    fun updateRecentsCapsAtMaxCapacityByDefault() {
        var current = emptyList<RecentDestination>()

        for (i in 1..7) {
            val dest = RecentDestination(
                id = "id_$i",
                name = "Place $i",
                coordinates = GeoCoordinates(-6.0 - (i * 0.01), 106.0 + (i * 0.01)),
                timestamp = Instant.fromEpochMilliseconds(i * 1000L),
            )
            current = RecentDestination.updateRecents(current, dest)
        }

        assertEquals(RecentDestination.MAX_CAPACITY, current.size)
        assertEquals(5, current.size)
        // Most recent should be id_7, down to id_3
        assertEquals("id_7", current[0].id)
        assertEquals("id_6", current[1].id)
        assertEquals("id_5", current[2].id)
        assertEquals("id_4", current[3].id)
        assertEquals("id_3", current[4].id)
    }

    @Test
    fun updateRecentsRespectsCustomMaxEntries() {
        val item1 = RecentDestination(id = "1", name = "Place 1", coordinates = coordsA, timestamp = time0)
        val item2 = RecentDestination(id = "2", name = "Place 2", coordinates = coordsB, timestamp = time1)
        val item3 = RecentDestination(
            id = "3",
            name = "Place 3",
            coordinates = GeoCoordinates(-6.3, 106.9),
            timestamp = time2,
        )

        val updated = RecentDestination.updateRecents(
            current = listOf(item2, item1),
            latest = item3,
            maxEntries = 2,
        )

        assertEquals(2, updated.size)
        assertEquals("3", updated[0].id)
        assertEquals("2", updated[1].id)
    }

    @Test
    fun updateRecentsDeduplicatesByIdAndMovesToFront() {
        val item1 = RecentDestination(id = "1", name = "Place 1", coordinates = coordsA, timestamp = time0)
        val item2 = RecentDestination(id = "2", name = "Place 2", coordinates = coordsB, timestamp = time0)
        val updatedItem1 = RecentDestination(
            id = "1",
            name = "Place 1 Renamed",
            coordinates = coordsA,
            timestamp = time1,
        )

        val updated = RecentDestination.updateRecents(
            current = listOf(item2, item1),
            latest = updatedItem1,
        )

        assertEquals(2, updated.size)
        assertEquals("1", updated[0].id)
        assertEquals("Place 1 Renamed", updated[0].name)
        assertEquals(time1, updated[0].timestamp)
        assertEquals("2", updated[1].id)
    }

    @Test
    fun updateRecentsDeduplicatesByCaseInsensitiveTrimmedName() {
        val item1 = RecentDestination(
            id = "1",
            name = "  Stasiun Gambir  ",
            coordinates = coordsA,
            timestamp = time0,
        )
        val item2 = RecentDestination(id = "2", name = "Other Place", coordinates = coordsB, timestamp = time0)
        val latest = RecentDestination(
            id = "3",
            name = "stasiun gambir",
            coordinates = GeoCoordinates(-6.25, 106.85),
            timestamp = time1,
        )

        val updated = RecentDestination.updateRecents(
            current = listOf(item1, item2),
            latest = latest,
        )

        assertEquals(2, updated.size)
        assertEquals("3", updated[0].id)
        assertEquals("2", updated[1].id)
    }

    @Test
    fun updateRecentsDeduplicatesByCoordinateProximityWithin50Meters() {
        // -6.200000, 106.800000 and -6.200200, 106.800000 is ~22.2 meters apart
        val nearbyCoords = GeoCoordinates(-6.200200, 106.800000)
        val distance = HaversineCalculator.calculateDistanceMeters(coordsA, nearbyCoords)
        assertTrue(distance < 50.0, "Distance should be under 50m but was $distance")

        val existing = RecentDestination(
            id = "station_exit_a",
            name = "Gambir Exit A",
            coordinates = coordsA,
            timestamp = time0,
        )
        val latest = RecentDestination(
            id = "station_exit_b",
            name = "Gambir Exit B",
            coordinates = nearbyCoords,
            timestamp = time1,
        )

        val updated = RecentDestination.updateRecents(
            current = listOf(existing),
            latest = latest,
        )

        assertEquals(1, updated.size)
        assertEquals("station_exit_b", updated[0].id)
    }

    @Test
    fun updateRecentsDoesNotDeduplicateBeyondProximityThreshold() {
        // coordsA and coordsB are ~760m apart
        val distance = HaversineCalculator.calculateDistanceMeters(coordsA, coordsB)
        assertTrue(distance > 50.0, "Distance should be over 50m but was $distance")

        val existing = RecentDestination(
            id = "dest_a",
            name = "Place A",
            coordinates = coordsA,
            timestamp = time0,
        )
        val latest = RecentDestination(
            id = "dest_b",
            name = "Place B",
            coordinates = coordsB,
            timestamp = time1,
        )

        val updated = RecentDestination.updateRecents(
            current = listOf(existing),
            latest = latest,
        )

        assertEquals(2, updated.size)
        assertEquals("dest_b", updated[0].id)
        assertEquals("dest_a", updated[1].id)
    }

    @Test
    fun updateRecentsAllowsCustomDistanceCalculatorAndProximity() {
        val dummyCalculator = DistanceCalculator { _, _ -> 100.0 }
        val item1 = RecentDestination(id = "1", name = "Place 1", coordinates = coordsA, timestamp = time0)
        val latest = RecentDestination(id = "2", name = "Place 2", coordinates = coordsB, timestamp = time1)

        // With 150m threshold and distance returning 100m, it should deduplicate
        val deduplicated = RecentDestination.updateRecents(
            current = listOf(item1),
            latest = latest,
            proximityMeters = 150.0,
            distanceCalculator = dummyCalculator,
        )
        assertEquals(1, deduplicated.size)
        assertEquals("2", deduplicated[0].id)

        // With 50m threshold and distance returning 100m, it should NOT deduplicate
        val preserved = RecentDestination.updateRecents(
            current = listOf(item1),
            latest = latest,
            proximityMeters = 50.0,
            distanceCalculator = dummyCalculator,
        )
        assertEquals(2, preserved.size)
        assertEquals("2", preserved[0].id)
        assertEquals("1", preserved[1].id)
    }
}
