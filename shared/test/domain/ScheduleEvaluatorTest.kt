package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TimeWindow
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleEvaluatorTest {
    private val evaluator = ScheduleEvaluator(JAKARTA)

    /** 23:00–01:00, Fridays only (window belongs to the day it starts). */
    private val fridayNight = AlarmSchedule(
        type = ScheduleType.SCHEDULED,
        startMinuteOfDay = minuteOfDay(23),
        endMinuteOfDay = minuteOfDay(1),
        daysOfWeekMask = AlarmSchedule.FRIDAY,
    )

    @Test
    fun insideWeekdayWindowIsActive() {
        assertEquals(
            TimeWindow(jakarta(2026, 10, 26, 7), jakarta(2026, 10, 26, 9, 30)),
            evaluator.activeWindowAt(WEEKDAY_COMMUTE, jakarta(2026, 10, 26, 8)),
        )
    }

    @Test
    fun windowStartIsInclusiveAndEndIsExclusive() {
        assertTrue(evaluator.isActive(WEEKDAY_COMMUTE, jakarta(2026, 10, 26, 7)))
        assertFalse(evaluator.isActive(WEEKDAY_COMMUTE, jakarta(2026, 10, 26, 6, 59)))
        assertFalse(evaluator.isActive(WEEKDAY_COMMUTE, jakarta(2026, 10, 26, 9, 30)))
    }

    @Test
    fun weekendIsNotActiveForWeekdayMask() {
        assertFalse(evaluator.isActive(WEEKDAY_COMMUTE, jakarta(2026, 10, 31, 8))) // Saturday
        assertFalse(evaluator.isActive(WEEKDAY_COMMUTE, jakarta(2026, 11, 1, 8))) // Sunday
    }

    @Test
    fun weekdayBitmaskUsesIsoOrder() {
        val active = DayOfWeek.entries.filter { WEEKDAY_COMMUTE.isActiveOn(it) }
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            active,
        )
        assertEquals(1, AlarmSchedule.dayBit(DayOfWeek.MONDAY))
        assertEquals(64, AlarmSchedule.dayBit(DayOfWeek.SUNDAY))
    }

    @Test
    fun nextWindowAfterFridayWindowIsMonday() {
        assertEquals(
            TimeWindow(jakarta(2026, 11, 2, 7), jakarta(2026, 11, 2, 9, 30)),
            evaluator.currentOrNextWindow(WEEKDAY_COMMUTE, jakarta(2026, 10, 30, 10)),
        )
    }

    @Test
    fun currentWindowIsReturnedWhileInsideIt() {
        assertEquals(
            jakarta(2026, 10, 26, 7),
            evaluator.currentOrNextWindow(WEEKDAY_COMMUTE, jakarta(2026, 10, 26, 8))?.start,
        )
    }

    @Test
    fun midnightCrossingWindowIsActiveOnBothSidesOfMidnight() {
        assertTrue(evaluator.isActive(fridayNight, jakarta(2026, 10, 30, 23, 30))) // Fri
        assertTrue(evaluator.isActive(fridayNight, jakarta(2026, 10, 31, 0, 30))) // Sat, started Fri
        assertFalse(evaluator.isActive(fridayNight, jakarta(2026, 10, 31, 1, 0)))
        assertFalse(evaluator.isActive(fridayNight, jakarta(2026, 10, 31, 23, 30))) // Sat not enabled
        assertFalse(evaluator.isActive(fridayNight, jakarta(2026, 10, 30, 0, 30))) // Thu not enabled
    }

    @Test
    fun midnightCrossingWindowEndsOnTheNextDay() {
        assertEquals(
            TimeWindow(jakarta(2026, 10, 30, 23), jakarta(2026, 10, 31, 1)),
            evaluator.currentOrNextWindow(fridayNight, jakarta(2026, 10, 30, 22)),
        )
    }

    @Test
    fun skippingTodaysWindowMovesToTomorrow() {
        val monday = evaluator.currentOrNextWindow(WEEKDAY_COMMUTE, jakarta(2026, 10, 26, 6))!!
        assertEquals(
            jakarta(2026, 10, 27, 7),
            evaluator.currentOrNextWindow(WEEKDAY_COMMUTE, monday.end)?.start,
        )
    }

    @Test
    fun skippingFridayHolidayMovesToMonday() {
        val friday = evaluator.currentOrNextWindow(WEEKDAY_COMMUTE, jakarta(2026, 10, 30, 6))!!
        assertEquals(jakarta(2026, 11, 2, 7), evaluator.currentOrNextWindow(WEEKDAY_COMMUTE, friday.end)?.start)
    }

    @Test
    fun oneTimeScheduleHasNoWindow() {
        assertNull(evaluator.currentOrNextWindow(AlarmSchedule.ONE_TIME, jakarta(2026, 10, 26, 8)))
    }

    @Test
    fun invalidSchedulesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            AlarmSchedule(ScheduleType.SCHEDULED, 1440, 60, AlarmSchedule.WEEKDAYS)
        }
        assertFailsWith<IllegalArgumentException> {
            AlarmSchedule(ScheduleType.SCHEDULED, 60, 120, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            AlarmSchedule(ScheduleType.SCHEDULED, 60, 60, AlarmSchedule.WEEKDAYS)
        }
        assertFailsWith<IllegalArgumentException> {
            AlarmSchedule(ScheduleType.SCHEDULED, null, 60, AlarmSchedule.WEEKDAYS)
        }
    }
}
