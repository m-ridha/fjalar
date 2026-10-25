package com.muhammadridha.fjalar.domain.ports.inbound

import com.muhammadridha.fjalar.domain.model.LocationAlarm
import kotlinx.coroutines.flow.Flow

interface ObserveAlarmsPort {
    fun observeAlarms(): Flow<List<LocationAlarm>>
}
