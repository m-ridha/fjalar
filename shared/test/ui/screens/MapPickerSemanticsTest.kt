package com.muhammadridha.fjalar.ui.screens

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPickerSemanticsTest {

    private val sampleCoordinates = GeoCoordinates(-6.2088, 106.8456)

    @Test
    fun confirmSemanticDescription_whenCoordinatesAreNull_returnsChooseDestination() {
        assertEquals(
            "Choose destination",
            mapPickerConfirmSemanticDescription(null, null, AppLocale.EN),
        )
        assertEquals(
            "Pilih tujuan",
            mapPickerConfirmSemanticDescription(null, null, AppLocale.ID),
        )
    }

    @Test
    fun confirmSemanticDescription_whenCoordinatesAreNullWithSelectionName_stillReturnsChooseDestination() {
        assertEquals(
            "Choose destination",
            mapPickerConfirmSemanticDescription("Monas", null, AppLocale.EN),
        )
        assertEquals(
            "Pilih tujuan",
            mapPickerConfirmSemanticDescription("Monas", null, AppLocale.ID),
        )
    }

    @Test
    fun confirmSemanticDescription_whenCoordinatesExistWithoutPlaceName_formatsCoordinates() {
        assertEquals(
            "Confirm destination -6.2088, 106.8456",
            mapPickerConfirmSemanticDescription(null, sampleCoordinates, AppLocale.EN),
        )
        assertEquals(
            "Konfirmasi tujuan -6.2088, 106.8456",
            mapPickerConfirmSemanticDescription(null, sampleCoordinates, AppLocale.ID),
        )
    }

    @Test
    fun confirmSemanticDescription_whenCoordinatesExistWithBlankPlaceName_formatsCoordinates() {
        assertEquals(
            "Confirm destination -6.2088, 106.8456",
            mapPickerConfirmSemanticDescription("   ", sampleCoordinates, AppLocale.EN),
        )
        assertEquals(
            "Konfirmasi tujuan -6.2088, 106.8456",
            mapPickerConfirmSemanticDescription("   ", sampleCoordinates, AppLocale.ID),
        )
    }

    @Test
    fun confirmSemanticDescription_whenPlaceNameExists_formatsPlaceName() {
        assertEquals(
            "Confirm destination Monas",
            mapPickerConfirmSemanticDescription("Monas", sampleCoordinates, AppLocale.EN),
        )
        assertEquals(
            "Konfirmasi tujuan Monas",
            mapPickerConfirmSemanticDescription("Monas", sampleCoordinates, AppLocale.ID),
        )
    }
}
