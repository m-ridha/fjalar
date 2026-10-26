package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.createDefaultViewModel
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.inbound.SyncSchedulesPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.fakes.FakeAlarmRinger
import com.muhammadridha.fjalar.fakes.FakeAlarmScheduler
import com.muhammadridha.fjalar.fakes.FakeLocationTracker
import com.muhammadridha.fjalar.fakes.InMemoryAlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformContainerTest {

    private class FakePlatformContainer(
        override val keyValueStorage: KeyValueStorage = InMemoryKeyValueStorage(),
        override val alarmRepository: AlarmRepositoryPort = InMemoryAlarmRepository(),
        override val preferences: PreferencesPort = PersistentPreferencesAdapter(keyValueStorage),
        override val alarmScheduler: AlarmSchedulerPort = FakeAlarmScheduler(),
        override val locationTracker: LocationTrackerPort = FakeLocationTracker(),
        override val alarmRinger: AlarmRingerPort = FakeAlarmRinger(),
    ) : FjalarPlatformContainer {

        var currentTimeZone: TimeZone = TimeZone.currentSystemDefault()
        val scheduleEvaluator = ScheduleEvaluator(timeZoneProvider = { currentTimeZone })

        override val alarmService = AlarmService(
            repository = alarmRepository,
            tracker = locationTracker,
            scheduler = alarmScheduler,
            ringer = alarmRinger,
            scheduleEvaluator = scheduleEvaluator,
        )

        override val syncSchedulesPort: SyncSchedulesPort
            get() = alarmService

        override fun onTimezoneChanged(newTimeZone: TimeZone) {
            currentTimeZone = newTimeZone
        }
    }

    @Test
    fun createDefaultViewModel_withDefaults_initializesSuccessfully() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val vm = createDefaultViewModel(coroutineScope = vmScope)
            val state = vm.uiState.value

            assertTrue(state.alarms.isEmpty())
            assertEquals(ThemeMode.SYSTEM, state.themeMode)
            assertEquals(AppLocale.EN, state.appLocale)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun createDefaultViewModel_withCustomContainerServices_wiresCustomInstances() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val container = FakePlatformContainer()
            container.preferences.setThemeMode(ThemeMode.DARK)
            container.preferences.setLocale(AppLocale.ID)

            val vm = createDefaultViewModel(
                keyValueStorage = container.keyValueStorage,
                alarmService = container.alarmService,
                preferencesPort = container.preferences,
                coroutineScope = vmScope,
            )

            val state = vm.uiState.value
            assertEquals(ThemeMode.DARK, state.themeMode)
            assertEquals(AppLocale.ID, state.appLocale)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun createDefaultViewModel_withCustomAdaptersWithoutAlarmService_wiresCorrectly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        try {
            val tracker = FakeLocationTracker()
            val scheduler = FakeAlarmScheduler()
            val ringer = FakeAlarmRinger()
            val storage = InMemoryKeyValueStorage()
            val preferences = PersistentPreferencesAdapter(storage)
            preferences.setThemeMode(ThemeMode.LIGHT)

            val vm = createDefaultViewModel(
                keyValueStorage = storage,
                tracker = tracker,
                scheduler = scheduler,
                ringer = ringer,
                preferencesPort = preferences,
                coroutineScope = vmScope,
            )

            val state = vm.uiState.value
            assertEquals(ThemeMode.LIGHT, state.themeMode)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun fakePlatformContainer_timezoneChange_updatesScheduleEvaluator() {
        val container = FakePlatformContainer()
        val initialService = container.alarmService

        container.onTimezoneChanged(TimeZone.of("Asia/Tokyo"))
        val newService = container.alarmService

        assertNotNull(newService)
        assertTrue(initialService === newService)
        assertEquals(TimeZone.of("Asia/Tokyo"), container.scheduleEvaluator.timeZone)
    }
}
