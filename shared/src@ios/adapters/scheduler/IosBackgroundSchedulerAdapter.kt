package com.muhammadridha.fjalar.adapters.scheduler

import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.app_name
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Instant
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of [AlarmSchedulerPort] using [UNUserNotificationCenter]
 * scheduled local notification triggers.
 *
 * ### iOS Background Execution Boundary:
 * On Android, `AlarmManager.setExactAndAllowWhileIdle()` wakes up the OS and triggers
 * a broadcast receiver with a `WakeLock` to execute `AlarmService.syncSchedules()`.
 * On iOS, local notifications scheduled via `UNUserNotificationCenter` are strictly user-facing alerts;
 * they do not afford background code execution to transition an alarm from `SCHEDULED_IDLE`
 * to `ARMED_MONITORING` while the app is suspended without user interaction.
 * Background refresh via `BGAppRefreshTask` is strictly best-effort (requiring `fetch` `UIBackgroundModes`
 * and permitted task identifiers): `earliestBeginDate` serves only as a lower bound, and execution
 * timing depends on iOS power heuristics. If refresh is delayed or skipped, the alarm remains
 * in `SCHEDULED_IDLE` until the user opens the app to synchronize schedules.
 */
class IosBackgroundSchedulerAdapter(
    private val notificationCenter: UNUserNotificationCenter? = null,
    private val clock: Clock = Clock.System,
    private val onError: ((String, NSError) -> Unit)? = null,
    private val titleResolver: IosNotificationTitleResolver = DefaultIosNotificationTitleResolver(),
) : AlarmSchedulerPort {

    constructor(
        notificationCenter: UNUserNotificationCenter? = null,
        clock: Clock = Clock.System,
        onError: ((String, NSError) -> Unit)? = null,
        localizedTitle: String,
    ) : this(
        notificationCenter = notificationCenter,
        clock = clock,
        onError = onError,
        titleResolver = object : IosNotificationTitleResolver {
            override suspend fun resolveTitle(preferredLanguage: String?): String = localizedTitle
        },
    )

    private fun resolveNotificationCenter(): UNUserNotificationCenter? {
        if (notificationCenter != null) return notificationCenter
        return try {
            UNUserNotificationCenter.currentNotificationCenter()
        } catch (_: Throwable) {
            null
        }
    }

    fun requestAuthorization() {
        val center = resolveNotificationCenter() ?: return
        try {
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { _, _ -> }
        } catch (_: Throwable) {}
    }

    override suspend fun scheduleWakeup(alarmId: String, at: Instant) {
        val center = resolveNotificationCenter() ?: return
        requestAuthorization()
        val now = clock.now()
        val delaySeconds = (at - now).inWholeSeconds.coerceAtLeast(1)

        val content = UNMutableNotificationContent().apply {
            setTitle(resolveNotificationTitle())
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

        center.addNotificationRequest(request) { error ->
            if (error != null) {
                // Defensive degradation if local notification scheduling fails
                onError?.invoke(alarmId, error)
            }
        }
    }

    override suspend fun cancelWakeup(alarmId: String) {
        val center = resolveNotificationCenter() ?: return
        val identifier = notificationIdentifier(alarmId)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
    }

    internal suspend fun resolveNotificationTitle(preferredLanguage: String? = null): String {
        return titleResolver.resolveTitle(preferredLanguage)
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
