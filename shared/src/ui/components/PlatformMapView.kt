package com.muhammadridha.fjalar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onCameraCenterChanged: ((GeoCoordinates) -> Unit)? = null,
    viewportRequestId: Long = 0L,
    modifier: Modifier = Modifier,
)
