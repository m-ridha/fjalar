package com.muhammadridha.fjalar.domain.model

import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A real platform observation; destinations and viewport defaults are never fixes. */
data class LocationFix(
    val coordinates: GeoCoordinates,
    val horizontalAccuracyMeters: Float,
    val observedAt: Instant,
    /** Observation age survives flow replay and ViewModel recreation; wall time is informational. */
    val monotonicObservation: TimeMark = TimeSource.Monotonic.markNow(),
) {
    init {
        require(horizontalAccuracyMeters.isFinite() && horizontalAccuracyMeters >= 0f)
    }
}

enum class LocationAvailability {
    AVAILABLE,
    PERMISSION_DENIED,
    PROVIDER_UNAVAILABLE,
}
