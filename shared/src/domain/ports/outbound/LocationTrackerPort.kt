package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Starts/stops platform location updates. Fixes flow back into the domain through
 * HandleLocationUpdatePort. Implementations must treat repeated calls as idempotent.
 */
interface LocationTrackerPort {
    fun observeFixes(): Flow<LocationFix> = emptyFlow()
    fun observeAvailability(): Flow<LocationAvailability> = emptyFlow()
    /** Foreground map ownership is independent from alarm ownership. */
    suspend fun setForegroundMapTracking(enabled: Boolean) {}
    suspend fun startTracking(updateIntervalMillis: Long)
    suspend fun stopTracking()
}
