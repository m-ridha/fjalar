package com.muhammadridha.fjalar.domain.ports.inbound

import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent

/**
 * Inbound port driven by platform geofencing adapters when a monitored circular region
 * reports an ENTER or EXIT transition.
 */
interface HandleGeofenceTransitionPort {
    suspend fun handleGeofenceTransition(event: GeofenceTransitionEvent)
}
