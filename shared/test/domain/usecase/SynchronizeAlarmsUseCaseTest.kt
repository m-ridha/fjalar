package com.muhammadridha.fjalar.domain.usecase

import com.muhammadridha.fjalar.domain.WEEKDAY_COMMUTE
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort
import com.muhammadridha.fjalar.fakes.FakeBackgroundSyncAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SynchronizeAlarmsUseCaseTest {

    private class FakeSyncSchedulesPort(
        var alarmsToReturn: List<LocationAlarm> = emptyList(),
    ) : SyncSchedulesPort {
        var callCount = 0
            private set

        override suspend fun syncSchedules(): List<LocationAlarm> {
            callCount++
            return alarmsToReturn
        }
    }

    private fun scheduledAlarm(state: AlarmState): LocationAlarm =
        manggaraiAlarm(schedule = WEEKDAY_COMMUTE).copy(state = state)

    @Test
    fun invokeWithoutBackgroundSyncPortOnlyCallsSyncSchedules() = runTest {
        val expectedAlarms = listOf(scheduledAlarm(AlarmState.SCHEDULED_IDLE))
        val syncSchedulesPort = FakeSyncSchedulesPort(alarmsToReturn = expectedAlarms)
        val useCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = syncSchedulesPort,
            backgroundSyncPort = null,
        )

        val result = useCase()

        assertEquals(1, syncSchedulesPort.callCount)
        assertEquals(expectedAlarms, result)
    }

    @Test
    fun invokeSchedulesBackgroundSyncForActiveScheduledStatesAndSnoozedAlarms() = runTest {
        val cases = listOf(
            scheduledAlarm(AlarmState.SCHEDULED_IDLE),
            scheduledAlarm(AlarmState.ARMED_MONITORING),
            scheduledAlarm(AlarmState.COMPLETED_FOR_WINDOW),
            manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(state = AlarmState.SNOOZED),
        )
        for (alarm in cases) {
            val syncSchedulesPort = FakeSyncSchedulesPort(alarmsToReturn = listOf(alarm))
            val backgroundSyncAdapter = FakeBackgroundSyncAdapter()
            val useCase = SynchronizeAlarmsUseCase(
                syncSchedulesPort = syncSchedulesPort,
                backgroundSyncPort = backgroundSyncAdapter,
            )

            val result = useCase()

            assertEquals(listOf(alarm), result)
            assertEquals(SynchronizeAlarmsUseCase.DEFAULT_NEXT_SYNC_MINUTES, backgroundSyncAdapter.scheduledDelayMinutes)
            assertFalse(backgroundSyncAdapter.isCancelled)
        }
    }

    @Test
    fun invokeCancelsBackgroundSyncWhenNoScheduledOrSnoozedAlarmsRequireRefresh() = runTest {
        val cases = listOf(
            emptyList(),
            listOf(scheduledAlarm(AlarmState.DISABLED)),
            listOf(manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(state = AlarmState.ARMED_MONITORING)),
            listOf(manggaraiAlarm(schedule = AlarmSchedule.ONE_TIME).copy(state = AlarmState.DISABLED)),
            listOf(scheduledAlarm(AlarmState.RINGING)),
        )
        for (alarms in cases) {
            val syncSchedulesPort = FakeSyncSchedulesPort(alarmsToReturn = alarms)
            val backgroundSyncAdapter = FakeBackgroundSyncAdapter()
            val useCase = SynchronizeAlarmsUseCase(
                syncSchedulesPort = syncSchedulesPort,
                backgroundSyncPort = backgroundSyncAdapter,
            )

            val result = useCase()

            assertEquals(alarms, result)
            assertNull(backgroundSyncAdapter.scheduledDelayMinutes)
            assertTrue(backgroundSyncAdapter.isCancelled)
        }
    }

    @Test
    fun invokeWithCustomDelaySchedulesExpectedDelay() = runTest {
        val syncSchedulesPort = FakeSyncSchedulesPort(
            alarmsToReturn = listOf(scheduledAlarm(AlarmState.SCHEDULED_IDLE)),
        )
        val backgroundSyncAdapter = FakeBackgroundSyncAdapter()
        val useCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = syncSchedulesPort,
            backgroundSyncPort = backgroundSyncAdapter,
        )

        useCase(nextSyncDelayMinutes = 30L)

        assertEquals(1, syncSchedulesPort.callCount)
        assertEquals(30L, backgroundSyncAdapter.scheduledDelayMinutes)
    }

    @Test
    fun invokeWithNegativeDelayClampsToMinimumDelay() = runTest {
        val syncSchedulesPort = FakeSyncSchedulesPort(
            alarmsToReturn = listOf(scheduledAlarm(AlarmState.SCHEDULED_IDLE)),
        )
        val backgroundSyncAdapter = FakeBackgroundSyncAdapter()
        val useCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = syncSchedulesPort,
            backgroundSyncPort = backgroundSyncAdapter,
        )

        useCase(nextSyncDelayMinutes = -5L)

        assertEquals(SynchronizeAlarmsUseCase.MINIMUM_SYNC_DELAY_MINUTES, backgroundSyncAdapter.scheduledDelayMinutes)
    }

    @Test
    fun invokeWithFailingBackgroundSyncPortStillReturnsUpdatedAlarms() = runTest {
        val expectedAlarms = listOf(scheduledAlarm(AlarmState.SCHEDULED_IDLE))
        val syncSchedulesPort = FakeSyncSchedulesPort(alarmsToReturn = expectedAlarms)
        val failingAdapter = object : BackgroundSyncPort {
            override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
                throw IllegalStateException("Simulated background refresh failure")
            }
            override suspend fun cancelBackgroundSync() {
                throw IllegalStateException("Simulated cancel failure")
            }
        }
        val useCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = syncSchedulesPort,
            backgroundSyncPort = failingAdapter,
        )

        val result = useCase()

        assertEquals(1, syncSchedulesPort.callCount)
        assertEquals(expectedAlarms, result)
    }

    @Test
    fun invokeRethrowsCancellationExceptionOnScheduleAndCancel() = runTest {
        val cancellingAdapter = object : BackgroundSyncPort {
            override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
                throw CancellationException("Schedule cancelled")
            }
            override suspend fun cancelBackgroundSync() {
                throw CancellationException("Cancel cancelled")
            }
        }

        val scheduleUseCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = FakeSyncSchedulesPort(listOf(scheduledAlarm(AlarmState.SCHEDULED_IDLE))),
            backgroundSyncPort = cancellingAdapter,
        )
        assertFailsWith<CancellationException> { scheduleUseCase() }

        val cancelUseCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = FakeSyncSchedulesPort(emptyList()),
            backgroundSyncPort = cancellingAdapter,
        )
        assertFailsWith<CancellationException> { cancelUseCase() }
    }
}
