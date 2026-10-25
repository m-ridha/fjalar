package com.muhammadridha.fjalar.domain.ports.inbound

import com.muhammadridha.fjalar.domain.model.LocationAlarm

interface SaveAlarmPort {
    /** Creates or updates an alarm. Field validation happens when the [LocationAlarm] is constructed. */
    suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit>
}
