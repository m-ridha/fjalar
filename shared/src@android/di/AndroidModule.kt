package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.accessibility.AndroidAccessibilityAdapter
import com.muhammadridha.fjalar.adapters.location.AndroidFusedLocationAdapter
import com.muhammadridha.fjalar.adapters.ringer.AndroidSystemAlarmRingerAdapter
import com.muhammadridha.fjalar.adapters.scheduler.AndroidAlarmManagerSchedulerAdapter
import com.muhammadridha.fjalar.adapters.search.AndroidNominatimEngine
import com.muhammadridha.fjalar.adapters.search.HybridPlaceSearchAdapter
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.SharedPreferencesKeyValueStorage
import com.muhammadridha.fjalar.adapters.sync.AndroidBackgroundSyncAdapter
import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.BackgroundSyncPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.dsl.onClose

val androidPlatformModule = module {
    single<KeyValueStorage> { SharedPreferencesKeyValueStorage(androidContext()) }
    single<AccessibilityPort> { AndroidAccessibilityAdapter(androidContext()) }
    single<AlarmSchedulerPort> { AndroidAlarmManagerSchedulerAdapter(androidContext()) }
    single<AlarmRingerPort> {
        val storage = get<KeyValueStorage>()
        AndroidSystemAlarmRingerAdapter(
            androidContext(),
            localeProvider = { PersistentPreferencesAdapter.readLocale(storage) },
        )
    }
    single {
        // Resolve only when a fix arrives: AlarmService itself depends on this tracker.
        AndroidFusedLocationAdapter(androidContext(), updatePortProvider = { get() })
    } onClose { it?.close() }
    single<LocationTrackerPort> { get<AndroidFusedLocationAdapter>() }
    single<SearchPlacesPort> { HybridPlaceSearchAdapter(onlineEngine = AndroidNominatimEngine()) }
    single<BackgroundSyncPort> { AndroidBackgroundSyncAdapter() }
}
