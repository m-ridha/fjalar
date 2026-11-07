package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.adapters.storage.PersistentAlarmRepository
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.adapters.storage.PersistentRecentDestinationsAdapter
import com.muhammadridha.fjalar.domain.ports.inbound.*
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRepositoryPort
import com.muhammadridha.fjalar.domain.ports.outbound.PreferencesPort
import com.muhammadridha.fjalar.domain.ports.outbound.RecentDestinationsPort
import com.muhammadridha.fjalar.domain.service.AlarmService
import com.muhammadridha.fjalar.domain.service.ScheduleEvaluator
import com.muhammadridha.fjalar.domain.usecase.SynchronizeAlarmsUseCase
import com.muhammadridha.fjalar.ui.FjalarViewModel
import com.muhammadridha.fjalar.ui.screens.MapPickerViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Mutable system setting shared by the evaluator without replacing its service or mutex. */
class SystemTimeZone {
    @kotlin.concurrent.Volatile
    var current: TimeZone = TimeZone.currentSystemDefault()
        private set

    fun refresh(timeZone: TimeZone = TimeZone.currentSystemDefault()) {
        current = timeZone
    }
}

val persistenceModule = module {
    single<AlarmRepositoryPort> { PersistentAlarmRepository(get()) }
    single<PreferencesPort> { PersistentPreferencesAdapter(get()) }
    single<RecentDestinationsPort> { PersistentRecentDestinationsAdapter(get()) }
}

val alarmModule = module {
    single { SystemTimeZone() }
    single { ScheduleEvaluator(timeZoneProvider = { get<SystemTimeZone>().current }) }
    single { AlarmService(get(), get(), get(), get(), get()) }
    single<ObserveAlarmsPort> { get<AlarmService>() }
    single<SaveAlarmPort> { get<AlarmService>() }
    single<ToggleAlarmPort> { get<AlarmService>() }
    single<HandleLocationUpdatePort> { get<AlarmService>() }
    single<DismissAlarmPort> { get<AlarmService>() }
    single<SnoozeAlarmPort> { get<AlarmService>() }
    single<SkipTodayPort> { get<AlarmService>() }
    single<SyncSchedulesPort> { get<AlarmService>() }
    single<DeleteAlarmPort> { get<AlarmService>() }
    single { SynchronizeAlarmsUseCase(get(), getOrNull()) }
}

val presentationModule = module {
    viewModel {
        FjalarViewModel(
            observeAlarmsPort = get(),
            saveAlarmPort = get(),
            toggleAlarmPort = get(),
            dismissAlarmPort = get(),
            skipTodayPort = get(),
            preferencesPort = get(),
            syncSchedulesPort = get(),
            searchPlacesPort = get(),
            deleteAlarmPort = get(),
            locationTrackerPort = get(),
            snoozeAlarmPort = get(),
            recentDestinationsPort = getOrNull(),
        )
    }
    viewModel {
        MapPickerViewModel(
            recentDestinationsPort = get(),
            searchPlacesPort = getOrNull(),
        )
    }
}

/** Platform bindings are explicit so isolated tests can supply a module of fakes. */
fun fjalarModule(platformModule: Module): Module = module {
    includes(persistenceModule, alarmModule, presentationModule, platformModule)
}

fun startFjalar(platformModule: Module, configure: KoinAppDeclaration = {}) = startKoin {
    configure()
    modules(fjalarModule(platformModule))
}
