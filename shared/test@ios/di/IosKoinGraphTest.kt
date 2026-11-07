package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.accessibility.IosAccessibilityAdapter
import com.muhammadridha.fjalar.adapters.location.IosCLLocationAdapter
import com.muhammadridha.fjalar.adapters.ringer.IosCriticalAudioRingerAdapter
import com.muhammadridha.fjalar.adapters.scheduler.IosBackgroundSchedulerAdapter
import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.sync.IosBackgroundSyncAdapter
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase
import com.muhammadridha.fjalar.ui.screens.MapPickerViewModel
import kotlinx.datetime.TimeZone
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class IosKoinGraphTest {
    @Test
    fun shippingPlatformModuleResolvesWithoutStartingMonitoring() {
        // Only persistent storage is replaced; native constructors must resolve without GPS/audio startup.
        val app = koinApplication {
            modules(fjalarModule(iosPlatformModule), module {
                single<KeyValueStorage> { InMemoryKeyValueStorage() }
            })
        }
        try {
            val koin = app.koin
            assertIs<IosBackgroundSchedulerAdapter>(koin.get<AlarmSchedulerPort>())
            assertIs<IosAccessibilityAdapter>(koin.get<AccessibilityPort>())
            assertIs<IosCriticalAudioRingerAdapter>(koin.get<AlarmRingerPort>())
            val tracker = assertIs<IosCLLocationAdapter>(koin.get<LocationTrackerPort>())
            assertSame(tracker, koin.get<IosCLLocationAdapter>())
            assertFalse(tracker.isTracking)
            assertSame(koin.get<AlarmService>(), koin.get<ObserveAlarmsPort>())
            koin.get<SearchPlacesPort>()
            koin.get<SystemTimeZone>().refresh(TimeZone.of("Asia/Tokyo"))
            koin.get<IosSystemEvents>()
            assertNotNull(koin.getOrNull<RecentDestinationsPort>())
            assertNotNull(koin.getOrNull<MapPickerViewModel>())
            assertIs<IosBackgroundSyncAdapter>(koin.get<BackgroundSyncPort>())
            assertNotNull(koin.getOrNull<SynchronizeAlarmsUseCase>())
        } finally {
            app.close()
        }
    }
}
