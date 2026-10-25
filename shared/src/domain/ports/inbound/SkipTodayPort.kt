package com.muhammadridha.fjalar.domain.ports.inbound

interface SkipTodayPort {
    /** Skips the current scheduled window, or the next one if none is open (holidays, early arrival). */
    suspend fun skipToday(alarmId: String): Result<Unit>
}
