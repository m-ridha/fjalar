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

    // MapView creation and lifecycle observer setup
    val mapView = remember(context) {
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adapter.detachMap()
            mapView.onDestroy()
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

            map.setStyle(targetStyleUrl) {
                currentStyleUrl = targetStyleUrl
            }
        }
    }

    // React to theme changes and update style
    LaunchedEffect(isDarkTheme, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        adapter.updateTheme(isDarkTheme)
        if (currentStyleUrl != targetStyleUrl) {
            map.setStyle(targetStyleUrl) {
                currentStyleUrl = targetStyleUrl
            }
        }
    }

    // React to center and zoom changes
    LaunchedEffect(center, zoom, mapLibreMap) {
        if (center != null) {
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
