package com.muhammadridha.fjalar.domain.ports.outbound

import kotlin.time.Instant

/** Exact OS wakeups (window start/end). On fire, the adapter calls SyncSchedulesPort. One wakeup per alarm. */
interface AlarmSchedulerPort {
    suspend fun scheduleWakeup(alarmId: String, at: Instant)
    suspend fun cancelWakeup(alarmId: String)
}
