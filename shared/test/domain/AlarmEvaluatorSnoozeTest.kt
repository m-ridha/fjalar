package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.service.AlarmEvaluator
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmEvaluatorSnoozeTest {
    private val evaluator = AlarmEvaluator(ScheduleEvaluator(JAKARTA))

    @Test
    fun expiredSnoozeOutsideItsWindowReturnsToIdleWithoutRinging() {
        val now = jakarta(2026, 10, 26, 10)
        val snoozed = manggaraiAlarm().copy(
            state = AlarmState.SNOOZED,
            snoozedUntil = jakarta(2026, 10, 26, 9, 59),
        )

        val synced = evaluator.syncSchedule(snoozed, now)

        assertEquals(AlarmState.SCHEDULED_IDLE, synced.state)
        assertNull(synced.snoozedUntil)
        assertNull(synced.suppressedUntil)
    }

    @Test
    fun expiredSnoozeInsideItsWindowRings() {
        val now = jakarta(2026, 10, 26, 8)
        val snoozed = manggaraiAlarm().copy(
            state = AlarmState.SNOOZED,
            snoozedUntil = jakarta(2026, 10, 26, 7, 59),
            suppressedUntil = jakarta(2026, 10, 26, 9, 30),
        )

        val synced = evaluator.syncSchedule(snoozed, now)

        assertEquals(AlarmState.RINGING, synced.state)
        assertNull(synced.snoozedUntil)
        assertNull(synced.suppressedUntil)
    }

    @Test
    fun expiredOneTimeSnoozeStillRingsWithoutAWindow() {
        val now = jakarta(2026, 10, 26, 8)
        val snoozed = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(
            state = AlarmState.SNOOZED,
            snoozedUntil = jakarta(2026, 10, 26, 7, 59),
        )

        val synced = evaluator.syncSchedule(snoozed, now)

        assertEquals(AlarmState.RINGING, synced.state)
        assertNull(synced.snoozedUntil)
        assertNull(synced.suppressedUntil)
    }
}
