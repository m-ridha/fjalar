package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.adapters.map.MapUserPosition

/** Retained raw fixes must not bypass the shared permission, freshness, and accuracy policy. */
internal fun FjalarUiState.mapUserPosition(): MapUserPosition? {
    val coordinates = userLocation ?: return null
    val fix = locationFix ?: return null
    if (fix.coordinates != coordinates || fix.horizontalAccuracyMeters > 1_000f) return null
    return MapUserPosition(coordinates, fix.horizontalAccuracyMeters.toDouble())
}
