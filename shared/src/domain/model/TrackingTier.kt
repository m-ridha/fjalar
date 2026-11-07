package com.muhammadridha.fjalar.domain.model

/**
 * Distance-tiered adaptive location tracking policy levels (PRD 7.3 Requirement L4).
 *
 * Far (> 5 km): Coarse updates at 60s interval.
 * Medium (2 km - 5 km): Balanced updates at 20s interval.
 * Approach (< 2 km): High-accuracy GPS updates at 10s interval.
 */
enum class TrackingTier(val intervalMillis: Long) {
    FAR(60_000L),
    MEDIUM(20_000L),
    APPROACH(10_000L);

    companion object {
        fun fromInterval(intervalMillis: Long): TrackingTier = when {
            intervalMillis >= 60_000L -> FAR
            intervalMillis >= 20_000L -> MEDIUM
            else -> APPROACH
        }
    }
}
