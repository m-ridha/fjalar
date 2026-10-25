package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeClock
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmStateTransitionTest {
    private val clock = FakeClock(jakarta(2026, 10, 26, 6)) // Monday 06:00
    private val repository = InMemoryAlarmRepository()
    private val tracker = FakeLocationTracker()
    private val scheduler = FakeAlarmScheduler()
    private val ringer = FakeAlarmRinger()
    private val service = AlarmService(repository, tracker, scheduler, ringer, ScheduleEvaluator(JAKARTA), clock)

    private suspend fun state() = repository.getAlarm("manggarai")!!.state

    @Test
    fun weekdayCommuteFromIdleToCompleted() = runTest {
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        service.toggleAlarm("manggarai", enabled = true).getOrThrow()

        // 06:00: outside the window, waiting for the OS wakeup at 07:00 with GPS off.
        assertEquals(AlarmState.SCHEDULED_IDLE, state())
        assertFalse(tracker.isTracking)
        assertEquals(jakarta(2026, 10, 26, 7), scheduler.wakeups["manggarai"])

        // 07:00: window opens.
        clock.now = jakarta(2026, 10, 26, 7)
        service.syncSchedules()
        assertEquals(AlarmState.ARMED_MONITORING, state())
        assertTrue(tracker.isTracking)
        assertEquals(jakarta(2026, 10, 26, 9, 30), scheduler.wakeups["manggarai"])

        // Leaving Sudirman: ~3.1 km away, keep monitoring.
        clock.now = jakarta(2026, 10, 26, 7, 40)
        assertTrue(service.handleLocationUpdate(SUDIRMAN, 15f).isEmpty())
        assertEquals(AlarmState.ARMED_MONITORING, state())

        // Entering the 500 m radius around Manggarai.
        clock.now = jakarta(2026, 10, 26, 7, 48)
        val triggered = service.handleLocationUpdate(MANGGARAI, 15f)
        assertEquals(listOf("manggarai"), triggered.map { it.id })
        assertEquals(AlarmState.RINGING, state())
        assertEquals(setOf("manggarai"), ringer.ringingIds)
        assertFalse(tracker.isTracking)

        // Slide to stop.
        service.dismissAlarm("manggarai").getOrThrow()
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, state())
        assertTrue(ringer.ringingIds.isEmpty())
        assertFalse(tracker.isTracking)
        assertEquals(jakarta(2026, 10, 26, 9, 30), scheduler.wakeups["manggarai"])

        // Still at the office later that morning: must not re-trigger.
        clock.now = jakarta(2026, 10, 26, 8, 30)
        assertTrue(service.handleLocationUpdate(MANGGARAI, 10f).isEmpty())
        assertEquals(1, ringer.startCount)

        // 09:30: window closes, back to idle until tomorrow.
        clock.now = jakarta(2026, 10, 26, 9, 30)
        service.syncSchedules()
        assertEquals(AlarmState.SCHEDULED_IDLE, state())
        assertEquals(jakarta(2026, 10, 27, 7), scheduler.wakeups["manggarai"])

        // Tuesday 07:00: armed again.
        clock.now = jakarta(2026, 10, 27, 7)
        service.syncSchedules()
        assertEquals(AlarmState.ARMED_MONITORING, state())
    }

    @Test
    fun oneTimeTripIsDisabledAfterDismiss() = runTest {
        service.saveAlarm(manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME)).getOrThrow()
        service.toggleAlarm("manggarai", enabled = true).getOrThrow()
        assertEquals(AlarmState.ARMED_MONITORING, state())
        assertTrue(tracker.isTracking)
        assertTrue(scheduler.wakeups.isEmpty())

        service.handleLocationUpdate(MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, state())

        service.dismissAlarm("manggarai").getOrThrow()
        assertEquals(AlarmState.DISABLED, state())
        assertFalse(tracker.isTracking)
    }

    @Test
    fun skipTodayBeforeWindowSuppressesTheWholeWindow() = runTest {
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        service.toggleAlarm("manggarai", enabled = true).getOrThrow()

        service.skipToday("manggarai").getOrThrow() // Monday 06:00, holiday
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, state())
        assertEquals(jakarta(2026, 10, 26, 9, 30), scheduler.wakeups["manggarai"])

        clock.now = jakarta(2026, 10, 26, 7, 30)
        service.syncSchedules()
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, state())
        assertFalse(tracker.isTracking)
        assertTrue(service.handleLocationUpdate(MANGGARAI, 10f).isEmpty())

        clock.now = jakarta(2026, 10, 27, 7, 5)
        service.syncSchedules()
        assertEquals(AlarmState.ARMED_MONITORING, state())
    }

    @Test
    fun skipTodayWhileRingingStopsTheRinger() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        service.toggleAlarm("manggarai", enabled = true).getOrThrow()
        service.handleLocationUpdate(MANGGARAI, 10f)

        service.skipToday("manggarai").getOrThrow()
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, state())
        assertTrue(ringer.ringingIds.isEmpty())
    }

    @Test
    fun disablingStopsRingingTrackingAndWakeups() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        service.toggleAlarm("manggarai", enabled = true).getOrThrow()
        service.handleLocationUpdate(MANGGARAI, 10f)

        service.toggleAlarm("manggarai", enabled = false).getOrThrow()
        assertEquals(AlarmState.DISABLED, state())
        assertTrue(ringer.ringingIds.isEmpty())
        assertFalse(tracker.isTracking)
        assertTrue(scheduler.wakeups.isEmpty())
    }

    @Test
    fun invalidCommandsFailWithoutSideEffects() = runTest {
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        assertTrue(service.dismissAlarm("manggarai").isFailure) // not ringing
        assertTrue(service.toggleAlarm("missing", enabled = true).isFailure)
        assertTrue(service.skipToday("manggarai").isFailure) // disabled
        assertEquals(AlarmState.DISABLED, state())
    }

    @Test
    fun observeAlarmsStreamsRepositoryContent() = runTest {
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        assertEquals(listOf("manggarai"), service.observeAlarms().first().map { it.id })
    }
}
