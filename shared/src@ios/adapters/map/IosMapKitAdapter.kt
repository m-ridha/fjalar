package com.muhammadridha.fjalar.adapters.map

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.outbound.MapRendererPort
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2D
import platform.MapKit.MKCircle
import platform.MapKit.MKCoordinateRegion
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.addOverlay
import platform.MapKit.removeOverlay
import platform.UIKit.UIColor
import kotlin.math.pow

/**
 * iOS Apple MapKit adapter implementing [MapRendererPort].
 *
 * Coordinates map viewport updates, destination pin placement, and geofence radius circle overlays
 * using Apple MapKit (`MKMapView`, `MKCoordinateRegion`, `MKPointAnnotation`, `MKCircle`).
 */
@OptIn(ExperimentalForeignApi::class)
class IosMapKitAdapter(
    var isDarkTheme: Boolean = false,
) : MapRendererPort {

    var activeMap: MKMapView? = null
        private set

    var currentViewport: Pair<GeoCoordinates, Double>? = null
        private set

    var currentPin: GeoCoordinates? = null
        private set

    var currentGeofence: Pair<GeoCoordinates, TriggerRadius>? = null
        private set

    private var activeAnnotation: MKPointAnnotation? = null
    private var activeOverlay: MKCircle? = null

    /**
     * Attaches an initialized [MKMapView] to this adapter, immediately applying any pending
     * viewport, marker, or geofence state.
     */
    fun attachMap(map: MKMapView) {
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
        val map = activeMap
        val annotation = activeAnnotation
        val overlay = activeOverlay
        if (map != null) {
            if (annotation != null) map.removeAnnotation(annotation)
            if (overlay != null) map.removeOverlay(overlay)
        }
        activeAnnotation = null
        activeOverlay = null
        activeMap = null
    }

    /**
     * Updates theme state and refreshes geofence overlay if needed.
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
        val annotation = activeAnnotation
        val overlay = activeOverlay
        if (map != null) {
            if (annotation != null) map.removeAnnotation(annotation)
            if (overlay != null) map.removeOverlay(overlay)
        }
        activeAnnotation = null
        activeOverlay = null
    }

    fun removePin() {
        currentPin = null
        val map = activeMap
        val annotation = activeAnnotation
        if (map != null && annotation != null) {
            map.removeAnnotation(annotation)
        }
        activeAnnotation = null
    }

    fun removeGeofence() {
        currentGeofence = null
        val map = activeMap
        val overlay = activeOverlay
        if (map != null && overlay != null) {
            map.removeOverlay(overlay)
        }
        activeOverlay = null
    }

    private fun applyViewport(center: GeoCoordinates, zoom: Double, animate: Boolean) {
        val map = activeMap ?: return
        val centerCoord = center.toCLLocationCoordinate2D()
        val (latDelta, lonDelta) = zoomToSpan(zoom)
        val region = cValue<MKCoordinateRegion> {
            this.center.latitude = centerCoord.useContents { latitude }
            this.center.longitude = centerCoord.useContents { longitude }
            this.span.latitudeDelta = latDelta
            this.span.longitudeDelta = lonDelta
        }
        (map.delegate as? FjalarMapKitDelegate)?.beginProgrammaticViewportChange()
        map.setRegion(region, animated = animate)
    }

    private fun applyPin(coordinates: GeoCoordinates) {
        val map = activeMap ?: return
        val target = coordinates.toCLLocationCoordinate2D()
        val annotation = activeAnnotation
        if (annotation != null) {
            annotation.setCoordinate(target)
        } else {
            val newAnnotation = MKPointAnnotation()
            newAnnotation.setCoordinate(target)
            newAnnotation.setTitle("Destination")
            activeAnnotation = newAnnotation
            map.addAnnotation(newAnnotation)
        }
    }

    private fun applyGeofence(center: GeoCoordinates, radius: TriggerRadius) {
        val map = activeMap ?: return
        val existingOverlay = activeOverlay
        if (existingOverlay != null) {
            map.removeOverlay(existingOverlay)
        }

        val centerCoord = center.toCLLocationCoordinate2D()
        val circle = MKCircle.circleWithCenterCoordinate(
            coord = centerCoord,
            radius = radius.meters.toDouble()
        )
        activeOverlay = circle
        map.addOverlay(circle)
    }

    /**
     * Pure color token representation matching Fjalar design tokens.
     */
    data class ColorToken(
        val red: Double,
        val green: Double,
        val blue: Double,
        val alpha: Double,
    ) {
        fun toUIColor(): UIColor =
            UIColor.colorWithRed(red = red, green = green, blue = blue, alpha = alpha)
    }

    companion object {
        const val GEOFENCE_STROKE_WIDTH: Double = 2.0
        const val GEOFENCE_FILL_ALPHA: Double = 0.20
        const val GEOFENCE_STROKE_ALPHA: Double = 0.80

        // Light mode: Primary #007AFF (R: 0/255, G: 122/255, B: 255/255)
        val LIGHT_PRIMARY = ColorToken(red = 0.0, green = 122.0 / 255.0, blue = 1.0, alpha = 1.0)
        val LIGHT_GEOFENCE_FILL = LIGHT_PRIMARY.copy(alpha = GEOFENCE_FILL_ALPHA)
        val LIGHT_GEOFENCE_STROKE = LIGHT_PRIMARY.copy(alpha = GEOFENCE_STROKE_ALPHA)

        // Dark mode: Primary #00D2FF (R: 0/255, G: 210/255, B: 255/255)
        val DARK_PRIMARY = ColorToken(red = 0.0, green = 210.0 / 255.0, blue = 1.0, alpha = 1.0)
        val DARK_GEOFENCE_FILL = DARK_PRIMARY.copy(alpha = GEOFENCE_FILL_ALPHA)
        val DARK_GEOFENCE_STROKE = DARK_PRIMARY.copy(alpha = GEOFENCE_STROKE_ALPHA)

        fun getGeofenceColors(isDark: Boolean): Pair<ColorToken, ColorToken> =
            if (isDark) {
                DARK_GEOFENCE_FILL to DARK_GEOFENCE_STROKE
            } else {
                LIGHT_GEOFENCE_FILL to LIGHT_GEOFENCE_STROKE
            }

        fun getGeofenceUiColors(isDark: Boolean): Pair<UIColor, UIColor> {
            val (fill, stroke) = getGeofenceColors(isDark)
            return fill.toUIColor() to stroke.toUIColor()
        }

        fun getPrimaryPinColor(isDark: Boolean): ColorToken =
            if (isDark) DARK_PRIMARY else LIGHT_PRIMARY

        fun getPrimaryUiColor(isDark: Boolean): UIColor =
            getPrimaryPinColor(isDark).toUIColor()

        fun GeoCoordinates.toCLLocationCoordinate2D(): CValue<CLLocationCoordinate2D> =
            cValue {
                latitude = this@toCLLocationCoordinate2D.latitude
                longitude = this@toCLLocationCoordinate2D.longitude
            }

        fun CValue<CLLocationCoordinate2D>.toGeoCoordinates(): GeoCoordinates =
            useContents {
                GeoCoordinates(
                    latitude = latitude.coerceIn(-90.0, 90.0),
                    longitude = normalizeLongitude(longitude),
                )
            }

        fun CLLocationCoordinate2D.toGeoCoordinates(): GeoCoordinates =
            GeoCoordinates(
                latitude = latitude.coerceIn(-90.0, 90.0),
                longitude = normalizeLongitude(longitude),
            )

        fun normalizeLongitude(lon: Double): Double {
            var l = lon
            while (l < -180.0) l += 360.0
            while (l > 180.0) l -= 360.0
            return l
        }

        /**
         * Maps a Web Mercator zoom level (typically 1.0 to 20.0) to MapKit coordinate span delta degrees.
         */
        fun zoomToSpan(zoom: Double): Pair<Double, Double> {
            val clampedZoom = zoom.coerceIn(1.0, 20.0)
            val span = 360.0 / 2.0.pow(clampedZoom)
            val latSpan = span.coerceIn(0.0001, 180.0)
            val lonSpan = span.coerceIn(0.0001, 360.0)
            return latSpan to lonSpan
        }
    }
}
