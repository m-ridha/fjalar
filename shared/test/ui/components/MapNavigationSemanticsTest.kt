package com.muhammadridha.fjalar.ui.components

import com.muhammadridha.fjalar.domain.model.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class MapNavigationSemanticsTest {

    @Test
    fun compassSemanticDescription_resolvesAllEightDirectionsEnglish() {
        assertEquals(
            "Compass pointing 0 degrees north, tap to reset north",
            compassSemanticDescription(0.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 45 degrees north-east, tap to reset north",
            compassSemanticDescription(45.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 90 degrees east, tap to reset north",
            compassSemanticDescription(90.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 135 degrees south-east, tap to reset north",
            compassSemanticDescription(135.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 180 degrees south, tap to reset north",
            compassSemanticDescription(180.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 225 degrees south-west, tap to reset north",
            compassSemanticDescription(225.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 270 degrees west, tap to reset north",
            compassSemanticDescription(270.0, AppLocale.EN),
        )
        assertEquals(
            "Compass pointing 315 degrees north-west, tap to reset north",
            compassSemanticDescription(315.0, AppLocale.EN),
        )
    }

    @Test
    fun compassSemanticDescription_resolvesAllEightDirectionsIndonesian() {
        assertEquals(
            "Kompas mengarah 0 derajat utara, ketuk untuk atur ulang utara",
            compassSemanticDescription(0.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 45 derajat timur laut, ketuk untuk atur ulang utara",
            compassSemanticDescription(45.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 90 derajat timur, ketuk untuk atur ulang utara",
            compassSemanticDescription(90.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 135 derajat tenggara, ketuk untuk atur ulang utara",
            compassSemanticDescription(135.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 180 derajat selatan, ketuk untuk atur ulang utara",
            compassSemanticDescription(180.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 225 derajat barat daya, ketuk untuk atur ulang utara",
            compassSemanticDescription(225.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 270 derajat barat, ketuk untuk atur ulang utara",
            compassSemanticDescription(270.0, AppLocale.ID),
        )
        assertEquals(
            "Kompas mengarah 315 derajat barat laut, ketuk untuk atur ulang utara",
            compassSemanticDescription(315.0, AppLocale.ID),
        )
    }

    @Test
    fun compassSemanticDescription_handlesBoundaryAndWrappedAngles() {
        // Negative angle wrapped to 315° NW
        assertEquals(
            "Compass pointing 315 degrees north-west, tap to reset north",
            compassSemanticDescription(-45.0, AppLocale.EN),
        )
        assertEquals(
            "Kompas mengarah 315 derajat barat laut, ketuk untuk atur ulang utara",
            compassSemanticDescription(-45.0, AppLocale.ID),
        )

        // Near-360° rounding to 0° North
        assertEquals(
            "Compass pointing 0 degrees north, tap to reset north",
            compassSemanticDescription(359.8, AppLocale.EN),
        )
        assertEquals(
            "Kompas mengarah 0 derajat utara, ketuk untuk atur ulang utara",
            compassSemanticDescription(359.8, AppLocale.ID),
        )

        // Multiple complete rotations (720°)
        assertEquals(
            "Compass pointing 0 degrees north, tap to reset north",
            compassSemanticDescription(720.0, AppLocale.EN),
        )
        assertEquals(
            "Kompas mengarah 0 derajat utara, ketuk untuk atur ulang utara",
            compassSemanticDescription(720.0, AppLocale.ID),
        )
    }

    @Test
    fun compassSemanticDescription_handlesNonFiniteAnglesSafely() {
        assertEquals(
            "Compass pointing 0 degrees north, tap to reset north",
            compassSemanticDescription(Double.NaN, AppLocale.EN),
        )
        assertEquals(
            "Kompas mengarah 0 derajat utara, ketuk untuk atur ulang utara",
            compassSemanticDescription(Double.NaN, AppLocale.ID),
        )

        assertEquals(
            "Compass pointing 0 degrees north, tap to reset north",
            compassSemanticDescription(Double.POSITIVE_INFINITY, AppLocale.EN),
        )
        assertEquals(
            "Kompas mengarah 0 derajat utara, ketuk untuk atur ulang utara",
            compassSemanticDescription(Double.POSITIVE_INFINITY, AppLocale.ID),
        )

        assertEquals(
            "Compass pointing 0 degrees north, tap to reset north",
            compassSemanticDescription(Double.NEGATIVE_INFINITY, AppLocale.EN),
        )
        assertEquals(
            "Kompas mengarah 0 derajat utara, ketuk untuk atur ulang utara",
            compassSemanticDescription(Double.NEGATIVE_INFINITY, AppLocale.ID),
        )
    }

    @Test
    fun armedStatusPillStateDescription_returnsCorrectLocalizedPhrasing() {
        assertEquals(
            "Commute monitoring active",
            armedStatusPillStateDescription(AppLocale.EN),
        )
        assertEquals(
            "Pemantauan komuter aktif",
            armedStatusPillStateDescription(AppLocale.ID),
        )
    }
}
