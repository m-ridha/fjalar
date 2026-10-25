package com.muhammadridha.fjalar.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element

class CatalogParityTest {

    data class CatalogEntry(
        val key: String,
        val value: String,
        val placeholders: List<String>
    )

    private val requiredKeys = listOf(
        "app_name",
        "hud_search_placeholder",
        "hud_theme_switch_light",
        "hud_theme_switch_dark",
        "hud_locale_label",
        "hud_recenter_location",
        "status_armed_scheduled",
        "status_armed_one_time",
        "sheet_saved_title",
        "sheet_active_count",
        "sheet_new_alarm",
        "card_skip_today",
        "card_skipped_today",
        "card_one_time_trip",
        "card_loud_and_vibrate",
        "editor_title",
        "editor_cancel",
        "editor_save",
        "editor_label_placeholder",
        "editor_start_time",
        "editor_end_time",
        "editor_trigger_radius",
        "editor_mode_one_time",
        "editor_mode_scheduled",
        "editor_active_window",
        "editor_alert_and_haptics",
        "editor_loud_sound",
        "editor_strong_vibrate",
        "editor_earphone_override",
        "ringing_in_zone_badge",
        "ringing_headline",
        "ringing_snooze_action",
        "ringing_slide_to_stop",
        "notif_monitoring_title",
        "notif_monitoring_body",
        "perm_location_title",
        "perm_location_body"
    )

    private val placeholderRegex = Regex("""%(\d+\$)?[-\#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]|\{[^}]+\}""")

    private fun findComposeResourcesDir(): File {
        val candidates = listOf(
            File("shared/composeResources"),
            File("../shared/composeResources"),
            File("composeResources"),
            File("../../shared/composeResources")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isDirectory) {
                return candidate.canonicalFile
            }
        }
        var current: File? = File(".").canonicalFile
        while (current != null) {
            val candidate = File(current, "shared/composeResources")
            if (candidate.exists() && candidate.isDirectory) {
                return candidate.canonicalFile
            }
            current = current.parentFile
        }
        error("Could not locate shared/composeResources directory from ${File(".").canonicalPath}")
    }

    private fun parseCatalog(xmlFile: File): Map<String, CatalogEntry> {
        assertTrue(xmlFile.exists(), "Resource file must exist: ${xmlFile.absolutePath}")
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlFile)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, CatalogEntry>()

        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            val text = node.textContent
            assertFalse(name.isBlank(), "Resource key name cannot be blank in ${xmlFile.name}")
            assertFalse(result.containsKey(name), "Duplicate key '$name' found in ${xmlFile.name}")
            val placeholders = placeholderRegex.findAll(text).map { it.value }.toList()
            result[name] = CatalogEntry(name, text, placeholders)
        }
        return result
    }

    private fun loadCatalogs(): Triple<Map<String, CatalogEntry>, Map<String, CatalogEntry>, Map<String, CatalogEntry>> {
        val baseDir = findComposeResourcesDir()
        val enFile = File(baseDir, "values/strings.xml")
        val idFile = File(baseDir, "values-id/strings.xml")
        val inFile = File(baseDir, "values-in/strings.xml")

        val enCatalog = parseCatalog(enFile)
        val idCatalog = parseCatalog(idFile)
        val inCatalog = parseCatalog(inFile)

        return Triple(enCatalog, idCatalog, inCatalog)
    }

    @Test
    fun allRequiredKeysPresentInAllCatalogs() {
        val (en, id, inCat) = loadCatalogs()
        for (key in requiredKeys) {
            assertTrue(en.containsKey(key), "Missing required key '$key' in values/strings.xml")
            assertTrue(id.containsKey(key), "Missing required key '$key' in values-id/strings.xml")
            assertTrue(inCat.containsKey(key), "Missing required key '$key' in values-in/strings.xml")
        }
    }

    @Test
    fun keySetParityAcrossCatalogs() {
        val (en, id, inCat) = loadCatalogs()
        val enMissingInId = en.keys - id.keys
        val idMissingInEn = id.keys - en.keys
        val idMissingInIn = id.keys - inCat.keys
        val inMissingInId = inCat.keys - id.keys

        assertTrue(enMissingInId.isEmpty(), "Keys present in EN but missing in ID: $enMissingInId")
        assertTrue(idMissingInEn.isEmpty(), "Keys present in ID but missing in EN: $idMissingInEn")
        assertTrue(idMissingInIn.isEmpty(), "Keys present in ID but missing in IN: $idMissingInIn")
        assertTrue(inMissingInId.isEmpty(), "Keys present in IN but missing in ID: $inMissingInId")
    }

    @Test
    fun placeholderParityBetweenEnglishAndIndonesian() {
        val (en, id, inCat) = loadCatalogs()
        for ((key, enEntry) in en) {
            val idEntry = id[key]
            assertNotNull(idEntry, "Key '$key' missing in ID catalog")
            val inEntry = inCat[key]
            assertNotNull(inEntry, "Key '$key' missing in IN catalog")

            val enPlaceholders = enEntry.placeholders.sorted()
            val idPlaceholders = idEntry.placeholders.sorted()
            val inPlaceholders = inEntry.placeholders.sorted()

            assertEquals(
                enPlaceholders,
                idPlaceholders,
                "Placeholders mismatch for key '$key': EN has $enPlaceholders but ID has $idPlaceholders"
            )
            assertEquals(
                idPlaceholders,
                inPlaceholders,
                "Placeholders mismatch for key '$key' between ID and IN catalogs: ID has $idPlaceholders but IN has $inPlaceholders"
            )
        }
    }

    @Test
    fun idAndInCatalogsAreIdenticalInValues() {
        val (_, id, inCat) = loadCatalogs()
        assertEquals(id.keys, inCat.keys, "ID and IN catalogs must have identical key sets")
        for ((key, idEntry) in id) {
            val inEntry = inCat[key]
            assertNotNull(inEntry, "Key '$key' missing in IN catalog")
            assertEquals(
                idEntry.value,
                inEntry.value,
                "Value mismatch for key '$key' between values-id and values-in"
            )
        }
    }

    @Test
    fun noBlankValuesInAnyCatalog() {
        val (en, id, inCat) = loadCatalogs()
        for ((key, entry) in en) {
            assertFalse(entry.value.isBlank(), "EN value for key '$key' is blank")
        }
        for ((key, entry) in id) {
            assertFalse(entry.value.isBlank(), "ID value for key '$key' is blank")
        }
        for ((key, entry) in inCat) {
            assertFalse(entry.value.isBlank(), "IN value for key '$key' is blank")
        }
    }

    @Test
    fun indonesianTextExpansionVerifications() {
        val (en, id, _) = loadCatalogs()
        // Check known Indonesian text expansion invariants from UI spec
        val slideEn = en["ringing_slide_to_stop"]?.value ?: ""
        val slideId = id["ringing_slide_to_stop"]?.value ?: ""
        assertTrue(
            slideId.length > slideEn.length,
            "Expected Indonesian 'ringing_slide_to_stop' ($slideId) to be longer than English ($slideEn)"
        )

        val skipEn = en["card_skip_today"]?.value ?: ""
        val skipId = id["card_skip_today"]?.value ?: ""
        assertTrue(
            skipId.length > skipEn.length,
            "Expected Indonesian 'card_skip_today' ($skipId) to be longer than English ($skipEn)"
        )
    }
}
