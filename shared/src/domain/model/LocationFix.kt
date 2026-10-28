package com.muhammadridha.fjalar.domain.model

import kotlin.time.Instant

/** A real platform observation; destinations and viewport defaults are never fixes. */
data class LocationFix(
    val coordinates: GeoCoordinates,
    val horizontalAccuracyMeters: Float,
    val observedAt: Instant,
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
