package com.muhammadridha.fjalar.domain.ports.inbound

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm

interface HandleLocationUpdatePort {
    /** Evaluates a GPS fix against all armed alarms and returns the alarms that started ringing. */
    suspend fun handleLocationUpdate(coordinates: GeoCoordinates, horizontalAccuracyMeters: Float): List<LocationAlarm>
}
