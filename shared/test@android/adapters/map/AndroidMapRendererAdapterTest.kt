package com.muhammadridha.fjalar.adapters.map

import com.muhammadridha.fjalar.adapters.map.AndroidMapRendererAdapter.Companion.toGeoCoordinates
import com.muhammadridha.fjalar.adapters.map.AndroidMapRendererAdapter.Companion.toLatLng
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.expressions.Expression
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidMapRendererAdapterTest {
    @Test
    fun landmarkFilterRequiresNamedSupportedKindsAndSourceZoom() {
        val filter = Expression.raw(AndroidMapRendererAdapter.LANDMARK_FILTER).toString()
        assertTrue(filter.contains("\"has\", \"name\""))
        for (kind in listOf("hospital", "school", "university", "college", "station", "railway_station", "bus_station", "government")) {
            assertTrue(filter.contains("\"$kind\""), "Missing $kind")
        }
        assertTrue(filter.contains("\"min_zoom\""))
        assertTrue(filter.contains("\"zoom\""))
        assertTrue(!filter.contains("\"office\""))
    }

    @Test
    fun liveFixAndPermissionLossNeverReplaceDestinationOrGeofence() {
        val adapter = AndroidMapRendererAdapter()
        val destination = GeoCoordinates(-33.86, 151.2)
        adapter.placePin(destination)
        adapter.showGeofence(destination, TriggerRadius(250))
        adapter.showUserPosition(MapUserPosition(GeoCoordinates(-33.85, 151.21), 25.0))
        adapter.showUserPosition(MapUserPosition(GeoCoordinates(-33.851, 151.211), 50.0))
        assertEquals(destination, adapter.currentPin)
        assertEquals(50.0, adapter.currentUserPosition?.accuracyMeters)
        adapter.showUserPosition(null)
        assertNull(adapter.currentUserPosition)
        assertEquals(destination, adapter.currentPin)
        assertEquals(destination to TriggerRadius(250), adapter.currentGeofence)
    }


    @Test
    fun coordinateConversions_preservesPrecisionAndMapsCorrectly() {
        val geo = GeoCoordinates(latitude = -6.208763, longitude = 106.845599)
        val latLng = geo.toLatLng()

        assertEquals(-6.208763, latLng.latitude, 1e-6)
        assertEquals(106.845599, latLng.longitude, 1e-6)

        val roundTrip = latLng.toGeoCoordinates()
        assertEquals(geo.latitude, roundTrip.latitude, 1e-6)
        assertEquals(geo.longitude, roundTrip.longitude, 1e-6)
    }

    @Test
    fun coordinateConversions_handlesClampingAndLongitudeNormalization() {
        val outOfBoundsLatLng = LatLng(85.0, 190.0)
        val geo = outOfBoundsLatLng.toGeoCoordinates()

        assertEquals(85.0, geo.latitude)
        assertEquals(-170.0, geo.longitude, 1e-6)

        val negativeOutOfBoundsLatLng = LatLng(-85.0, -200.0)
        val geoNeg = negativeOutOfBoundsLatLng.toGeoCoordinates()

        assertEquals(-85.0, geoNeg.latitude)
        assertEquals(160.0, geoNeg.longitude, 1e-6)
    }

    @Test
    fun styleUrlResolution_returnsCorrectCartoStylesForTheme() {
        val lightUrl = AndroidMapRendererAdapter.getMapStyleUrl(isDark = false)
        val darkUrl = AndroidMapRendererAdapter.getMapStyleUrl(isDark = true)

        assertEquals("https://basemaps.cartocdn.com/gl/positron-gl-style/style.json", lightUrl)
        assertEquals("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json", darkUrl)
    }

    @Test
    fun geofenceColors_matchDesignTokensForLightAndDark() {
        val (lightFill, lightStroke) = AndroidMapRendererAdapter.getGeofenceColors(isDark = false)
        val (darkFill, darkStroke) = AndroidMapRendererAdapter.getGeofenceColors(isDark = true)

        assertEquals(0x33007AFF.toInt(), lightFill)
        assertEquals(0xCC007AFF.toInt(), lightStroke)

        assertEquals(0x3300D2FF.toInt(), darkFill)
        assertEquals(0xCC00D2FF.toInt(), darkStroke)
    }

    @Test
    fun geofencePolygonGeneration_producesExactNumberOfVertices() {
        val center = GeoCoordinates(-6.2088, 106.8456)
        val polygon64 = AndroidMapRendererAdapter.calculateGeofencePolygon(center, 500.0)
        assertEquals(64, polygon64.size)

        val polygon32 = AndroidMapRendererAdapter.calculateGeofencePolygon(center, 500.0, steps = 32)
        assertEquals(32, polygon32.size)

        assertFailsWith<IllegalArgumentException> {
            AndroidMapRendererAdapter.calculateGeofencePolygon(center, 500.0, steps = 2)
        }
    }

    @Test
    fun geofencePolygonGeneration_scalesAccuratelyAcrossLatitudesAndRadii() {
        val testLocations = listOf(
            GeoCoordinates(-6.2088, 106.8456),   // Jakarta (equatorial)
            GeoCoordinates(35.6762, 139.6503),   // Tokyo (mid-latitude)
            GeoCoordinates(51.5074, -0.1278),    // London (temperate)
            GeoCoordinates(59.9139, 10.7522),    // Oslo (high-latitude)
            GeoCoordinates(69.6492, 18.9553),    // Tromsø (sub-arctic)
        )

        val testRadii = listOf(
            TriggerRadius(100),
            TriggerRadius(250),
            TriggerRadius(500),
            TriggerRadius(1000),
            TriggerRadius(2000),
            TriggerRadius(5000),
        )

        for (center in testLocations) {
            for (radius in testRadii) {
                val radiusMeters = radius.meters.toDouble()
                val polygon = AndroidMapRendererAdapter.calculateGeofencePolygon(center, radiusMeters, steps = 64)

                for (vertex in polygon) {
                    val actualDistance = HaversineCalculator.distanceMeters(center, vertex)
                    val errorMeters = abs(actualDistance - radiusMeters)
                    // Geodesic formula error must be less than 0.05 meters across all latitudes
                    assertTrue(
                        errorMeters < 0.05,
                        "At $center with radius $radiusMeters m, vertex $vertex had distance $actualDistance m (error $errorMeters m)"
                    )
                }
            }
        }
    }

    @Test
    fun calculateGeofenceLatLngs_returnsMappedLatLngList() {
        val center = GeoCoordinates(-6.2088, 106.8456)
        val latLngs = AndroidMapRendererAdapter.calculateGeofenceLatLngs(center, 300.0, steps = 16)
        assertEquals(16, latLngs.size)
        for (latLng in latLngs) {
            assertTrue(latLng.latitude in -90.0..90.0)
            assertTrue(latLng.longitude in -180.0..180.0)
        }
    }

    @Test
    fun adapterStateManagement_managesViewportAndOverlaysWithoutActiveMap() {
        val adapter = AndroidMapRendererAdapter(isDarkTheme = false)
        assertNull(adapter.activeMap)
        assertNull(adapter.currentViewport)
        assertNull(adapter.currentPin)
        assertNull(adapter.currentGeofence)

        val center = GeoCoordinates(-6.2088, 106.8456)
        adapter.setViewport(center, 14.5)
        assertEquals(center to 14.5, adapter.currentViewport)

        val pin = GeoCoordinates(-6.2100, 106.8500)
        adapter.placePin(pin)
        assertEquals(pin, adapter.currentPin)

        val geofenceRadius = TriggerRadius(500)
        adapter.showGeofence(pin, geofenceRadius)
        assertEquals(pin to geofenceRadius, adapter.currentGeofence)

        adapter.updateTheme(isDark = true)
        assertTrue(adapter.isDarkTheme)

        adapter.removePin()
        assertNull(adapter.currentPin)
        assertNotNull(adapter.currentGeofence)

        adapter.removeGeofence()
        assertNull(adapter.currentGeofence)

        adapter.placePin(pin)
        adapter.showGeofence(pin, geofenceRadius)
        adapter.clearOverlays()
        assertNull(adapter.currentPin)
        assertNull(adapter.currentGeofence)

        adapter.detachMap()
        assertNull(adapter.activeMap)
    }
}
