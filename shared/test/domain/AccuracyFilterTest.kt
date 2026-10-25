package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.domain.service.AlarmEvaluator
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeClock
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccuracyFilterTest {
    private val evaluator = AlarmEvaluator(ScheduleEvaluator(JAKARTA))
    private val armed = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME, radius = TriggerRadius(500))
        .copy(state = AlarmState.ARMED_MONITORING)

    @Test
    fun fixCoarserThan80PercentOfRadiusIsRejected() {
        assertEquals(AlarmState.ARMED_MONITORING, evaluator.evaluateFix(armed, MANGGARAI, 401f).state)
    }

    @Test
    fun fixExactlyAt80PercentOfRadiusIsAccepted() {
        assertEquals(AlarmState.RINGING, evaluator.evaluateFix(armed, MANGGARAI, 400f).state)
    }

    @Test
    fun invalidAccuracyValuesAreRejected() {
        assertEquals(AlarmState.ARMED_MONITORING, evaluator.evaluateFix(armed, MANGGARAI, Float.NaN).state)
        assertEquals(AlarmState.ARMED_MONITORING, evaluator.evaluateFix(armed, MANGGARAI, -1f).state)
    }

    @Test
    fun accurateFixOutsideRadiusDoesNotTrigger() {
        assertEquals(AlarmState.ARMED_MONITORING, evaluator.evaluateFix(armed, SUDIRMAN, 5f).state)
    }

    @Test
    fun unarmedAlarmIgnoresFixes() {
        val idle = armed.copy(state = AlarmState.SCHEDULED_IDLE)
        assertEquals(AlarmState.SCHEDULED_IDLE, evaluator.evaluateFix(idle, MANGGARAI, 5f).state)
    }

    @Test
    fun gpsSpikeInsideRadiusIsIgnoredUntilAPreciseFixArrives() = runTest {
        val repository = InMemoryAlarmRepository()
        val ringer = FakeAlarmRinger()
        val service = AlarmService(
            repository, FakeLocationTracker(), FakeAlarmScheduler(), ringer,
            ScheduleEvaluator(JAKARTA), FakeClock(jakarta(2026, 10, 26, 8)),
        )
        service.saveAlarm(armed.copy(state = AlarmState.DISABLED)).getOrThrow()
        service.toggleAlarm(armed.id, enabled = true).getOrThrow()

        service.handleLocationUpdate(SUDIRMAN, 8f)
        service.handleLocationUpdate(MANGGARAI, 1_500f) // cell-tower spike
        assertEquals(AlarmState.ARMED_MONITORING, repository.getAlarm(armed.id)!!.state)
        assertEquals(0, ringer.startCount)

        service.handleLocationUpdate(MANGGARAI, 12f)
        assertEquals(AlarmState.RINGING, repository.getAlarm(armed.id)!!.state)
    }
}
