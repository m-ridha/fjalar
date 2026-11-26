package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNominatimEngineUrlTest {

    private val photonBase = "https://photon.komoot.io/api"
    private val nominatimBase = "https://nominatim.openstreetmap.org"
    private val proximity = GeoCoordinates(35.66, 139.70)

    @Test
    fun photonUrlIncludesRoundedBiasWhenProximityPresent() {
        val url = buildRequestUrl(photonBase, "Shibuya Station", AppLocale.EN, proximity, 8)

        assertTrue(url.contains("lat=35.66"), url)
        assertTrue(url.contains("lon=139.7"), url)
        assertTrue(url.contains("zoom=12"), url)
    }

    @Test
    fun photonUrlOmitsBiasWithoutProximity() {
        val url = buildRequestUrl(photonBase, "Shibuya Station", AppLocale.EN, null, 8)

        assertFalse(url.contains("lat="), url)
        assertFalse(url.contains("lon="), url)
        assertFalse(url.contains("zoom="), url)
    }

    @Test
    fun photonUrlUsesEnglishOnlyForEnglishLocale() {
        val english = buildRequestUrl(photonBase, "Shibuya Station", AppLocale.EN, null, 8)
        val indonesian = buildRequestUrl(photonBase, "Shibuya Station", AppLocale.ID, null, 8)

        assertTrue(english.contains("lang=en"), english)
        assertFalse(indonesian.contains("lang="), indonesian)
    }

    @Test
    fun photonUrlEncodesQuery() {
        val url = buildRequestUrl(photonBase, "Stasiun Manggarai", AppLocale.ID, null, 8)

        assertTrue(url.contains("q=Stasiun+Manggarai"), url)
    }

    @Test
    fun photonUrlForwardsLimit() {
        val url = buildRequestUrl(photonBase, "Shibuya Station", AppLocale.EN, null, 8)

        assertTrue(url.contains("limit=8"), url)
    }

    @Test
    fun nominatimUrlUsesSearchPathWithoutCoordinates() {
        val url = buildRequestUrl(nominatimBase, "Shibuya Station", AppLocale.EN, proximity, 8)

        assertTrue(url.contains("/search?q="), url)
        assertTrue(url.contains("format=json"), url)
        assertTrue(url.contains("addressdetails=1"), url)
        assertFalse(url.contains("lat="), url)
        assertFalse(url.contains("lon="), url)
    }

    @Test
    fun resolveCategoryMapsOsmBusTagsToBusStop() {
        val engine = AndroidNominatimEngine()

        assertEquals(PlaceCategory.BUS_STOP, engine.resolveCategory("highway", "bus_stop"))
        assertEquals(PlaceCategory.BUS_STOP, engine.resolveCategory("amenity", "bus_station"))
        assertEquals(PlaceCategory.BUS_STOP, engine.resolveCategory("bus_station", "station"))
        assertEquals(PlaceCategory.TRANSIT_STOP, engine.resolveCategory("public_transport", "platform"))
    }
}
