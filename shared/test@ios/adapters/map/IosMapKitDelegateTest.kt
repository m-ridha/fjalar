package com.muhammadridha.fjalar.adapters.map

import com.muhammadridha.fjalar.adapters.map.IosMapKitAdapter.Companion.toCLLocationCoordinate2D
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKAnnotationViewDragStateEnding
import platform.MapKit.MKAnnotationViewDragStateDragging
import platform.MapKit.MKMapViewDelegateProtocol
import platform.objc.sel_registerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosMapKitDelegateTest {
    @Test
    fun browseDestinationIsNotDraggableAndIgnoresDragCallbacks() {
        val dragged = mutableListOf<GeoCoordinates>()
        val delegate = FjalarMapKitDelegate(false, { dragged.add(it) }, null)
        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        val annotation = MKPointAnnotation().apply {
            setCoordinate(GeoCoordinates(51.5, -0.1).toCLLocationCoordinate2D())
        }
        val view = assertNotNull(delegate.mapView(map, viewForAnnotation = annotation))
        assertFalse(view.draggable)
        delegate.mapView(map, view, MKAnnotationViewDragStateEnding, MKAnnotationViewDragStateDragging)
        assertTrue(dragged.isEmpty())
    }

    @Test
    fun pickerDestinationRemainsDraggableAndReportsDragEnd() {
        val dragged = mutableListOf<GeoCoordinates>()
        val delegate = FjalarMapKitDelegate(false, { dragged.add(it) }, null, destinationEditingEnabled = true)
        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        val coordinates = GeoCoordinates(51.5, -0.1)
        val annotation = MKPointAnnotation().apply { setCoordinate(coordinates.toCLLocationCoordinate2D()) }
        val view = assertNotNull(delegate.mapView(map, viewForAnnotation = annotation))
        assertTrue(view.draggable)
        delegate.mapView(map, view, MKAnnotationViewDragStateEnding, MKAnnotationViewDragStateDragging)
        assertEquals(listOf(coordinates), dragged)
    }

    @Test
    fun compassReceivesTheLiveNativeHeading() {
        val headings = mutableListOf<Double>()
        val delegate = FjalarMapKitDelegate(false, null, null)
        delegate.onBearingChanged = { headings.add(it) }
        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        val adapter = IosMapKitAdapter()
        adapter.attachMap(map)
        adapter.setViewport(GeoCoordinates(51.5, -0.1), 15.0)
        val camera = map.camera
        camera.heading = 120.0
        map.setCamera(camera, animated = false)
        delegate.mapViewDidChangeVisibleRegion(map)
        assertEquals(120.0, headings.last(), 0.001)
        adapter.detachMap()
    }

    @Test
    fun visibleRegionChangesReportEachCenterWithoutWaitingForMovementToEnd() {
        val reportedCenters = mutableListOf<GeoCoordinates>()
        val userGestures = mutableListOf<Boolean>()
        val delegate = FjalarMapKitDelegate(
            isDarkTheme = false,
            onPinDrag = null,
            onCameraCenterChanged = { coordinates, isUserGesture ->
                reportedCenters.add(coordinates)
                userGestures.add(isUserGesture)
            },
        )
        assertTrue(
            delegate.respondsToSelector(sel_registerName("mapViewDidChangeVisibleRegion:")),
            "The picker needs continuous camera updates before deceleration ends",
        )

        val map = MKMapView(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        val callbacks: MKMapViewDelegateProtocol = delegate
        val centers = listOf(
            GeoCoordinates(-6.1376, 106.8146),
            GeoCoordinates(-6.1380, 106.8150),
        )
        for (center in centers) {
            map.setCenterCoordinate(center.toCLLocationCoordinate2D(), animated = false)
            callbacks.mapViewDidChangeVisibleRegion(map)
            assertEquals(center.latitude, reportedCenters.last().latitude, 0.000001)
            assertEquals(center.longitude, reportedCenters.last().longitude, 0.000001)
        }
        assertEquals(centers.size, reportedCenters.size)
        assertTrue(userGestures.none { it }, "Programmatic camera initialization cannot select a destination")
    }
}
