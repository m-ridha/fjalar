package com.muhammadridha.fjalar.domain.ports.outbound

/**
 * Starts/stops platform location updates. Fixes flow back into the domain through
 * HandleLocationUpdatePort. Implementations must treat repeated calls as idempotent.
 */
interface LocationTrackerPort {
    suspend fun startTracking(updateIntervalMillis: Long)
    suspend fun stopTracking()
}
