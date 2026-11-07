package com.muhammadridha.fjalar.domain.service

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.TrackingTier

/**
 * Pure domain policy evaluating adaptive location polling tiers based on distance (PRD 7.3 Requirement L4).
 *
 * Distance boundaries:
 * - Far: > 5,000 meters -> 60s updates
 * - Medium: 2,000 meters .. 5,000 meters -> 20s updates
 * - Approach: < 2,000 meters -> 10s high-accuracy GPS updates
 *
 * Hysteresis Guard:
 * When stepping down from a finer tier to a coarser tier (e.g. Approach -> Medium, or Medium -> Far),
 * a 100m margin is required to prevent rapid ping-ponging across tier boundaries caused by GPS jitter.
 */
object LocationTrackingPolicy {
    const val FAR_DISTANCE_METERS = 5_000.0
    const val APPROACH_DISTANCE_METERS = 2_000.0
    const val HYSTERESIS_MARGIN_METERS = 100.0

    val INITIAL_TIER = TrackingTier.MEDIUM

    fun evaluateTier(minDistanceMeters: Double, currentTier: TrackingTier = INITIAL_TIER): TrackingTier {
        return when (currentTier) {
            TrackingTier.APPROACH -> {
                when {
                    minDistanceMeters > FAR_DISTANCE_METERS + HYSTERESIS_MARGIN_METERS -> TrackingTier.FAR
                    minDistanceMeters > APPROACH_DISTANCE_METERS + HYSTERESIS_MARGIN_METERS -> TrackingTier.MEDIUM
                    else -> TrackingTier.APPROACH
                }
            }
            TrackingTier.MEDIUM -> {
                when {
                    minDistanceMeters < APPROACH_DISTANCE_METERS -> TrackingTier.APPROACH
                    minDistanceMeters > FAR_DISTANCE_METERS + HYSTERESIS_MARGIN_METERS -> TrackingTier.FAR
                    else -> TrackingTier.MEDIUM
                }
            }
            TrackingTier.FAR -> {
                when {
                    minDistanceMeters < APPROACH_DISTANCE_METERS -> TrackingTier.APPROACH
                    minDistanceMeters <= FAR_DISTANCE_METERS -> TrackingTier.MEDIUM
                    else -> TrackingTier.FAR
                }
            }
        }
    }

    fun minimumDistanceMeters(
        position: GeoCoordinates,
        armedAlarms: Collection<LocationAlarm>,
        distanceCalculator: DistanceCalculator = HaversineCalculator,
    ): Double? {
        if (armedAlarms.isEmpty()) return null
        return armedAlarms.minOf { alarm ->
            val centerDistance = distanceCalculator.calculateDistanceMeters(position, alarm.coordinates)
            maxOf(0.0, centerDistance - alarm.radius.meters)
        }
    }
}
