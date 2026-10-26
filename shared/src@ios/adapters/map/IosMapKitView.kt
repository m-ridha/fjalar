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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
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
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject
import platform.objc.sel_registerName

/**
 * Custom [MKMapViewDelegateProtocol] handling geofence circle rendering,
 * draggable destination pins, and pin drag state changes.
 */
@OptIn(ExperimentalForeignApi::class)
private class FjalarMapKitDelegate(
    var isDarkTheme: Boolean,
    var onPinDrag: ((GeoCoordinates) -> Unit)?,
) : NSObject(), MKMapViewDelegateProtocol {

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        if (rendererForOverlay is MKCircle) {
            val renderer = MKCircleRenderer(circle = rendererForOverlay)
            val (fillColor, strokeColor) = IosMapKitAdapter.getGeofenceUiColors(isDarkTheme)
            renderer.fillColor = fillColor
            renderer.strokeColor = strokeColor
            renderer.lineWidth = IosMapKitAdapter.GEOFENCE_STROKE_WIDTH
            return renderer
        }
        return MKOverlayRenderer(rendererForOverlay)
    }

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) {
            return null
        }
        val identifier = "FjalarDestinationMarker"
        val annotationView = (mapView.dequeueReusableAnnotationViewWithIdentifier(identifier) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)

        annotationView.annotation = viewForAnnotation
        annotationView.draggable = true
        annotationView.markerTintColor = IosMapKitAdapter.getPrimaryUiColor(isDarkTheme)
        return annotationView
    }

    override fun mapView(
        mapView: MKMapView,
        annotationView: MKAnnotationView,
        didChangeDragState: MKAnnotationViewDragState,
        fromOldState: MKAnnotationViewDragState,
    ) {
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

/**
 * Objective-C target handling map tap gestures and dispatching converted coordinates.
 */
@OptIn(ExperimentalForeignApi::class)
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
    viewportRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val adapter = remember { IosMapKitAdapter(isDarkTheme = isDarkTheme) }
    val mapView = remember { MKMapView() }

    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnPinDrag by rememberUpdatedState(onPinDrag)

    val delegate = remember {
        FjalarMapKitDelegate(
            isDarkTheme = isDarkTheme,
            onPinDrag = { currentOnPinDrag(it) },
        )
    }
    val tapHandler = remember(mapView) {
        MapTapHandler(mapView) { currentOnMapClick }
    }

    DisposableEffect(mapView, delegate, tapHandler) {
        adapter.attachMap(mapView)
        mapView.delegate = delegate

        val tapRecognizer = UITapGestureRecognizer(
            target = tapHandler,
            action = sel_registerName("handleTap:"),
        ).apply {
            cancelsTouchesInView = false
        }
        mapView.addGestureRecognizer(tapRecognizer)

        onDispose {
            mapView.removeGestureRecognizer(tapRecognizer)
            mapView.delegate = null
            adapter.detachMap()
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
            adapter.setViewport(center, zoom)
        }
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
