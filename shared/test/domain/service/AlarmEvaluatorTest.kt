package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.JAKARTA
import com.muhammadridha.fjalar.domain.MANGGARAI
import com.muhammadridha.fjalar.domain.SUDIRMAN
import com.muhammadridha.fjalar.domain.WEEKDAY_COMMUTE
import com.muhammadridha.fjalar.domain.jakarta
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmEvaluatorTest {
    private val evaluator = AlarmEvaluator(ScheduleEvaluator(JAKARTA))
    private val now = jakarta(2026, 10, 26, 6) // Monday 06:00

    @Test
    fun evaluateFix_whenAlreadyInsideRadiusOnArmedStart_doesNotRingImmediately() {
        val original = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME, radius = TriggerRadius(500))
        val armed = evaluator.enable(original, now)

        assertEquals(AlarmState.ARMED_MONITORING, armed.state)
        assertTrue(armed.mustExitBeforeTrigger)

        // First fix at destination coordinates inside radius
        val evaluated = evaluator.evaluateFix(armed, MANGGARAI, 10f)

        assertEquals(AlarmState.ARMED_MONITORING, evaluated.state)
        assertTrue(evaluated.mustExitBeforeTrigger)
    }

    @Test
    fun evaluateFix_afterExitingRadius_armsAndRingsUponReentry() {
        val original = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME, radius = TriggerRadius(500))
        val armed = evaluator.enable(original, now)
        val initialInside = evaluator.evaluateFix(armed, MANGGARAI, 10f)
        assertTrue(initialInside.mustExitBeforeTrigger)

        // Commuter moves away to Sudirman (~3 km away > 500m + 10m)
        val outside = evaluator.evaluateFix(initialInside, SUDIRMAN, 10f)
        assertEquals(AlarmState.ARMED_MONITORING, outside.state)
        assertFalse(outside.mustExitBeforeTrigger)

        // Commuter re-enters destination radius at Manggarai
        val reentered = evaluator.evaluateFix(outside, MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, reentered.state)
    }

    @Test
    fun syncSchedule_enteringActiveWindowInsideRadius_suppressesImmediateRinging() {
        val scheduled = manggaraiAlarm(schedule = WEEKDAY_COMMUTE, radius = TriggerRadius(500))
            .copy(state = AlarmState.SCHEDULED_IDLE)
        // Window opens at 07:00. Time is 07:05 (Monday).
        val windowActiveTime = jakarta(2026, 10, 26, 7, 5)

        val armed = evaluator.syncSchedule(scheduled, windowActiveTime)
        assertEquals(AlarmState.ARMED_MONITORING, armed.state)
        assertTrue(armed.mustExitBeforeTrigger)

        // Commuter is already at Manggarai when commute window opens
        val evaluated = evaluator.evaluateFix(armed, MANGGARAI, 10f)
        assertEquals(AlarmState.ARMED_MONITORING, evaluated.state)
        assertTrue(evaluated.mustExitBeforeTrigger)

        // Depart to Sudirman
        val departed = evaluator.evaluateFix(evaluated, SUDIRMAN, 10f)
        assertEquals(AlarmState.ARMED_MONITORING, departed.state)
        assertFalse(departed.mustExitBeforeTrigger)

        // Return to Manggarai
        val arrived = evaluator.evaluateFix(departed, MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, arrived.state)
    }
}
