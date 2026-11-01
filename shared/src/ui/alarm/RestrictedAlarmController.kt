package com.muhammadridha.fjalar.ui.alarm

import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.SnoozeDeliveryUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RestrictedAlarmState(
    val alarm: LocationAlarm? = null,
    val isLoading: Boolean = true,
    val isOperationPending: Boolean = false,
    val hasOperationFailure: Boolean = false,
    val snoozeDeliveryUnavailable: Boolean = false,
    val operationFailureId: Long = 0,
)

/** Only the currently ringing alarm named by the notification can receive actions. */
class RestrictedAlarmController(
    private val alarmId: String,
    observeAlarms: ObserveAlarmsPort,
    private val dismissAlarm: DismissAlarmPort,
    private val snoozeAlarm: SnoozeAlarmPort,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(RestrictedAlarmState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            observeAlarms.observeAlarms().collect { alarms ->
                val ringingAlarm = alarms.firstOrNull { it.id == alarmId && it.state == AlarmState.RINGING }
                mutableState.update { it.copy(alarm = ringingAlarm, isLoading = false) }
            }
        }
    }

    fun dismiss() = perform { dismissAlarm.dismissAlarm(alarmId) }

    fun snooze() = perform { snoozeAlarm.snoozeAlarm(alarmId) }

    private fun perform(operation: suspend () -> Result<Unit>) {
        val current = mutableState.value
        if (current.alarm == null || current.isOperationPending) return
        mutableState.update { it.copy(isOperationPending = true, hasOperationFailure = false) }
        scope.launch {
            val result = try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            mutableState.update {
                it.copy(
                    isOperationPending = false,
                    hasOperationFailure = result.isFailure,
                    snoozeDeliveryUnavailable = result.exceptionOrNull() is SnoozeDeliveryUnavailableException,
                    operationFailureId = it.operationFailureId + if (result.isFailure) 1 else 0,
                )
            }
        }
    }
}
