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
import kotlinx.coroutines.flow.first
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
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

    private suspend fun assertFailedWriteKeepsRingingOwner(
        operation: suspend (AlarmService) -> Result<Unit>,
    ) {
        val originalFailure = IllegalStateException("initial storage write failed")
        var failedWrites = 0
        val failingRepository = object : AlarmRepositoryPort by repository {
            var failWrites = false

            override suspend fun upsertAlarm(alarm: LocationAlarm) {
                if (failWrites) {
                    failedWrites++
                    if (failedWrites == 1) throw originalFailure
                    throw IllegalStateException("rollback storage write also failed")
                }
                repository.upsertAlarm(alarm)
            }
        }
        val testedService = AlarmService(
            failingRepository, tracker, scheduler, ringer, ScheduleEvaluator(JAKARTA), clock,
        )
        clock.now = com.muhammadridha.fjalar.domain.jakarta(2026, 10, 26, 8)
        val owner = createSampleAlarm(
            id = "a", state = AlarmState.RINGING,
            schedule = com.muhammadridha.fjalar.domain.WEEKDAY_COMMUTE,
        )
        testedService.saveAlarm(owner).getOrThrow()
        testedService.saveAlarm(owner.copy(id = "b")).getOrThrow()
        failingRepository.failWrites = true

        val result = operation(testedService)

        assertTrue(result.isFailure)
        kotlin.test.assertSame(originalFailure, result.exceptionOrNull())
        val displayedOwner = testedService.observeAlarms().first()
            .filter { it.state == AlarmState.RINGING }.minByOrNull { it.id }
        kotlin.test.assertEquals("a", displayedOwner?.id)
        kotlin.test.assertEquals(setOf(displayedOwner!!.id), ringer.ringingIds)
        kotlin.test.assertEquals(owner, repository.getAlarm("a"))
        assertNull(scheduler.wakeups["a"])
    }

    @Test
    fun failedDismissStorageWriteRestoresDisplayedAudibleOwner() = runTest {
        assertFailedWriteKeepsRingingOwner { it.dismissAlarm("a") }
    }

    @Test
    fun failedSnoozeAndRollbackStorageWritesRestoreDisplayedAudibleOwner() = runTest {
        assertFailedWriteKeepsRingingOwner { it.snoozeAlarm("a") }
    }

    @Test
    fun failedDisableStorageWriteRestoresDisplayedAudibleOwner() = runTest {
        assertFailedWriteKeepsRingingOwner { it.toggleAlarm("a", false) }
    }

    @Test
    fun failedSkipStorageWriteRestoresDisplayedAudibleOwner() = runTest {
        assertFailedWriteKeepsRingingOwner { it.skipToday("a") }
    }

    @Test
    fun snoozePersistsDeadlineAndRingsWithoutFix() = runTest {
        val alarm = createSampleAlarm(state = AlarmState.RINGING)
        service.saveAlarm(alarm).getOrThrow()
        val deadline = clock.now() + kotlin.time.Duration.parse("2m")
        service.snoozeAlarm(alarm.id).getOrThrow()
        kotlin.test.assertEquals(AlarmState.SNOOZED, repository.getAlarm(alarm.id)!!.state)
        kotlin.test.assertEquals(deadline, repository.getAlarm(alarm.id)!!.snoozedUntil)
        kotlin.test.assertEquals(deadline, scheduler.wakeups[alarm.id])
        assertFalse(tracker.isTracking)
        assertTrue(service.handleLocationUpdate(alarm.coordinates, 1f).isEmpty())
        clock.now = deadline
        service.syncSchedules()
        kotlin.test.assertEquals(AlarmState.RINGING, repository.getAlarm(alarm.id)!!.state)
        kotlin.test.assertEquals(alarm.radius, repository.getAlarm(alarm.id)!!.radius)
    }

    @Test
    fun failedSnoozeStopLeavesRingingForRetry() = runTest {
        val alarm = createSampleAlarm(state = AlarmState.RINGING)
        service.saveAlarm(alarm).getOrThrow()
        ringer.shouldFailOnStop = true
        assertTrue(service.snoozeAlarm(alarm.id).isFailure)
        kotlin.test.assertEquals(AlarmState.RINGING, repository.getAlarm(alarm.id)!!.state)
    }

    @Test
    fun simultaneousAlarmsHaveOneDeterministicOwner() = runTest {
        service.saveAlarm(createSampleAlarm(id = "b", state = AlarmState.ARMED_MONITORING)).getOrThrow()
        service.saveAlarm(createSampleAlarm(id = "a", state = AlarmState.ARMED_MONITORING)).getOrThrow()
        kotlin.test.assertEquals(2, service.handleLocationUpdate(GeoCoordinates(-6.2, 106.8), 1f).size)
        kotlin.test.assertEquals(setOf("a"), ringer.ringingIds)
        service.dismissAlarm("a").getOrThrow()
        kotlin.test.assertEquals(setOf("b"), ringer.ringingIds)
    }

    @Test
    fun failedSnoozeSchedulingLeavesRingingAndAudible() = runTest {
        val alarm = createSampleAlarm(state = AlarmState.RINGING)
        service.saveAlarm(alarm).getOrThrow()
        scheduler.shouldFailOnSchedule = true
        assertTrue(service.snoozeAlarm(alarm.id).isFailure)
        kotlin.test.assertEquals(alarm, repository.getAlarm(alarm.id))
        kotlin.test.assertEquals(setOf(alarm.id), ringer.ringingIds)
    }

    @Test
    fun snoozeOwnerHandsPlaybackToPendingAlarm() = runTest {
        service.saveAlarm(createSampleAlarm(id = "a", state = AlarmState.RINGING)).getOrThrow()
        service.saveAlarm(createSampleAlarm(id = "b", state = AlarmState.RINGING)).getOrThrow()
        service.snoozeAlarm("a").getOrThrow()
        kotlin.test.assertEquals(setOf("b"), ringer.ringingIds)
        kotlin.test.assertEquals(AlarmState.RINGING, repository.getAlarm("b")!!.state)
    }

    @Test
    fun recreatedServiceCanStopExistingPlaybackWhenSnoozing() = runTest {
        val alarm = createSampleAlarm(state = AlarmState.RINGING)
        service.saveAlarm(alarm).getOrThrow()
        val recreated = AlarmService(repository, tracker, scheduler, ringer, ScheduleEvaluator(JAKARTA), clock)
        recreated.snoozeAlarm(alarm.id).getOrThrow()
        assertTrue(ringer.ringingIds.isEmpty())
        kotlin.test.assertEquals(AlarmState.SNOOZED, repository.getAlarm(alarm.id)!!.state)
    }

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
