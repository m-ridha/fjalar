package com.muhammadridha.fjalar.adapters.location

/** Monotonic milliseconds prevent wall-clock changes from making historical fixes current. */
fun isFreshLocationFix(fixElapsedMillis: Long, nowElapsedMillis: Long): Boolean {
    if (fixElapsedMillis <= 0L || fixElapsedMillis > nowElapsedMillis) return false
    return nowElapsedMillis - fixElapsedMillis <= 30_000L
}
