package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.LocationAlarm

/** Plays the system default alarm sound / vibration per [LocationAlarm.soundConfig] and shows the ringing UI. */
interface AlarmRingerPort {
    suspend fun startRinging(alarm: LocationAlarm)
    suspend fun stopRinging(alarmId: String)
}
