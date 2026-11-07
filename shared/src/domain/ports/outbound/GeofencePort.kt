package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.GeofenceRegion
import com.muhammadridha.fjalar.domain.model.GeofenceTransitionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Outbound port for native platform geofencing capabilities.
 *
 * Implementations register circular regions with platform geofencing services
 * and stream transition events (ENTER / EXIT) to domain receivers.
 */
interface GeofencePort {
    suspend fun registerGeofences(regions: List<GeofenceRegion>)
    suspend fun unregisterGeofences(regionIds: List<String>)
    fun observeGeofenceTransitions(): Flow<GeofenceTransitionEvent> = emptyFlow()
}
