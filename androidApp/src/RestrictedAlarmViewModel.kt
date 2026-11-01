package com.muhammadridha.fjalar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import com.muhammadridha.fjalar.ui.alarm.RestrictedAlarmController

/** Retains an in-flight alarm action across activity recreation without retaining the activity. */
class RestrictedAlarmViewModel(
    alarmId: String,
    observeAlarms: ObserveAlarmsPort,
    dismissAlarm: DismissAlarmPort,
    snoozeAlarm: SnoozeAlarmPort,
) : ViewModel() {
    val controller = RestrictedAlarmController(alarmId, observeAlarms, dismissAlarm, snoozeAlarm, viewModelScope)
}
