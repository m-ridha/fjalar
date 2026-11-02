package com.muhammadridha.fjalar.ui

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FjalarStringsTest {

    @Test
    fun destructiveActionUsesShortRecognizableLabels() {
        assertEquals("Delete", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.EN))
        assertEquals("Hapus", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.ID))
    }

    @Test
    fun placeholderSignaturesMatchForEveryTranslation() {
        val placeholder = Regex("%(?:[0-9]+\\$)?[sd]")
        for (key in FjalarStringKey.entries) {
            val en = placeholder.findAll(FjalarStrings.get(key, AppLocale.EN)).map { it.value }.toList()
            val id = placeholder.findAll(FjalarStrings.get(key, AppLocale.ID)).map { it.value }.toList()
            assertEquals(en, id, "Placeholder signature differs for $key")
        }
    }

    @Test
    fun snoozeCopyPromisesOnlyTwoMinutes() {
        assertEquals("Snooze (+2 min)", FjalarStrings.get(FjalarStringKey.RINGING_SNOOZE_ACTION, AppLocale.EN))
        assertEquals("Tunda (+2 mnt)", FjalarStrings.get(FjalarStringKey.RINGING_SNOOZE_ACTION, AppLocale.ID))
    }

    @Test
    fun argumentPlaceholderTextRemainsLiteral() {
        for (locale in AppLocale.entries) {
            val label = "Station %s %2\$s %d"
            val result = FjalarStrings.get(FjalarStringKey.STATUS_ARMED_ONE_TIME, locale, label, "500m")
            assertTrue(result.contains(label))
        }
    }

    @Test
    fun backgroundPermissionInstructionsPreserveTheSystemSettingsLabel() {
        val label = "Allow all the time %s"
        for (locale in AppLocale.entries) {
            val instructions = FjalarStrings.get(FjalarStringKey.PERM_BACKGROUND_SETTINGS_BODY, locale, label)
            assertTrue(instructions.contains(label))
            assertTrue(instructions.contains(if (locale == AppLocale.EN) "system location permission settings" else "pengaturan izin lokasi sistem"))
        }
        assertEquals("Continue", FjalarStrings.get(FjalarStringKey.PERM_BACKGROUND_SETTINGS_ACTION, AppLocale.EN))
        assertEquals("Lanjutkan", FjalarStrings.get(FjalarStringKey.PERM_BACKGROUND_SETTINGS_ACTION, AppLocale.ID))
    }

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
        assertEquals("Hapus alarm 'Morning Commute'?", idDeleteMsg)
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

        assertEquals("Add Alarm", FjalarStrings.get(FjalarStringKey.NAV_ADD_ALARM, AppLocale.EN))
        assertEquals("Tambah Alarm", FjalarStrings.get(FjalarStringKey.NAV_ADD_ALARM, AppLocale.ID))

        assertEquals("Delete Alarm?", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_TITLE, AppLocale.EN))
        assertEquals("Hapus Alarm?", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_TITLE, AppLocale.ID))

        assertEquals("Delete", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.EN))
        assertEquals("Hapus", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.ID))

        assertEquals("Choose destination", FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, AppLocale.EN))
        assertEquals("Pilih tujuan", FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, AppLocale.ID))

        assertEquals("Grant Permission", FjalarStrings.get(FjalarStringKey.PERM_LOCATION_GRANT, AppLocale.EN))
        assertEquals("Izinkan Akses", FjalarStrings.get(FjalarStringKey.PERM_LOCATION_GRANT, AppLocale.ID))

        assertEquals("Dismiss", FjalarStrings.get(FjalarStringKey.PERM_LOCATION_DISMISS, AppLocale.EN))
        assertEquals("Abaikan", FjalarStrings.get(FjalarStringKey.PERM_LOCATION_DISMISS, AppLocale.ID))
    }
}
