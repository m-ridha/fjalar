package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.accessibility.IosAccessibilityAdapter
import com.muhammadridha.fjalar.adapters.location.IosCLLocationAdapter
import com.muhammadridha.fjalar.adapters.ringer.IosCriticalAudioRingerAdapter
import com.muhammadridha.fjalar.adapters.scheduler.IosBackgroundSchedulerAdapter
import com.muhammadridha.fjalar.adapters.search.HybridPlaceSearchAdapter
import com.muhammadridha.fjalar.adapters.search.IosAppleGeocoderEngine
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.UserDefaultsKeyValueStorage
import com.muhammadridha.fjalar.domain.ports.outbound.AccessibilityPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.ports.outbound.SearchPlacesPort
import org.koin.dsl.module
import org.koin.dsl.onClose

val iosPlatformModule = module {
    single<KeyValueStorage> { UserDefaultsKeyValueStorage() }
    single<AccessibilityPort> { IosAccessibilityAdapter() }
    single {
        val storage = get<KeyValueStorage>()
        IosBackgroundSchedulerAdapter(localeProvider = { PersistentPreferencesAdapter.readLocale(storage) })
    }
    single<AlarmSchedulerPort> { get<IosBackgroundSchedulerAdapter>() }
    single<AlarmRingerPort> { IosCriticalAudioRingerAdapter() }
    single { IosCLLocationAdapter(updatePortProvider = { get() }) } onClose { it?.close() }
    single { IosSystemEvents(get()) } onClose { it?.close() }
    single<LocationTrackerPort> { get<IosCLLocationAdapter>() }
    single<SearchPlacesPort> { HybridPlaceSearchAdapter(onlineEngine = IosAppleGeocoderEngine()) }
}

// SwiftUI may recreate controllers; application resources and observers live once per process.
private val iosApplication by lazy {
    startFjalar(iosPlatformModule).also { application ->
        application.koin.get<IosBackgroundSchedulerAdapter>().requestAuthorization()
        application.koin.get<IosSystemEvents>()
    }
}

fun initializeIosApp() {
    iosApplication
}

internal class IosSystemEvents(timeZone: SystemTimeZone) {
    private val observer = platform.Foundation.NSNotificationCenter.defaultCenter.addObserverForName(
        name = platform.Foundation.NSSystemTimeZoneDidChangeNotification,
        `object` = null,
        queue = platform.Foundation.NSOperationQueue.mainQueue,
    ) { timeZone.refresh() }

    fun close() {
        platform.Foundation.NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
}
