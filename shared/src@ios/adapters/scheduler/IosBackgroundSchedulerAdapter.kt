package com.muhammadridha.fjalar.adapters.scheduler

import com.muhammadridha.fjalar.domain.ports.outbound.AlarmSchedulerPort
import com.muhammadridha.fjalar.domain.ports.outbound.SnoozeDeliveryUnavailableException
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.localization.FjalarStrings
import com.muhammadridha.fjalar.localization.FjalarStringKey
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import platform.UserNotifications.UNNotificationSound
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
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNNotificationSettingEnabled

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
    private val localeProvider: () -> AppLocale? = { null },
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
        scheduleNotification(alarmId, at, null)
    }

    override suspend fun scheduleSnoozeWakeup(alarm: LocationAlarm, at: Instant) {
        scheduleNotification(alarm.id, at, alarm)
    }

    private suspend fun scheduleNotification(alarmId: String, at: Instant, snoozedAlarm: LocationAlarm?) {
        val center = resolveNotificationCenter()
        if (center == null) {
            check(snoozedAlarm == null) { "Notification center unavailable" }
            return
        }
        if (snoozedAlarm != null) {
            suspendCancellableCoroutine<Unit> { continuation ->
                center.requestAuthorizationWithOptions(
                    options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
                ) { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
            val deliveryAvailable = suspendCancellableCoroutine<Boolean> { continuation ->
                center.getNotificationSettingsWithCompletionHandler { settings ->
                    if (continuation.isActive) {
                        continuation.resume(snoozeDeliveryAvailable(
                            authorized = settings?.authorizationStatus == UNAuthorizationStatusAuthorized,
                            alertsEnabled = settings?.alertSetting == UNNotificationSettingEnabled,
                            soundEnabled = settings?.soundSetting == UNNotificationSettingEnabled,
                            soundRequired = snoozedAlarm.soundConfig.soundEnabled,
                        ))
                    }
                }
            }
            if (!deliveryAvailable) throw SnoozeDeliveryUnavailableException()
        } else {
            requestAuthorization()
        }
        val now = clock.now()
        val delaySeconds = (at - now).inWholeMilliseconds.coerceAtLeast(1000) / 1000.0

        val content = UNMutableNotificationContent().apply {
            setTitle(resolveNotificationTitle())
            setBody(if (snoozedAlarm == null) resolveNotificationBody() else resolveSnoozeBody())
            if (snoozedAlarm?.soundConfig?.soundEnabled == true) {
                setSound(UNNotificationSound.defaultSound)
            }
            setUserInfo(mapOf("alarmId" to alarmId, "type" to if (snoozedAlarm == null) NOTIFICATION_TYPE_COMMUTE_WINDOW else NOTIFICATION_TYPE_SNOOZE))
        }

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = delaySeconds,
            repeats = false,
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = notificationIdentifier(alarmId),
            content = content,
            trigger = trigger,
        )

        suspendCancellableCoroutine<Unit> { continuation ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    onError?.invoke(alarmId, error)
                    if (snoozedAlarm != null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                        }
                        return@addNotificationRequest
                    }
                }
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    override suspend fun cancelWakeup(alarmId: String) {
        val center = resolveNotificationCenter() ?: return
        val identifier = notificationIdentifier(alarmId)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
    }

    internal suspend fun resolveNotificationTitle(preferredLanguage: String? = null): String {
        return titleResolver.resolveTitle(preferredLanguage ?: localeProvider()?.tag)
    }

    internal fun resolveNotificationBody(preferredLanguage: String? = null): String =
        FjalarStrings.get(FjalarStringKey.NOTIF_SCHEDULE_REMINDER_BODY, notificationLocale(preferredLanguage))

    internal fun resolveSnoozeBody(preferredLanguage: String? = null): String =
        FjalarStrings.get(FjalarStringKey.SNOOZE_NOTIFICATION_BODY, notificationLocale(preferredLanguage))

    private fun notificationLocale(preferredLanguage: String?): AppLocale {
        val language = preferredLanguage ?: localeProvider()?.tag
            ?: (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()
        val primarySubtag = language.lowercase().split('-', '_').firstOrNull()
        return if (primarySubtag == "id" || primarySubtag == "in") AppLocale.ID else AppLocale.EN
    }

    companion object {
        const val NOTIFICATION_TYPE_SNOOZE = "com.muhammadridha.fjalar.SNOOZE"
        const val NOTIFICATION_TYPE_COMMUTE_WINDOW = "com.muhammadridha.fjalar.COMMUTE_WINDOW"

        fun notificationIdentifier(alarmId: String): String =
            "com.muhammadridha.fjalar.wakeup.$alarmId"
    }
}

internal fun snoozeDeliveryAvailable(
    authorized: Boolean,
    alertsEnabled: Boolean,
    soundEnabled: Boolean,
    soundRequired: Boolean,
): Boolean = authorized && alertsEnabled && (!soundRequired || soundEnabled)
