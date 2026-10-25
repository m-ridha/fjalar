package com.muhammadridha.fjalar.domain.model

import kotlin.time.Instant

data class AlarmSoundConfig(
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val volume: Float = 1f,
) {
    init {
        require(soundEnabled || vibrateEnabled) { "an alarm must at least sound or vibrate" }
        require(volume in 0f..1f) { "volume must be within 0..1, was $volume" }
    }
}

enum class AlarmState { DISABLED, SCHEDULED_IDLE, ARMED_MONITORING, RINGING, COMPLETED_FOR_WINDOW }

data class LocationAlarm(
    val id: String,
    val label: String,
    val coordinates: GeoCoordinates,
    val radius: TriggerRadius,
    val schedule: AlarmSchedule,
    val soundConfig: AlarmSoundConfig = AlarmSoundConfig(),
    val state: AlarmState = AlarmState.DISABLED,
    /** Optional human-readable reverse-geocoded address label (PRD 9.1). */
    val addressLabel: String? = null,
    /** While [AlarmState.COMPLETED_FOR_WINDOW], stay silent until this instant (end of the dismissed/skipped window). */
    val suppressedUntil: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
    }

    val isEnabled: Boolean
        get() = state != AlarmState.DISABLED
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLocale(val tag: String) { EN("en"), ID("id") }
