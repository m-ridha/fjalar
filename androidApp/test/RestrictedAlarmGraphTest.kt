package com.muhammadridha.fjalar

import androidx.lifecycle.ViewModelStore
import com.muhammadridha.fjalar.di.androidPresentationModule
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SnoozeAlarmPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RestrictedAlarmGraphTest {
    @Test
    fun productionPresentationModuleAcceptsNotificationAlarmIdWithoutStartingServices() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = koinApplication {
            modules(androidPresentationModule, module {
                single<ObserveAlarmsPort> {
                    object : ObserveAlarmsPort {
                        override fun observeAlarms() = MutableStateFlow(emptyList<LocationAlarm>())
                    }
                }
                single<DismissAlarmPort> {
                    object : DismissAlarmPort {
                        override suspend fun dismissAlarm(alarmId: String) = Result.success(Unit)
                    }
                }
                single<SnoozeAlarmPort> {
                    object : SnoozeAlarmPort {
                        override suspend fun snoozeAlarm(alarmId: String) = Result.success(Unit)
                    }
                }
            })
        }
        val store = ViewModelStore()
        try {
            val viewModel = app.koin.get<RestrictedAlarmViewModel> { parametersOf("stale-notification") }
            store.put("restricted", viewModel)
            assertFalse(viewModel.controller.state.value.isLoading)
            assertNull(viewModel.controller.state.value.alarm)
        } finally {
            store.clear()
            app.close()
            Dispatchers.resetMain()
        }
    }
}
