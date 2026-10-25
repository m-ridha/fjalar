package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.storage.InMemoryKeyValueStorage
import com.muhammadridha.fjalar.adapters.storage.PersistentPreferencesAdapter
import com.muhammadridha.fjalar.domain.manggaraiAlarm
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.ports.inbound.DismissAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.ObserveAlarmsPort
import com.muhammadridha.fjalar.domain.ports.inbound.SaveAlarmPort
import com.muhammadridha.fjalar.domain.ports.inbound.SkipTodayPort
import com.muhammadridha.fjalar.domain.ports.inbound.ToggleAlarmPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FjalarViewModelTest {

    private class FakeObserveAlarmsPort(initialAlarms: List<LocationAlarm> = emptyList()) : ObserveAlarmsPort {
        val flow = MutableStateFlow(initialAlarms)
        override fun observeAlarms(): Flow<List<LocationAlarm>> = flow
    }

    private class FakeSaveAlarmPort(
        var shouldFail: Boolean = false,
    ) : SaveAlarmPort {
        val savedAlarms = mutableListOf<LocationAlarm>()
        override suspend fun saveAlarm(alarm: LocationAlarm): Result<Unit> {
            if (shouldFail) {
                return Result.failure(IllegalStateException("Simulated save failure"))
            }
            savedAlarms.add(alarm)
            return Result.success(Unit)
        }
    }

    private class FakeToggleAlarmPort : ToggleAlarmPort {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        override suspend fun toggleAlarm(alarmId: String, enabled: Boolean): Result<Unit> {
            toggled.add(alarmId to enabled)
            return Result.success(Unit)
        }
    }

    private class FakeDismissAlarmPort : DismissAlarmPort {
        val dismissed = mutableListOf<String>()
        override suspend fun dismissAlarm(alarmId: String): Result<Unit> {
            dismissed.add(alarmId)
            return Result.success(Unit)
        }
    }

    private class FakeSkipTodayPort : SkipTodayPort {
        val skipped = mutableListOf<String>()
        override suspend fun skipToday(alarmId: String): Result<Unit> {
            skipped.add(alarmId)
            return Result.success(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.createTestViewModel(
        observePort: ObserveAlarmsPort = FakeObserveAlarmsPort(),
        savePort: SaveAlarmPort = FakeSaveAlarmPort(),
        togglePort: ToggleAlarmPort = FakeToggleAlarmPort(),
        dismissPort: DismissAlarmPort = FakeDismissAlarmPort(),
        skipTodayPort: SkipTodayPort = FakeSkipTodayPort(),
        storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage(),
    ): Triple<FjalarViewModel, PersistentPreferencesAdapter, CoroutineScope> {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(testDispatcher + Job())
        val preferencesPort = PersistentPreferencesAdapter(storage)
        val viewModel = FjalarViewModel(
            observeAlarmsPort = observePort,
            saveAlarmPort = savePort,
            toggleAlarmPort = togglePort,
            dismissAlarmPort = dismissPort,
            skipTodayPort = skipTodayPort,
            preferencesPort = preferencesPort,
            coroutineScope = vmScope,
        )
        return Triple(viewModel, preferencesPort, vmScope)
    }

    @Test
    fun initialStateLoadsAlarmsAndPreferences() = runTest {
        val initialAlarm = manggaraiAlarm()
        val observePort = FakeObserveAlarmsPort(listOf(initialAlarm))
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.DARK.name)
            putString(PersistentPreferencesAdapter.KEY_LOCALE, AppLocale.ID.name)
        }

        val (viewModel, _, vmScope) = createTestViewModel(
            observePort = observePort,
            storage = storage,
        )
        try {
            val state = viewModel.uiState.value
            assertEquals(listOf(initialAlarm), state.alarms)
            assertEquals(ThemeMode.DARK, state.themeMode)
            assertEquals(AppLocale.ID, state.appLocale)
            assertNull(state.activeRingingAlarm)
            assertNull(state.selectedAlarmForEdit)
            assertFalse(state.isConfigureSheetVisible)
            assertEquals("", state.searchQuery)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleAlarmDispatchesToPort() = runTest {
        val togglePort = FakeToggleAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(togglePort = togglePort)
        try {
            viewModel.onToggleAlarm("manggarai", false)
            assertEquals(listOf("manggarai" to false), togglePort.toggled)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun saveAlarmDispatchesToPortAndClosesConfigureSheet() = runTest {
        val savePort = FakeSaveAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(savePort = savePort)
        try {
            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)

            val newAlarm = manggaraiAlarm()
            viewModel.onSaveAlarm(newAlarm)

            assertEquals(listOf(newAlarm), savePort.savedAlarms)
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleThemeSwitchesBetweenModes() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.LIGHT.name)
        }
        val (viewModel, _, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme()
            assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme()
            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleLocaleSwitchesBetweenEnAndId() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_LOCALE, AppLocale.EN.name)
        }
        val (viewModel, _, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(AppLocale.EN, viewModel.uiState.value.appLocale)

            viewModel.onToggleLocale()
            assertEquals(AppLocale.ID, viewModel.uiState.value.appLocale)

            viewModel.onToggleLocale()
            assertEquals(AppLocale.EN, viewModel.uiState.value.appLocale)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun ringingAlarmDetectionExtractsFirstRingingAlarm() = runTest {
        val alarm1 = manggaraiAlarm().copy(id = "1", state = AlarmState.ARMED_MONITORING)
        val alarm2 = manggaraiAlarm().copy(id = "2", state = AlarmState.RINGING)
        val observePort = FakeObserveAlarmsPort(listOf(alarm1))

        val (viewModel, _, vmScope) = createTestViewModel(observePort = observePort)
        try {
            assertNull(viewModel.uiState.value.activeRingingAlarm)

            // Alarm 2 starts ringing
            observePort.flow.value = listOf(alarm1, alarm2)
            assertEquals(alarm2, viewModel.uiState.value.activeRingingAlarm)

            // Alarm 2 silenced
            observePort.flow.value = listOf(alarm1, alarm2.copy(state = AlarmState.COMPLETED_FOR_WINDOW))
            assertNull(viewModel.uiState.value.activeRingingAlarm)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun dismissRingingAlarmDispatchesToPort() = runTest {
        val dismissPort = FakeDismissAlarmPort()
        val (viewModel, _, vmScope) = createTestViewModel(dismissPort = dismissPort)
        try {
            viewModel.onDismissRingingAlarm("alarm-ring-123")
            assertEquals(listOf("alarm-ring-123"), dismissPort.dismissed)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun skipTodayDispatchesToPort() = runTest {
        val skipPort = FakeSkipTodayPort()
        val (viewModel, _, vmScope) = createTestViewModel(skipTodayPort = skipPort)
        try {
            viewModel.onSkipToday("manggarai")
            assertEquals(listOf("manggarai"), skipPort.skipped)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun sheetVisibilityTransitions() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)

            val alarm = manggaraiAlarm()
            viewModel.onOpenEditAlarm(alarm)
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertEquals(alarm, viewModel.uiState.value.selectedAlarmForEdit)

            viewModel.onDismissConfigureSheet()
            assertFalse(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)

            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
            assertNull(viewModel.uiState.value.selectedAlarmForEdit)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun searchQueryUpdatesState() = runTest {
        val (viewModel, _, vmScope) = createTestViewModel()
        try {
            viewModel.onSearchQueryChanged("Sudirman")
            assertEquals("Sudirman", viewModel.uiState.value.searchQuery)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun saveAlarmFailureKeepsConfigureSheetOpen() = runTest {
        val savePort = FakeSaveAlarmPort(shouldFail = true)
        val (viewModel, _, vmScope) = createTestViewModel(savePort = savePort)
        try {
            viewModel.onOpenNewAlarm()
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)

            val alarm = manggaraiAlarm()
            viewModel.onSaveAlarm(alarm)

            // Sheet should remain open on failure so user doesn't lose inputs
            assertTrue(viewModel.uiState.value.isConfigureSheetVisible)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun toggleThemeFromSystemSwitchesToDark() = runTest {
        val storage = InMemoryKeyValueStorage().apply {
            putString(PersistentPreferencesAdapter.KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        }
        val (viewModel, preferences, vmScope) = createTestViewModel(storage = storage)
        try {
            assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

            viewModel.onToggleTheme()
            assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
            assertEquals(ThemeMode.DARK, preferences.observeThemeMode().first())
        } finally {
            vmScope.cancel()
        }
    }
}
