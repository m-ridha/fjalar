package com.muhammadridha.fjalar.adapters.map

import com.muhammadridha.fjalar.adapters.map.IosMapKitAdapter.Companion.toCLLocationCoordinate2D
import com.muhammadridha.fjalar.adapters.map.IosMapKitAdapter.Companion.toGeoCoordinates
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.MapKit.overlays
import platform.MapKit.MKMapView
import platform.CoreGraphics.CGRectMake
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosMapKitAdapterTest {

    @Test
    fun separateLiveDotAndAccuracyHaloPreserveDestinationAcrossFixesAndPermissionLoss() {
        val adapter = IosMapKitAdapter()
        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        map.delegate = FjalarMapKitDelegate(false, null, null)
        adapter.attachMap(map)
        val destination = GeoCoordinates(51.51, -0.11)
        adapter.placePin(destination)
        val pin = map.annotations.filterIsInstance<platform.MapKit.MKPointAnnotation>().single()
        adapter.showUserPosition(MapUserPosition(GeoCoordinates(51.5, -0.1), 25.0))
        assertFalse(map.showsUserLocation, "Shared location ownership must not start a second native stream")
        val dot = map.annotations.filterIsInstance<FjalarUserPositionAnnotation>().single()
        val delegate = map.delegate as FjalarMapKitDelegate
        assertTrue(delegate.mapView(mapView = map, viewForAnnotation = pin) is platform.MapKit.MKMarkerAnnotationView)
        val dotView = delegate.mapView(mapView = map, viewForAnnotation = dot)!!
        assertFalse(dotView is platform.MapKit.MKMarkerAnnotationView, "User position is a dot, never a destination pin")
        assertFalse(dotView.draggable)
        val halo = map.overlays.filterIsInstance<platform.MapKit.MKCircle>().single()
        assertEquals(25.0, halo.radius)
        val renderer = delegate.mapView(mapView = map, rendererForOverlay = halo) as platform.MapKit.MKCircleRenderer
        assertEquals(0.0, renderer.lineWidth, "Accuracy halo must not use the destination geofence stroke")
        adapter.showUserPosition(MapUserPosition(GeoCoordinates(51.501, -0.101), 40.0))
        assertEquals(destination, adapter.currentPin)
        assertTrue(map.annotations.any { it == pin })
        assertEquals(40.0, map.overlays.filterIsInstance<platform.MapKit.MKCircle>().single().radius)
        adapter.showUserPosition(null)
        assertEquals(listOf(pin), map.annotations)
        assertTrue(map.overlays.isEmpty())
        adapter.detachMap()
    }

    @Test
    fun northResetPreservesNativeCenterAndZoomAndReportsProgrammaticCamera() {
        val adapter = IosMapKitAdapter()
        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        val gestures = mutableListOf<Boolean>()
        val delegate = FjalarMapKitDelegate(false, null, { _, userGesture -> gestures.add(userGesture) })
        map.delegate = delegate
        adapter.attachMap(map)
        adapter.setViewport(GeoCoordinates(-33.86, 151.2), 14.0)
        val rotated = map.camera
        rotated.heading = 75.0
        map.setCamera(rotated, animated = false)
        assertEquals(75.0, map.camera.heading, 0.001, "Test must start with a rotated map")
        val centerBefore = map.centerCoordinate.toGeoCoordinates()
        val altitudeBefore = map.camera.altitude
        adapter.resetNorth()
        assertEquals(0.0, map.camera.heading, 0.001)
        assertEquals(centerBefore.latitude, map.centerCoordinate.toGeoCoordinates().latitude, 0.000001)
        assertEquals(centerBefore.longitude, map.centerCoordinate.toGeoCoordinates().longitude, 0.000001)
        assertEquals(altitudeBefore, map.camera.altitude, 0.001)
        delegate.mapViewDidChangeVisibleRegion(map)
        assertFalse(gestures.last(), "Compass must not create a picker selection")
        adapter.detachMap()
    }


    @Test
    fun initialViewportIsAppliedBeforeTheMapEntersAWindow() {
        val adapter = IosMapKitAdapter(isDarkTheme = false)
        val mapView = MKMapView()
        adapter.attachMap(mapView)

        val destination = GeoCoordinates(-6.2088, 106.8456)
        adapter.setViewport(destination, 15.0)
        mapView.setFrame(CGRectMake(0.0, 0.0, 390.0, 844.0))
        mapView.layoutIfNeeded()

        mapView.region.useContents {
            assertEquals(destination.latitude, center.latitude, 0.001)
            assertEquals(destination.longitude, center.longitude, 0.001)
            assertTrue(span.latitudeDelta < 0.1, "Initial camera must show the destination neighborhood")
        }
        adapter.detachMap()
    }

    @Test
    fun tappedSelectionPreservesLiveZoomAfterStartingAtWorldOverview() {
        val adapter = IosMapKitAdapter()
        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        adapter.attachMap(map)
        adapter.setViewport(GeoCoordinates(0.0, 0.0), 2.0)
        // A native zoom gesture changes the map independently of the last requested zoom.
        val zoomedRegion = kotlinx.cinterop.cValue<platform.MapKit.MKCoordinateRegion> {
            center.latitude = 51.5
            center.longitude = -0.1
            span.latitudeDelta = 0.005
            span.longitudeDelta = 0.005
        }
        map.setRegion(zoomedRegion, animated = false)
        val spanBeforeTap = map.region.useContents { span.longitudeDelta }
        assertTrue(spanBeforeTap < 0.1)
        val tappedPoint = GeoCoordinates(51.501, -0.099)
        adapter.recenterPreservingZoom(tappedPoint)
        map.region.useContents {
            assertEquals(spanBeforeTap, span.longitudeDelta, 0.000001)
            assertEquals(tappedPoint.latitude, center.latitude, 0.000001)
            assertEquals(tappedPoint.longitude, center.longitude, 0.000001)
        }
        adapter.detachMap()
    }

    @Test
    fun coordinateConversion_preservesValidCoordinates() {
        val original = GeoCoordinates(latitude = -6.2088, longitude = 106.8456)
        val clCoord = original.toCLLocationCoordinate2D()

        clCoord.useContents {
            assertEquals(-6.2088, latitude, 0.00001)
            assertEquals(106.8456, longitude, 0.00001)
        }

        val roundTrip = clCoord.toGeoCoordinates()
        assertEquals(original.latitude, roundTrip.latitude, 0.00001)
        assertEquals(original.longitude, roundTrip.longitude, 0.00001)
    }

    @Test
    fun coordinateConversion_clampsLatitudeAndNormalizesLongitude() {
        val outOfBounds = kotlinx.cinterop.cValue<platform.CoreLocation.CLLocationCoordinate2D> {
            latitude = 95.0
            longitude = 190.0
        }

        val converted = outOfBounds.toGeoCoordinates()
        assertEquals(90.0, converted.latitude, 0.0001)
        assertEquals(-170.0, converted.longitude, 0.0001)

        val negativeOutOfBounds = kotlinx.cinterop.cValue<platform.CoreLocation.CLLocationCoordinate2D> {
            latitude = -95.0
            longitude = -190.0
        }
        val negativeConverted = negativeOutOfBounds.toGeoCoordinates()
        assertEquals(-90.0, negativeConverted.latitude, 0.0001)
        assertEquals(170.0, negativeConverted.longitude, 0.0001)
    }

    @Test
    fun zoomToSpan_decreasesSpanAsZoomIncreases() {
        val (latSpan1, lonSpan1) = IosMapKitAdapter.zoomToSpan(1.0)
        val (latSpan10, lonSpan10) = IosMapKitAdapter.zoomToSpan(10.0)
        val (latSpan14, lonSpan14) = IosMapKitAdapter.zoomToSpan(14.0)
        val (latSpan18, lonSpan18) = IosMapKitAdapter.zoomToSpan(18.0)

        // Monotonic decrease: higher zoom level must show smaller area
        assertTrue(latSpan1 > latSpan10)
        assertTrue(latSpan10 > latSpan14)
        assertTrue(latSpan14 > latSpan18)

        assertTrue(lonSpan1 > lonSpan10)
        assertTrue(lonSpan10 > lonSpan14)
        assertTrue(lonSpan14 > lonSpan18)

        // Clamping check
        val (minLatSpan, minLonSpan) = IosMapKitAdapter.zoomToSpan(25.0) // clamped to 20
        val (at20LatSpan, at20LonSpan) = IosMapKitAdapter.zoomToSpan(20.0)
        assertEquals(at20LatSpan, minLatSpan, 0.000001)
        assertEquals(at20LonSpan, minLonSpan, 0.000001)

        val (maxLatSpan, maxLonSpan) = IosMapKitAdapter.zoomToSpan(-5.0) // clamped to 1
        val (at1LatSpan, at1LonSpan) = IosMapKitAdapter.zoomToSpan(1.0)
        assertEquals(at1LatSpan, maxLatSpan, 0.000001)
        assertEquals(at1LonSpan, maxLonSpan, 0.000001)
    }

    @Test
    fun spanToZoom_roundTripsZoomToSpan() {
        for (zoom in listOf(2.0, 13.5, 15.0, 20.0)) {
            val (_, lonSpan) = IosMapKitAdapter.zoomToSpan(zoom)
            assertEquals(zoom, IosMapKitAdapter.spanToZoom(lonSpan), 0.000001)
        }
        // Out-of-range spans clamp to the supported zoom range.
        assertEquals(20.0, IosMapKitAdapter.spanToZoom(0.0000001), 0.000001)
        assertEquals(1.0, IosMapKitAdapter.spanToZoom(360.0), 0.000001)
    }

    @Test
    fun appliedViewportKeepsTheRequestedZoomAfterMapKitFitsTheView() {
        val frames = listOf(
            CGRectMake(0.0, 0.0, 390.0, 844.0),
            CGRectMake(0.0, 0.0, 844.0, 390.0),
        )
        for (frame in frames) {
            val adapter = IosMapKitAdapter()
            val map = MKMapView(frame = frame)
            adapter.attachMap(map)

            adapter.setViewport(GeoCoordinates(-6.2088, 106.8456), 15.0)

            val restoredZoom = map.region.useContents {
                IosMapKitAdapter.spanToZoom(span.longitudeDelta)
            }
            assertEquals(15.0, restoredZoom, 0.01, "Restored viewport must keep the requested zoom")
            adapter.detachMap()
        }
    }

    @Test
    fun appliedViewportMatchesTheProjectedAspectRatioAtHighLatitude() {
        for (latitude in listOf(60.0, 70.0)) {
            val adapter = IosMapKitAdapter()
            val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
            adapter.attachMap(map)

            adapter.setViewport(GeoCoordinates(latitude, 10.0), 3.0)

            val longitudeSpan = map.region.useContents { span.longitudeDelta }

            val restoredZoom = IosMapKitAdapter.spanToZoom(longitudeSpan)
            assertEquals(3.0, restoredZoom, 0.01, "Restored viewport must keep the requested zoom at $latitude°")
            adapter.detachMap()
        }
    }

    @Test
    fun stateManagement_tracksViewportPinAndGeofence() {
        val adapter = IosMapKitAdapter(isDarkTheme = false)

        assertNull(adapter.currentViewport)
        assertNull(adapter.currentPin)
        assertNull(adapter.currentGeofence)

        // Set viewport
        val center = GeoCoordinates(-6.1754, 106.8272)
        adapter.setViewport(center, 15.0)
        assertEquals(center, adapter.currentViewport?.first)
        assertEquals(15.0, adapter.currentViewport?.second)

        // Place pin
        val pin = GeoCoordinates(-6.2088, 106.8456)
        adapter.placePin(pin)
        assertEquals(pin, adapter.currentPin)

        // Show geofence
        adapter.showGeofence(pin, TriggerRadius(500))
        assertEquals(pin, adapter.currentGeofence?.first)
        assertEquals(TriggerRadius(500), adapter.currentGeofence?.second)

        // Remove pin retains geofence and viewport
        adapter.removePin()
        assertNull(adapter.currentPin)
        assertEquals(pin, adapter.currentGeofence?.first)
        assertEquals(center, adapter.currentViewport?.first)

        // Remove geofence retains viewport
        adapter.removeGeofence()
        assertNull(adapter.currentGeofence)
        assertEquals(center, adapter.currentViewport?.first)

        // Place pin and geofence again, then clearOverlays
        adapter.placePin(pin)
        adapter.showGeofence(pin, TriggerRadius(1000))
        adapter.clearOverlays()
        assertNull(adapter.currentPin)
        assertNull(adapter.currentGeofence)
        assertEquals(center, adapter.currentViewport?.first)
    }

    @Test
    fun themeColors_selectsFjalarTokens() {
        // Light Theme: Primary #007AFF
        val (lightFill, lightStroke) = IosMapKitAdapter.getGeofenceColors(isDark = false)
        assertEquals(0.0, lightFill.red, 0.001)
        assertEquals(122.0 / 255.0, lightFill.green, 0.001)
        assertEquals(1.0, lightFill.blue, 0.001)
        assertEquals(0.20, lightFill.alpha, 0.001)

        assertEquals(0.0, lightStroke.red, 0.001)
        assertEquals(122.0 / 255.0, lightStroke.green, 0.001)
        assertEquals(1.0, lightStroke.blue, 0.001)
        assertEquals(0.80, lightStroke.alpha, 0.001)

        val lightPin = IosMapKitAdapter.getPrimaryPinColor(isDark = false)
        assertEquals(1.0, lightPin.alpha, 0.001)
        assertEquals(0.0, lightPin.red, 0.001)
        assertEquals(122.0 / 255.0, lightPin.green, 0.001)
        assertEquals(1.0, lightPin.blue, 0.001)

        // Dark Theme: Primary #00D2FF
        val (darkFill, darkStroke) = IosMapKitAdapter.getGeofenceColors(isDark = true)
        assertEquals(0.0, darkFill.red, 0.001)
        assertEquals(210.0 / 255.0, darkFill.green, 0.001)
        assertEquals(1.0, darkFill.blue, 0.001)
        assertEquals(0.20, darkFill.alpha, 0.001)

        assertEquals(0.0, darkStroke.red, 0.001)
        assertEquals(210.0 / 255.0, darkStroke.green, 0.001)
        assertEquals(1.0, darkStroke.blue, 0.001)
        assertEquals(0.80, darkStroke.alpha, 0.001)

        val darkPin = IosMapKitAdapter.getPrimaryPinColor(isDark = true)
        assertEquals(1.0, darkPin.alpha, 0.001)
        assertEquals(0.0, darkPin.red, 0.001)
        assertEquals(210.0 / 255.0, darkPin.green, 0.001)
        assertEquals(1.0, darkPin.blue, 0.001)

        // Adapter theme toggle
        val adapter = IosMapKitAdapter(isDarkTheme = false)
        assertFalse(adapter.isDarkTheme)
        adapter.updateTheme(true)
        assertTrue(adapter.isDarkTheme)
    }

    @Test
    fun geofenceOverlayParameters_calculatesValidRadiusValues() {
        for (preset in TriggerRadius.PRESETS) {
            assertTrue(preset.meters > 0, "Preset ${preset.meters}m must be positive")
            assertTrue(
                preset.meters in TriggerRadius.MIN_METERS..TriggerRadius.MAX_METERS,
                "Preset ${preset.meters}m must be within valid range",
            )
        }
    }

    @Test
    fun adapterWithActiveMapView_updatesOverlaysAndAnnotationsWithoutThrowing() {
        val adapter = IosMapKitAdapter(isDarkTheme = false)
        val mapView = MKMapView()

        adapter.attachMap(mapView)
        assertEquals(mapView, adapter.activeMap)

        val center = GeoCoordinates(-6.1754, 106.8272)
        adapter.setViewport(center, 15.0)
        assertEquals(center, adapter.currentViewport?.first)
        assertEquals(15.0, adapter.currentViewport?.second)

        val pin = GeoCoordinates(-6.2088, 106.8456)
        adapter.placePin(pin)
        assertEquals(pin, adapter.currentPin)

        val radius = TriggerRadius(500)
        adapter.showGeofence(pin, radius)
        assertEquals(pin, adapter.currentGeofence?.first)
        assertEquals(radius, adapter.currentGeofence?.second)

        adapter.detachMap()
        assertNull(adapter.activeMap)
    }
}
