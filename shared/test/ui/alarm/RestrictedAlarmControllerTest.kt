package com.muhammadridha.fjalar.ui.alarm

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import com.muhammadridha.fjalar.domain.ports.outbound.SnoozeDeliveryUnavailableException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestrictedAlarmControllerTest {
    private val alarm = LocationAlarm(
        id = "ringing",
        label = "Private destination",
        coordinates = GeoCoordinates(-6.2, 106.8),
        radius = TriggerRadius(500),
        schedule = AlarmSchedule.ONE_TIME,
        state = AlarmState.RINGING,
    )

    @Test
    fun staleOrUnrelatedNotificationCannotOperateAnotherAlarm() = runTest {
        val ports = FakeAlarmPorts(listOf(alarm.copy(id = "other")))
        val controller = RestrictedAlarmController("ringing", ports, ports, ports, backgroundScope)
        runCurrent()
        controller.dismiss()
        controller.snooze()
        runCurrent()
        assertNull(controller.state.value.alarm)
        assertFalse(controller.state.value.isLoading)
        assertTrue(ports.actions.isEmpty())
    }

    @Test
    fun persistedRingingAlarmRestoresAndDismissalClosesRestrictedPresentation() = runTest {
        val ports = FakeAlarmPorts(listOf(alarm))
        val controller = RestrictedAlarmController(alarm.id, ports, ports, ports, backgroundScope)
        runCurrent()
        assertEquals(alarm, controller.state.value.alarm)
        controller.dismiss()
        runCurrent()
        assertEquals(listOf("dismiss:ringing"), ports.actions)
        assertNull(controller.state.value.alarm)
    }

    @Test
    fun snoozeOnlyAppliesToRingingTargetAndCannotRepeatAfterCompletion() = runTest {
        val ports = FakeAlarmPorts(listOf(alarm))
        val controller = RestrictedAlarmController(alarm.id, ports, ports, ports, backgroundScope)
        runCurrent()
        controller.snooze()
        runCurrent()
        controller.dismiss()
        controller.snooze()
        runCurrent()
        assertEquals(listOf("snooze:ringing"), ports.actions)
        assertNull(controller.state.value.alarm)
    }

    @Test
    fun remoteCompletionOrDeletionClosesPresentationWithoutDispatchingAnAction() = runTest {
        for (remaining in listOf(emptyList(), listOf(alarm.copy(state = AlarmState.DISABLED)))) {
            val ports = FakeAlarmPorts(listOf(alarm))
            val controller = RestrictedAlarmController(alarm.id, ports, ports, ports, backgroundScope)
            runCurrent()
            ports.alarms.value = remaining
            runCurrent()
            assertNull(controller.state.value.alarm)
            assertTrue(ports.actions.isEmpty())
        }
    }

    @Test
    fun pendingAndFailedActionsKeepTheAlarmVisibleForRetry() = runTest {
        val ports = FakeAlarmPorts(listOf(alarm))
        val completion = CompletableDeferred<Result<Unit>>()
        ports.operationResult = { completion.await() }
        val controller = RestrictedAlarmController(alarm.id, ports, ports, ports, backgroundScope)
        runCurrent()
        controller.snooze()
        controller.dismiss()
        runCurrent()
        assertTrue(controller.state.value.isOperationPending)
        completion.complete(Result.failure(SnoozeDeliveryUnavailableException()))
        runCurrent()
        assertEquals(listOf("snooze:ringing"), ports.actions)
        assertEquals(alarm, controller.state.value.alarm)
        assertFalse(controller.state.value.isOperationPending)
        assertTrue(controller.state.value.snoozeDeliveryUnavailable)
        assertTrue(controller.state.value.hasOperationFailure)
        assertEquals(1L, controller.state.value.operationFailureId)
    }

    @Test
    fun persistedCompletionRemainsPendingUntilPlatformCleanupFinishes() = runTest {
        val ports = FakeAlarmPorts(listOf(alarm))
        val cleanup = CompletableDeferred<Unit>()
        ports.afterPersist = { cleanup.await() }
        val controller = RestrictedAlarmController(alarm.id, ports, ports, ports, backgroundScope)
        runCurrent()
        controller.dismiss()
        runCurrent()
        assertNull(controller.state.value.alarm)
        assertTrue(controller.state.value.isOperationPending)
        cleanup.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.isOperationPending)
    }

    private class FakeAlarmPorts(initial: List<LocationAlarm>) : ObserveAlarmsPort, DismissAlarmPort, SnoozeAlarmPort {
        val alarms = MutableStateFlow(initial)
        val actions = mutableListOf<String>()
        var operationResult: suspend () -> Result<Unit> = { Result.success(Unit) }
        var afterPersist: suspend () -> Unit = {}

        override fun observeAlarms() = alarms

        override suspend fun dismissAlarm(alarmId: String) = perform("dismiss", alarmId, AlarmState.DISABLED)

        override suspend fun snoozeAlarm(alarmId: String) = perform("snooze", alarmId, AlarmState.SNOOZED)

        private suspend fun perform(action: String, id: String, state: AlarmState): Result<Unit> {
            actions += "$action:$id"
            val result = operationResult()
            if (result.isSuccess) alarms.value = alarms.value.map { if (it.id == id) it.copy(state = state) else it }
            afterPersist()
            return result
        }
    }
}
