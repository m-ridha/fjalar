package com.muhammadridha.fjalar.domain.ports.outbound

/**
 * Outbound port for scheduling background periodic or opportunistic synchronization.
 *
 * Implemented on platforms that support background task scheduling (such as BGAppRefreshTask
 * on iOS) to periodically evaluate alarm schedules and arm monitoring when commute windows begin.
 */
interface BackgroundSyncPort {
    suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long)
    suspend fun cancelBackgroundSync()
}
