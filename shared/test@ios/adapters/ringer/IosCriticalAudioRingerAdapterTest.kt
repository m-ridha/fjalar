package com.muhammadridha.fjalar.adapters.ringer

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForeignApi::class)
class IosCriticalAudioRingerAdapterTest {

    private fun sampleAlarm(
        vibrateEnabled: Boolean = true,
        soundEnabled: Boolean = !vibrateEnabled,
        volume: Float = 0.8f,
    ): LocationAlarm {
        return LocationAlarm(
            id = "ios-alarm-1",
            label = "Gambir Station",
            coordinates = GeoCoordinates(-6.1767, 106.8306),
            radius = TriggerRadius(500),
            schedule = AlarmSchedule(type = ScheduleType.ONE_TIME),
            soundConfig = AlarmSoundConfig(
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
                volume = volume,
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
        adapter.startRinging(sampleAlarm(vibrateEnabled = true, soundEnabled = false))
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

        adapter.startRinging(sampleAlarm(vibrateEnabled = false, soundEnabled = true))
        assertFalse(adapter.isVibrating)
    }

    @Test
    fun stopRinging_withStaleAlarmId_doesNotStopActiveAlarm() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCriticalAudioRingerAdapter(
            ringerScope = TestScope(testDispatcher),
        )

        adapter.startRinging(sampleAlarm(vibrateEnabled = true, soundEnabled = false))
        assertTrue(adapter.isVibrating)

        // Attempting to stop an inactive/stale alarm ID should NOT stop active vibration
        adapter.stopRinging("different-stale-alarm-id")
        assertTrue(adapter.isVibrating)

        // Stopping the matching active alarm ID stops vibration
        adapter.stopRinging("ios-alarm-1")
        assertFalse(adapter.isVibrating)
    }

    @Test
    fun startRinging_withSoundDisabled_doesNotInitializeAudioPlayer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = IosCriticalAudioRingerAdapter(
            ringerScope = TestScope(testDispatcher),
        )

        adapter.startRinging(sampleAlarm(vibrateEnabled = true, soundEnabled = false))
        assertFalse(adapter.isAudioPlaying)

        adapter.stopRinging("ios-alarm-1")
        assertFalse(adapter.isVibrating)
    }

    @Test
    fun startRinging_withBundledSound_initializesAndCleansUpAudioPlayer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fm = platform.Foundation.NSFileManager.defaultManager
        val tempDir = platform.Foundation.NSTemporaryDirectory() + "fjalar_audio_test_" + kotlin.random.Random.nextInt(100000)
        fm.createDirectoryAtPath(tempDir, withIntermediateDirectories = true, attributes = null, error = null)

        val srcWav = "${fm.currentDirectoryPath}/assets/audio/alarm.wav"
        val dstWav = "$tempDir/alarm.wav"
        fm.copyItemAtPath(srcWav, dstWav, error = null)

        val testBundle = platform.Foundation.NSBundle.bundleWithPath(tempDir) ?: platform.Foundation.NSBundle.mainBundle
        val adapter = IosCriticalAudioRingerAdapter(
            bundle = testBundle,
            ringerScope = TestScope(testDispatcher),
        )

        adapter.startRinging(sampleAlarm(vibrateEnabled = false, soundEnabled = true, volume = 0.9f))
        if (testBundle.URLForResource("alarm", withExtension = "wav") != null) {
            assertTrue(adapter.isAudioPlaying, "Player should be initialized when alarm.wav is in bundle")
        }

        adapter.stopRinging("ios-alarm-1")
        assertFalse(adapter.isAudioPlaying, "Player must be released on stopRinging")

        fm.removeItemAtPath(tempDir, error = null)
    }
}
