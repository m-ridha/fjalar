package com.muhammadridha.fjalar.adapters.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.muhammadridha.fjalar.adapters.map.IosMapKitAdapter.Companion.toGeoCoordinates
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKAnnotationViewDragState
import platform.MapKit.MKAnnotationViewDragStateEnding
import platform.MapKit.MKCircle
import platform.MapKit.MKCircleRenderer
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKUserLocation
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.darwin.NSObject
import platform.objc.sel_registerName

/**
 * Custom [MKMapViewDelegateProtocol] handling geofence circle rendering,
 * draggable destination pins, and pin drag state changes.
 */
@OptIn(ExperimentalForeignApi::class)
internal class FjalarMapKitDelegate(
    var isDarkTheme: Boolean,
    var onPinDrag: ((GeoCoordinates) -> Unit)?,
    var onCameraCenterChanged: ((GeoCoordinates, Boolean) -> Unit)?,
    var destinationEditingEnabled: Boolean = false,
) : NSObject(), MKMapViewDelegateProtocol {

    var userAccuracyOverlay: MKCircle? = null
    var onBearingChanged: (Double) -> Unit = {}

    private var cameraMovedByUser = false
    private var programmaticViewportChange = false

    fun beginProgrammaticViewportChange() {
        programmaticViewportChange = true
        cameraMovedByUser = false
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionWillChangeAnimated: Boolean) {
        if (mapView.hasActiveMapGesture()) {
            programmaticViewportChange = false
            cameraMovedByUser = true
        } else {
            cameraMovedByUser = false
        }
    }

    override fun mapViewDidChangeVisibleRegion(mapView: MKMapView) {
        onBearingChanged(mapView.camera.heading)
        if (!programmaticViewportChange && mapView.hasActiveMapGesture()) cameraMovedByUser = true
        onCameraCenterChanged?.invoke(mapView.centerCoordinate.toGeoCoordinates(), cameraMovedByUser)
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        onBearingChanged(mapView.camera.heading)
        onCameraCenterChanged?.invoke(mapView.centerCoordinate.toGeoCoordinates(), cameraMovedByUser)
        cameraMovedByUser = false
        programmaticViewportChange = false
    }

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        if (rendererForOverlay is MKCircle) {
            val renderer = MKCircleRenderer(circle = rendererForOverlay)
            if (rendererForOverlay == userAccuracyOverlay) {
                renderer.fillColor = UIColor.colorWithRed(0.0, 122.0 / 255.0, 1.0, 0.15)
                renderer.strokeColor = UIColor.clearColor
                renderer.lineWidth = 0.0
            } else {
                val (fillColor, strokeColor) = IosMapKitAdapter.getGeofenceUiColors(isDarkTheme)
                renderer.fillColor = fillColor
                renderer.strokeColor = strokeColor
                renderer.lineWidth = IosMapKitAdapter.GEOFENCE_STROKE_WIDTH
            }
            return renderer
        }
        return MKOverlayRenderer(rendererForOverlay)
    }

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) {
            return null
        }
        if (viewForAnnotation is FjalarUserPositionAnnotation) {
            val identifier = "FjalarUserDot"
            val dot = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                ?: MKAnnotationView(viewForAnnotation, identifier)
            dot.annotation = viewForAnnotation
            dot.setFrame(CGRectMake(0.0, 0.0, 20.0, 20.0))
            dot.backgroundColor = UIColor.colorWithRed(0.0, 122.0 / 255.0, 1.0, 1.0)
            dot.layer.cornerRadius = 10.0
            dot.layer.borderWidth = 3.0
            dot.layer.borderColor = UIColor.whiteColor.CGColor
            dot.draggable = false
            dot.canShowCallout = false
            return dot
        }
        val identifier = "FjalarDestinationMarker"
        val annotationView = (mapView.dequeueReusableAnnotationViewWithIdentifier(identifier) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)

        annotationView.annotation = viewForAnnotation
        annotationView.draggable = destinationEditingEnabled
        annotationView.markerTintColor = UIColor.colorWithRed(1.0, 59.0 / 255.0, 48.0 / 255.0, 1.0)
        return annotationView
    }

    override fun mapView(
        mapView: MKMapView,
        annotationView: MKAnnotationView,
        didChangeDragState: MKAnnotationViewDragState,
        fromOldState: MKAnnotationViewDragState,
    ) {
        if (!destinationEditingEnabled || annotationView.annotation is FjalarUserPositionAnnotation) return
        if (didChangeDragState == MKAnnotationViewDragStateEnding) {
            annotationView.annotation?.coordinate?.useContents {
                onPinDrag?.invoke(
                    GeoCoordinates(
                        latitude = latitude.coerceIn(-90.0, 90.0),
                        longitude = IosMapKitAdapter.normalizeLongitude(longitude),
                    )
                )
            }
        }
    }
}

private fun UIView.hasActiveMapGesture(): Boolean {
    val activeHere = gestureRecognizers.orEmpty().filterIsInstance<UIGestureRecognizer>().any {
        it.state == UIGestureRecognizerStateBegan || it.state == UIGestureRecognizerStateChanged
    }
    if (activeHere) return true
    return subviews.filterIsInstance<UIView>().any { it.hasActiveMapGesture() }
}

