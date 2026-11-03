package com.muhammadridha.fjalar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhammadridha.fjalar.adapters.map.MapUserPosition
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius

@Composable
expect fun PlatformMapView(
    center: GeoCoordinates?,
    zoom: Double,
    pinLocation: GeoCoordinates?,
    geofenceCenter: GeoCoordinates?,
    geofenceRadius: TriggerRadius?,
    isDarkTheme: Boolean,
    onMapClick: (GeoCoordinates) -> Unit,
    onPinDrag: (GeoCoordinates) -> Unit,
    destinationEditingEnabled: Boolean = false,
    // The Boolean is true only for a camera movement initiated by a user map gesture.
    onCameraCenterChanged: ((GeoCoordinates, Boolean) -> Unit)? = null,
    viewportRequestId: Long = 0L,
    preserveZoomOnViewportRequest: Boolean = false,
    userPosition: MapUserPosition? = null,
    onBearingChanged: (Double) -> Unit = {},
    northRequestId: Long = 0L,
    modifier: Modifier = Modifier,
)
