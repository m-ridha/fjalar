package com.muhammadridha.fjalar.adapters.ringer

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidSystemAlarmRingerAdapterTest {

    private class FakeRingerPlatformGateway : RingerPlatformGateway {
        data class NotificationRecord(
            val notificationId: Int,
            val channelId: String,
            val alarmId: String,
            val alarmLabel: String,
        )

        var audioFailure: Exception? = null
        var audioStarted: Boolean = false
        var lastAudioVolume: Float? = null
        var audioStoppedCount: Int = 0

        var vibrationStarted: Boolean = false
        var lastVibrationPattern: LongArray? = null
        var lastRepeatIndex: Int? = null
        var vibrationStoppedCount: Int = 0

        val notificationsPosted = mutableListOf<NotificationRecord>()
        val notificationsCancelled = mutableListOf<Int>()

        override fun startAudio(volume: Float) {
            audioFailure?.let { throw it }
            audioStarted = true
            lastAudioVolume = volume
        }

        override fun stopAudio() {
            audioStarted = false
            audioStoppedCount++
        }

        override fun startVibration(pattern: LongArray, repeatIndex: Int) {
            vibrationStarted = true
            lastVibrationPattern = pattern.clone()
            lastRepeatIndex = repeatIndex
        }

        override fun stopVibration() {
            vibrationStarted = false
            vibrationStoppedCount++
        }

        override fun postAlarmNotification(
            notificationId: Int,
            channelId: String,
            alarmId: String,
            alarmLabel: String,
        ) {
            notificationsPosted.add(
                NotificationRecord(
                    notificationId = notificationId,
                    channelId = channelId,
                    alarmId = alarmId,
                    alarmLabel = alarmLabel,
                )
            )
        }

        override fun cancelAlarmNotification(notificationId: Int) {
            notificationsCancelled.add(notificationId)
            notificationsPosted.removeAll { it.notificationId == notificationId }
        }
    }

    private fun createAlarm(
        id: String = "test-alarm-1",
        label: String = "Manggarai Station",
        soundEnabled: Boolean = true,
        vibrateEnabled: Boolean = true,
        volume: Float = 1.0f,
    ): LocationAlarm {
        return LocationAlarm(
            id = id,
            label = label,
            coordinates = GeoCoordinates(-6.2098, 106.8501),
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
    fun audioFailure_stillStartsVibrationAndNotification() = runTest {
        val gateway = FakeRingerPlatformGateway().apply {
            audioFailure = IllegalStateException("Unavailable player")
        }
        val failures = mutableListOf<Exception>()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = gateway, reportFailure = { _, error -> failures.add(error) })

        adapter.startRinging(createAlarm())

        assertTrue(gateway.vibrationStarted)
        assertEquals(1, gateway.notificationsPosted.size)
        assertEquals(1, failures.size)
    }

    @Test
    fun startRinging_initiatesAudioVibrationAndNotification_whenFullAlertConfigured() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)
        val alarm = createAlarm(volume = 0.85f)

        adapter.startRinging(alarm)

        assertEquals("test-alarm-1", adapter.activeRingingAlarmId)
        assertTrue(fakeGateway.audioStarted, "Audio playback should be started")
        assertEquals(0.85f, fakeGateway.lastAudioVolume)

        assertTrue(fakeGateway.vibrationStarted, "Vibration should be started")
        assertContentEquals(
            longArrayOf(0L, 800L, 400L, 800L, 400L),
            fakeGateway.lastVibrationPattern,
            "Should use repeating waveform pattern",
        )
        assertEquals(0, fakeGateway.lastRepeatIndex, "Repeat index 0 for repeating vibration")

        assertEquals(1, fakeGateway.notificationsPosted.size)
        val notif = fakeGateway.notificationsPosted.first()
        assertEquals(AndroidSystemAlarmRingerAdapter.CHANNEL_ID, notif.channelId)
        assertEquals("test-alarm-1", notif.alarmId)
        assertEquals("Manggarai Station", notif.alarmLabel)
        assertEquals(AndroidSystemAlarmRingerAdapter.notificationIdFor("test-alarm-1"), notif.notificationId)
    }

    @Test
    fun startRinging_respectsSoundConfig_whenSoundDisabled() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)
        val alarm = createAlarm(soundEnabled = false, vibrateEnabled = true)

        adapter.startRinging(alarm)

        assertFalse(fakeGateway.audioStarted, "Audio should not be started when soundEnabled=false")
        assertTrue(fakeGateway.vibrationStarted, "Vibration should still be active")
        assertEquals(1, fakeGateway.notificationsPosted.size, "Notification should still be posted")
    }

    @Test
    fun startRinging_respectsSoundConfig_whenVibrationDisabled() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)
        val alarm = createAlarm(soundEnabled = true, vibrateEnabled = false)

        adapter.startRinging(alarm)

        assertTrue(fakeGateway.audioStarted, "Audio should be started")
        assertFalse(fakeGateway.vibrationStarted, "Vibration should not be started when vibrateEnabled=false")
        assertEquals(1, fakeGateway.notificationsPosted.size, "Notification should still be posted")
    }

    @Test
    fun stopRinging_cleansUpAudioVibrationAndNotification() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)
        val alarm = createAlarm()

        adapter.startRinging(alarm)
        assertEquals(1, fakeGateway.notificationsPosted.size)

        adapter.stopRinging(alarm.id)

        assertNull(adapter.activeRingingAlarmId)
        assertFalse(fakeGateway.audioStarted)
        assertEquals(1, fakeGateway.audioStoppedCount)
        assertFalse(fakeGateway.vibrationStarted)
        assertEquals(1, fakeGateway.vibrationStoppedCount)
        val expectedNotificationId = AndroidSystemAlarmRingerAdapter.notificationIdFor(alarm.id)
        assertTrue(
            fakeGateway.notificationsCancelled.contains(expectedNotificationId),
            "Notification must be cancelled",
        )
        assertEquals(0, fakeGateway.notificationsPosted.size)
    }

    @Test
    fun stopRinging_isIdempotent_whenCalledMultipleTimesOrWhenIdle() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)

        // Idle stop call should not throw
        adapter.stopRinging("non-existent-id")
        assertNull(adapter.activeRingingAlarmId)
        assertEquals(1, fakeGateway.audioStoppedCount)
        assertEquals(1, fakeGateway.vibrationStoppedCount)

        // Multiple consecutive stop calls
        val alarm = createAlarm()
        adapter.startRinging(alarm)
        adapter.stopRinging(alarm.id)
        adapter.stopRinging(alarm.id)

        assertNull(adapter.activeRingingAlarmId)
        assertEquals(3, fakeGateway.audioStoppedCount)
        assertEquals(3, fakeGateway.vibrationStoppedCount)
    }

    @Test
    fun startRinging_stopsPreviousAlarm_whenNewAlarmTriggeredWhileRinging() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)
        val alarm1 = createAlarm(id = "alarm-1", label = "Station 1")
        val alarm2 = createAlarm(id = "alarm-2", label = "Station 2")

        adapter.startRinging(alarm1)
        assertEquals("alarm-1", adapter.activeRingingAlarmId)

        adapter.startRinging(alarm2)
        assertEquals("alarm-2", adapter.activeRingingAlarmId)

        val notif1Id = AndroidSystemAlarmRingerAdapter.notificationIdFor("alarm-1")
        val notif2Id = AndroidSystemAlarmRingerAdapter.notificationIdFor("alarm-2")

        assertTrue(fakeGateway.notificationsCancelled.contains(notif1Id), "Previous alarm notification must be cancelled")
        assertEquals(1, fakeGateway.notificationsPosted.size)
        assertEquals("alarm-2", fakeGateway.notificationsPosted.first().alarmId)
        assertEquals(notif2Id, fakeGateway.notificationsPosted.first().notificationId)
    }

    @Test
    fun stopRinging_ignoresStaleAlarmId_whenDifferentAlarmIsRinging() = runTest {
        val fakeGateway = FakeRingerPlatformGateway()
        val adapter = AndroidSystemAlarmRingerAdapter(gateway = fakeGateway)
        val alarm = createAlarm(id = "alarm-active")

        adapter.startRinging(alarm)
        assertEquals("alarm-active", adapter.activeRingingAlarmId)
        assertTrue(fakeGateway.audioStarted)
        assertTrue(fakeGateway.vibrationStarted)

        // Stale stop call for a different alarm should be ignored (Safety Invariant 3)
        adapter.stopRinging("stale-alarm-id")

        assertEquals("alarm-active", adapter.activeRingingAlarmId)
        assertTrue(fakeGateway.audioStarted)
        assertTrue(fakeGateway.vibrationStarted)
        assertEquals(0, fakeGateway.audioStoppedCount)
        assertEquals(0, fakeGateway.vibrationStoppedCount)
    }
}

