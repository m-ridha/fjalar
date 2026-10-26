package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchResultPlaceTest {

    private val manggaraiCoords = GeoCoordinates(-6.209944, 106.850306)

    @Test
    fun instantiationWithValidPropertiesSucceeds() {
        val place = SearchResultPlace(
            id = "station_mri",
            name = "Stasiun Manggarai",
            coordinates = manggaraiCoords,
            category = PlaceCategory.TRAIN_STATION,
            detail = "Commuter Line B, C",
            aliases = listOf("MRI", "Manggarai Central"),
        )

        assertEquals("station_mri", place.id)
        assertEquals("Stasiun Manggarai", place.name)
        assertEquals(manggaraiCoords, place.coordinates)
        assertEquals(PlaceCategory.TRAIN_STATION, place.category)
        assertEquals("Commuter Line B, C", place.detail)
        assertEquals(listOf("MRI", "Manggarai Central"), place.aliases)
    }

    @Test
    fun defaultParametersAreRespected() {
        val place = SearchResultPlace(
            id = "monas",
            name = "Monas",
            coordinates = GeoCoordinates(-6.175392, 106.827153),
            category = PlaceCategory.LANDMARK,
        )

        assertNull(place.detail)
        assertTrue(place.aliases.isEmpty())
    }

    @Test
    fun blankIdIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SearchResultPlace(
                id = "",
                name = "Test Place",
                coordinates = manggaraiCoords,
                category = PlaceCategory.GENERAL,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            SearchResultPlace(
                id = "   ",
                name = "Test Place",
                coordinates = manggaraiCoords,
                category = PlaceCategory.GENERAL,
            )
        }
    }

    @Test
    fun blankNameIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SearchResultPlace(
                id = "place_1",
                name = "",
                coordinates = manggaraiCoords,
                category = PlaceCategory.GENERAL,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            SearchResultPlace(
                id = "place_1",
                name = "  \t\n  ",
                coordinates = manggaraiCoords,
                category = PlaceCategory.GENERAL,
            )
        }
    }

    @Test
    fun equalityAndHashCodeWorkAsExpected() {
        val placeA = SearchResultPlace(
            id = "mri",
            name = "Manggarai",
            coordinates = manggaraiCoords,
            category = PlaceCategory.TRAIN_STATION,
            detail = "Line 1",
            aliases = listOf("MRI"),
        )
        val placeB = SearchResultPlace(
            id = "mri",
            name = "Manggarai",
            coordinates = manggaraiCoords,
            category = PlaceCategory.TRAIN_STATION,
            detail = "Line 1",
            aliases = listOf("MRI"),
        )
        val placeDifferent = placeA.copy(category = PlaceCategory.TRANSIT_STOP)

        assertEquals(placeA, placeB)
        assertEquals(placeA.hashCode(), placeB.hashCode())
        assertNotEquals(placeA, placeDifferent)
    }

    @Test
    fun allPlaceCategoriesAreRecognized() {
        val categories = PlaceCategory.entries
        assertEquals(5, categories.size)
        assertTrue(categories.contains(PlaceCategory.TRAIN_STATION))
        assertTrue(categories.contains(PlaceCategory.METRO_STATION))
        assertTrue(categories.contains(PlaceCategory.TRANSIT_STOP))
        assertTrue(categories.contains(PlaceCategory.LANDMARK))
        assertTrue(categories.contains(PlaceCategory.GENERAL))
    }

    @Test
    fun matchesQueryByExactOrPartialNameCaseInsensitive() {
        val place = SearchResultPlace(
            id = "stn_sudirman",
            name = "Stasiun Sudirman",
            coordinates = GeoCoordinates(-6.202444, 106.823194),
            category = PlaceCategory.TRAIN_STATION,
            aliases = listOf("SUD", "Sudirman Baru"),
        )

        assertTrue(place.matchesQuery("Sudirman"))
        assertTrue(place.matchesQuery("sudirman"))
        assertTrue(place.matchesQuery("STASIUN"))
        assertTrue(place.matchesQuery("dirman"))
        assertTrue(place.matchesQuery(""))
        assertTrue(place.matchesQuery("   "))
    }

    @Test
    fun matchesQueryByAliasCaseInsensitive() {
        val place = SearchResultPlace(
            id = "stn_sudirman",
            name = "Stasiun Sudirman",
            coordinates = GeoCoordinates(-6.202444, 106.823194),
            category = PlaceCategory.TRAIN_STATION,
            aliases = listOf("SUD", "BNI City"),
        )

        assertTrue(place.matchesQuery("SUD"))
        assertTrue(place.matchesQuery("sud"))
        assertTrue(place.matchesQuery("bni"))
        assertTrue(place.matchesQuery("City"))
        assertFalse(place.matchesQuery("Gambir"))
    }
}
