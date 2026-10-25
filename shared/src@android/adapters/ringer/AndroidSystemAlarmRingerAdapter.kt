package com.muhammadridha.fjalar.adapters.ringer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmRingerPort

/**
 * Android implementation of [AlarmRingerPort] using [MediaPlayer] with [AudioAttributes.USAGE_ALARM],
 * [Vibrator] with repeating waveform patterns, and high-priority full-screen intent lock-screen notifications.
 *
 * Implements Sleepy-Eyes Dismissal Safety (Safety Invariant 3) by displaying an unmistakable
 * full-screen alert over the lock screen on channel [CHANNEL_ID].
 */
class AndroidSystemAlarmRingerAdapter(
    private val context: Context? = null,
    private val gateway: RingerPlatformGateway = DefaultAndroidRingerPlatformGateway(
        context = requireNotNull(context) { "Context is required when gateway is not provided" },
    ),
) : AlarmRingerPort {

    @Volatile
    private var currentlyRingingAlarmId: String? = null

    val activeRingingAlarmId: String?
        get() = currentlyRingingAlarmId

    override suspend fun startRinging(alarm: LocationAlarm) {
        // Stop any previously ringing alarm before starting new alarm
        val previousAlarmId = currentlyRingingAlarmId
        if (previousAlarmId != null) {
            stopRinging(previousAlarmId)
        }

        currentlyRingingAlarmId = alarm.id

        // 1. Audio stream playback on USAGE_ALARM
        if (alarm.soundConfig.soundEnabled) {
            gateway.startAudio(volume = alarm.soundConfig.volume)
        }

        // 2. Repeating tactile haptics waveform
        if (alarm.soundConfig.vibrateEnabled) {
            gateway.startVibration(
                pattern = DEFAULT_VIBRATION_PATTERN,
                repeatIndex = 0,
            )
        }

        // 3. High-priority full-screen alert over lock screen
        gateway.postAlarmNotification(
            notificationId = notificationIdFor(alarm.id),
            channelId = CHANNEL_ID,
            alarmId = alarm.id,
            alarmLabel = alarm.label,
        )
    }

    override suspend fun stopRinging(alarmId: String) {
        val activeId = currentlyRingingAlarmId
        val targetId = activeId ?: alarmId

        gateway.stopAudio()
        gateway.stopVibration()

        gateway.cancelAlarmNotification(notificationIdFor(targetId))
        if (activeId != null && activeId != alarmId) {
            gateway.cancelAlarmNotification(notificationIdFor(alarmId))
        }

        currentlyRingingAlarmId = null
    }

    companion object {
        const val CHANNEL_ID = "fjalar_alarm_channel"
        const val CHANNEL_NAME = "Fjalar Alarm"
        const val EXTRA_ALARM_ID = "com.muhammadridha.fjalar.extra.ALARM_ID"
        const val EXTRA_IS_RINGING = "com.muhammadridha.fjalar.extra.IS_RINGING"
        const val DEFAULT_ACTIVITY_CLASS_NAME = "com.muhammadridha.fjalar.MainActivity"

        val DEFAULT_VIBRATION_PATTERN = longArrayOf(0L, 800L, 400L, 800L, 400L)

        fun notificationIdFor(alarmId: String): Int {
            val hash = alarmId.hashCode() and 0x7FFFFFFF
            return if (hash == 0) 1001 else hash
        }

        fun resolveDefaultActivity(): Class<*>? {
            return try {
                Class.forName(DEFAULT_ACTIVITY_CLASS_NAME)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
    }
}

/**
 * SPI boundary for audio, vibration, and lock-screen notification dispatching,
 * enabling deterministic unit testing without external mock frameworks or Android device emulators.
 */
interface RingerPlatformGateway {
    fun startAudio(volume: Float)
    fun stopAudio()
    fun startVibration(pattern: LongArray, repeatIndex: Int)
    fun stopVibration()
    fun postAlarmNotification(
        notificationId: Int,
        channelId: String,
        alarmId: String,
        alarmLabel: String,
    )
    fun cancelAlarmNotification(notificationId: Int)
}

/**
 * Production implementation of [RingerPlatformGateway] dispatching to Android platform SDKs.
 */
open class DefaultAndroidRingerPlatformGateway(
    private val context: Context,
    private val activityClass: Class<*>? = AndroidSystemAlarmRingerAdapter.resolveDefaultActivity(),
) : RingerPlatformGateway {

    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            @Suppress("DEPRECATION")
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val mediaLock = Any()

    init {
        createNotificationChannelIfNeeded()
    }

    override fun startAudio(volume: Float) {
        synchronized(mediaLock) {
            stopAudioInternal()
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Settings.System.DEFAULT_ALARM_ALERT_URI

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val player = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, alarmUri)
                    isLooping = true
                    val clampedVolume = volume.coerceIn(0f, 1f)
                    setVolume(clampedVolume, clampedVolume)
                    prepare()
                    start()
                }
                mediaPlayer = player
            } catch (_: Exception) {
                // Defensive degradation: ensure vibration and notifications still trigger
            }
        }
    }

    override fun stopAudio() {
        synchronized(mediaLock) {
            stopAudioInternal()
        }
    }

    private fun stopAudioInternal() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
            try {
                player.reset()
            } catch (_: Exception) {}
            try {
                player.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
    }

    override fun startVibration(pattern: LongArray, repeatIndex: Int) {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, repeatIndex)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, repeatIndex)
            }
        } catch (_: Exception) {
            // Defensive degradation
        }
    }

    override fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    override fun postAlarmNotification(
        notificationId: Int,
        channelId: String,
        alarmId: String,
        alarmLabel: String,
    ) {
        try {
            val manager = notificationManager ?: return
            createNotificationChannelIfNeeded()

            val targetIntent = if (activityClass != null) {
                Intent(context, activityClass)
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AndroidSystemAlarmRingerAdapter.EXTRA_ALARM_ID, alarmId)
                putExtra(AndroidSystemAlarmRingerAdapter.EXTRA_IS_RINGING, true)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                targetIntent,
                flags,
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            builder.setContentTitle(alarmLabel)
                .setContentText("Approaching $alarmLabel")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                builder.setPriority(Notification.PRIORITY_HIGH)
            }

            val notification = builder.build()
            manager.notify(notificationId, notification)
        } catch (_: Exception) {
            // Defensive degradation
        }
    }

    override fun cancelAlarmNotification(notificationId: Int) {
        try {
            notificationManager?.cancel(notificationId)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = notificationManager ?: return
            try {
                if (manager.getNotificationChannel(AndroidSystemAlarmRingerAdapter.CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        AndroidSystemAlarmRingerAdapter.CHANNEL_ID,
                        AndroidSystemAlarmRingerAdapter.CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "High-priority wake-up alerts when approaching destination"
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        setBypassDnd(true)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    manager.createNotificationChannel(channel)
                }
            } catch (_: Exception) {}
        }
    }
}
