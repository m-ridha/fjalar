package com.muhammadridha.fjalar.domain.ports.inbound

interface SnoozeAlarmPort {
    suspend fun snoozeAlarm(alarmId: String): Result<Unit>
}
