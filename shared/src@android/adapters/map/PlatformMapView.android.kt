package com.muhammadridha.fjalar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhammadridha.fjalar.adapters.map.AndroidMapLibreView
import com.muhammadridha.fjalar.adapters.map.MapUserPosition
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius

@Composable
actual fun PlatformMapView(
    center: GeoCoordinates?,
    zoom: Double,
    pinLocation: GeoCoordinates?,
    geofenceCenter: GeoCoordinates?,
    geofenceRadius: TriggerRadius?,
    isDarkTheme: Boolean,
    onMapClick: (GeoCoordinates) -> Unit,
    onPinDrag: (GeoCoordinates) -> Unit,
    onCameraCenterChanged: ((GeoCoordinates, Boolean) -> Unit)?,
    viewportRequestId: Long,
    preserveZoomOnViewportRequest: Boolean,
    userPosition: MapUserPosition?,
    onBearingChanged: (Double) -> Unit,
    northRequestId: Long,
    modifier: Modifier,
) {
    AndroidMapLibreView(
        center = center,
        zoom = zoom,
        pinLocation = pinLocation,
        geofenceCenter = geofenceCenter,
        geofenceRadius = geofenceRadius,
        isDarkTheme = isDarkTheme,
        onMapClick = onMapClick,
        onPinDrag = onPinDrag,
        onCameraCenterChanged = onCameraCenterChanged,
        viewportRequestId = viewportRequestId,
        preserveZoomOnViewportRequest = preserveZoomOnViewportRequest,
        userPosition = userPosition,
        onBearingChanged = onBearingChanged,
        northRequestId = northRequestId,
        modifier = modifier,
    )
}
