package com.muhammadridha.fjalar.domain.model

import kotlin.time.Instant

enum class GeofenceTransitionType {
    ENTER,
    EXIT,
}

data class GeofenceRegion(
    val id: String,
    val center: GeoCoordinates,
    val radiusMeters: Double,
    val transitionTypes: Set<GeofenceTransitionType> = setOf(GeofenceTransitionType.ENTER),
) {
    init {
        require(id.isNotBlank()) { "GeofenceRegion id cannot be blank" }
        require(radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS) {
            "radiusMeters must be within $MIN_RADIUS_METERS..$MAX_RADIUS_METERS m, was $radiusMeters"
        }
        require(transitionTypes.isNotEmpty()) { "transitionTypes cannot be empty" }
    }

    companion object {
        const val MIN_RADIUS_METERS: Double = 50.0
        const val MAX_RADIUS_METERS: Double = 10_000.0
    }
}

data class GeofenceTransitionEvent(
    val regionId: String,
    val transitionType: GeofenceTransitionType,
    val timestamp: Instant,
) {
    init {
        require(regionId.isNotBlank()) { "GeofenceTransitionEvent regionId cannot be blank" }
    }
}
