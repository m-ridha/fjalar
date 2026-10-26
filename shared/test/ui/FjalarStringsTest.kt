package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FjalarStringsTest {

    @Test
    fun get_returnsEnglishAndIndonesianStringsAccurately() {
        val enTitle = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, AppLocale.EN)
        val idTitle = FjalarStrings.get(FjalarStringKey.EDITOR_TITLE, AppLocale.ID)

        assertEquals("Configure Alarm", enTitle)
        assertEquals("Atur Alarm", idTitle)

        val enAppName = FjalarStrings.get(FjalarStringKey.APP_NAME, AppLocale.EN)
        val idAppName = FjalarStrings.get(FjalarStringKey.APP_NAME, AppLocale.ID)
        assertEquals("Wake Me at", enAppName)
        assertEquals("Bangunkan Saya di", idAppName)
    }

    @Test
    fun get_withArguments_formatsCorrectlyInBothLocales() {
        val enDistance = FjalarStrings.get(
            FjalarStringKey.STATUS_ARMED_ONE_TIME,
            AppLocale.EN,
            "Manggarai",
            "500m",
        )
        val idDistance = FjalarStrings.get(
            FjalarStringKey.STATUS_ARMED_ONE_TIME,
            AppLocale.ID,
            "Manggarai",
            "500m",
        )

        assertTrue(enDistance.contains("Armed: Manggarai"))
        assertTrue(enDistance.contains("500m away"))
        assertTrue(idDistance.contains("Aktif: Manggarai"))
        assertTrue(idDistance.contains("500m lagi"))

        val enCount = FjalarStrings.get(FjalarStringKey.SHEET_ACTIVE_COUNT, AppLocale.EN, 2)
        val idCount = FjalarStrings.get(FjalarStringKey.SHEET_ACTIVE_COUNT, AppLocale.ID, 2)
        assertEquals("2 Active", enCount)
        assertEquals("2 Aktif", idCount)

        val enDeleteMsg = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_MESSAGE, AppLocale.EN, "Morning Commute")
        val idDeleteMsg = FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_MESSAGE, AppLocale.ID, "Morning Commute")
        assertEquals("Are you sure you want to delete 'Morning Commute'?", enDeleteMsg)
        assertEquals("Apakah Anda yakin ingin menghapus 'Morning Commute'?", idDeleteMsg)
    }

    @Test
    fun get_withSpecialCharactersInArguments_formatsSafelyWithoutRegexError() {
        val formatted = FjalarStrings.get(
            FjalarStringKey.STATUS_ARMED_ONE_TIME,
            AppLocale.EN,
            "Special $100 \\ Station",
            "500m",
        )
        assertTrue(formatted.contains("Special $100 \\ Station"))
    }

    @Test
    fun catalogParity_allKeysExistInBothEnglishAndIndonesian() {
        for (key in FjalarStringKey.entries) {
            val en = FjalarStrings.get(key, AppLocale.EN)
            val id = FjalarStrings.get(key, AppLocale.ID)

            assertTrue(en.isNotBlank(), "Key $key is missing or blank in English catalog")
            assertTrue(id.isNotBlank(), "Key $key is missing or blank in Indonesian catalog")
        }
    }

    @Test
    fun newNavigationAndDialogKeys_haveCorrectTranslations() {
        assertEquals("Map", FjalarStrings.get(FjalarStringKey.NAV_MAP, AppLocale.EN))
        assertEquals("Peta", FjalarStrings.get(FjalarStringKey.NAV_MAP, AppLocale.ID))

        assertEquals("Alarms", FjalarStrings.get(FjalarStringKey.NAV_ALARMS, AppLocale.EN))
        assertEquals("Alarm", FjalarStrings.get(FjalarStringKey.NAV_ALARMS, AppLocale.ID))

        assertEquals("Add", FjalarStrings.get(FjalarStringKey.NAV_ADD, AppLocale.EN))
        assertEquals("Tambah", FjalarStrings.get(FjalarStringKey.NAV_ADD, AppLocale.ID))

        assertEquals("Delete Alarm?", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_TITLE, AppLocale.EN))
        assertEquals("Hapus Alarm?", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_TITLE, AppLocale.ID))

        assertEquals("Delete", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.EN))
        assertEquals("Hapus", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.ID))

        assertEquals("Set Location", FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, AppLocale.EN))
        assertEquals("Tetapkan Lokasi", FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, AppLocale.ID))
    }
}
