package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.JAKARTA
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeClock
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AlarmServiceTest {
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1000000000L))
    private val repository = InMemoryAlarmRepository()
    private val tracker = FakeLocationTracker()
    private val scheduler = FakeAlarmScheduler()
    private val ringer = FakeAlarmRinger()
    private val service = AlarmService(repository, tracker, scheduler, ringer, ScheduleEvaluator(JAKARTA), clock)

    private fun createSampleAlarm(
        id: String = "sample-1",
        label: String = "Sample Alarm",
        coordinates: GeoCoordinates = GeoCoordinates(-6.2, 106.8),
        radius: TriggerRadius = TriggerRadius(500),
        schedule: AlarmSchedule = AlarmSchedule.ONE_TIME,
        state: AlarmState = AlarmState.DISABLED,
    ) = LocationAlarm(
        id = id,
        label = label,
        coordinates = coordinates,
        radius = radius,
        schedule = schedule,
        state = state,
    )

    @Test
    fun convertedCompletedAlarmKeepsWakeupUntilSuppressionEnds() = runTest {
        val end = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds() + 60_000L)
        val old = createSampleAlarm(schedule = AlarmSchedule(
            type = ScheduleType.SCHEDULED, startMinuteOfDay = 420,
            endMinuteOfDay = 540, daysOfWeekMask = AlarmSchedule.WEEKDAYS,
        ), state = AlarmState.COMPLETED_FOR_WINDOW).copy(suppressedUntil = end)
        service.saveAlarm(old).getOrThrow()
        service.saveAlarm(old.copy(schedule = AlarmSchedule.ONE_TIME)).getOrThrow()
        assertFalse(tracker.isTracking)
        kotlin.test.assertEquals(end, scheduler.wakeups[old.id])
        clock.now = end
        service.syncSchedules()
        assertTrue(tracker.isTracking)
    }

    @Test
    fun deleteAlarm_removesAlarmFromRepository() = runTest {
        val alarm = createSampleAlarm(id = "del-1")
        service.saveAlarm(alarm)

        val result = service.deleteAlarm("del-1")

        assertTrue(result.isSuccess)
        assertNull(repository.getAlarm("del-1"))
    }

    @Test
    fun deleteAlarm_whenAlarmIsArmed_stopsTrackingAndCancelsSchedule() = runTest {
        val armed = createSampleAlarm(id = "del-armed", state = AlarmState.ARMED_MONITORING)
        service.saveAlarm(armed)
        scheduler.cancelledWakeups.clear()

        service.deleteAlarm("del-armed")

        assertFalse(tracker.isTracking)
        assertTrue(scheduler.cancelledWakeups.contains("del-armed"))
    }

    @Test
    fun deleteAlarm_whenAlarmIsRinging_stopsRingerAndTracking() = runTest {
        val ringing = createSampleAlarm(id = "del-ringing", state = AlarmState.RINGING)
        service.saveAlarm(ringing)

        service.deleteAlarm("del-ringing")

        assertFalse(ringer.ringingIds.contains("del-ringing"))
        assertFalse(tracker.isTracking)
        assertNull(repository.getAlarm("del-ringing"))
    }

    @Test
    fun deleteAlarm_nonExistentAlarm_succeedsWithoutError() = runTest {
        val result = service.deleteAlarm("non-existent")
        assertTrue(result.isSuccess)
    }
}
