package com.muhammadridha.fjalar.di

import com.muhammadridha.fjalar.RestrictedAlarmViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidPresentationModule = module {
    viewModel { (alarmId: String) -> RestrictedAlarmViewModel(alarmId, get(), get(), get()) }
}
