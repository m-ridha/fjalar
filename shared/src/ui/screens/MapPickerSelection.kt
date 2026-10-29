package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.domain.model.GeoCoordinates

/** The neutral initial camera position is framing, never a selected destination. */
data class MapPickerSelection(val coordinates: GeoCoordinates?) {
    val viewportCenter: GeoCoordinates
        get() = coordinates ?: GeoCoordinates(0.0, 0.0)

    fun onCameraCenterChanged(center: GeoCoordinates, isUserGesture: Boolean = false): MapPickerSelection {
        // Native maps also report initialization and programmatic recentering.
        if (!isUserGesture) return this
        return select(center)
    }

    fun select(coordinates: GeoCoordinates): MapPickerSelection = copy(coordinates = coordinates)
}
