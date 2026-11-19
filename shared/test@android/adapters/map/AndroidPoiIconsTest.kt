package com.muhammadridha.fjalar.adapters.map

import org.maplibre.android.style.expressions.Expression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidPoiIconsTest {

    @Test
    fun poiExpressionsAndTierFiltersParseCleanlyViaMapLibre() {
        assertNotNull(Expression.raw(AndroidMapRendererAdapter.POI_MAJOR_FILTER))
        assertNotNull(Expression.raw(AndroidMapRendererAdapter.POI_NEIGHBORHOOD_FILTER))
        assertNotNull(Expression.raw(AndroidMapRendererAdapter.POI_LOCAL_FILTER))
        assertNotNull(AndroidPoiIcons.iconImageExpression())
        assertNotNull(AndroidPoiIcons.textColorExpression(isDarkTheme = false))
        assertNotNull(AndroidPoiIcons.textColorExpression(isDarkTheme = true))
    }

    @Test
    fun resolvePoiIconId_mapsReligionMedicalTransitFinanceAndCommerceCategories() {
        assertEquals(AndroidPoiIcons.POI_ICON_MOSQUE, AndroidPoiIcons.resolvePoiIconId("place_of_worship", "muslim"))
        assertEquals(AndroidPoiIcons.POI_ICON_CHURCH, AndroidPoiIcons.resolvePoiIconId("place_of_worship", "christian"))
        assertEquals(AndroidPoiIcons.POI_ICON_WORSHIP, AndroidPoiIcons.resolvePoiIconId("place_of_worship", "hindu"))
        assertEquals(AndroidPoiIcons.POI_ICON_WORSHIP, AndroidPoiIcons.resolvePoiIconId("place_of_worship", null))

        assertEquals(AndroidPoiIcons.POI_ICON_HOSPITAL, AndroidPoiIcons.resolvePoiIconId("hospital"))
        assertEquals(AndroidPoiIcons.POI_ICON_CLINIC, AndroidPoiIcons.resolvePoiIconId("clinic"))
        assertEquals(AndroidPoiIcons.POI_ICON_CLINIC, AndroidPoiIcons.resolvePoiIconId("dentist"))
        assertEquals(AndroidPoiIcons.POI_ICON_CLINIC, AndroidPoiIcons.resolvePoiIconId("doctors"))
        assertEquals(AndroidPoiIcons.POI_ICON_PHARMACY, AndroidPoiIcons.resolvePoiIconId("pharmacy"))
        assertEquals(AndroidPoiIcons.POI_ICON_PHARMACY, AndroidPoiIcons.resolvePoiIconId("chemist"))

        assertEquals(AndroidPoiIcons.POI_ICON_TRANSIT_RAIL, AndroidPoiIcons.resolvePoiIconId("station"))
        assertEquals(AndroidPoiIcons.POI_ICON_TRANSIT_RAIL, AndroidPoiIcons.resolvePoiIconId("railway_station"))
        assertEquals(AndroidPoiIcons.POI_ICON_TRANSIT_RAIL, AndroidPoiIcons.resolvePoiIconId("subway_entrance"))
        assertEquals(AndroidPoiIcons.POI_ICON_TRANSIT_RAIL, AndroidPoiIcons.resolvePoiIconId("train_station_entrance"))
        assertEquals(AndroidPoiIcons.POI_ICON_TRANSIT_RAIL, AndroidPoiIcons.resolvePoiIconId("light_rail"))

        assertEquals(AndroidPoiIcons.POI_ICON_BUS, AndroidPoiIcons.resolvePoiIconId("bus_station"))
        assertEquals(AndroidPoiIcons.POI_ICON_BUS, AndroidPoiIcons.resolvePoiIconId("bus_stop"))
        assertEquals(AndroidPoiIcons.POI_ICON_BUS, AndroidPoiIcons.resolvePoiIconId("tram_stop"))
        assertEquals(AndroidPoiIcons.POI_ICON_FERRY, AndroidPoiIcons.resolvePoiIconId("ferry_terminal"))
        assertEquals(AndroidPoiIcons.POI_ICON_AIRPORT, AndroidPoiIcons.resolvePoiIconId("aerodrome"))
        assertEquals(AndroidPoiIcons.POI_ICON_AIRPORT, AndroidPoiIcons.resolvePoiIconId("airport"))

        assertEquals(AndroidPoiIcons.POI_ICON_SCHOOL, AndroidPoiIcons.resolvePoiIconId("school"))
        assertEquals(AndroidPoiIcons.POI_ICON_SCHOOL, AndroidPoiIcons.resolvePoiIconId("university"))
        assertEquals(AndroidPoiIcons.POI_ICON_POST, AndroidPoiIcons.resolvePoiIconId("post_office"))
        assertEquals(AndroidPoiIcons.POI_ICON_CIVIC, AndroidPoiIcons.resolvePoiIconId("government"))
        assertEquals(AndroidPoiIcons.POI_ICON_CIVIC, AndroidPoiIcons.resolvePoiIconId("library"))

        assertEquals(AndroidPoiIcons.POI_ICON_ATM, AndroidPoiIcons.resolvePoiIconId("atm"))
        assertEquals(AndroidPoiIcons.POI_ICON_ATM, AndroidPoiIcons.resolvePoiIconId("bank"))
        assertEquals(AndroidPoiIcons.POI_ICON_ATM, AndroidPoiIcons.resolvePoiIconId("bureau_de_change"))
        assertEquals(AndroidPoiIcons.POI_ICON_PARKING, AndroidPoiIcons.resolvePoiIconId("parking"))
        assertEquals(AndroidPoiIcons.POI_ICON_FUEL, AndroidPoiIcons.resolvePoiIconId("fuel"))

        assertEquals(AndroidPoiIcons.POI_ICON_FOOD, AndroidPoiIcons.resolvePoiIconId("restaurant"))
        assertEquals(AndroidPoiIcons.POI_ICON_FOOD, AndroidPoiIcons.resolvePoiIconId("pub"))
        assertEquals(AndroidPoiIcons.POI_ICON_CAFE, AndroidPoiIcons.resolvePoiIconId("cafe"))
        assertEquals(AndroidPoiIcons.POI_ICON_CAFE, AndroidPoiIcons.resolvePoiIconId("bakery"))
        assertEquals(AndroidPoiIcons.POI_ICON_HOTEL, AndroidPoiIcons.resolvePoiIconId("hotel"))
        assertEquals(AndroidPoiIcons.POI_ICON_PET, AndroidPoiIcons.resolvePoiIconId("pet"))
        assertEquals(AndroidPoiIcons.POI_ICON_SHOP, AndroidPoiIcons.resolvePoiIconId("mall"))
        assertEquals(AndroidPoiIcons.POI_ICON_SHOP, AndroidPoiIcons.resolvePoiIconId("supermarket"))
        assertEquals(AndroidPoiIcons.POI_ICON_SHOP, AndroidPoiIcons.resolvePoiIconId("convenience"))
        assertEquals(AndroidPoiIcons.POI_ICON_PARK, AndroidPoiIcons.resolvePoiIconId("park"))
        assertEquals(AndroidPoiIcons.POI_ICON_PARK, AndroidPoiIcons.resolvePoiIconId("stadium"))
        assertEquals(AndroidPoiIcons.POI_ICON_PARK, AndroidPoiIcons.resolvePoiIconId("playground"))
        assertEquals(AndroidPoiIcons.POI_ICON_LANDMARK, AndroidPoiIcons.resolvePoiIconId("cinema"))
        assertEquals(AndroidPoiIcons.POI_ICON_LANDMARK, AndroidPoiIcons.resolvePoiIconId("attraction"))
        assertEquals(AndroidPoiIcons.POI_ICON_LANDMARK, AndroidPoiIcons.resolvePoiIconId("unmapped_custom_poi"))
    }

    @Test
    fun resolvePoiTextColorHex_andHaloMatchOsmCartoLightAndDarkPalettes() {
        assertEquals("#F5F5F3", AndroidPoiIcons.textHaloColorHex(isDarkTheme = false))
        assertEquals("#181818", AndroidPoiIcons.textHaloColorHex(isDarkTheme = true))

        assertEquals("#BF0000", AndroidPoiIcons.resolvePoiTextColorHex("hospital", isDarkTheme = false))
        assertEquals("#FF6B6B", AndroidPoiIcons.resolvePoiTextColorHex("hospital", isDarkTheme = true))

        assertEquals("#0072B2", AndroidPoiIcons.resolvePoiTextColorHex("station", isDarkTheme = false))
        assertEquals("#4DC3FF", AndroidPoiIcons.resolvePoiTextColorHex("station", isDarkTheme = true))

        assertEquals("#734A08", AndroidPoiIcons.resolvePoiTextColorHex("atm", isDarkTheme = false))
        assertEquals("#E0B070", AndroidPoiIcons.resolvePoiTextColorHex("atm", isDarkTheme = true))

        assertEquals("#C77400", AndroidPoiIcons.resolvePoiTextColorHex("cafe", isDarkTheme = false))
        assertEquals("#FFB74D", AndroidPoiIcons.resolvePoiTextColorHex("cafe", isDarkTheme = true))

        assertEquals("#AC39AC", AndroidPoiIcons.resolvePoiTextColorHex("pet", isDarkTheme = false))
        assertEquals("#E07BE0", AndroidPoiIcons.resolvePoiTextColorHex("pet", isDarkTheme = true))

        assertEquals("#2E7D32", AndroidPoiIcons.resolvePoiTextColorHex("park", isDarkTheme = false))
        assertEquals("#66BB6A", AndroidPoiIcons.resolvePoiTextColorHex("park", isDarkTheme = true))

        assertEquals("#555555", AndroidPoiIcons.resolvePoiTextColorHex("place_of_worship", isDarkTheme = false))
        assertEquals("#E0E0E0", AndroidPoiIcons.resolvePoiTextColorHex("place_of_worship", isDarkTheme = true))
        assertEquals("#555555", AndroidPoiIcons.resolvePoiTextColorHex("school", isDarkTheme = false))
        assertEquals("#D4D4D4", AndroidPoiIcons.resolvePoiTextColorHex("school", isDarkTheme = true))
    }

    @Test
    fun tierListsAreDisjointCoverAllIconsAndExcludeOsmNoiseKinds() {
        val major = AndroidPoiIcons.MAJOR_TIER_KINDS.toSet()
        val neighborhood = AndroidPoiIcons.NEIGHBORHOOD_TIER_KINDS.toSet()
        val local = AndroidPoiIcons.LOCAL_TIER_KINDS.toSet()

        assertTrue((major intersect neighborhood).isEmpty())
        assertTrue((major intersect local).isEmpty())
        assertTrue((neighborhood intersect local).isEmpty())

        val allTierKinds = major + neighborhood + local
        val coveredIconIds = buildSet {
            for (kind in allTierKinds) {
                if (kind == "place_of_worship") {
                    add(AndroidPoiIcons.resolvePoiIconId(kind, "muslim"))
                    add(AndroidPoiIcons.resolvePoiIconId(kind, "christian"))
                    add(AndroidPoiIcons.resolvePoiIconId(kind, null))
                } else {
                    add(AndroidPoiIcons.resolvePoiIconId(kind))
                }
            }
        }
        assertEquals(AndroidPoiIcons.ICON_SPECS.map { it.id }.toSet(), coveredIconIds)

        val noiseKinds = listOf(
            "administrative",
            "tree",
            "crossing",
            "traffic_signals",
            "signal",
            "stop",
            "street_lamp",
            "switch",
            "construction",
            "meadow",
            "water",
            "yes",
        )
        for (noise in noiseKinds) {
            assertFalse(noise in allTierKinds, "Noise kind '$noise' must not match any POI tier")
        }
    }
}
