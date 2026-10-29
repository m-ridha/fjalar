package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapPickerSelectionTest {
    @Test fun worldViewportIsNotASelectedDestination() {
        val selection = MapPickerSelection(null)
        assertEquals(GeoCoordinates(0.0, 0.0), selection.viewportCenter)
        assertNull(selection.coordinates)
        assertNull(selection.onCameraCenterChanged(GeoCoordinates(0.0, 0.0), isUserGesture = false).coordinates)
        assertNull(selection.onCameraCenterChanged(GeoCoordinates(0.001, 0.001), isUserGesture = false).coordinates)
    }

    @Test fun userPanOrTapCanExplicitlyChooseAnyGlobalPoint() {
        val world = MapPickerSelection(null)
        val sydney = GeoCoordinates(-33.86, 151.2)
        assertEquals(sydney, world.onCameraCenterChanged(sydney, isUserGesture = true).coordinates)
        assertEquals(GeoCoordinates(0.0, 0.0), world.select(GeoCoordinates(0.0, 0.0)).coordinates)
        assertEquals(sydney, MapPickerSelection(sydney).coordinates)
    }
}
