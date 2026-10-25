package com.muhammadridha.fjalar.domain.ports.inbound

interface ToggleAlarmPort {
    /** Arms a one-time trip / enables a recurring schedule, or disables the alarm. */
    suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit>
}