class AlarmAudioPlaybackTest {
    private class Player(val fails: Boolean = false) : AlarmAudioPlayer {
        var playing = false
        var releases = 0
        val pauses = mutableListOf<Boolean>()
        var error: (() -> Unit)? = null
        override fun start(source: String, volume: Float, initiallyPaused: Boolean, onError: () -> Unit) {
            playing = !initiallyPaused
            error = onError
            if (fails) throw IllegalStateException("Unplayable URI")
        }
        override fun setPaused(paused: Boolean) { pauses.add(paused); playing = !paused }
        override fun release() { releases++ }
    }

    @Test
    fun failedPreparation_releasesPartialPlayerAndTriesFallback() {
        val first = Player(fails = true)
        val fallback = Player()
        val players = ArrayDeque(listOf(first, fallback))
        val failures = mutableListOf<Exception>()
        val playback = AlarmAudioPlayback({ players.removeFirst() }, { _, error -> failures.add(error) })
        playback.start(listOf("invalid", "installed-fallback"), 0.7f)
        assertEquals(1, first.releases)
        assertEquals(0, fallback.releases)
        assertEquals(1, failures.size)
        playback.stop()
        assertEquals(1, fallback.releases)
    }

    @Test
    fun threeSources_triesBundledFallbackWhenPrimaryAndInstalledFail() {
        val primary = Player(fails = true)
        val installed = Player(fails = true)
        val bundled = Player(fails = false)
        val players = ArrayDeque(listOf(primary, installed, bundled))
        val failures = mutableListOf<Exception>()
        val playback = AlarmAudioPlayback({ players.removeFirst() }, { _, error -> failures.add(error) })

        playback.start(listOf("primary", "installed", "bundled"), 0.9f)

        assertEquals(1, primary.releases)
        assertEquals(1, installed.releases)
        assertEquals(0, bundled.releases)
        assertTrue(bundled.playing)
        assertEquals(2, failures.size)

        playback.stop()
        assertEquals(1, bundled.releases)
    }

