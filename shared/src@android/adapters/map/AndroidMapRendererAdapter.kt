package com.muhammadridha.fjalar.adapters.map

import android.graphics.Color
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Android MapLibre adapter implementing [MapRendererPort].
 *
 * Coordinates map viewport updates, destination pin placement, and geofence radius circle overlays
 * using MapLibre GL for Android runtime GeoJSON sources and style layers.
 *
 * Overlays are rendered on the GPU using [GeoJsonSource], [FillLayer], [LineLayer], and [CircleLayer]
 * rather than deprecated legacy Annotation classes, guaranteeing smooth vector rendering, precise
 * design token fidelity, and future compatibility without third-party plugin bloat.
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

    /**
     * Attaches an initialized [MapLibreMap] to this adapter, immediately applying any pending
     * viewport, marker, or geofence state.
     */
    fun attachMap(map: MapLibreMap) {
        this.activeMap = map

        currentViewport?.let { (center, zoom) ->
            applyViewport(center, zoom, animate = false)
        }
        val style = map.style
        if (style != null && style.isFullyLoaded) {
            onStyleLoaded(style)
        }
    }

    /**
     * Detaches the active map instance and cleans up overlay references.
     */
    fun detachMap() {
        activeMap = null
    }

    /**
     * Re-applies active pin and geofence layers when a style finishes loading.
     * Invoked whenever the initial style loads or when switching between light and dark Carto styles.
     */
    fun onStyleLoaded(style: Style) {
        currentPin?.let { pin ->
            applyPin(pin, style)
        }
        currentGeofence?.let { (center, radius) ->
            applyGeofence(center, radius, style)
        }
    }

    /**
     * Updates theme state and refreshes layer paint properties to match tokens.
     */
    fun updateTheme(isDark: Boolean) {
        if (this.isDarkTheme != isDark) {
            this.isDarkTheme = isDark
            val style = getActiveStyle() ?: return
            val primaryColor = Color.parseColor(getPrimaryColorHex(isDark))

            (style.getLayer(GEOFENCE_FILL_LAYER_ID) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(primaryColor)
            )
            (style.getLayer(GEOFENCE_STROKE_LAYER_ID) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(primaryColor)
            )
            (style.getLayer(PIN_LAYER_ID) as? CircleLayer)?.setProperties(
                PropertyFactory.circleColor(primaryColor)
            )
        }
    }

    override fun setViewport(center: GeoCoordinates, zoom: Double) {
        currentViewport = center to zoom
        applyViewport(center, zoom, animate = true)
    }

    override fun placePin(coordinates: GeoCoordinates) {
        currentPin = coordinates
        val style = getActiveStyle()
        if (style != null) {
            applyPin(coordinates, style)
        }
    }

    override fun showGeofence(center: GeoCoordinates, radius: TriggerRadius) {
        currentGeofence = center to radius
        val style = getActiveStyle()
        if (style != null) {
            applyGeofence(center, radius, style)
        }
    }

    override fun clearOverlays() {
        currentPin = null
        currentGeofence = null

        val style = getActiveStyle() ?: return
        style.removeLayer(PIN_LAYER_ID)
        style.removeSource(PIN_SOURCE_ID)
        style.removeLayer(GEOFENCE_STROKE_LAYER_ID)
        style.removeLayer(GEOFENCE_FILL_LAYER_ID)
        style.removeSource(GEOFENCE_SOURCE_ID)
    }

    fun removePin() {
        currentPin = null
        val style = getActiveStyle() ?: return
        style.removeLayer(PIN_LAYER_ID)
        style.removeSource(PIN_SOURCE_ID)
    }

    fun removeGeofence() {
        currentGeofence = null
        val style = getActiveStyle() ?: return
        style.removeLayer(GEOFENCE_STROKE_LAYER_ID)
        style.removeLayer(GEOFENCE_FILL_LAYER_ID)
        style.removeSource(GEOFENCE_SOURCE_ID)
    }

    private fun getActiveStyle(): Style? {
        val map = activeMap ?: return null
        val style = map.style
        return if (style != null && style.isFullyLoaded) style else null
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

    private fun applyPin(coordinates: GeoCoordinates, style: Style) {
        val point = Point.fromLngLat(coordinates.longitude, coordinates.latitude)
        val feature = Feature.fromGeometry(point)

        val existingSource = style.getSource(PIN_SOURCE_ID) as? GeoJsonSource
        if (existingSource != null) {
            existingSource.setGeoJson(feature)
        } else {
            style.addSource(GeoJsonSource(PIN_SOURCE_ID, feature))
        }

        if (style.getLayer(PIN_LAYER_ID) == null) {
            val primaryColor = Color.parseColor(getPrimaryColorHex(isDarkTheme))
            val circleLayer = CircleLayer(PIN_LAYER_ID, PIN_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(primaryColor),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleOpacity(1f),
            )
            style.addLayer(circleLayer)
        }
    }

    private fun applyGeofence(center: GeoCoordinates, radius: TriggerRadius, style: Style) {
        val points = calculateGeofencePolygon(center, radius.meters.toDouble())
        val ring = points.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        if (ring.isNotEmpty() && (ring.first().longitude() != ring.last().longitude() || ring.first().latitude() != ring.last().latitude())) {
            ring.add(ring.first())
        }
        val polygon = Polygon.fromLngLats(listOf(ring))
        val feature = Feature.fromGeometry(polygon)

        val existingSource = style.getSource(GEOFENCE_SOURCE_ID) as? GeoJsonSource
        if (existingSource != null) {
            existingSource.setGeoJson(feature)
        } else {
            style.addSource(GeoJsonSource(GEOFENCE_SOURCE_ID, feature))
        }

        val primaryColor = Color.parseColor(getPrimaryColorHex(isDarkTheme))

        if (style.getLayer(GEOFENCE_FILL_LAYER_ID) == null) {
            val fillLayer = FillLayer(GEOFENCE_FILL_LAYER_ID, GEOFENCE_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(primaryColor),
                PropertyFactory.fillOpacity(0.2f),
                PropertyFactory.fillAntialias(true),
            )
            val pinLayer = style.getLayer(PIN_LAYER_ID)
            if (pinLayer != null) {
                style.addLayerBelow(fillLayer, PIN_LAYER_ID)
            } else {
                style.addLayer(fillLayer)
            }
        }

        if (style.getLayer(GEOFENCE_STROKE_LAYER_ID) == null) {
            val strokeLayer = LineLayer(GEOFENCE_STROKE_LAYER_ID, GEOFENCE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(primaryColor),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.8f),
            )
            val pinLayer = style.getLayer(PIN_LAYER_ID)
            if (pinLayer != null) {
                style.addLayerBelow(strokeLayer, PIN_LAYER_ID)
            } else {
                style.addLayer(strokeLayer)
            }
        }
    }

    companion object {
        const val PIN_SOURCE_ID = "fjalar-pin-source"
        const val PIN_LAYER_ID = "fjalar-pin-layer"
        const val GEOFENCE_SOURCE_ID = "fjalar-geofence-source"
        const val GEOFENCE_FILL_LAYER_ID = "fjalar-geofence-fill-layer"
        const val GEOFENCE_STROKE_LAYER_ID = "fjalar-geofence-stroke-layer"

        const val MAP_STYLE_LIGHT = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
        const val MAP_STYLE_DARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

        const val LIGHT_PRIMARY_HEX = "#007AFF"
        const val DARK_PRIMARY_HEX = "#00D2FF"

        // Light mode: Primary #007AFF with 20% alpha fill and 80% alpha stroke
        const val LIGHT_GEOFENCE_FILL = 0x33007AFF.toInt()
        const val LIGHT_GEOFENCE_STROKE = 0xCC007AFF.toInt()

        // Dark mode: Primary #00D2FF with 20% alpha fill and 80% alpha stroke
        const val DARK_GEOFENCE_FILL = 0x3300D2FF.toInt()
        const val DARK_GEOFENCE_STROKE = 0xCC00D2FF.toInt()

        fun getPrimaryColorHex(isDark: Boolean): String =
            if (isDark) DARK_PRIMARY_HEX else LIGHT_PRIMARY_HEX

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
