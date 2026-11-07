package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.JAKARTA
import com.muhammadridha.fjalar.domain.MANGGARAI
import com.muhammadridha.fjalar.domain.SUDIRMAN
import com.muhammadridha.fjalar.domain.WEEKDAY_COMMUTE
import com.muhammadridha.fjalar.domain.jakarta
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmEvaluatorTest {
    private val evaluator = AlarmEvaluator(ScheduleEvaluator(JAKARTA))
    private val now = jakarta(2026, 10, 26, 6) // Monday 06:00

    @Test
    fun enable_oneTimeAlarm_doesNotRequireDepartureAndRingsImmediatelyWhenInside() {
        val original = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME, radius = TriggerRadius(500))
        val armed = evaluator.enable(original, now)

        assertEquals(AlarmState.ARMED_MONITORING, armed.state)
        assertFalse(armed.mustExitBeforeTrigger)

        // One-time alarm approaching stop or armed while in zone triggers immediately
        val evaluated = evaluator.evaluateFix(armed, MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, evaluated.state)
    }

    @Test
    fun syncSchedule_enteringActiveWindowInsideRadius_suppressesImmediateRingingUntilDeparture() {
        val scheduled = manggaraiAlarm(schedule = WEEKDAY_COMMUTE, radius = TriggerRadius(500))
            .copy(state = AlarmState.SCHEDULED_IDLE)
        val windowActiveTime = jakarta(2026, 10, 26, 7, 5)

        val armed = evaluator.syncSchedule(scheduled, windowActiveTime)
        assertEquals(AlarmState.ARMED_MONITORING, armed.state)
        assertTrue(armed.mustExitBeforeTrigger)

        // Commuter is already at Manggarai when commute window opens
        val evaluated = evaluator.evaluateFix(armed, MANGGARAI, 10f)
        assertEquals(AlarmState.ARMED_MONITORING, evaluated.state)
        assertTrue(evaluated.mustExitBeforeTrigger)

        // Depart to Sudirman (~3 km away)
        val departed = evaluator.evaluateFix(evaluated, SUDIRMAN, 10f)
        assertEquals(AlarmState.ARMED_MONITORING, departed.state)
        assertFalse(departed.mustExitBeforeTrigger)

        // Return to Manggarai
        val arrived = evaluator.evaluateFix(departed, MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, arrived.state)
    }

    @Test
    fun evaluateFix_clearsDepartureGuardWhenOutsideRadiusEvenWithinAccuracyMargin() {
        val scheduled = manggaraiAlarm(schedule = WEEKDAY_COMMUTE, radius = TriggerRadius(500))
            .copy(state = AlarmState.SCHEDULED_IDLE)
        val windowActiveTime = jakarta(2026, 10, 26, 7, 5)

        val armed = evaluator.syncSchedule(scheduled, windowActiveTime)
        assertTrue(armed.mustExitBeforeTrigger)

        // ~522 meters north of Manggarai: distance > radius (500m), but distance <= radius + accuracy (500 + 30 = 530m)
        val nearOutside = GeoCoordinates(MANGGARAI.latitude + 0.0047, MANGGARAI.longitude)
        val distance = HaversineCalculator.distanceMeters(nearOutside, MANGGARAI)
        assertTrue(distance > 500.0 && distance < 530.0, "Expected distance in (500, 530), was $distance")

        // First fix just outside radius clears departure guard without requiring distance > radius + accuracy
        val cleared = evaluator.evaluateFix(armed, nearOutside, 30f)
        assertEquals(AlarmState.ARMED_MONITORING, cleared.state)
        assertFalse(cleared.mustExitBeforeTrigger)

        // Commuter moves inward to Manggarai destination -> rings
        val arrived = evaluator.evaluateFix(cleared, MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, arrived.state)
    }
}