/**
 * Objective-C target handling map tap gestures and dispatching converted coordinates.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class MapTapHandler(
    private val mapView: MKMapView,
    private val onMapClickProvider: () -> (GeoCoordinates) -> Unit,
) : NSObject() {
    @ObjCAction
    fun handleTap(recognizer: UITapGestureRecognizer) {
        val tapPoint = recognizer.locationInView(mapView)
        val coord = mapView.convertPoint(tapPoint, toCoordinateFromView = mapView)
        val geo = coord.toGeoCoordinates()
        onMapClickProvider()(geo)
    }
}

/**
 * Composable hosting Apple MapKit [MKMapView] via UIKitView.
 *
 * Coordinates MapKit lifecycle events, theme style updates matching [isDarkTheme], user clicks,
 * camera viewport animations, pin marker rendering, and geofence circle overlays.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun IosMapKitView(
    center: GeoCoordinates?,
    zoom: Double,
    pinLocation: GeoCoordinates?,
    geofenceCenter: GeoCoordinates?,
    geofenceRadius: TriggerRadius?,
    isDarkTheme: Boolean,
    onMapClick: (GeoCoordinates) -> Unit,
    onPinDrag: (GeoCoordinates) -> Unit,
    destinationEditingEnabled: Boolean = false,
    onCameraCenterChanged: ((GeoCoordinates, Boolean) -> Unit)? = null,
    viewportRequestId: Long = 0L,
    preserveZoomOnViewportRequest: Boolean = false,
    userPosition: MapUserPosition? = null,
    onBearingChanged: (Double) -> Unit = {},
    northRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val adapter = remember { IosMapKitAdapter(isDarkTheme = isDarkTheme) }
    val mapView = remember { MKMapView() }

    val currentOnCameraCenterChanged by rememberUpdatedState(onCameraCenterChanged)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnPinDrag by rememberUpdatedState(onPinDrag)
    val currentOnBearingChanged by rememberUpdatedState(onBearingChanged)

    val delegate = remember {
        FjalarMapKitDelegate(
            isDarkTheme = isDarkTheme,
            destinationEditingEnabled = destinationEditingEnabled,
            onPinDrag = { currentOnPinDrag(it) },
            onCameraCenterChanged = { coordinates, isUserGesture -> currentOnCameraCenterChanged?.invoke(coordinates, isUserGesture) },
        )
    }
    val tapHandler = remember(mapView) {
        MapTapHandler(mapView) { currentOnMapClick }
    }

    DisposableEffect(mapView, delegate, tapHandler) {
        adapter.attachMap(mapView)
        mapView.delegate = delegate
        delegate.onBearingChanged = { currentOnBearingChanged(it) }

        onDispose {
            mapView.delegate = null
            adapter.detachMap()
        }
    }

    DisposableEffect(mapView, tapHandler, destinationEditingEnabled) {
        if (!destinationEditingEnabled) {
            return@DisposableEffect onDispose {}
        }
        val tapRecognizer = UITapGestureRecognizer(
            target = tapHandler,
            action = sel_registerName("handleTap:"),
        ).apply {
            cancelsTouchesInView = false
        }
        mapView.addGestureRecognizer(tapRecognizer)
        onDispose { mapView.removeGestureRecognizer(tapRecognizer) }
    }

    LaunchedEffect(destinationEditingEnabled) {
        delegate.destinationEditingEnabled = destinationEditingEnabled
        for (annotation in mapView.annotations.filterIsInstance<MKAnnotationProtocol>()) {
            if (annotation is MKUserLocation || annotation is FjalarUserPositionAnnotation) continue
            mapView.viewForAnnotation(annotation)?.draggable = destinationEditingEnabled
        }
    }

    LaunchedEffect(isDarkTheme) {
        adapter.updateTheme(isDarkTheme)
        delegate.isDarkTheme = isDarkTheme
        mapView.overrideUserInterfaceStyle = if (isDarkTheme) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
    }

    LaunchedEffect(currentOnPinDrag) {
        delegate.onPinDrag = { currentOnPinDrag(it) }
    }

    LaunchedEffect(mapView) {
        if (center != null) {
            adapter.setViewport(center, zoom)
        }
    }

    LaunchedEffect(viewportRequestId) {
        if (center != null && viewportRequestId > 0L) {
            if (preserveZoomOnViewportRequest) {
                adapter.recenterPreservingZoom(center)
            } else {
                adapter.setViewport(center, zoom)
            }
        }
    }

    LaunchedEffect(userPosition) {
        adapter.showUserPosition(userPosition)
    }

    LaunchedEffect(northRequestId) {
        if (northRequestId > 0L) adapter.resetNorth()
    }

    LaunchedEffect(pinLocation) {
        if (pinLocation != null) {
            adapter.placePin(pinLocation)
        } else {
            adapter.removePin()
        }
    }

    LaunchedEffect(geofenceCenter, geofenceRadius) {
        if (geofenceCenter != null && geofenceRadius != null) {
            adapter.showGeofence(geofenceCenter, geofenceRadius)
        } else {
            adapter.removeGeofence()
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overrideUserInterfaceStyle = if (isDarkTheme) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
        },
    )
}
