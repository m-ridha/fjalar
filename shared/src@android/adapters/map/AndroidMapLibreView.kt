package com.muhammadridha.fjalar.adapters.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Composable hosting MapLibre [MapView] via AndroidView.
 *
 * Manages MapLibre lifecycle events, style URL updates matching theme mode, user clicks,
 * camera viewport animations, pin marker rendering, and geofence circle overlays.
 */
@Composable
fun AndroidMapLibreView(
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre singleton safely before MapView instantiation
    remember(context) {
        MapLibre.getInstance(context)
    }

    val adapter = remember { AndroidMapRendererAdapter(isDarkTheme = isDarkTheme) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyleUrl by remember { mutableStateOf<String?>(null) }

    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnPinDrag by rememberUpdatedState(onPinDrag)

    // MapView creation and lifecycle observer setup
    val mapView = remember(context) {
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var isDestroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    if (!isDestroyed) {
                        isDestroyed = true
                        mapView.onDestroy()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adapter.detachMap()
            val state = lifecycleOwner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                mapView.onPause()
            }
            if (state.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
            }
            if (!isDestroyed) {
                isDestroyed = true
                mapView.onDestroy()
            }
        }
    }

    // Touch-based pin dragging and long-click repositioning
    val density = context.resources.displayMetrics.density
    val touchSlopPx = 48f * density
    var isDraggingPin by remember { mutableStateOf(false) }

    DisposableEffect(mapView, pinLocation, mapLibreMap) {
        mapView.setOnTouchListener { v, event ->
            val map = mapLibreMap
            val activePin = pinLocation
            if (map != null && activePin != null) {
                val pinScreenPoint = with(AndroidMapRendererAdapter) {
                    map.projection.toScreenLocation(activePin.toLatLng())
                }
                val dx = event.x - pinScreenPoint.x
                val dy = event.y - pinScreenPoint.y
                val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        if (distance <= touchSlopPx) {
                            isDraggingPin = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            return@setOnTouchListener true
                        }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (isDraggingPin) {
                            val newLatLng = map.projection.fromScreenLocation(android.graphics.PointF(event.x, event.y))
                            with(AndroidMapRendererAdapter) {
                                currentOnPinDrag(newLatLng.toGeoCoordinates())
                            }
                            return@setOnTouchListener true
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingPin) {
                            isDraggingPin = false
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            val newLatLng = map.projection.fromScreenLocation(android.graphics.PointF(event.x, event.y))
                            with(AndroidMapRendererAdapter) {
                                currentOnPinDrag(newLatLng.toGeoCoordinates())
                            }
                            return@setOnTouchListener true
                        }
                    }
                }
            }
            false
        }

        onDispose {
            mapView.setOnTouchListener(null)
        }
    }

    val targetStyleUrl = AndroidMapRendererAdapter.getMapStyleUrl(isDarkTheme)

    // Initialize MapLibreMap asynchronously
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            adapter.attachMap(map)

            map.addOnMapClickListener { latLng ->
                with(AndroidMapRendererAdapter) {
                    currentOnMapClick(latLng.toGeoCoordinates())
                }
                true
            }

            map.addOnMapLongClickListener { latLng ->
                with(AndroidMapRendererAdapter) {
                    currentOnPinDrag(latLng.toGeoCoordinates())
                }
                true
            }

            map.setStyle(targetStyleUrl) { style ->
                currentStyleUrl = targetStyleUrl
                adapter.onStyleLoaded(style)
            }
        }
    }

    // React to theme changes and update style
    LaunchedEffect(isDarkTheme, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        adapter.updateTheme(isDarkTheme)
        if (currentStyleUrl != targetStyleUrl) {
            map.setStyle(targetStyleUrl) { style ->
                currentStyleUrl = targetStyleUrl
                adapter.onStyleLoaded(style)
            }
        }
    }

    // Set initial viewport once map is attached
    LaunchedEffect(mapLibreMap) {
        if (mapLibreMap != null && center != null) {
            adapter.setViewport(center, zoom)
        }
    }

    // Re-center or viewport change request
    LaunchedEffect(viewportRequestId) {
        if (center != null && viewportRequestId > 0L) {
            adapter.setViewport(center, zoom)
        }
    }

    // React to pinLocation changes
    LaunchedEffect(pinLocation, mapLibreMap) {
        if (pinLocation != null) {
            adapter.placePin(pinLocation)
        } else {
            adapter.removePin()
        }
    }

    // React to geofence changes
    LaunchedEffect(geofenceCenter, geofenceRadius, mapLibreMap) {
        if (geofenceCenter != null && geofenceRadius != null) {
            adapter.showGeofence(geofenceCenter, geofenceRadius)
        } else {
            adapter.removeGeofence()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
