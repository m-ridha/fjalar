package com.muhammadridha.fjalar.adapters.map

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
import org.maplibre.android.style.layers.Property.TEXT_ANCHOR_TOP
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
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

    var currentUserPosition: MapUserPosition? = null
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
        addPoiLayers(style)
        currentUserPosition?.let { applyUserPosition(it, style) }
        currentPin?.let { pin ->
            applyPin(pin, style)
        }
        currentGeofence?.let { (center, radius) ->
            applyGeofence(center, radius, style)
        }
    }

    private fun addPoiLayers(style: Style) {
        if (style.getSource(POI_SOURCE_ID) == null) return

        style.addImages(HashMap(AndroidPoiIcons.createPoiIconBitmaps(isDarkTheme)))

        val belowMinorRoadAnchor = if (style.getLayer(ROADNAME_MINOR_LAYER_ID) != null) {
            ROADNAME_MINOR_LAYER_ID
        } else {
            TOP_OVERLAY_ANCHOR_ORDER.firstOrNull { style.getLayer(it) != null }
        }
        val topOverlayAnchor = TOP_OVERLAY_ANCHOR_ORDER.firstOrNull { style.getLayer(it) != null }

        if (style.getLayer(POI_LOCAL_LAYER_ID) == null) {
            insertLayer(
                style = style,
                layer = createPoiLayer(
                    layerId = POI_LOCAL_LAYER_ID,
                    minZoomLevel = 16f,
                    filterJson = POI_LOCAL_FILTER,
                    fontStack = POI_DETAIL_FONT,
                    iconScale = 0.34f,
                    textSizeSp = 10f,
                    textPaddingPx = 5f,
                    iconPaddingPx = 2f,
                ),
                anchorLayerId = belowMinorRoadAnchor,
            )
        }

        if (style.getLayer(POI_NEIGHBORHOOD_LAYER_ID) == null) {
            insertLayer(
                style = style,
                layer = createPoiLayer(
                    layerId = POI_NEIGHBORHOOD_LAYER_ID,
                    minZoomLevel = 15f,
                    filterJson = POI_NEIGHBORHOOD_FILTER,
                    fontStack = POI_DETAIL_FONT,
                    iconScale = 0.36f,
                    textSizeSp = 10.5f,
                    textPaddingPx = 6f,
                    iconPaddingPx = 3f,
                ),
                anchorLayerId = belowMinorRoadAnchor,
            )
        }

        if (style.getLayer(POI_MAJOR_LAYER_ID) == null) {
            insertLayer(
                style = style,
                layer = createPoiLayer(
                    layerId = POI_MAJOR_LAYER_ID,
                    minZoomLevel = 13f,
                    filterJson = POI_MAJOR_FILTER,
                    fontStack = POI_MAJOR_FONT,
                    iconScale = 0.42f,
                    textSizeSp = 11.5f,
                    textPaddingPx = 8f,
                    iconPaddingPx = 4f,
                    sortKeyJson = POI_MAJOR_SORT_KEY,
                ),
                anchorLayerId = topOverlayAnchor,
            )
        }
    }

    private fun createPoiLayer(
        layerId: String,
        minZoomLevel: Float,
        filterJson: String,
        fontStack: Array<String>,
        iconScale: Float,
        textSizeSp: Float,
        textPaddingPx: Float,
        iconPaddingPx: Float,
        sortKeyJson: String? = null,
    ): SymbolLayer = SymbolLayer(layerId, POI_SOURCE_ID).apply {
        sourceLayer = POI_SOURCE_LAYER
        minZoom = minZoomLevel
        setFilter(Expression.raw(filterJson))
        setProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(fontStack),
            PropertyFactory.iconImage(AndroidPoiIcons.iconImageExpression()),
            PropertyFactory.iconAnchor(ICON_ANCHOR_BOTTOM),
            PropertyFactory.textAnchor(TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 0.25f)),
            PropertyFactory.iconSize(iconScale),
            PropertyFactory.textSize(textSizeSp),
            PropertyFactory.textMaxWidth(8f),
            PropertyFactory.textPadding(textPaddingPx),
            PropertyFactory.iconPadding(iconPaddingPx),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.textColor(AndroidPoiIcons.textColorExpression(isDarkTheme)),
            PropertyFactory.textHaloColor(AndroidPoiIcons.textHaloColorHex(isDarkTheme)),
            PropertyFactory.textHaloWidth(1.5f),
        )
        if (sortKeyJson != null) {
            setProperties(PropertyFactory.symbolSortKey(Expression.raw(sortKeyJson)))
        }
    }

    private fun insertLayer(style: Style, layer: Layer, anchorLayerId: String?) {
        if (anchorLayerId != null) {
            style.addLayerBelow(layer, anchorLayerId)
        } else {
            style.addLayer(layer)
        }
    }

    /**
     * Updates theme state and refreshes layer paint properties and POI sprites to match tokens.
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

            if (style.getSource(POI_SOURCE_ID) != null) {
                style.addImages(HashMap(AndroidPoiIcons.createPoiIconBitmaps(isDark)))
            }
            val poiTextColor = AndroidPoiIcons.textColorExpression(isDark)
            val poiHaloColor = AndroidPoiIcons.textHaloColorHex(isDark)
            for (poiLayerId in POI_LAYER_IDS) {
                (style.getLayer(poiLayerId) as? SymbolLayer)?.setProperties(
                    PropertyFactory.textColor(poiTextColor),
                    PropertyFactory.textHaloColor(poiHaloColor),
                )
            }
        }
    }

    override fun setViewport(center: GeoCoordinates, zoom: Double) {
        currentViewport = center to zoom
        applyViewport(center, zoom, animate = true)
    }

    /** Move the selected point under the crosshair while retaining the live native camera. */
    fun recenterPreservingZoom(center: GeoCoordinates) {
        val map = activeMap ?: return
        val camera = CameraPosition.Builder(map.cameraPosition)
            .target(center.toLatLng())
            .build()
        currentViewport = center to camera.zoom
        map.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
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

        if (style.getImage(PIN_IMAGE_ID) == null) {
            style.addImage(PIN_IMAGE_ID, destinationPinBitmap())
        }
        if (style.getLayer(PIN_LAYER_ID) == null) {
            style.addLayer(SymbolLayer(PIN_LAYER_ID, PIN_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(PIN_IMAGE_ID),
                PropertyFactory.iconAnchor(ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ))
        }
    }

    /** Runtime vector artwork keeps the destination visibly different from the real blue dot. */
    private fun destinationPinBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(40, 52, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(20f, 50f)
            cubicTo(15f, 40f, 2f, 30f, 2f, 20f)
            cubicTo(2f, -4f, 38f, -4f, 38f, 20f)
            cubicTo(38f, 30f, 25f, 40f, 20f, 50f)
            close()
        }
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 59, 48)
        canvas.drawPath(path, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(20f, 19f, 6f, paint)
        return bitmap
    }

    fun showUserPosition(position: MapUserPosition?) {
        currentUserPosition = position
        val style = getActiveStyle() ?: return
        if (position == null) {
            style.removeLayer(USER_DOT_LAYER_ID)
            style.removeLayer(USER_ACCURACY_LAYER_ID)
            style.removeSource(USER_SOURCE_ID)
            style.removeSource(USER_ACCURACY_SOURCE_ID)
        } else {
            applyUserPosition(position, style)
        }
    }

    fun resetNorth() {
        val map = activeMap ?: return
        val camera = CameraPosition.Builder(map.cameraPosition).bearing(0.0).build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
    }

    private fun applyUserPosition(position: MapUserPosition, style: Style) {
        val coordinates = position.coordinates
        val point = Feature.fromGeometry(Point.fromLngLat(coordinates.longitude, coordinates.latitude))
        val dotSource = style.getSource(USER_SOURCE_ID) as? GeoJsonSource
        if (dotSource == null) style.addSource(GeoJsonSource(USER_SOURCE_ID, point)) else dotSource.setGeoJson(point)

        val ring = calculateGeofencePolygon(coordinates, position.accuracyMeters)
            .map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        ring.add(ring.first())
        val accuracy = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        val accuracySource = style.getSource(USER_ACCURACY_SOURCE_ID) as? GeoJsonSource
        if (accuracySource == null) {
            style.addSource(GeoJsonSource(USER_ACCURACY_SOURCE_ID, accuracy))
        } else {
            accuracySource.setGeoJson(accuracy)
        }
        if (style.getLayer(USER_ACCURACY_LAYER_ID) == null) {
            val halo = FillLayer(USER_ACCURACY_LAYER_ID, USER_ACCURACY_SOURCE_ID).withProperties(
                PropertyFactory.fillColor("#007AFF"),
                PropertyFactory.fillOpacity(0.15f),
            )
            if (style.getLayer(PIN_LAYER_ID) != null) style.addLayerBelow(halo, PIN_LAYER_ID) else style.addLayer(halo)
        }
        if (style.getLayer(USER_DOT_LAYER_ID) == null) {
            val dot = CircleLayer(USER_DOT_LAYER_ID, USER_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#007AFF"),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3f),
            )
            if (style.getLayer(PIN_LAYER_ID) != null) style.addLayerBelow(dot, PIN_LAYER_ID) else style.addLayer(dot)
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
        val needsFill = style.getLayer(GEOFENCE_FILL_LAYER_ID) == null
        val needsStroke = style.getLayer(GEOFENCE_STROKE_LAYER_ID) == null
        val anchor = if (needsFill || needsStroke) {
            resolveGeofenceAnchorLayerId(
                style.layers.map { StyleLayerDescriptor(id = it.id, isSymbol = it is SymbolLayer) },
            )
        } else {
            null
        }

        if (needsFill) {
            val fillLayer = FillLayer(GEOFENCE_FILL_LAYER_ID, GEOFENCE_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(primaryColor),
                PropertyFactory.fillOpacity(0.2f),
                PropertyFactory.fillAntialias(true),
            )
            insertLayer(style, fillLayer, anchor)
        }

        if (needsStroke) {
            val strokeLayer = LineLayer(GEOFENCE_STROKE_LAYER_ID, GEOFENCE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(primaryColor),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.8f),
            )
            insertLayer(style, strokeLayer, anchor)
        }
    }

    companion object {
        data class StyleLayerDescriptor(val id: String, val isSymbol: Boolean)

        const val POI_SOURCE_ID = "protomaps"
        const val POI_SOURCE_LAYER = "pois"
        const val ROADNAME_MINOR_LAYER_ID = "roadname_minor"

        const val POI_MAJOR_LAYER_ID = "fjalar-poi-major"
        const val POI_NEIGHBORHOOD_LAYER_ID = "fjalar-poi-neighborhood"
        const val POI_LOCAL_LAYER_ID = "fjalar-poi-local"

        val POI_MAJOR_FILTER: String = AndroidPoiIcons.tierFilterJson(AndroidPoiIcons.MAJOR_TIER_KINDS)
        val POI_NEIGHBORHOOD_FILTER: String = AndroidPoiIcons.tierFilterJson(AndroidPoiIcons.NEIGHBORHOOD_TIER_KINDS)
        val POI_LOCAL_FILTER: String = AndroidPoiIcons.tierFilterJson(AndroidPoiIcons.LOCAL_TIER_KINDS)

        private val POI_MAJOR_FONT = arrayOf(
            "Montserrat SemiBold",
            "Open Sans Bold",
            "Noto Sans Regular",
            "Noto Sans CJK JP Regular",
        )
        private val POI_DETAIL_FONT = arrayOf(
            "Montserrat Medium",
            "Open Sans Regular",
            "Noto Sans Regular",
            "Noto Sans CJK JP Regular",
        )
        private const val POI_MAJOR_SORT_KEY =
            """["case",["==",["get","kind"],"hospital"],0,["in",["get","kind"],["literal",["station","railway_station","subway_entrance","train_station_entrance","light_rail","ferry_terminal","aerodrome","airport"]]],1,["in",["get","kind"],["literal",["bus_station","university"]]],2,3]"""

        const val USER_SOURCE_ID = "fjalar-user-source"
        const val USER_DOT_LAYER_ID = "fjalar-user-dot"
        const val USER_ACCURACY_SOURCE_ID = "fjalar-user-accuracy-source"
        const val USER_ACCURACY_LAYER_ID = "fjalar-user-accuracy"
        const val PIN_IMAGE_ID = "fjalar-destination-pin"
        const val PIN_SOURCE_ID = "fjalar-pin-source"
        const val PIN_LAYER_ID = "fjalar-pin-layer"
        const val GEOFENCE_SOURCE_ID = "fjalar-geofence-source"
        const val GEOFENCE_FILL_LAYER_ID = "fjalar-geofence-fill-layer"
        const val GEOFENCE_STROKE_LAYER_ID = "fjalar-geofence-stroke-layer"

        private val POI_LAYER_IDS = listOf(
            POI_MAJOR_LAYER_ID,
            POI_NEIGHBORHOOD_LAYER_ID,
            POI_LOCAL_LAYER_ID,
        )

        private val TOP_OVERLAY_ANCHOR_ORDER = listOf(
            USER_ACCURACY_LAYER_ID,
            USER_DOT_LAYER_ID,
            PIN_LAYER_ID,
        )

        private val MANAGED_OVERLAY_LAYER_IDS = setOf(
            GEOFENCE_FILL_LAYER_ID,
            GEOFENCE_STROKE_LAYER_ID,
            USER_ACCURACY_LAYER_ID,
            USER_DOT_LAYER_ID,
            PIN_LAYER_ID,
            POI_MAJOR_LAYER_ID,
            POI_NEIGHBORHOOD_LAYER_ID,
            POI_LOCAL_LAYER_ID,
        )

        private val FALLBACK_OVERLAY_ANCHOR_ORDER = listOf(
            USER_ACCURACY_LAYER_ID,
            USER_DOT_LAYER_ID,
            PIN_LAYER_ID,
            POI_LOCAL_LAYER_ID,
            POI_NEIGHBORHOOD_LAYER_ID,
            POI_MAJOR_LAYER_ID,
        )

        private val FALLBACK_OVERLAY_ANCHOR_IDS = FALLBACK_OVERLAY_ANCHOR_ORDER.toSet()

        /**
         * Selects the style layer ID immediately below which geofence fill and stroke layers
         * should be inserted. Anchors below the base style's trailing symbol-label block so
         * early in-geometry symbols (such as Positron's `waterway_label` below roads/buildings)
         * do not trap geofence polygons underneath base geometry.
         */
        fun resolveGeofenceAnchorLayerId(layers: List<StyleLayerDescriptor>): String? {
            val baseLayers = layers.filterNot { it.id in MANAGED_OVERLAY_LAYER_IDS }
            val lastNonSymbolIndex = baseLayers.indexOfLast { !it.isSymbol }
            val trailingSymbolAnchor = baseLayers.getOrNull(lastNonSymbolIndex + 1)?.id
            if (trailingSymbolAnchor != null) {
                return trailingSymbolAnchor
            }

            return layers.firstOrNull { it.id in FALLBACK_OVERLAY_ANCHOR_IDS }?.id
        }

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
