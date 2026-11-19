package com.muhammadridha.fjalar.domain.usecase

import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort
import kotlinx.coroutines.CancellationException

class SynchronizeAlarmsUseCase(
    private val syncSchedulesPort: SyncSchedulesPort,
    private val backgroundSyncPort: BackgroundSyncPort? = null,
) {
    suspend operator fun invoke(nextSyncDelayMinutes: Long = DEFAULT_NEXT_SYNC_MINUTES): List<LocationAlarm> {
        val updatedAlarms = syncSchedulesPort.syncSchedules()
        val delayMinutes = nextSyncDelayMinutes.coerceAtLeast(MINIMUM_SYNC_DELAY_MINUTES)
        val port = backgroundSyncPort
        if (port != null) {
            try {
                if (needsBackgroundRefresh(updatedAlarms)) {
                    port.scheduleBackgroundSync(delayMinutes)
                } else {
                    port.cancelBackgroundSync()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Auxiliary background refresh failure does not invalidate completed schedule synchronization
            }
        }
        return updatedAlarms
    }

    companion object {
        const val DEFAULT_NEXT_SYNC_MINUTES: Long = 15L
        const val MINIMUM_SYNC_DELAY_MINUTES: Long = 1L

        fun needsBackgroundRefresh(alarms: List<LocationAlarm>): Boolean =
            alarms.any { alarm ->
                if (alarm.state == AlarmState.SNOOZED) {
                    return@any true
                }
                alarm.schedule.type == ScheduleType.SCHEDULED &&
                    alarm.state in BACKGROUND_REFRESH_SCHEDULED_STATES
            }

        private val BACKGROUND_REFRESH_SCHEDULED_STATES = setOf(
            AlarmState.SCHEDULED_IDLE,
            AlarmState.ARMED_MONITORING,
            AlarmState.COMPLETED_FOR_WINDOW,
        )
    }
}
