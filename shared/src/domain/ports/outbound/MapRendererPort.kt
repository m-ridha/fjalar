package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius

/** Map provider contract (MapLibre/OSM on Android, MapKit on iOS). */
interface MapRendererPort {
    fun setViewport(center: GeoCoordinates, zoom: Double)
    fun placePin(coordinates: GeoCoordinates)
    fun showGeofence(center: GeoCoordinates, radius: TriggerRadius)
    fun clearOverlays()
}
