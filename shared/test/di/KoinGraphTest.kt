package com.muhammadridha.fjalar.di

import androidx.lifecycle.ViewModelStore
import com.muhammadridha.fjalar.adapters.search.HybridPlaceSearchAdapter
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.inbound.*
import com.muhammadridha.fjalar.domain.ports.outbound.*
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.fakes.*
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.screens.MapPickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.TimeZone
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.*

internal fun fakePlatformModule() = module {
    single<KeyValueStorage> { InMemoryKeyValueStorage() }
    single<AccessibilityPort> { FakeAccessibilityAdapter() }
    single<LocationTrackerPort> { FakeLocationTracker() }
    single<AlarmSchedulerPort> { FakeAlarmScheduler() }
    single<AlarmRingerPort> { FakeAlarmRinger() }
    single<SearchPlacesPort> { HybridPlaceSearchAdapter() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KoinGraphTest {
    @Test
    fun allInboundPortsShareOneServiceAndPersistenceIsSingleton() {
        val app = koinApplication { modules(fjalarModule(fakePlatformModule())) }
        try {
            val koin = app.koin
            val service = koin.get<AlarmService>()
            assertSame(service, koin.get<ObserveAlarmsPort>())
            assertSame(service, koin.get<SaveAlarmPort>())
            assertSame(service, koin.get<ToggleAlarmPort>())
            assertSame(service, koin.get<HandleLocationUpdatePort>())
            assertSame(service, koin.get<DismissAlarmPort>())
            assertSame(service, koin.get<SnoozeAlarmPort>())
            assertSame(service, koin.get<SkipTodayPort>())
            assertSame(service, koin.get<SyncSchedulesPort>())
            assertSame(service, koin.get<DeleteAlarmPort>())
            assertIs<AccessibilityPort>(koin.get<AccessibilityPort>())
            assertFalse(koin.get<AccessibilityPort>().isReduceTransparencyActive)
            assertIs<PersistentAlarmRepository>(koin.get<AlarmRepositoryPort>())
            assertSame(koin.get<AlarmRepositoryPort>(), koin.get<AlarmRepositoryPort>())
            assertSame(koin.get<PreferencesPort>(), koin.get<PreferencesPort>())
            assertFalse((koin.get<LocationTrackerPort>() as FakeLocationTracker).isTracking)
            assertTrue((koin.get<AlarmRingerPort>() as FakeAlarmRinger).ringingIds.isEmpty())
            koin.get<SystemTimeZone>().refresh(TimeZone.of("Asia/Tokyo"))
            assertEquals(TimeZone.of("Asia/Tokyo"), koin.get<ScheduleEvaluator>().timeZone)
            assertSame(service, koin.get<AlarmService>())
            assertNotNull(koin.getOrNull<RecentDestinationsPort>())
            assertNotNull(koin.getOrNull<MapPickerViewModel>())
        } finally {
            app.close()
        }
    }

    @Test
    fun presentationResolvesOverridesAndUsesDistinctViewModels() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = InMemoryAlarmRepository()
        val app = koinApplication {
            modules(fjalarModule(fakePlatformModule()), module {
                single<AlarmRepositoryPort> { repository }
            })
        }
        val store = ViewModelStore()
        try {
            val koin = app.koin
            assertSame(repository, koin.get<AlarmRepositoryPort>())
            val preferences = koin.get<PreferencesPort>()
            preferences.setThemeMode(ThemeMode.DARK)
            preferences.setLocale(AppLocale.ID)
            val first = koin.get<FjalarViewModel>()
            val second = koin.get<FjalarViewModel>()
            store.put("first", first)
            store.put("second", second)
            assertNotSame(first, second)
            assertEquals(ThemeMode.DARK, first.uiState.value.themeMode)
            assertEquals(AppLocale.ID, second.uiState.value.appLocale)
            assertTrue(first.uiState.value.alarms.isEmpty())
        } finally {
            store.clear()
            app.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun missingPlatformBindingsFailInsteadOfCreatingFallbackServices() {
        val app = koinApplication { modules(fjalarModule(module {})) }
        try {
            assertFails { app.koin.get<AlarmService>() }
        } finally {
            app.close()
        }
    }
}
