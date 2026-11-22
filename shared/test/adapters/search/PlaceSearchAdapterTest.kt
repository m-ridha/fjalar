package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import com.muhammadridha.fjalar.fakes.FakeOnlineGeocodingEngine
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaceSearchAdapterTest {

    private fun place(
        id: String,
        latitude: Double,
        longitude: Double,
        category: PlaceCategory = PlaceCategory.GENERAL,
    ): SearchResultPlace = SearchResultPlace(
        id = id,
        name = "Place $id",
        coordinates = GeoCoordinates(latitude, longitude),
        category = category,
    )

    @Test
    fun blankQueryReturnsEmptyWithoutCallingEngine() = runTest {
        val engine = FakeOnlineGeocodingEngine()
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        assertTrue(adapter.searchPlaces("", AppLocale.EN).isEmpty())
        assertTrue(adapter.searchPlaces("   ", AppLocale.ID).isEmpty())

        assertEquals(0, engine.queryCount)
    }

    @Test
    fun twoCharacterQueryDoesNotCallEngine() = runTest {
        val engine = FakeOnlineGeocodingEngine()
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        assertTrue(adapter.searchPlaces("ab", AppLocale.EN).isEmpty())

        assertEquals(0, engine.queryCount)
    }

    @Test
    fun forwardsTrimmedQueryLocaleLimitAndCoarseProximity() = runTest {
        val engine = FakeOnlineGeocodingEngine()
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        adapter.searchPlaces("  Shibuya  ", AppLocale.ID, GeoCoordinates(35.65858, 139.70144))

        assertEquals("Shibuya", engine.lastQuery)
        assertEquals(AppLocale.ID, engine.lastLocale)
        assertEquals(8, engine.lastLimit)
        assertEquals(GeoCoordinates(35.66, 139.7), engine.lastProximity)
    }

    @Test
    fun sendsTwoDecimalCoordinatesButRanksByExactDistance() = runTest {
        val proximity = GeoCoordinates(51.50070, -0.12460)
        val far = place("far", 51.5200, -0.1246)
        val near = place("near", 51.5040, -0.1246)

        val engine = FakeOnlineGeocodingEngine().apply {
            places = listOf(far, near)
        }
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        val results = adapter.searchPlaces("station", AppLocale.EN, proximity)

        assertEquals(GeoCoordinates(51.5, -0.12), engine.lastProximity)
        val expectedOrder = listOf(far, near)
            .sortedBy { HaversineCalculator.distanceMeters(proximity, it.coordinates) }
            .map { it.id }
        assertEquals(expectedOrder, results.map { it.id })
        assertEquals(listOf("near", "far"), results.map { it.id })
    }

    @Test
    fun deduplicatesByIdAndWithinFiftyMeters() = runTest {
        val origin = place("a", -6.20000, 106.80000)
        val sameIdDifferentCase = place("A", -6.10000, 106.70000)
        val nearby = place("b", -6.20001, 106.80001)
        val far = place("c", -6.30000, 106.90000)

        val engine = FakeOnlineGeocodingEngine().apply {
            places = listOf(origin, sameIdDifferentCase, nearby, far)
        }
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        val results = adapter.searchPlaces("station", AppLocale.EN, GeoCoordinates(-6.2, 106.8))

        assertEquals(listOf("a", "c"), results.map { it.id })
    }

    @Test
    fun capsResultsAtConfiguredLimit() = runTest {
        val engine = FakeOnlineGeocodingEngine().apply {
            places = listOf(
                place("a", -6.10, 106.70),
                place("b", -6.20, 106.80),
                place("c", -6.30, 106.90),
            )
        }
        val adapter = PlaceSearchAdapter(onlineEngine = engine, resultLimit = 2)

        val results = adapter.searchPlaces("station", AppLocale.EN)

        assertEquals(2, results.size)
        assertEquals(2, engine.lastLimit)
    }

    @Test
    fun returnsEmptyWhenEngineThrows() = runTest {
        val engine = FakeOnlineGeocodingEngine().apply {
            failure = RuntimeException("network down")
        }
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        assertEquals(emptyList(), adapter.searchPlaces("station", AppLocale.EN))
        assertEquals(1, engine.queryCount)
    }

    @Test
    fun propagatesCancellation() = runTest {
        val engine = FakeOnlineGeocodingEngine().apply {
            failure = CancellationException("cancelled")
        }
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        assertFailsWith<CancellationException> {
            adapter.searchPlaces("station", AppLocale.EN)
        }
    }

    @Test
    fun preservesProviderCategories() = runTest {
        val metro = place("metro", -6.20, 106.80, PlaceCategory.METRO_STATION)
        val landmark = place("landmark", -6.30, 106.90, PlaceCategory.LANDMARK)

        val engine = FakeOnlineGeocodingEngine().apply {
            places = listOf(metro, landmark)
        }
        val adapter = PlaceSearchAdapter(onlineEngine = engine)

        val results = adapter.searchPlaces("place", AppLocale.EN)

        assertEquals(
            listOf(PlaceCategory.METRO_STATION, PlaceCategory.LANDMARK),
            results.map { it.category },
        )
    }

    @Test
    fun coarseCoordinatesRoundsToTwoDecimalPlaces() {
        assertEquals(
            GeoCoordinates(-6.21, 106.85),
            coarseCoordinates(GeoCoordinates(-6.20881, 106.84561)),
        )
    }
}
