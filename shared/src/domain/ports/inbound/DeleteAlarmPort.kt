package com.muhammadridha.fjalar.domain.ports.inbound

interface DeleteAlarmPort {
    suspend fun deleteAlarm(alarmId: String): Result<Unit>
}
