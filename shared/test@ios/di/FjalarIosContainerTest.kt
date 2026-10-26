package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FjalarIosContainerTest {

    @Test
    fun iosContainer_instantiatesPlatformContract() {
        val storage = InMemoryKeyValueStorage()
        val repo = InMemoryAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val tracker = FakeLocationTracker()
        val ringer = FakeAlarmRinger()

        val container = FjalarIosContainer(
            keyValueStorage = storage,
            alarmRepository = repo,
            alarmScheduler = scheduler,
            locationTracker = tracker,
            alarmRinger = ringer,
        )

        assertNotNull(container.alarmService)
        assertNotNull(container.syncSchedulesPort)
        assertNotNull(container.preferences)
        assertNotNull(container.locationTracker)
        assertNotNull(container.alarmScheduler)
        assertNotNull(container.alarmRinger)
    }

    @Test
    fun iosContainer_onTimezoneChanged_rebuildsAlarmService() {
        val storage = InMemoryKeyValueStorage()
        val repo = InMemoryAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val tracker = FakeLocationTracker()
        val ringer = FakeAlarmRinger()

        val container = FjalarIosContainer(
            keyValueStorage = storage,
            alarmRepository = repo,
            alarmScheduler = scheduler,
            locationTracker = tracker,
            alarmRinger = ringer,
        )

        val initialService = container.alarmService
        container.onTimezoneChanged(TimeZone.of("Europe/London"))
        val updatedService = container.alarmService

        assertNotNull(updatedService)
        // AlarmService remains a stable singleton instance to prevent mutex and consumer divergence
        assertTrue(initialService === updatedService)
        assertEquals(TimeZone.of("Europe/London"), container.scheduleEvaluator.timeZone)
    }
}
