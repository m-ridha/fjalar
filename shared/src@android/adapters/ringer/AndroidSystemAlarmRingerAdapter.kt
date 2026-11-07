package com.muhammadridha.fjalar.adapters.ringer

import android.app.Notification
import com.muhammadridha.fjalar.adapters.localization.withAppLocale
import com.muhammadridha.fjalar.domain.model.AppLocale
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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
    private val reportFailure: (String, Exception) -> Unit = { message, error -> Log.e("FjalarAlarm", message, error) },
    localeProvider: () -> AppLocale = { AppLocale.EN },
    private val gateway: RingerPlatformGateway = DefaultAndroidRingerPlatformGateway(
        context = requireNotNull(context) { "Context is required when gateway is not provided" },
        localeProvider = localeProvider,
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
            try {
                gateway.startAudio(volume = alarm.soundConfig.volume)
            } catch (error: Exception) {
                reportFailure("Alarm audio failed; continuing haptics and alert", error)
            }
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
        if (activeId != null && activeId != alarmId) {
            return
        }

        gateway.stopAudio()
        gateway.stopVibration()

        val targetId = activeId ?: alarmId
        gateway.cancelAlarmNotification(notificationIdFor(targetId))

        currentlyRingingAlarmId = null
    }

    companion object {
        const val CHANNEL_ID = "fjalar_alarm_channel"
        const val CHANNEL_NAME = "Fjalar Alarm"
        const val EXTRA_ALARM_ID = "com.muhammadridha.fjalar.extra.ALARM_ID"
        const val EXTRA_IS_RINGING = "com.muhammadridha.fjalar.extra.IS_RINGING"
        const val DEFAULT_ACTIVITY_CLASS_NAME = "com.muhammadridha.fjalar.AlarmActivity"

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
    private val localeProvider: () -> AppLocale = { AppLocale.EN },
) : RingerPlatformGateway {

    private companion object {
        const val ACTION_SHOW_ALARM = "com.muhammadridha.fjalar.action.SHOW_ALARM"
        const val TAG = "FjalarAlarm"
    }

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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val mediaLock = Any()
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private val focusOwnership = AlarmAudioFocusOwnership(mediaLock)

    private fun handleFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Alarm audio focus lost; stopping playback")
                stopAudio()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> playback.setPaused(true)
            AudioManager.AUDIOFOCUS_GAIN -> playback.setPaused(false)
        }
    }
    private val playback = AlarmAudioPlayback(
        createPlayer = {
            val nativePlayer = MediaPlayer()
            object : AlarmAudioPlayer {
                override fun start(source: String, volume: Float, initiallyPaused: Boolean, onError: () -> Unit) {
                    nativePlayer.setAudioAttributes(attributes)
                    nativePlayer.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Alarm MediaPlayer error what=$what extra=$extra")
                        onError()
                        true
                    }
                    nativePlayer.setDataSource(context, Uri.parse(source))
                    nativePlayer.isLooping = true
                    val level = volume.coerceIn(0f, 1f)
                    nativePlayer.setVolume(level, level)
                    nativePlayer.prepare()
                    if (!initiallyPaused) nativePlayer.start()
                }

                override fun setPaused(paused: Boolean) {
                    if (paused) nativePlayer.pause() else nativePlayer.start()
                }

                override fun release() {
                    nativePlayer.release()
                }
            }
        },
        reportFailure = { message, error -> Log.e(TAG, message, error) },
        onExhausted = { abandonFocus() },
        lock = mediaLock,
    )

    init {
        createNotificationChannelIfNeeded()
    }

    override fun startAudio(volume: Float) {
        synchronized(mediaLock) {
            stopAudio()
            try {
                val sources = alarmSources()
                val callback = focusOwnership.beginRequest(::handleFocusChange)
                val listener = AudioManager.OnAudioFocusChangeListener { callback(it) }
                focusListener = listener
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(listener)
                        .build()
                    focusRequest = request
                    audioManager.requestAudioFocus(request)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(listener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }
                hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (!hasFocus) {
                    Log.w(TAG, "Alarm audio focus denied; haptics and alert remain available")
                    abandonFocus()
                    return
                }
                playback.start(sources, volume)
            } catch (error: Exception) {
                Log.e(TAG, "Initializing alarm playback failed", error)
                stopAudio()
            }
        }
    }

    internal fun alarmSources(): List<String> {
        val primary = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        // Use a real installed alarm as fallback: symbolic default URIs may resolve to a missing file.
        val manager = RingtoneManager(context)
        manager.setType(RingtoneManager.TYPE_ALARM)
        val fallback = try {
            manager.cursor.use { cursor ->
                (0 until cursor.count).firstNotNullOfOrNull { index ->
                    manager.getRingtoneUri(index)?.takeIf { it != primary }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Resolving fallback alarm tone failed", error)
            null
        }
        val bundled = try {
            val resId = context.resources.getIdentifier("alarm", "raw", context.packageName)
            if (resId != 0) {
                "android.resource://${context.packageName}/$resId"
            } else null
        } catch (error: Exception) {
            Log.e(TAG, "Resolving bundled alarm audio failed", error)
            null
        }
        return listOfNotNull(primary, fallback, bundled).map { it.toString() }
    }

    override fun stopAudio() {
        synchronized(mediaLock) {
            playback.stop()
            abandonFocus()
        }
    }

    private fun abandonFocus() {
        focusOwnership.invalidate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else if (hasFocus) {
                @Suppress("DEPRECATION")
                focusListener?.let { audioManager.abandonAudioFocus(it) }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Abandoning alarm audio focus failed", error)
        } finally {
            focusListener = null
            focusRequest = null
            hasFocus = false
        }
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
        } catch (error: Exception) {
            Log.e(TAG, "Alarm platform operation failed", error)
        }
    }

    override fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (error: Exception) {
            Log.e(TAG, "Alarm platform cleanup failed", error)
        }
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

            // Never fall back to the ordinary application on the locked device.
            val alarmActivity = requireNotNull(activityClass) { "Restricted alarm activity is missing" }
            val targetIntent = Intent(context, alarmActivity).apply {
                action = ACTION_SHOW_ALARM
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

            val contentText = try {
                val resId = context.resources.getIdentifier("notif_alarm_approaching", "string", context.packageName)
                if (resId != 0) {
                    context.withAppLocale(localeProvider()).getString(resId, alarmLabel)
                } else {
                    "Alarm: $alarmLabel"
                }
            } catch (_: Throwable) {
                "Alarm: $alarmLabel"
            }

            val publicBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val appNameId = context.resources.getIdentifier("app_name", "string", context.packageName)
            val publicTitle = if (appNameId != 0) context.withAppLocale(localeProvider()).getString(appNameId) else "Wake Me at"
            val publicNotification = publicBuilder
                .setContentTitle(publicTitle)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(Notification.CATEGORY_ALARM)
                .build()

            builder.setContentTitle(alarmLabel)
                .setPublicVersion(publicNotification)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
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
        } catch (error: Exception) {
            Log.e(TAG, "Alarm platform operation failed", error)
        }
    }

    override fun cancelAlarmNotification(notificationId: Int) {
        try {
            notificationManager?.cancel(notificationId)
        } catch (error: Exception) {
            Log.e(TAG, "Alarm platform cleanup failed", error)
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager ?: return
        try {
            val localized = context.withAppLocale(localeProvider())
            val nameId = context.resources.getIdentifier("notification_channel_alarm_name", "string", context.packageName)
            val channelName = if (nameId != 0) localized.getString(nameId) else AndroidSystemAlarmRingerAdapter.CHANNEL_NAME
            val descId = context.resources.getIdentifier("notification_channel_alarm_desc", "string", context.packageName)
            val channelDesc = if (descId != 0) localized.getString(descId) else "Alerts near an alarm destination"
            val existing = manager.getNotificationChannel(AndroidSystemAlarmRingerAdapter.CHANNEL_ID)
            if (existing != null) {
                // Rename only: preserve the user's channel importance, sound, and visibility.
                existing.name = channelName
                existing.description = channelDesc
                manager.createNotificationChannel(existing)
                return
            }
            val channel = NotificationChannel(
                AndroidSystemAlarmRingerAdapter.CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = channelDesc
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setBypassDnd(true)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        } catch (error: Exception) {
            Log.e(TAG, "Creating alarm notification channel failed", error)
        }
    }
}