    @Test
    fun asynchronousError_triesFallbackOnceAndIgnoresOldPlayerCallbacks() {
        val first = Player()
        val fallback = Player()
        val players = ArrayDeque(listOf(first, fallback))
        val playback = AlarmAudioPlayback({ players.removeFirst() }, { _, _ -> })
        playback.start(listOf("alarm", "fallback"), 1f)
        first.error!!()
        first.error!!()
        assertEquals(1, first.releases)
        assertEquals(0, fallback.releases)
        fallback.error!!()
        assertEquals(1, fallback.releases)
        assertTrue(players.isEmpty())
    }

    @Test
    fun focusLossThenError_keepsFallbackSilentUntilFocusGain() {
        val first = Player()
        val fallback = Player()
        val players = ArrayDeque(listOf(first, fallback))
        val playback = AlarmAudioPlayback({ players.removeFirst() }, { _, _ -> })
        playback.start(listOf("alarm", "fallback"), 1f)
        playback.setPaused(true)
        first.error!!()
        assertFalse(fallback.playing)
        playback.setPaused(true)
        assertTrue(fallback.pauses.isEmpty())
        playback.setPaused(false)
        assertTrue(fallback.playing)
    }

    @Test
    fun initializationFailure_isReportedAndBounded() {
        var attempts = 0
        var failures = 0
        val playback = AlarmAudioPlayback({ attempts++; error("No player") }, { _, _ -> failures++ })
        playback.start(listOf("alarm", "fallback"), 1f)
        assertEquals(2, attempts)
        assertEquals(3, failures)
    }

    @Test
    fun transientFocusChange_pausesAndResumesWithoutReplacingPlayer() {
        val player = Player()
        val playback = AlarmAudioPlayback({ player }, { _, _ -> })
        playback.start(listOf("alarm"), 1f)
        playback.setPaused(true)
        playback.setPaused(false)
        assertEquals(listOf(true, false), player.pauses)
        assertEquals(0, player.releases)
        playback.stop()
        playback.setPaused(false)
        assertEquals(listOf(true, false), player.pauses)
    }

    @Test
    fun replacementAndStop_releaseOwnersAndIgnoreStaleErrors() {
        val first = Player()
        val replacement = Player()
        val players = ArrayDeque(listOf(first, replacement))
        val playback = AlarmAudioPlayback({ players.removeFirst() }, { _, _ -> })
        playback.start(listOf("alarm"), 1f)
        playback.start(listOf("alarm"), 1f)
        first.error!!()
        assertEquals(1, first.releases)
        assertEquals(0, replacement.releases)
        playback.stop()
        replacement.error!!()
        playback.stop()
        assertEquals(1, replacement.releases)
    }
}

class AlarmAudioFocusOwnershipTest {
    @Test
    fun replacementRequest_ignoresDelayedLossAndGainFromPreviousRequest() {
        val ownership = AlarmAudioFocusOwnership()
        val changes = mutableListOf<Int>()
        val first = ownership.beginRequest { changes.add(it) }
        first(-2)
        val replacement = ownership.beginRequest { changes.add(it * 10) }
        first(-1)
        first(1)
        replacement(-2)
        replacement(1)
        assertEquals(listOf(-2, -20, 10), changes)
    }

    @Test
    fun stopOrDeniedRequest_invalidatesQueuedCallbacksBeforeAbandoningFocus() {
        val ownership = AlarmAudioFocusOwnership()
        val changes = mutableListOf<Int>()
        val stopped = ownership.beginRequest { changes.add(it) }
        ownership.invalidate()
        stopped(-1)
        stopped(1)
        val denied = ownership.beginRequest { changes.add(it) }
        ownership.invalidate()
        denied(1)
        val active = ownership.beginRequest { changes.add(it) }
        active(1)
        assertEquals(listOf(1), changes)
    }
}
