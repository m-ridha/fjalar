package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HybridPlaceSearchAdapterTest {

    private class FakeOnlineGeocodingEngine : OnlineGeocodingEngine {
        var queryCount = 0
        var lastQuery: String? = null
        var lastLocale: AppLocale? = null
        var lastProximity: GeoCoordinates? = null
        var lastLimit: Int? = null
        var placesToReturn: List<SearchResultPlace> = emptyList()
        var shouldThrow: Boolean = false

        override suspend fun geocode(
            query: String,
            locale: AppLocale,
            proximity: GeoCoordinates?,
            limit: Int,
        ): List<SearchResultPlace> {
            queryCount++
            lastQuery = query
            lastLocale = locale
            lastProximity = proximity
            lastLimit = limit
            if (shouldThrow) {
                throw RuntimeException("Simulated network failure")
            }
            return placesToReturn.take(limit)
        }
    }

    @Test
    fun blankQueryReturnsEmptyListImmediately() = runTest {
        val fakeEngine = FakeOnlineGeocodingEngine()
        val adapter = HybridPlaceSearchAdapter(onlineEngine = fakeEngine)

        val result1 = adapter.searchPlaces("", AppLocale.EN)
        val result2 = adapter.searchPlaces("   ", AppLocale.ID)

        assertTrue(result1.isEmpty())
        assertTrue(result2.isEmpty())
        assertEquals(0, fakeEngine.queryCount)
    }

    @Test
    fun matchesStationByNameAndAlias() = runTest {
        val adapter = HybridPlaceSearchAdapter()

        // By name
        val manggaraiResults = adapter.searchPlaces("Manggarai", AppLocale.ID)
        assertTrue(manggaraiResults.any { it.id == "krl_mri" })

        // By 3-letter alias
        val mriResults = adapter.searchPlaces("MRI", AppLocale.ID)
        assertTrue(mriResults.any { it.id == "krl_mri" })

        // Case-insensitivity
        val mriLower = adapter.searchPlaces("mri", AppLocale.ID)
        assertTrue(mriLower.any { it.id == "krl_mri" })

        // MRT station by alias
        val lbbResults = adapter.searchPlaces("Lebak Bulus", AppLocale.ID)
        assertTrue(lbbResults.any { it.id == "mrt_lbb" })
    }

    @Test
    fun matchesSubstringAndPrefix() = runTest {
        val adapter = HybridPlaceSearchAdapter()

        // Prefix match
        val prefixResults = adapter.searchPlaces("Mangg", AppLocale.ID)
        assertTrue(prefixResults.any { it.id == "krl_mri" })

        // Substring match
        val subResults = adapter.searchPlaces("gara", AppLocale.ID)
        assertTrue(subResults.any { it.id == "krl_mri" })
    }

    @Test
    fun sortsByProximityWhenProximityProvided() = runTest {
        // Sudirman station coords: -6.20235, 106.82365
        val nearSudirman = GeoCoordinates(-6.2024, 106.8237)
        val adapter = HybridPlaceSearchAdapter()

        // "Stasiun" matches many stations
        val results = adapter.searchPlaces("Stasiun", AppLocale.ID, proximity = nearSudirman)

        assertTrue(results.size >= 2)
        // First result should be Sudirman or Dukuh Atas (closest to nearSudirman)
        val firstResult = results.first()
        assertTrue(
            firstResult.id == "krl_sud" || firstResult.id == "mrt_dka" || firstResult.id == "lrt_dka",
            "Expected closest station near Sudirman/Dukuh Atas, but got: ${firstResult.name}",
        )
    }

    @Test
    fun skipsOnlineEngineWhenOfflineMatchesReachThreshold() = runTest {
        val fakeEngine = FakeOnlineGeocodingEngine()
        val adapter = HybridPlaceSearchAdapter(
            onlineEngine = fakeEngine,
            offlineMatchThreshold = 3,
        )

        // "Stasiun" matches > 3 offline stations
        val results = adapter.searchPlaces("Stasiun", AppLocale.ID)

        assertTrue(results.size >= 3)
        assertEquals(0, fakeEngine.queryCount, "Online engine should not be called when threshold met")
    }

    @Test
    fun queriesOnlineEngineWhenOfflineMatchesBelowThreshold() = runTest {
        val fakeEngine = FakeOnlineGeocodingEngine().apply {
            placesToReturn = listOf(
                SearchResultPlace(
                    id = "online_monas",
                    name = "Monumen Nasional",
                    coordinates = GeoCoordinates(-6.17539, 106.82715),
                    category = PlaceCategory.LANDMARK,
                    detail = "Jakarta Pusat",
                )
            )
        }

        val testStations = listOf(
            SearchResultPlace(
                id = "krl_mri",
                name = "Stasiun Manggarai",
                coordinates = GeoCoordinates(-6.20988, 106.85016),
                category = PlaceCategory.TRAIN_STATION,
            )
        )

        val adapter = HybridPlaceSearchAdapter(
            offlineStations = testStations,
            onlineEngine = fakeEngine,
            offlineMatchThreshold = 5,
        )

        val results = adapter.searchPlaces("Mangg", AppLocale.ID)

        assertEquals(1, fakeEngine.queryCount)
        assertEquals("Mangg", fakeEngine.lastQuery)
        assertEquals(4, fakeEngine.lastLimit) // 5 threshold - 1 offline match = 4 remaining
        assertEquals(2, results.size)
        assertEquals("krl_mri", results[0].id)
        assertEquals("online_monas", results[1].id)
    }

    @Test
    fun handlesOnlineEngineFailureGracefullyWithoutThrowing() = runTest {
        val fakeEngine = FakeOnlineGeocodingEngine().apply {
            shouldThrow = true
        }

        val testStations = listOf(
            SearchResultPlace(
                id = "krl_mri",
                name = "Stasiun Manggarai",
                coordinates = GeoCoordinates(-6.20988, 106.85016),
                category = PlaceCategory.TRAIN_STATION,
            )
        )

        val adapter = HybridPlaceSearchAdapter(
            offlineStations = testStations,
            onlineEngine = fakeEngine,
            offlineMatchThreshold = 5,
        )

        // Should return offline match without throwing
        val results = adapter.searchPlaces("Manggarai", AppLocale.ID)

        assertEquals(1, fakeEngine.queryCount)
        assertEquals(1, results.size)
        assertEquals("krl_mri", results.first().id)
    }

    @Test
    fun deduplicatesOnlineResultsByIdAndProximity() = runTest {
        val offlineSudirman = SearchResultPlace(
            id = "krl_sud",
            name = "Stasiun Sudirman",
            coordinates = GeoCoordinates(-6.20235, 106.82365),
            category = PlaceCategory.TRAIN_STATION,
        )

        val fakeEngine = FakeOnlineGeocodingEngine().apply {
            placesToReturn = listOf(
                // Same ID duplicate
                SearchResultPlace(
                    id = "krl_sud",
                    name = "Sudirman Station Duplicate",
                    coordinates = GeoCoordinates(-6.20235, 106.82365),
                    category = PlaceCategory.TRAIN_STATION,
                ),
                // Different ID but coordinate within 50m (approx 10m away)
                SearchResultPlace(
                    id = "online_sud_near",
                    name = "Sudirman Near",
                    coordinates = GeoCoordinates(-6.20240, 106.82370),
                    category = PlaceCategory.TRAIN_STATION,
                ),
                // Legitimate new place > 50m away
                SearchResultPlace(
                    id = "online_kuningan",
                    name = "Kuningan City",
                    coordinates = GeoCoordinates(-6.22480, 106.82980),
                    category = PlaceCategory.GENERAL,
                ),
            )
        }

        val adapter = HybridPlaceSearchAdapter(
            offlineStations = listOf(offlineSudirman),
            onlineEngine = fakeEngine,
            offlineMatchThreshold = 5,
        )

        val results = adapter.searchPlaces("Sudirman", AppLocale.ID)

        // offlineSudirman + online_kuningan (the duplicates must be filtered out)
        assertEquals(2, results.size)
        assertEquals("krl_sud", results[0].id)
        assertEquals("online_kuningan", results[1].id)
    }

    @Test
    fun transitStationCatalogHasAllKeyTransitHubsAndDistinctIds() {
        val stations = TransitStationCatalog.STATIONS
        assertTrue(stations.isNotEmpty())

        val ids = stations.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "All station IDs in catalog must be distinct")

        // Check key Commuter Line stations
        val krlIds = listOf("krl_mri", "krl_sud", "krl_thb", "krl_jua", "krl_bog", "krl_dp", "krl_bks", "krl_ckr", "krl_srp", "krl_tng")
        krlIds.forEach { id ->
            assertNotNull(stations.find { it.id == id }, "Missing required KRL station: $id")
        }

        // Check key MRT stations
        val mrtIds = listOf("mrt_lbb", "mrt_ftm", "mrt_cpr", "mrt_hjn", "mrt_bla", "mrt_blm", "mrt_ssm", "mrt_sny", "mrt_ist", "mrt_bnh", "mrt_stb", "mrt_dka", "mrt_bhi")
        mrtIds.forEach { id ->
            assertNotNull(stations.find { it.id == id }, "Missing required MRT station: $id")
        }

        // Check key LRT stations
        val lrtIds = listOf("lrt_dka", "lrt_stb", "lrt_rsd", "lrt_kng", "lrt_pcr", "lrt_cwg", "lrt_hlm", "lrt_hjm", "lrt_jtm", "lrt_vld", "lrt_pgd")
        lrtIds.forEach { id ->
            assertNotNull(stations.find { it.id == id }, "Missing required LRT station: $id")
        }

        // Check Intercity & Airports
        val transitHubIds = listOf("kai_gmr", "kai_pse", "apt_cgk", "apt_hlp", "trm_kbr", "trm_plg")
        transitHubIds.forEach { id ->
            assertNotNull(stations.find { it.id == id }, "Missing required transit hub: $id")
        }

        // Verify valid coordinates and categories
        for (station in stations) {
            assertTrue(station.coordinates.latitude in -90.0..90.0)
            assertTrue(station.coordinates.longitude in -180.0..180.0)
            assertTrue(station.name.isNotBlank())
            assertTrue(station.id.isNotBlank())
        }
    }
}
