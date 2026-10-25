package com.muhammadridha.fjalar.adapters.scheduler

import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import kotlin.time.Clock
import kotlin.time.Instant
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of [AlarmSchedulerPort] using [UNUserNotificationCenter]
 * scheduled local notification triggers.
 */
class IosBackgroundSchedulerAdapter(
    private val notificationCenter: UNUserNotificationCenter? = null,
    private val clock: Clock = Clock.System,
) : AlarmSchedulerPort {

    private fun resolveNotificationCenter(): UNUserNotificationCenter? {
        if (notificationCenter != null) return notificationCenter
        return try {
            UNUserNotificationCenter.currentNotificationCenter()
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun scheduleWakeup(alarmId: String, at: Instant) {
        val center = resolveNotificationCenter() ?: return
        val now = clock.now()
        val delaySeconds = (at - now).inWholeSeconds.coerceAtLeast(1)

        val content = UNMutableNotificationContent().apply {
            setTitle("Fjalar")
            setBody(resolveNotificationBody())
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

        center.addNotificationRequest(request) { _ ->
            // Defensively handle callback
        }
    }

    override suspend fun cancelWakeup(alarmId: String) {
        val center = resolveNotificationCenter() ?: return
        val identifier = notificationIdentifier(alarmId)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
    }

    internal fun resolveNotificationBody(preferredLanguage: String? = null): String {
        val lang = preferredLanguage?.lowercase() ?: try {
            (NSLocale.preferredLanguages.firstOrNull() as? String)?.lowercase() ?: ""
        } catch (_: Throwable) {
            ""
        }
        return if (lang.startsWith("id") || lang.startsWith("in")) {
            "Jendela alarm perjalanan aktif"
        } else {
            "Commute alarm window is active"
        }
    }

    companion object {
        const val NOTIFICATION_TYPE_COMMUTE_WINDOW = "com.muhammadridha.fjalar.COMMUTE_WINDOW"

        fun notificationIdentifier(alarmId: String): String =
            "com.muhammadridha.fjalar.wakeup.$alarmId"
    }
}
