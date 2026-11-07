package com.muhammadridha.fjalar.domain.usecase

import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort

class SynchronizeAlarmsUseCase(
    private val syncSchedulesPort: SyncSchedulesPort,
    private val backgroundSyncPort: BackgroundSyncPort? = null,
) {
    suspend operator fun invoke(nextSyncDelayMinutes: Long = DEFAULT_NEXT_SYNC_MINUTES): List<LocationAlarm> {
        val updatedAlarms = syncSchedulesPort.syncSchedules()
        val delayMinutes = nextSyncDelayMinutes.coerceAtLeast(MINIMUM_SYNC_DELAY_MINUTES)
        try {
            backgroundSyncPort?.scheduleBackgroundSync(delayMinutes)
        } catch (_: Throwable) {
            // Auxiliary background refresh failure does not invalidate completed schedule synchronization
        }
        return updatedAlarms
    }

    companion object {
        const val DEFAULT_NEXT_SYNC_MINUTES: Long = 15L
        const val MINIMUM_SYNC_DELAY_MINUTES: Long = 1L
    }
}
