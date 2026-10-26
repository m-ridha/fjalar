package com.muhammadridha.fjalar.adapters.ringer

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosCriticalAudioRingerAdapterTest {

    private fun sampleAlarm(vibrateEnabled: Boolean = true): LocationAlarm {
        return LocationAlarm(
            id = "ios-alarm-1",
            label = "Gambir Station",
            coordinates = GeoCoordinates(-6.1767, 106.8306),
            radius = TriggerRadius(500),
            schedule = AlarmSchedule(type = ScheduleType.ONE_TIME),
            soundConfig = AlarmSoundConfig(
                soundEnabled = if (vibrateEnabled) false else true,
                vibrateEnabled = vibrateEnabled,
            ),
        )
    }

    @Test
    fun startRinging_withVibrationEnabled_startsHapticLoop() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCriticalAudioRingerAdapter(
            ringerScope = TestScope(testDispatcher),
        )

        assertFalse(adapter.isVibrating)
        adapter.startRinging(sampleAlarm(vibrateEnabled = true))
        assertTrue(adapter.isVibrating)

        adapter.stopRinging("ios-alarm-1")
        assertFalse(adapter.isVibrating)
    }

    @Test
    fun startRinging_withVibrationDisabled_doesNotStartHapticLoop() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCriticalAudioRingerAdapter(
            ringerScope = TestScope(testDispatcher),
        )

        adapter.startRinging(sampleAlarm(vibrateEnabled = false))
        assertFalse(adapter.isVibrating)
    }

    @Test
    fun stopRinging_withStaleAlarmId_doesNotStopActiveAlarm() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCriticalAudioRingerAdapter(
            ringerScope = TestScope(testDispatcher),
        )

        adapter.startRinging(sampleAlarm(vibrateEnabled = true))
        assertTrue(adapter.isVibrating)

        // Attempting to stop an inactive/stale alarm ID should NOT stop active vibration
        adapter.stopRinging("different-stale-alarm-id")
        assertTrue(adapter.isVibrating)

        // Stopping the matching active alarm ID stops vibration
        adapter.stopRinging("ios-alarm-1")
        assertFalse(adapter.isVibrating)
    }
}
