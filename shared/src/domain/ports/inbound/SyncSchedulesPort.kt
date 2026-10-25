package com.muhammadridha.fjalar.domain.ports.inbound

import com.muhammadridha.fjalar.domain.model.LocationAlarm

interface SyncSchedulesPort {
    /**
     * Re-evaluates every alarm against the current time. Driven by OS wakeups scheduled via
     * AlarmSchedulerPort, device boot, and time/time-zone changes. Returns the updated alarms.
     */
    suspend fun syncSchedules(): List<LocationAlarm>
}
