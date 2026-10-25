package com.muhammadridha.fjalar.adapters.scheduler

import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import kotlin.time.Clock
import kotlin.time.Instant
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of [AlarmSchedulerPort] using [UNUserNotificationCenter]
 * scheduled local notification triggers.
 */
class IosBackgroundSchedulerAdapter(
    private val notificationCenter: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
    private val clock: Clock = Clock.System,
) : AlarmSchedulerPort {

    override suspend fun scheduleWakeup(alarmId: String, at: Instant) {
        val now = clock.now()
        val delaySeconds = (at - now).inWholeSeconds.coerceAtLeast(1)

        val content = UNMutableNotificationContent().apply {
            setTitle("Fjalar")
            setBody("Commute alarm window is active")
            setUserInfo(mapOf("alarmId" to alarmId, "type" to NOTIFICATION_TYPE_COMMUTE_WINDOW))
        }

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = delaySeconds.toDouble(),
            repeats = false,
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = notificationIdentifier(alarmId),
            content = content,
            trigger = trigger,
        )

        notificationCenter.addNotificationRequest(request) { _ ->
            // Defensively handle callback
        }
    }

    override suspend fun cancelWakeup(alarmId: String) {
        val identifier = notificationIdentifier(alarmId)
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
    }

    companion object {
        const val NOTIFICATION_TYPE_COMMUTE_WINDOW = "com.muhammadridha.fjalar.COMMUTE_WINDOW"

        fun notificationIdentifier(alarmId: String): String =
            "com.muhammadridha.fjalar.wakeup.$alarmId"
    }
}
