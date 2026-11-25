package com.muhammadridha.fjalar.localization

import com.muhammadridha.fjalar.domain.model.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogParityTest {

    private val placeholderRegex = Regex("""%(\d+\$)?[-\#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]|\{[^}]+\}""")

    private fun extractPlaceholders(template: String): List<String> =
        placeholderRegex.findAll(template).map {
            // Normalize positional vs non-positional markers identically to scripts/test_string_catalogs.py
            it.value.replace(Regex("""%(\d+\$)?([sd])"""), "%$2")
        }.sorted().toList()

    @Test
    fun allStringKeysExistAndAreNonBlankInBothLocales() {
        for (key in FjalarStringKey.entries) {
            val enTemplate = FjalarStrings.rawTemplate(key, AppLocale.EN)
            val idTemplate = FjalarStrings.rawTemplate(key, AppLocale.ID)

            assertNotNull(enTemplate, "Missing EN translation for $key")
            assertNotNull(idTemplate, "Missing ID translation for $key")
            assertFalse(enTemplate.isBlank(), "EN translation for $key is blank")
            assertFalse(idTemplate.isBlank(), "ID translation for $key is blank")
        }
    }

    @Test
    fun placeholderParityBetweenEnglishAndIndonesian() {
        for (key in FjalarStringKey.entries) {
            val enTemplate = assertNotNull(FjalarStrings.rawTemplate(key, AppLocale.EN))
            val idTemplate = assertNotNull(FjalarStrings.rawTemplate(key, AppLocale.ID))

            val enPlaceholders = extractPlaceholders(enTemplate)
            val idPlaceholders = extractPlaceholders(idTemplate)
            assertEquals(
                enPlaceholders,
                idPlaceholders,
                "Placeholder mismatch for $key: EN=$enPlaceholders ID=$idPlaceholders",
            )
        }
    }

    @Test
    fun snoozeAndDeleteCopyMatchExpectationsBilingually() {
        assertEquals("Snooze (+2 min)", FjalarStrings.get(FjalarStringKey.RINGING_SNOOZE_ACTION, AppLocale.EN))
        assertEquals("Tunda (+2 mnt)", FjalarStrings.get(FjalarStringKey.RINGING_SNOOZE_ACTION, AppLocale.ID))
        assertEquals("Delete", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.EN))
        assertEquals("Hapus", FjalarStrings.get(FjalarStringKey.DIALOG_DELETE_CONFIRM, AppLocale.ID))
    }

    @Test
    fun indonesianSlideInstructionProvidesExpandedCopyFixture() {
        val slideEn = FjalarStrings.get(FjalarStringKey.RINGING_SLIDE_TO_STOP, AppLocale.EN)
        val slideId = FjalarStrings.get(FjalarStringKey.RINGING_SLIDE_TO_STOP, AppLocale.ID)
        assertTrue(
            slideId.length > slideEn.length,
            "Expected Indonesian RINGING_SLIDE_TO_STOP ($slideId) to be longer than English ($slideEn)",
        )
    }
}
