package com.muhammadridha.fjalar.adapters.map

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polygon
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Android MapLibre adapter implementing [MapRendererPort].
 *
 * Coordinates map viewport updates, destination pin placement, and geofence radius circle overlays
 * using MapLibre GL for Android.
 */
class AndroidMapRendererAdapter(
    var isDarkTheme: Boolean = false,
) : MapRendererPort {

    var activeMap: MapLibreMap? = null
        private set

    var currentViewport: Pair<GeoCoordinates, Double>? = null
        private set

    var currentPin: GeoCoordinates? = null
        private set

    var currentGeofence: Pair<GeoCoordinates, TriggerRadius>? = null
        private set

    private var activeMarker: Marker? = null
    private var activePolygon: Polygon? = null

    /**
     * Attaches an initialized [MapLibreMap] to this adapter, immediately applying any pending
     * viewport, marker, or geofence state.
     */
    fun attachMap(map: MapLibreMap) {
        this.activeMap = map

        currentViewport?.let { (center, zoom) ->
            applyViewport(center, zoom, animate = false)
        }
        currentPin?.let { pin ->
            applyPin(pin)
        }
        currentGeofence?.let { (center, radius) ->
            applyGeofence(center, radius)
        }
    }

    /**
     * Detaches the active map instance and cleans up overlay references.
     */
    fun detachMap() {
        activeMarker = null
        activePolygon = null
        activeMap = null
    }

    /**
     * Updates theme state and refreshes overlays if needed.
     */
    fun updateTheme(isDark: Boolean) {
        if (this.isDarkTheme != isDark) {
            this.isDarkTheme = isDark
            currentGeofence?.let { (center, radius) ->
                applyGeofence(center, radius)
            }
        }
    }

    override fun setViewport(center: GeoCoordinates, zoom: Double) {
        currentViewport = center to zoom
        applyViewport(center, zoom, animate = true)
    }

    override fun placePin(coordinates: GeoCoordinates) {
        currentPin = coordinates
        applyPin(coordinates)
    }

    override fun showGeofence(center: GeoCoordinates, radius: TriggerRadius) {
        currentGeofence = center to radius
        applyGeofence(center, radius)
    }

    override fun clearOverlays() {
        currentPin = null
        currentGeofence = null

        val map = activeMap
        if (map != null) {
            activeMarker?.let { map.removeMarker(it) }
            activePolygon?.let { map.removePolygon(it) }
        }
        activeMarker = null
        activePolygon = null
    }

    fun removePin() {
        currentPin = null
        activeMarker?.let { activeMap?.removeMarker(it) }
        activeMarker = null
    }

    fun removeGeofence() {
        currentGeofence = null
        activePolygon?.let { activeMap?.removePolygon(it) }
        activePolygon = null
    }

    private fun applyViewport(center: GeoCoordinates, zoom: Double, animate: Boolean) {
        val map = activeMap ?: return
        val target = center.toLatLng()
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .build()
        )
        if (animate) {
            map.animateCamera(update)
        } else {
            map.moveCamera(update)
        }
    }

    private fun applyPin(coordinates: GeoCoordinates) {
        val map = activeMap ?: return
        val target = coordinates.toLatLng()
        val marker = activeMarker
        if (marker != null) {
            marker.position = target
        } else {
            activeMarker = map.addMarker(
                MarkerOptions()
                    .position(target)
                    .title("Destination")
            )
        }
    }

    private fun applyGeofence(center: GeoCoordinates, radius: TriggerRadius) {
        val map = activeMap ?: return
        activePolygon?.let { map.removePolygon(it) }

        val points = calculateGeofenceLatLngs(center, radius.meters.toDouble())
        val (fillColor, strokeColor) = getGeofenceColors(isDarkTheme)
        activePolygon = map.addPolygon(
            PolygonOptions()
                .addAll(points)
                .fillColor(fillColor)
                .strokeColor(strokeColor)
        )
    }

    companion object {
        const val MAP_STYLE_LIGHT = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
        const val MAP_STYLE_DARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

        // Light mode: Primary #007AFF with 20% alpha fill and 80% alpha stroke
        const val LIGHT_GEOFENCE_FILL = 0x33007AFF.toInt()
        const val LIGHT_GEOFENCE_STROKE = 0xCC007AFF.toInt()

        // Dark mode: Primary #00D2FF with 20% alpha fill and 80% alpha stroke
        const val DARK_GEOFENCE_FILL = 0x3300D2FF.toInt()
        const val DARK_GEOFENCE_STROKE = 0xCC00D2FF.toInt()

        fun getMapStyleUrl(isDark: Boolean): String =
            if (isDark) MAP_STYLE_DARK else MAP_STYLE_LIGHT

        fun getGeofenceColors(isDark: Boolean): Pair<Int, Int> =
            if (isDark) {
                DARK_GEOFENCE_FILL to DARK_GEOFENCE_STROKE
            } else {
                LIGHT_GEOFENCE_FILL to LIGHT_GEOFENCE_STROKE
            }

        fun GeoCoordinates.toLatLng(): LatLng = LatLng(latitude, longitude)

        fun LatLng.toGeoCoordinates(): GeoCoordinates =
            GeoCoordinates(latitude.coerceIn(-90.0, 90.0), normalizeLongitude(longitude))

        private fun normalizeLongitude(lon: Double): Double {
            var l = lon
            while (l < -180.0) l += 360.0
            while (l > 180.0) l -= 360.0
            return l
        }

        /**
         * Calculates a circle polygon of [steps] points with exact geodesic scaling across latitude variations.
         */
        fun calculateGeofencePolygon(
            center: GeoCoordinates,
            radiusMeters: Double,
            steps: Int = 64
        ): List<GeoCoordinates> {
            require(steps >= 3) { "Steps must be at least 3 for a valid polygon" }
            val points = ArrayList<GeoCoordinates>(steps)
            val centerLatRad = center.latitude * Math.PI / 180.0
            val centerLonRad = center.longitude * Math.PI / 180.0
            val angularDistance = radiusMeters / HaversineCalculator.EARTH_MEAN_RADIUS_METERS

            for (i in 0 until steps) {
                val bearing = 2.0 * Math.PI * i / steps
                val pointLatRad = kotlin.math.asin(
                    kotlin.math.sin(centerLatRad) * kotlin.math.cos(angularDistance) +
                    kotlin.math.cos(centerLatRad) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing)
                )
                val pointLonRad = centerLonRad + kotlin.math.atan2(
                    kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(centerLatRad),
                    kotlin.math.cos(angularDistance) - kotlin.math.sin(centerLatRad) * kotlin.math.sin(pointLatRad)
                )

                val latDeg = pointLatRad * 180.0 / Math.PI
                var lonDeg = pointLonRad * 180.0 / Math.PI
                while (lonDeg < -180.0) lonDeg += 360.0
                while (lonDeg > 180.0) lonDeg -= 360.0

                points.add(GeoCoordinates(latDeg.coerceIn(-90.0, 90.0), lonDeg))
            }
            return points
        }

        fun calculateGeofenceLatLngs(
            center: GeoCoordinates,
            radiusMeters: Double,
            steps: Int = 64
        ): List<LatLng> {
            return calculateGeofencePolygon(center, radiusMeters, steps).map { it.toLatLng() }
        }
    }
}
