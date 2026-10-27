package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.*
import com.muhammadridha.fjalar.domain.service.*
import kotlin.test.*

class SaveModeTransitionTest {
    private val evaluator = AlarmEvaluator(ScheduleEvaluator(JAKARTA))
    private val now = jakarta(2026, 10, 26, 12)

    @Test fun scheduledToOneTimeArmsEnabledAlarm() {
        val old = manggaraiAlarm().copy(state = AlarmState.SCHEDULED_IDLE)
        val saved = evaluator.prepareSave(old, old.copy(schedule = AlarmSchedule.ONE_TIME), now)
        assertEquals(AlarmState.ARMED_MONITORING, saved.state)
    }

    @Test fun conversionPreservesDisabledAndRinging() {
        for (state in listOf(AlarmState.DISABLED, AlarmState.RINGING)) {
            val old = manggaraiAlarm().copy(state = state)
            assertEquals(state, evaluator.prepareSave(old, old.copy(schedule = AlarmSchedule.ONE_TIME), now).state)
        }
    }

    @Test fun conversionRetainsSuppressionThenArmsAtDeadline() {
        val end = jakarta(2026, 10, 26, 13)
        val old = manggaraiAlarm().copy(state = AlarmState.COMPLETED_FOR_WINDOW, suppressedUntil = end)
        val saved = evaluator.prepareSave(old, old.copy(schedule = AlarmSchedule.ONE_TIME), now)
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, saved.state)
        assertEquals(end, saved.suppressedUntil)
        assertEquals(AlarmState.ARMED_MONITORING, evaluator.syncSchedule(saved, end).state)
    }
}
