package com.muhammadridha.fjalar.fakes

import com.muhammadridha.fjalar.adapters.search.HybridPlaceSearchAdapter
import com.muhammadridha.fjalar.adapters.storage.KeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.ui.FjalarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone

/** Explicit test fixture; fake services are never reachable from production startup. */
fun createTestViewModel(
    keyValueStorage: KeyValueStorage,
    tracker: LocationTrackerPort = FakeLocationTracker(),
    coroutineScope: CoroutineScope,
): FjalarViewModel {
    val service = AlarmService(
        PersistentAlarmRepository(keyValueStorage), tracker, FakeAlarmScheduler(),
        FakeAlarmRinger(), ScheduleEvaluator(TimeZone.UTC),
    )
    return FjalarViewModel(
        observeAlarmsPort = service,
        saveAlarmPort = service,
        toggleAlarmPort = service,
        dismissAlarmPort = service,
        skipTodayPort = service,
        preferencesPort = PersistentPreferencesAdapter(keyValueStorage),
        syncSchedulesPort = service,
        searchPlacesPort = HybridPlaceSearchAdapter(),
        deleteAlarmPort = service,
        locationTrackerPort = tracker,
        snoozeAlarmPort = service,
        coroutineScope = coroutineScope,
    )
}
