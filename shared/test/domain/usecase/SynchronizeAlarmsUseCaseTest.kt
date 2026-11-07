package com.muhammadridha.fjalar.domain.usecase

import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.fakes.FakeBackgroundSyncAdapter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun invokeWithoutBackgroundSyncPortOnlyCallsSyncSchedules() = runTest {
        val expectedAlarms = listOf(manggaraiAlarm())
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
    fun invokeWithBackgroundSyncPortSchedulesDefaultNextSyncDelay() = runTest {
        val expectedAlarms = listOf(manggaraiAlarm())
        val syncSchedulesPort = FakeSyncSchedulesPort(alarmsToReturn = expectedAlarms)
        val backgroundSyncAdapter = FakeBackgroundSyncAdapter()
        val useCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = syncSchedulesPort,
            backgroundSyncPort = backgroundSyncAdapter,
        )

        val result = useCase()

        assertEquals(1, syncSchedulesPort.callCount)
        assertEquals(expectedAlarms, result)
        assertEquals(SynchronizeAlarmsUseCase.DEFAULT_NEXT_SYNC_MINUTES, backgroundSyncAdapter.scheduledDelayMinutes)
    }

    @Test
    fun invokeWithCustomDelaySchedulesExpectedDelay() = runTest {
        val syncSchedulesPort = FakeSyncSchedulesPort()
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
        val syncSchedulesPort = FakeSyncSchedulesPort()
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
        val expectedAlarms = listOf(manggaraiAlarm())
        val syncSchedulesPort = FakeSyncSchedulesPort(alarmsToReturn = expectedAlarms)
        val failingAdapter = object : com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort {
            override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
                throw IllegalStateException("Simulated background refresh failure")
            }
            override suspend fun cancelBackgroundSync() {}
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
    fun invokeRethrowsCancellationException() = runTest {
        val syncSchedulesPort = FakeSyncSchedulesPort()
        val cancellingAdapter = object : com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort {
            override suspend fun scheduleBackgroundSync(earliestBeginMinutesFromNow: Long) {
                throw kotlinx.coroutines.CancellationException("Coroutine cancelled")
            }
            override suspend fun cancelBackgroundSync() {}
        }
        val useCase = SynchronizeAlarmsUseCase(
            syncSchedulesPort = syncSchedulesPort,
            backgroundSyncPort = cancellingAdapter,
        )

        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            useCase()
        }
    }
}
