package com.muhammadridha.fjalar.adapters.location

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Monotonic milliseconds prevent wall-clock changes from making historical fixes current. */
fun isFreshLocationFix(fixElapsedMillis: Long, nowElapsedMillis: Long): Boolean {
    if (fixElapsedMillis <= 0L || fixElapsedMillis > nowElapsedMillis) return false
    return isFreshLocationFix((nowElapsedMillis - fixElapsedMillis).milliseconds)
}

/** Shared ongoing-display policy, with the same inclusive boundary as native callback filtering. */
fun isFreshLocationFix(observationAge: Duration): Boolean =
    !observationAge.isNegative() && observationAge <= 30.seconds
