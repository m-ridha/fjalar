package com.muhammadridha.fjalar.domain.ports.inbound

interface DismissAlarmPort {
    /** Slide-to-stop: silences a ringing alarm and completes its current window (or trip). */
    suspend fun dismissAlarm(alarmId: String): Result<Unit>
}
