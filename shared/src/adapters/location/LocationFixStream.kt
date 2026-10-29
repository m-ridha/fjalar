package com.muhammadridha.fjalar.adapters.location

import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.model.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.asStateFlow

/** Conflated latest observation shared by all consumers of the single native listener. */
class LocationFixStream {
    private val latest = MutableStateFlow<LocationFix?>(null)
    private val status = MutableStateFlow(LocationAvailability.PROVIDER_UNAVAILABLE)
    private val generation = MutableStateFlow(0L)
    val fixes = latest.filterNotNull()
    val availability = status.asStateFlow()

    fun publish(fix: LocationFix): Boolean {
        // Platform adapters validate freshness before publishing. Wall time is informational
        // and may move backwards when the system clock is corrected.
        latest.value = fix
        status.value = LocationAvailability.AVAILABLE
        return true
    }

    fun currentGeneration(): Long = generation.value

    fun isCurrent(generation: Long): Boolean = this.generation.value == generation

    fun invalidateQueuedFixes() {
        while (true) {
            val previous = generation.value
            if (generation.compareAndSet(previous, previous + 1)) break
        }
    }

    fun reportAvailability(value: LocationAvailability) {
        invalidateQueuedFixes()
        status.value = value
    }
}
