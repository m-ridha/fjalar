package com.muhammadridha.fjalar.adapters.map

import com.muhammadridha.fjalar.domain.model.GeoCoordinates

/** Display-only position supplied by the shared location owner, never a native map subscription. */
data class MapUserPosition(
    val coordinates: GeoCoordinates,
    val accuracyMeters: Double,
) {
    init {
        require(accuracyMeters.isFinite() && accuracyMeters in 0.0..1_000.0)
    }
}
