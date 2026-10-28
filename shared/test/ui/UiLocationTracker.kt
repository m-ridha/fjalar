package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAvailability
import com.muhammadridha.fjalar.domain.model.LocationFix
import com.muhammadridha.fjalar.domain.ports.outbound.LocationTrackerPort
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/** Metadata-bearing native observations for the existing camera and destination tests. */
internal class UiLocationTracker : LocationTrackerPort {
    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    override fun observeFixes() = fixes
    override fun observeAvailability() = MutableStateFlow(LocationAvailability.AVAILABLE)
    override suspend fun startTracking(updateIntervalMillis: Long) {}
    override suspend fun stopTracking() {}

    fun emit(coordinates: GeoCoordinates) {
        check(fixes.tryEmit(LocationFix(coordinates, 10f, Instant.parse("2026-10-27T00:00:00Z"))))
    }
}
