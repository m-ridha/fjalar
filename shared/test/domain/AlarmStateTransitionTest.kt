package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.ScheduleType
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
    fun editorSavePreservesRepositoryDismissalEvenWhenSubmittedRuntimeIsStale() = runTest {
        val original = manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(state = AlarmState.RINGING)
        service.saveAlarm(original).getOrThrow()
        service.dismissAlarm(original.id).getOrThrow()
        // A dismissal can arrive after the UI constructed its save payload.
        service.saveAlarm(original.copy(label = "Edited after dismissal"), enabled = null).getOrThrow()
        val saved = repository.getAlarm(original.id)!!
        assertEquals(AlarmState.DISABLED, saved.state)
        assertEquals("Edited after dismissal", saved.label)
        assertFalse(tracker.isTracking)
        assertTrue(ringer.ringingIds.isEmpty())
    }

    @Test
    fun editorEnableAndDisableApplyTogetherWithBothModeChanges() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        for (initialSchedule in listOf(AlarmSchedule.ONE_TIME, manggaraiAlarm().schedule)) {
            val editedSchedule = if (initialSchedule.type == ScheduleType.ONE_TIME) {
                manggaraiAlarm().schedule
            } else {
                AlarmSchedule.ONE_TIME
            }
            val original = manggaraiAlarm(schedule = initialSchedule).copy(state = AlarmState.RINGING)
            service.saveAlarm(original).getOrThrow()
            service.snoozeAlarm(original.id).getOrThrow()
            val snoozed = repository.getAlarm(original.id)!!
            service.saveAlarm(snoozed.copy(schedule = editedSchedule), enabled = false).getOrThrow()
            val disabled = repository.getAlarm(original.id)!!
            assertEquals(AlarmState.DISABLED, disabled.state)
            assertEquals(editedSchedule, disabled.schedule)
            kotlin.test.assertNull(disabled.snoozedUntil)
            kotlin.test.assertNull(disabled.suppressedUntil)
            assertFalse(tracker.isTracking)

            service.saveAlarm(disabled.copy(schedule = initialSchedule), enabled = true).getOrThrow()
            assertEquals(AlarmState.ARMED_MONITORING, state())
            assertTrue(tracker.isTracking)
            service.deleteAlarm(original.id).getOrThrow()
        }
    }

    @Test
    fun editorEnableUsesCurrentScheduledWindowAndNewDisabledAlarmsStayStopped() = runTest {
        clock.now = jakarta(2026, 10, 26, 6)
        for (schedule in listOf(AlarmSchedule.ONE_TIME, manggaraiAlarm().schedule)) {
            val alarm = manggaraiAlarm(schedule = schedule)
            service.saveAlarm(alarm, enabled = false).getOrThrow()
            assertEquals(AlarmState.DISABLED, state())
            assertFalse(tracker.isTracking)
            assertTrue(scheduler.wakeups.isEmpty())
            service.saveAlarm(alarm, enabled = true).getOrThrow()
            val expected = if (schedule.type == ScheduleType.ONE_TIME) {
                AlarmState.ARMED_MONITORING
            } else {
                AlarmState.SCHEDULED_IDLE
            }
            assertEquals(expected, state())
            service.deleteAlarm(alarm.id).getOrThrow()
        }
    }

    @Test
    fun ordinaryEditorModeEditsPreserveSnoozeAndCompletionGuards() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        for (schedule in listOf(AlarmSchedule.ONE_TIME, manggaraiAlarm().schedule)) {
            val original = manggaraiAlarm(schedule = schedule).copy(state = AlarmState.RINGING)
            service.saveAlarm(original).getOrThrow()
            service.snoozeAlarm(original.id).getOrThrow()
            val before = repository.getAlarm(original.id)!!
            val editedSchedule = if (schedule.type == ScheduleType.ONE_TIME) {
                manggaraiAlarm().schedule
            } else {
                AlarmSchedule.ONE_TIME
            }
            service.saveAlarm(before.copy(schedule = editedSchedule), enabled = null).getOrThrow()
            val saved = repository.getAlarm(original.id)!!
            assertEquals(AlarmState.SNOOZED, saved.state)
            assertEquals(before.snoozedUntil, saved.snoozedUntil)
            assertEquals(before.suppressedUntil, saved.suppressedUntil)
            clock.now = jakarta(2026, 10, 26, 8, 1)
            service.syncSchedules()
            assertEquals(AlarmState.SNOOZED, state())
            assertTrue(ringer.ringingIds.isEmpty())
            clock.now = jakarta(2026, 10, 26, 8, 2)
            service.syncSchedules()
            assertEquals(AlarmState.RINGING, state())
            service.deleteAlarm(original.id).getOrThrow()
            clock.now = jakarta(2026, 10, 26, 8)
        }

        val ringing = manggaraiAlarm().copy(state = AlarmState.RINGING)
        service.saveAlarm(ringing).getOrThrow()
        service.dismissAlarm(ringing.id).getOrThrow()
        val completed = repository.getAlarm(ringing.id)!!
        service.saveAlarm(ringing.copy(schedule = AlarmSchedule.ONE_TIME), enabled = null).getOrThrow()
        val saved = repository.getAlarm(ringing.id)!!
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, saved.state)
        assertEquals(completed.suppressedUntil, saved.suppressedUntil)
        assertFalse(tracker.isTracking)
    }

    @Test
    fun editingSnoozedAlarmsRetainsDeadlineForBothScheduleTypes() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        for (schedule in listOf(AlarmSchedule.ONE_TIME, manggaraiAlarm().schedule)) {
            val ringing = manggaraiAlarm().copy(schedule = schedule, state = AlarmState.RINGING)
            service.saveAlarm(ringing).getOrThrow()
            service.snoozeAlarm(ringing.id).getOrThrow()
            val before = repository.getAlarm(ringing.id)!!
            val draft = com.muhammadridha.fjalar.ui.screens.ConfigureAlarmDraft.fromAlarm(before)
            val edited = draft.copy(label = "Renamed").toLocationAlarm(before)
            assertEquals(before.snoozedUntil, edited.snoozedUntil)
            // The service protects the deadline even when an older caller omits it.
            service.saveAlarm(edited.copy(snoozedUntil = null)).getOrThrow()
            val saved = repository.getAlarm(ringing.id)!!
            assertEquals(AlarmState.SNOOZED, saved.state)
            assertEquals(before.snoozedUntil, saved.snoozedUntil)
            service.deleteAlarm(ringing.id).getOrThrow()
        }
    }

    @Test
    fun snoozeExpiresAtOriginalWindowEndAndNextWindowUsesOriginalRadius() = runTest {
        clock.now = jakarta(2026, 10, 26, 9, 29)
        service.saveAlarm(manggaraiAlarm().copy(state = AlarmState.RINGING)).getOrThrow()
        service.snoozeAlarm("manggarai").getOrThrow()
        assertEquals(jakarta(2026, 10, 26, 9, 30), scheduler.wakeups["manggarai"])
        // Simulate a delayed OS callback, after the timer and window have both elapsed.
        clock.now = jakarta(2026, 10, 26, 9, 31)
        service.syncSchedules()
        assertEquals(AlarmState.SCHEDULED_IDLE, state())
        assertTrue(ringer.ringingIds.isEmpty())
        clock.now = jakarta(2026, 10, 27, 7)
        service.syncSchedules()
        assertEquals(AlarmState.ARMED_MONITORING, state())
        assertEquals(manggaraiAlarm().radius, repository.getAlarm("manggarai")!!.radius)
    }

    @Test
    fun snoozeRestoresFromPersistenceAndDoesNotRingInLaterWindow() = runTest {
        clock.now = jakarta(2026, 10, 26, 9, 29)
        service.saveAlarm(manggaraiAlarm().copy(state = AlarmState.RINGING)).getOrThrow()
        service.snoozeAlarm("manggarai").getOrThrow()
        clock.now = jakarta(2026, 10, 27, 7)
        val restarted = AlarmService(repository, tracker, scheduler, ringer, ScheduleEvaluator(JAKARTA), clock)
        restarted.syncSchedules()
        assertEquals(AlarmState.ARMED_MONITORING, state())
        assertTrue(ringer.ringingIds.isEmpty())
    }

    @Test
    fun skippingSnoozedAlarmCompletesCurrentWindow() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        service.saveAlarm(manggaraiAlarm().copy(state = AlarmState.RINGING)).getOrThrow()
        service.snoozeAlarm("manggarai").getOrThrow()
        service.skipToday("manggarai").getOrThrow()
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, state())
        assertEquals(jakarta(2026, 10, 26, 9, 30), repository.getAlarm("manggarai")!!.suppressedUntil)
        kotlin.test.assertNull(repository.getAlarm("manggarai")!!.snoozedUntil)
    }

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

        service.handleLocationUpdate(SUDIRMAN, 10f)
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
        service.handleLocationUpdate(SUDIRMAN, 10f)
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
        service.handleLocationUpdate(SUDIRMAN, 10f)
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
    fun ringerStopFailureRestoresRingingStateForRetry() = runTest {
        clock.now = jakarta(2026, 10, 26, 8)
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        service.toggleAlarm("manggarai", enabled = true).getOrThrow()
        service.handleLocationUpdate(SUDIRMAN, 10f)
        service.handleLocationUpdate(MANGGARAI, 10f)
        assertEquals(AlarmState.RINGING, state())

        // Simulate ringer failure during stop
        ringer.shouldFailOnStop = true
        val dismissResult = service.dismissAlarm("manggarai")
        assertTrue(dismissResult.isFailure)
        // State must remain/be rolled back to RINGING so dismissal can be retried
        assertEquals(AlarmState.RINGING, state())

        // Recover ringer and retry dismissal
        ringer.shouldFailOnStop = false
        val retryResult = service.dismissAlarm("manggarai")
        assertTrue(retryResult.isSuccess)
        assertEquals(AlarmState.COMPLETED_FOR_WINDOW, state())
        assertTrue(ringer.ringingIds.isEmpty())
    }

    @Test
    fun observeAlarmsStreamsRepositoryContent() = runTest {
        service.saveAlarm(manggaraiAlarm()).getOrThrow()
        assertEquals(listOf("manggarai"), service.observeAlarms().first().map { it.id })
    }
}
