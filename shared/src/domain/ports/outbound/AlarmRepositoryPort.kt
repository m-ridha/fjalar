package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.LocationAlarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepositoryPort {
    fun observeAlarms(): Flow<List<LocationAlarm>>
    suspend fun getAlarms(): List<LocationAlarm>
    suspend fun getAlarm(id: String): LocationAlarm?
    suspend fun upsertAlarm(alarm: LocationAlarm)
    suspend fun deleteAlarm(id: String)
}
