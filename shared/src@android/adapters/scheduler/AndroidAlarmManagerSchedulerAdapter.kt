package com.muhammadridha.fjalar.adapters.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import kotlin.time.Instant

/**
 * Android [AlarmManager] implementation of [AlarmSchedulerPort].
 *
 * Schedules exact OS wakeups targeting [com.muhammadridha.fjalar.CommuteWindowReceiver]
 * via [ACTION_COMMUTE_WINDOW]. Each scheduled alarm uses an isolated request code
 * derived from [alarmId.hashCode] and a distinct [PendingIntent] identity embedded with
 * [alarmId] via intent data URI and identifier to prevent token collisions across colliding hash codes.
 *
 * On Android 12+ (API 31+), verifies [AlarmManager.canScheduleExactAlarms] before attempting
 * exact scheduling. If exact alarm permission is missing or revoked, it falls back defensively
 * to inexact idle wakeup [AlarmManager.setAndAllowWhileIdle] to prevent [SecurityException] crashes.
 */
class AndroidAlarmManagerSchedulerAdapter(
    private val context: Context? = null,
    private val alarmManager: AlarmManager? = context?.getSystemService(Context.ALARM_SERVICE) as? AlarmManager,
    private val receiverClass: Class<*>? = resolveDefaultReceiver(),
    private val apiLevel: Int = Build.VERSION.SDK_INT,
    private val gateway: AlarmManagerGateway = DefaultAlarmManagerGateway(
        context = requireNotNull(context) { "Context is required when gateway is not provided" },
        alarmManager = requireNotNull(alarmManager) { "AlarmManager is required when gateway is not provided" },
        receiverClass = receiverClass,
    ),
) : AlarmSchedulerPort {

    override suspend fun scheduleWakeup(alarmId: String, at: Instant) {
        val requestCode = alarmId.hashCode()
        val triggerAtMillis = at.toEpochMilliseconds()

        val canScheduleExact = if (apiLevel >= Build.VERSION_CODES.S) {
            gateway.canScheduleExactAlarms()
        } else {
            true
        }

        gateway.schedule(
            requestCode = requestCode,
            triggerAtMillis = triggerAtMillis,
            action = ACTION_COMMUTE_WINDOW,
            alarmId = alarmId,
            exact = canScheduleExact,
        )
    }

    override suspend fun cancelWakeup(alarmId: String) {
        val requestCode = alarmId.hashCode()
        gateway.cancel(
            requestCode = requestCode,
            action = ACTION_COMMUTE_WINDOW,
            alarmId = alarmId,
        )
    }

    companion object {
        const val ACTION_COMMUTE_WINDOW = "com.muhammadridha.fjalar.ACTION_COMMUTE_WINDOW"
        const val EXTRA_ALARM_ID = "com.muhammadridha.fjalar.extra.ALARM_ID"
        const val DEFAULT_RECEIVER_CLASS_NAME = "com.muhammadridha.fjalar.CommuteWindowReceiver"

        private fun resolveDefaultReceiver(): Class<*>? {
            return try {
                Class.forName(DEFAULT_RECEIVER_CLASS_NAME)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
    }
}

/**
 * SPI boundary for AlarmManager and PendingIntent operations, enabling deterministic
 * testing without relying on Android framework stubs or mocks.
 */
interface AlarmManagerGateway {
    fun canScheduleExactAlarms(): Boolean

    fun schedule(
        requestCode: Int,
        triggerAtMillis: Long,
        action: String,
        alarmId: String,
        exact: Boolean,
    )

    fun cancel(
        requestCode: Int,
        action: String,
        alarmId: String,
    )
}

/**
 * Production implementation of [AlarmManagerGateway] dispatching to Android platform SDKs.
 */
class DefaultAlarmManagerGateway(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val receiverClass: Class<*>? = null,
) : AlarmManagerGateway {

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun schedule(
        requestCode: Int,
        triggerAtMillis: Long,
        action: String,
        alarmId: String,
        exact: Boolean,
    ) {
        val pendingIntent = createPendingIntent(requestCode, action, alarmId)
        if (exact) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            } catch (_: SecurityException) {
                // Defensive fallback: permission might be revoked at runtime on API 31+
                scheduleInexactFallback(triggerAtMillis, pendingIntent)
            }
        } else {
            scheduleInexactFallback(triggerAtMillis, pendingIntent)
        }
    }

    private fun scheduleInexactFallback(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    override fun cancel(
        requestCode: Int,
        action: String,
        alarmId: String,
    ) {
        val pendingIntent = createPendingIntent(requestCode, action, alarmId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createPendingIntent(requestCode: Int, action: String, alarmId: String): PendingIntent {
        val intent = createIntent(action, alarmId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createIntent(action: String, alarmId: String): Intent {
        val intent = if (receiverClass != null) {
            Intent(context, receiverClass)
        } else {
            Intent().setClassName(
                context.packageName,
                AndroidAlarmManagerSchedulerAdapter.DEFAULT_RECEIVER_CLASS_NAME,
            )
        }
        return intent.apply {
            this.action = action
            data = Uri.fromParts("alarm", alarmId, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                identifier = alarmId
            }
            putExtra(AndroidAlarmManagerSchedulerAdapter.EXTRA_ALARM_ID, alarmId)
        }
    }
}
