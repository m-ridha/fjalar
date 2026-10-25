package com.muhammadridha.fjalar.adapters.ringer

import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * iOS implementation of [AlarmRingerPort] using [AVAudioSession] and [AVAudioPlayer]
 * with critical playback category and looping system haptics.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCriticalAudioRingerAdapter(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
    private val bundle: NSBundle = NSBundle.mainBundle,
    private val ringerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : AlarmRingerPort {

    @kotlin.concurrent.Volatile
    private var currentPlayer: AVAudioPlayer? = null

    @kotlin.concurrent.Volatile
    private var ringingAlarmId: String? = null

    private var vibrationJob: Job? = null

    internal val isVibrating: Boolean
        get() = vibrationJob?.isActive == true

    override suspend fun startRinging(alarm: LocationAlarm) {
        stopRinging(alarm.id)
        ringingAlarmId = alarm.id

        if (alarm.soundConfig.soundEnabled) {
            try {
                audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
                audioSession.setActive(true, error = null)

                val soundUrl: NSURL? = bundle.URLForResource("alarm", withExtension = "mp3")
                    ?: bundle.URLForResource("alarm", withExtension = "wav")

                if (soundUrl != null) {
                    val player = AVAudioPlayer(contentsOfURL = soundUrl, error = null)
                    player.numberOfLoops = -1
                    player.volume = alarm.soundConfig.volume
                    player.prepareToPlay()
                    player.play()
                    currentPlayer = player
                } else {
                    // Fallback to system alert sound if bundled audio file is absent
                    AudioServicesPlaySystemSound(1005u)
                }
            } catch (_: Throwable) {
                // Defensive degradation: ensure vibration still executes even if audio fails
            }
        }

        if (alarm.soundConfig.vibrateEnabled) {
            vibrationJob = ringerScope.launch {
                while (isActive) {
                    try {
                        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
                    } catch (_: Throwable) {}
                    delay(1500L)
                }
            }
        }
    }

    override suspend fun stopRinging(alarmId: String) {
        vibrationJob?.cancel()
        vibrationJob = null

        currentPlayer?.let { player ->
            try {
                player.stop()
            } catch (_: Throwable) {}
        }
        currentPlayer = null
        ringingAlarmId = null

        try {
            audioSession.setActive(false, error = null)
        } catch (_: Throwable) {}
    }
}
