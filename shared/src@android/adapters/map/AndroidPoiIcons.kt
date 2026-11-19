package com.muhammadridha.fjalar.adapters.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.DisplayMetrics
import org.maplibre.android.style.expressions.Expression
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Self-contained OSM Carto-style POI icon catalog, zoom-tier kind lists, and MapLibre style
 * expression builders for Android.
 *
 * Uses [DisplayMetrics.DENSITY_DEFAULT] on generated `48x48 px` bitmaps so MapLibre treats every
 * sprite at `pixelRatio = 1.0`, making `iconSize` values (`0.42`, `0.36`, `0.34`) consistent
 * physical dp sizes across all screen densities.
 */
object AndroidPoiIcons {

    const val ICON_SIZE_PX = 48

    const val POI_ICON_MOSQUE = "fjalar-poi-mosque"
    const val POI_ICON_CHURCH = "fjalar-poi-church"
    const val POI_ICON_WORSHIP = "fjalar-poi-worship"
    const val POI_ICON_HOSPITAL = "fjalar-poi-hospital"
    const val POI_ICON_CLINIC = "fjalar-poi-clinic"
    const val POI_ICON_PHARMACY = "fjalar-poi-pharmacy"
    const val POI_ICON_TRANSIT_RAIL = "fjalar-poi-transit-rail"
    const val POI_ICON_BUS = "fjalar-poi-bus"
    const val POI_ICON_FERRY = "fjalar-poi-ferry"
    const val POI_ICON_AIRPORT = "fjalar-poi-airport"
    const val POI_ICON_SCHOOL = "fjalar-poi-school"
    const val POI_ICON_POST = "fjalar-poi-post"
    const val POI_ICON_CIVIC = "fjalar-poi-civic"
    const val POI_ICON_ATM = "fjalar-poi-atm"
    const val POI_ICON_PARKING = "fjalar-poi-parking"
    const val POI_ICON_FUEL = "fjalar-poi-fuel"
    const val POI_ICON_FOOD = "fjalar-poi-food"
    const val POI_ICON_CAFE = "fjalar-poi-cafe"
    const val POI_ICON_HOTEL = "fjalar-poi-hotel"
    const val POI_ICON_PET = "fjalar-poi-pet"
    const val POI_ICON_SHOP = "fjalar-poi-shop"
    const val POI_ICON_PARK = "fjalar-poi-park"
    const val POI_ICON_LANDMARK = "fjalar-poi-landmark"

    const val LIGHT_HALO_HEX = "#F5F5F3"
    const val DARK_HALO_HEX = "#181818"

    data class PoiIconThemeSpec(
        val id: String,
        val lightColorHex: String,
        val darkColorHex: String,
    )

    val MAJOR_TIER_KINDS: List<String> = listOf(
        "station",
        "railway_station",
        "subway_entrance",
        "train_station_entrance",
        "light_rail",
        "bus_station",
        "ferry_terminal",
        "aerodrome",
        "airport",
        "hospital",
        "university",
        "mall",
        "stadium",
    )

    val NEIGHBORHOOD_TIER_KINDS: List<String> = listOf(
        "bus_stop",
        "tram_stop",
        "school",
        "college",
        "kindergarten",
        "clinic",
        "dentist",
        "doctors",
        "pharmacy",
        "place_of_worship",
        "supermarket",
        "department_store",
        "marketplace",
        "hotel",
        "hostel",
        "guest_house",
        "motel",
        "bank",
        "atm",
        "bureau_de_change",
        "government",
        "townhall",
        "police",
        "fire_station",
        "post_office",
        "library",
        "museum",
        "community_centre",
        "park",
        "sports_centre",
        "parking",
        "fuel",
    )

    val LOCAL_TIER_KINDS: List<String> = listOf(
        "restaurant",
        "fast_food",
        "food_court",
        "bar",
        "pub",
        "cafe",
        "bakery",
        "convenience",
        "pet",
        "clothes",
        "books",
        "chemist",
        "florist",
        "hairdresser",
        "laundry",
        "furniture",
        "optician",
        "copyshop",
        "car",
        "car_repair",
        "car_parts",
        "car_rental",
        "variety_store",
        "kiosk",
        "doityourself",
        "general",
        "cinema",
        "gallery",
        "theatre",
        "monument",
        "memorial",
        "attraction",
        "viewpoint",
        "fitness_centre",
        "playground",
        "swimming_pool",
        "apartment",
        "residential",
        "commercial",
    )

    val ICON_SPECS: List<PoiIconThemeSpec> = listOf(
        PoiIconThemeSpec(POI_ICON_MOSQUE, "#555555", "#E0E0E0"),
        PoiIconThemeSpec(POI_ICON_CHURCH, "#555555", "#E0E0E0"),
        PoiIconThemeSpec(POI_ICON_WORSHIP, "#555555", "#E0E0E0"),
        PoiIconThemeSpec(POI_ICON_HOSPITAL, "#BF0000", "#FF6B6B"),
        PoiIconThemeSpec(POI_ICON_CLINIC, "#BF0000", "#FF6B6B"),
        PoiIconThemeSpec(POI_ICON_PHARMACY, "#BF0000", "#FF6B6B"),
        PoiIconThemeSpec(POI_ICON_TRANSIT_RAIL, "#0072B2", "#4DC3FF"),
        PoiIconThemeSpec(POI_ICON_BUS, "#0072B2", "#4DC3FF"),
        PoiIconThemeSpec(POI_ICON_FERRY, "#0072B2", "#4DC3FF"),
        PoiIconThemeSpec(POI_ICON_AIRPORT, "#5E35B1", "#9E8CFF"),
        PoiIconThemeSpec(POI_ICON_SCHOOL, "#555555", "#D4D4D4"),
        PoiIconThemeSpec(POI_ICON_POST, "#734A08", "#E0B070"),
        PoiIconThemeSpec(POI_ICON_CIVIC, "#555555", "#D4D4D4"),
        PoiIconThemeSpec(POI_ICON_ATM, "#734A08", "#E0B070"),
        PoiIconThemeSpec(POI_ICON_PARKING, "#0072B2", "#4DC3FF"),
        PoiIconThemeSpec(POI_ICON_FUEL, "#0072B2", "#4DC3FF"),
        PoiIconThemeSpec(POI_ICON_FOOD, "#C77400", "#FFB74D"),
        PoiIconThemeSpec(POI_ICON_CAFE, "#C77400", "#FFB74D"),
        PoiIconThemeSpec(POI_ICON_HOTEL, "#0072B2", "#4DC3FF"),
        PoiIconThemeSpec(POI_ICON_PET, "#AC39AC", "#E07BE0"),
        PoiIconThemeSpec(POI_ICON_SHOP, "#AC39AC", "#E07BE0"),
        PoiIconThemeSpec(POI_ICON_PARK, "#2E7D32", "#66BB6A"),
        PoiIconThemeSpec(POI_ICON_LANDMARK, "#666666", "#BDBDBD"),
    )

    private val ICON_SPECS_BY_ID: Map<String, PoiIconThemeSpec> =
        ICON_SPECS.associateBy { it.id }

    private val KIND_TO_ICON_ID: Map<String, String> = linkedMapOf(
        "hospital" to POI_ICON_HOSPITAL,
        "clinic" to POI_ICON_CLINIC,
        "dentist" to POI_ICON_CLINIC,
        "doctors" to POI_ICON_CLINIC,
        "pharmacy" to POI_ICON_PHARMACY,
        "chemist" to POI_ICON_PHARMACY,
        "station" to POI_ICON_TRANSIT_RAIL,
        "railway_station" to POI_ICON_TRANSIT_RAIL,
        "subway_entrance" to POI_ICON_TRANSIT_RAIL,
        "train_station_entrance" to POI_ICON_TRANSIT_RAIL,
        "light_rail" to POI_ICON_TRANSIT_RAIL,
        "bus_station" to POI_ICON_BUS,
        "bus_stop" to POI_ICON_BUS,
        "tram_stop" to POI_ICON_BUS,
        "ferry_terminal" to POI_ICON_FERRY,
        "aerodrome" to POI_ICON_AIRPORT,
        "airport" to POI_ICON_AIRPORT,
        "school" to POI_ICON_SCHOOL,
        "university" to POI_ICON_SCHOOL,
        "college" to POI_ICON_SCHOOL,
        "kindergarten" to POI_ICON_SCHOOL,
        "post_office" to POI_ICON_POST,
        "government" to POI_ICON_CIVIC,
        "townhall" to POI_ICON_CIVIC,
        "police" to POI_ICON_CIVIC,
        "fire_station" to POI_ICON_CIVIC,
        "library" to POI_ICON_CIVIC,
        "museum" to POI_ICON_CIVIC,
        "community_centre" to POI_ICON_CIVIC,
        "atm" to POI_ICON_ATM,
        "bank" to POI_ICON_ATM,
        "bureau_de_change" to POI_ICON_ATM,
        "parking" to POI_ICON_PARKING,
        "fuel" to POI_ICON_FUEL,
        "restaurant" to POI_ICON_FOOD,
        "fast_food" to POI_ICON_FOOD,
        "food_court" to POI_ICON_FOOD,
        "bar" to POI_ICON_FOOD,
        "pub" to POI_ICON_FOOD,
        "cafe" to POI_ICON_CAFE,
        "bakery" to POI_ICON_CAFE,
        "hotel" to POI_ICON_HOTEL,
        "hostel" to POI_ICON_HOTEL,
        "guest_house" to POI_ICON_HOTEL,
        "motel" to POI_ICON_HOTEL,
        "pet" to POI_ICON_PET,
        "mall" to POI_ICON_SHOP,
        "supermarket" to POI_ICON_SHOP,
        "department_store" to POI_ICON_SHOP,
        "marketplace" to POI_ICON_SHOP,
        "convenience" to POI_ICON_SHOP,
        "clothes" to POI_ICON_SHOP,
        "books" to POI_ICON_SHOP,
        "florist" to POI_ICON_SHOP,
        "hairdresser" to POI_ICON_SHOP,
        "laundry" to POI_ICON_SHOP,
        "furniture" to POI_ICON_SHOP,
        "optician" to POI_ICON_SHOP,
        "copyshop" to POI_ICON_SHOP,
        "car" to POI_ICON_SHOP,
        "car_repair" to POI_ICON_SHOP,
        "car_parts" to POI_ICON_SHOP,
        "car_rental" to POI_ICON_SHOP,
        "variety_store" to POI_ICON_SHOP,
        "kiosk" to POI_ICON_SHOP,
        "doityourself" to POI_ICON_SHOP,
        "general" to POI_ICON_SHOP,
        "park" to POI_ICON_PARK,
        "stadium" to POI_ICON_PARK,
        "sports_centre" to POI_ICON_PARK,
        "fitness_centre" to POI_ICON_PARK,
        "playground" to POI_ICON_PARK,
        "swimming_pool" to POI_ICON_PARK,
        "cinema" to POI_ICON_LANDMARK,
        "gallery" to POI_ICON_LANDMARK,
        "theatre" to POI_ICON_LANDMARK,
        "monument" to POI_ICON_LANDMARK,
        "memorial" to POI_ICON_LANDMARK,
        "attraction" to POI_ICON_LANDMARK,
        "viewpoint" to POI_ICON_LANDMARK,
        "apartment" to POI_ICON_LANDMARK,
        "residential" to POI_ICON_LANDMARK,
        "commercial" to POI_ICON_LANDMARK,
    )

    fun textHaloColorHex(isDarkTheme: Boolean): String =
        if (isDarkTheme) DARK_HALO_HEX else LIGHT_HALO_HEX

    /**
     * Resolves the sprite image ID in `createPoiIconBitmaps` for an OSM/Protomaps POI `kind`
     * and optional `kind_detail`.
     */
    fun resolvePoiIconId(kind: String, kindDetail: String? = null): String {
        if (kind == "place_of_worship") {
            return when (kindDetail) {
                "muslim" -> POI_ICON_MOSQUE
                "christian" -> POI_ICON_CHURCH
                else -> POI_ICON_WORSHIP
            }
        }
        return KIND_TO_ICON_ID[kind] ?: POI_ICON_LANDMARK
    }

    /**
     * Returns the OSM Carto category hex color string for a POI `kind` under Light or Dark theme.
     */
    fun resolvePoiTextColorHex(kind: String, isDarkTheme: Boolean): String {
        val iconId = resolvePoiIconId(kind, null)
        val spec = ICON_SPECS_BY_ID.getValue(iconId)
        return if (isDarkTheme) spec.darkColorHex else spec.lightColorHex
    }

    /** Builds the MapLibre filter expression JSON for a zoom-tier list of POI `kind` values. */
    fun tierFilterJson(kinds: List<String>): String {
        val literals = kinds.joinToString(",") { "\"$it\"" }
        return """["all",["has","name"],["in",["get","kind"],["literal",[$literals]]]]"""
    }

    /** Builds a MapLibre `Expression` matching `(kind, kind_detail)` to our icon sprite IDs. */
    fun iconImageExpressionJson(): String = buildString {
        append("""["match",["get","kind"],""")
        append(""""place_of_worship",["match",["coalesce",["get","kind_detail"],""],"muslim","$POI_ICON_MOSQUE","christian","$POI_ICON_CHURCH","$POI_ICON_WORSHIP"],""")
        for ((kind, iconId) in KIND_TO_ICON_ID) {
            append("\"$kind\",\"$iconId\",")
        }
        append("\"$POI_ICON_LANDMARK\"]")
    }

    fun iconImageExpression(): Expression = Expression.raw(iconImageExpressionJson())

    /** Builds a MapLibre `Expression` matching POI `kind` to OSM Carto category text colors. */
    fun textColorExpressionJson(isDarkTheme: Boolean): String = buildString {
        append("""["match",["get","kind"],""")
        append(""""place_of_worship","${resolvePoiTextColorHex("place_of_worship", isDarkTheme)}",""")
        for (kind in KIND_TO_ICON_ID.keys) {
            append("\"$kind\",\"${resolvePoiTextColorHex(kind, isDarkTheme)}\",")
        }
        append("\"${resolvePoiTextColorHex("", isDarkTheme)}\"]")
    }

    fun textColorExpression(isDarkTheme: Boolean): Expression =
        Expression.raw(textColorExpressionJson(isDarkTheme))

    /** Generates all 23 OSM Carto-style `48x48 px` POI bitmaps with Light or Dark halo contrast. */
    fun createPoiIconBitmaps(isDarkTheme: Boolean): Map<String, Bitmap> {
        val haloColor = Color.parseColor(textHaloColorHex(isDarkTheme))
        val badgeGlyphColor = if (isDarkTheme) haloColor else Color.WHITE
        val bitmaps = LinkedHashMap<String, Bitmap>(ICON_SPECS.size)
        for (spec in ICON_SPECS) {
            val fgColor = Color.parseColor(if (isDarkTheme) spec.darkColorHex else spec.lightColorHex)
            bitmaps[spec.id] = renderIconBitmap(spec.id, fgColor, haloColor, badgeGlyphColor)
        }
        return bitmaps
    }

    private fun renderIconBitmap(
        iconId: String,
        fgColor: Int,
        haloColor: Int,
        badgeGlyphColor: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        bitmap.density = DisplayMetrics.DENSITY_DEFAULT
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fgColor
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = haloColor
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val innerWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = haloColor
        }
        val innerWhiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = haloColor
            strokeWidth = 3.2f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        when (iconId) {
            POI_ICON_MOSQUE -> drawMosque(canvas, fgColor, haloColor)
            POI_ICON_CHURCH -> drawLatinCross(canvas, fillPaint, haloPaint)
            POI_ICON_WORSHIP -> drawTempleArch(canvas, fillPaint, haloPaint, innerWhitePaint)
            POI_ICON_HOSPITAL -> drawHospitalBadge(canvas, fillPaint, haloPaint, badgeGlyphColor)
            POI_ICON_CLINIC -> drawMedicalCross(canvas, fillPaint, haloPaint)
            POI_ICON_PHARMACY -> drawPharmacyBadge(canvas, fillPaint, haloPaint, badgeGlyphColor)
            POI_ICON_TRANSIT_RAIL -> drawTrain(canvas, fillPaint, haloPaint, fgColor, haloColor)
            POI_ICON_BUS -> drawBus(canvas, fillPaint, haloPaint, haloColor)
            POI_ICON_FERRY -> drawAnchor(canvas, fillPaint, haloPaint, fgColor, haloColor)
            POI_ICON_AIRPORT -> drawAirplane(canvas, fillPaint, haloPaint)
            POI_ICON_SCHOOL -> drawGraduationCap(canvas, fillPaint, haloPaint, fgColor, haloColor)
            POI_ICON_POST -> drawEnvelope(canvas, fillPaint, haloPaint, innerWhiteStroke)
            POI_ICON_CIVIC -> drawCivicBuilding(canvas, fillPaint, haloPaint, innerWhitePaint)
            POI_ICON_ATM -> drawAtmCard(canvas, fillPaint, haloPaint, innerWhitePaint, fgColor)
            POI_ICON_PARKING -> drawParkingBadge(canvas, fillPaint, haloPaint, badgeGlyphColor)
            POI_ICON_FUEL -> drawFuelPump(canvas, fillPaint, haloPaint, fgColor, haloColor)
            POI_ICON_FOOD -> drawForkAndKnife(canvas, fillPaint, haloPaint, haloColor)
            POI_ICON_CAFE -> drawCoffeeCup(canvas, fillPaint, haloPaint, fgColor)
            POI_ICON_HOTEL -> drawBed(canvas, fillPaint, haloPaint)
            POI_ICON_PET -> drawPawPrint(canvas, fillPaint, haloPaint)
            POI_ICON_SHOP -> drawShoppingBag(canvas, fillPaint, haloPaint, fgColor)
            POI_ICON_PARK -> drawTree(canvas, fillPaint, haloPaint)
            else -> drawLandmarkDiamond(canvas, fillPaint, haloPaint, innerWhitePaint)
        }
        return bitmap
    }

    private fun drawHaloAndFill(canvas: Canvas, path: Path, fillPaint: Paint, haloPaint: Paint) {
        canvas.drawPath(path, haloPaint)
        canvas.drawPath(path, fillPaint)
    }

    private fun drawMosque(canvas: Canvas, fgColor: Int, haloColor: Int) {
        val outer = Path().apply { addCircle(22f, 24f, 15.5f, Path.Direction.CW) }
        val inner = Path().apply { addCircle(27.5f, 22f, 12.5f, Path.Direction.CW) }
        val crescent = Path().apply { op(outer, inner, Path.Op.DIFFERENCE) }

        val star = buildStarPath(cx = 34.5f, cy = 19f, outerR = 6.2f, innerR = 2.6f)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = haloColor
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fgColor
        }
        canvas.drawPath(crescent, halo)
        canvas.drawPath(star, halo)
        canvas.drawPath(crescent, fill)
        canvas.drawPath(star, fill)
    }

    private fun buildStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float): Path =
        Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val angle = (-90.0 + i * 36.0) * PI / 180.0
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

    private fun drawLatinCross(canvas: Canvas, fillPaint: Paint, haloPaint: Paint) {
        val cross = Path().apply {
            addRoundRect(RectF(20.5f, 6f, 27.5f, 42f), 1.5f, 1.5f, Path.Direction.CW)
            addRoundRect(RectF(11f, 14f, 37f, 21f), 1.5f, 1.5f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, cross, fillPaint, haloPaint)
    }

    private fun drawTempleArch(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        cutoutPaint: Paint,
    ) {
        val outer = Path().apply {
            moveTo(24f, 6f)
            quadTo(37f, 11f, 37f, 22f)
            lineTo(39f, 22f)
            lineTo(39f, 38f)
            lineTo(42f, 41f)
            lineTo(6f, 41f)
            lineTo(9f, 38f)
            lineTo(9f, 22f)
            lineTo(11f, 22f)
            quadTo(11f, 11f, 24f, 6f)
            close()
        }
        drawHaloAndFill(canvas, outer, fillPaint, haloPaint)
        val arch = Path().apply {
            moveTo(19f, 39f)
            lineTo(19f, 27f)
            quadTo(24f, 21f, 29f, 27f)
            lineTo(29f, 39f)
            close()
        }
        canvas.drawPath(arch, cutoutPaint)
    }

    private fun drawHospitalBadge(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        glyphColor: Int,
    ) {
        val badge = Path().apply {
            addRoundRect(RectF(7f, 7f, 41f, 41f), 7f, 7f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, badge, fillPaint, haloPaint)
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = glyphColor
        }
        canvas.drawRect(14.5f, 13f, 19.5f, 35f, glyph)
        canvas.drawRect(28.5f, 13f, 33.5f, 35f, glyph)
        canvas.drawRect(14.5f, 21.5f, 33.5f, 26.5f, glyph)
    }

    private fun drawMedicalCross(canvas: Canvas, fillPaint: Paint, haloPaint: Paint) {
        val cross = Path().apply {
            addRoundRect(RectF(19f, 7f, 29f, 41f), 2f, 2f, Path.Direction.CW)
            addRoundRect(RectF(7f, 19f, 41f, 29f), 2f, 2f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, cross, fillPaint, haloPaint)
    }

    private fun drawPharmacyBadge(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        glyphColor: Int,
    ) {
        val badge = Path().apply {
            addRoundRect(RectF(7f, 7f, 41f, 41f), 11f, 11f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, badge, fillPaint, haloPaint)
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = glyphColor
            strokeWidth = 3.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Stylized R + x mark
        val rxPath = Path().apply {
            // R stem and loop
            moveTo(13.5f, 31f)
            lineTo(13.5f, 14f)
            lineTo(20f, 14f)
            cubicTo(24.5f, 14f, 24.5f, 22.5f, 20f, 22.5f)
            lineTo(13.5f, 22.5f)
            // R leg extending down-right into X crossbar
            moveTo(18.5f, 22.5f)
            lineTo(31f, 34.5f)
            moveTo(31f, 24.5f)
            lineTo(22.5f, 34.5f)
        }
        canvas.drawPath(rxPath, glyph)
    }

    private fun drawTrain(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        fgColor: Int,
        haloColor: Int,
    ) {
        val body = Path().apply {
            addRoundRect(RectF(12f, 9f, 36f, 35f), 5f, 5f, Path.Direction.CW)
        }
        val railStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = fgColor
            strokeWidth = 3.2f
            strokeCap = Paint.Cap.ROUND
        }
        val tracks = Path().apply {
            moveTo(19f, 6.5f)
            lineTo(29f, 6.5f)
            moveTo(17f, 35f)
            lineTo(12.5f, 42.5f)
            moveTo(31f, 35f)
            lineTo(35.5f, 42.5f)
            moveTo(14.5f, 39.5f)
            lineTo(33.5f, 39.5f)
        }
        canvas.drawPath(tracks, haloPaint)
        canvas.drawPath(body, haloPaint)
        canvas.drawPath(tracks, railStroke)
        canvas.drawPath(body, fillPaint)

        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = haloColor
        }
        canvas.drawRoundRect(RectF(15.5f, 13f, 32.5f, 22f), 2.5f, 2.5f, windowPaint)
        canvas.drawCircle(17.5f, 29f, 2.3f, windowPaint)
        canvas.drawCircle(30.5f, 29f, 2.3f, windowPaint)
    }

    private fun drawBus(canvas: Canvas, fillPaint: Paint, haloPaint: Paint, haloColor: Int) {
        val bus = Path().apply {
            addRoundRect(RectF(10f, 8f, 38f, 36f), 4.5f, 4.5f, Path.Direction.CW)
            addRect(RectF(13f, 36f, 18f, 41f), Path.Direction.CW)
            addRect(RectF(30f, 36f, 35f, 41f), Path.Direction.CW)
            addRect(RectF(6.5f, 14f, 10f, 22f), Path.Direction.CW)
            addRect(RectF(38f, 14f, 41.5f, 22f), Path.Direction.CW)
        }
        drawHaloAndFill(canvas, bus, fillPaint, haloPaint)
        val cutout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = haloColor
        }
        canvas.drawRoundRect(RectF(13.5f, 12.5f, 34.5f, 23f), 2f, 2f, cutout)
        canvas.drawCircle(16f, 30f, 2.2f, cutout)
        canvas.drawCircle(32f, 30f, 2.2f, cutout)
    }

    private fun drawAnchor(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        fgColor: Int,
        haloColor: Int,
    ) {
        val anchorStroke = Path().apply {
            moveTo(24f, 12f)
            lineTo(24f, 39f)
            moveTo(15f, 18.5f)
            lineTo(33f, 18.5f)
            moveTo(10f, 28f)
            quadTo(11f, 39f, 24f, 39f)
            quadTo(37f, 39f, 38f, 28f)
        }
        val ring = Path().apply { addCircle(24f, 10f, 3.8f, Path.Direction.CW) }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = fgColor
            strokeWidth = 3.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val haloStroke = Paint(haloPaint).apply { strokeWidth = 7.2f }
        canvas.drawPath(anchorStroke, haloStroke)
        canvas.drawPath(ring, haloPaint)
        canvas.drawPath(anchorStroke, strokePaint)
        canvas.drawPath(ring, fillPaint)
        canvas.drawCircle(
            24f,
            10f,
            1.6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = haloColor },
        )
    }

    private fun drawAirplane(canvas: Canvas, fillPaint: Paint, haloPaint: Paint) {
        val plane = Path().apply {
            moveTo(24f, 6f)
            lineTo(27f, 10f)
            lineTo(27f, 18.5f)
            lineTo(41f, 27f)
            lineTo(41f, 31f)
            lineTo(27f, 26f)
            lineTo(27f, 35.5f)
            lineTo(32.5f, 39.5f)
            lineTo(32.5f, 42f)
            lineTo(24f, 39.5f)
            lineTo(15.5f, 42f)
            lineTo(15.5f, 39.5f)
            lineTo(21f, 35.5f)
            lineTo(21f, 26f)
            lineTo(7f, 31f)
            lineTo(7f, 27f)
            lineTo(21f, 18.5f)
            lineTo(21f, 10f)
            close()
        }
        drawHaloAndFill(canvas, plane, fillPaint, haloPaint)
    }

    private fun drawGraduationCap(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        fgColor: Int,
        haloColor: Int,
    ) {
        val capTop = Path().apply {
            moveTo(24f, 10f)
            lineTo(42f, 18.5f)
            lineTo(24f, 27f)
            lineTo(6f, 18.5f)
            close()
        }
        val bowl = Path().apply {
            moveTo(13.5f, 23.5f)
            lineTo(24f, 28.5f)
            lineTo(34.5f, 23.5f)
            lineTo(34.5f, 31.5f)
            quadTo(24f, 37.5f, 13.5f, 31.5f)
            close()
        }
        val tassel = Path().apply {
            moveTo(39f, 19.5f)
            lineTo(39f, 34f)
        }
        val tasselPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = fgColor
            strokeWidth = 2.6f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(capTop, haloPaint)
        canvas.drawPath(bowl, haloPaint)
        canvas.drawPath(tassel, haloPaint)
        canvas.drawPath(bowl, fillPaint)
        canvas.drawPath(capTop, fillPaint)
        // Thin gap separator between cap top and bowl
        canvas.drawLine(
            13f,
            23f,
            24f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = haloColor
                strokeWidth = 1.6f
            },
        )
        canvas.drawPath(tassel, tasselPaint)
        canvas.drawCircle(39f, 35.5f, 2.2f, fillPaint)
    }

    private fun drawEnvelope(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        innerStroke: Paint,
    ) {
        val env = Path().apply {
            addRoundRect(RectF(7f, 12f, 41f, 36f), 3f, 3f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, env, fillPaint, haloPaint)
        val flap = Path().apply {
            moveTo(8f, 13.5f)
            lineTo(24f, 26f)
            lineTo(40f, 13.5f)
        }
        canvas.drawPath(flap, innerStroke)
    }

    private fun drawCivicBuilding(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        innerPaint: Paint,
    ) {
        val building = Path().apply {
            moveTo(24f, 7f)
            lineTo(41f, 16f)
            lineTo(7f, 16f)
            close()
            addRect(RectF(9f, 17.5f, 39f, 35f), Path.Direction.CW)
            addRect(RectF(6.5f, 36f, 41.5f, 41f), Path.Direction.CW)
        }
        drawHaloAndFill(canvas, building, fillPaint, haloPaint)
        // Carve gaps between 3 columns
        canvas.drawRect(RectF(15f, 19f, 21f, 34f), innerPaint)
        canvas.drawRect(RectF(27f, 19f, 33f, 34f), innerPaint)
    }

    private fun drawAtmCard(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        innerPaint: Paint,
        fgColor: Int,
    ) {
        val card = Path().apply {
            addRoundRect(RectF(7f, 11f, 41f, 37f), 4f, 4f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, card, fillPaint, haloPaint)
        canvas.drawRect(RectF(7.5f, 16.5f, 40.5f, 21f), innerPaint)
        canvas.drawCircle(24f, 29f, 4.5f, innerPaint)
        canvas.drawCircle(
            24f,
            29f,
            2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgColor },
        )
    }

    private fun drawParkingBadge(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        glyphColor: Int,
    ) {
        val badge = Path().apply {
            addRoundRect(RectF(8f, 8f, 40f, 40f), 7f, 7f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, badge, fillPaint, haloPaint)
        val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = glyphColor
            strokeWidth = 4.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val letterP = Path().apply {
            moveTo(18f, 34f)
            lineTo(18f, 14f)
            lineTo(26.5f, 14f)
            cubicTo(32.5f, 14f, 32.5f, 24.5f, 26.5f, 24.5f)
            lineTo(18f, 24.5f)
        }
        canvas.drawPath(letterP, glyphStroke)
    }

    private fun drawFuelPump(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        fgColor: Int,
        haloColor: Int,
    ) {
        val pumpBody = Path().apply {
            addRoundRect(RectF(10f, 8f, 31f, 41f), 3.5f, 3.5f, Path.Direction.CW)
        }
        val hose = Path().apply {
            moveTo(31f, 18f)
            lineTo(35.5f, 18f)
            lineTo(37.5f, 22f)
            lineTo(37.5f, 35f)
            quadTo(37.5f, 39f, 33f, 39f)
            lineTo(31f, 39f)
            moveTo(33.5f, 12f)
            lineTo(39f, 17.5f)
        }
        val hosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = fgColor
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(pumpBody, haloPaint)
        canvas.drawPath(hose, haloPaint)
        canvas.drawPath(pumpBody, fillPaint)
        canvas.drawPath(hose, hosePaint)
        canvas.drawRoundRect(
            RectF(14f, 12.5f, 27f, 21f),
            1.5f,
            1.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = haloColor },
        )
    }

    private fun drawForkAndKnife(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        haloColor: Int,
    ) {
        val cutlery = Path().apply {
            // Fork tines & stem (left)
            addRect(RectF(11.5f, 7f, 14f, 19f), Path.Direction.CW)
            addRect(RectF(15.5f, 7f, 18f, 19f), Path.Direction.CW)
            addRect(RectF(19.5f, 7f, 22f, 19f), Path.Direction.CW)
            addRoundRect(RectF(11.5f, 17f, 22f, 22.5f), 2f, 2f, Path.Direction.CW)
            addRoundRect(RectF(15.2f, 21.5f, 18.3f, 41f), 1.5f, 1.5f, Path.Direction.CW)
            // Knife blade & handle (right)
            moveTo(31f, 7f)
            cubicTo(36.5f, 7f, 37.5f, 18f, 36.5f, 24.5f)
            lineTo(33f, 24.5f)
            lineTo(33f, 41f)
            lineTo(29.8f, 41f)
            lineTo(29.8f, 7f)
            close()
        }
        drawHaloAndFill(canvas, cutlery, fillPaint, haloPaint)
        // Re-assert fine fork tine spacing on tight scaling
        val gap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = haloColor
            strokeWidth = 1.2f
        }
        canvas.drawLine(14.75f, 7f, 14.75f, 17f, gap)
        canvas.drawLine(18.75f, 7f, 18.75f, 17f, gap)
    }

    private fun drawCoffeeCup(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        fgColor: Int,
    ) {
        val cup = Path().apply {
            moveTo(10f, 19f)
            lineTo(33f, 19f)
            lineTo(33f, 28f)
            quadTo(33f, 36f, 21.5f, 36f)
            quadTo(10f, 36f, 10f, 28f)
            close()
            addRoundRect(RectF(8f, 38f, 35f, 41f), 1.5f, 1.5f, Path.Direction.CW)
        }
        val handleAndSteam = Path().apply {
            // C-handle
            moveTo(33f, 21f)
            cubicTo(40f, 21f, 40f, 31f, 32f, 31f)
            // Two subtle steam curls
            moveTo(17.5f, 15.5f)
            quadTo(16f, 12f, 18.5f, 8.5f)
            moveTo(25f, 15.5f)
            quadTo(23.5f, 12f, 26f, 8.5f)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = fgColor
            strokeWidth = 2.8f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(cup, haloPaint)
        canvas.drawPath(handleAndSteam, haloPaint)
        canvas.drawPath(handleAndSteam, strokePaint)
        canvas.drawPath(cup, fillPaint)
    }

    private fun drawBed(canvas: Canvas, fillPaint: Paint, haloPaint: Paint) {
        val bed = Path().apply {
            // Left headboard & leg
            addRoundRect(RectF(7f, 13f, 11.5f, 38f), 1.5f, 1.5f, Path.Direction.CW)
            // Pillow
            addRoundRect(RectF(13f, 17.5f, 22f, 24f), 2.5f, 2.5f, Path.Direction.CW)
            // Blanket / mattress body
            addRoundRect(RectF(13f, 24.5f, 36.5f, 32f), 2f, 2f, Path.Direction.CW)
            // Mattress frame base & right footboard
            addRoundRect(RectF(7f, 29f, 41f, 34f), 1.5f, 1.5f, Path.Direction.CW)
            addRoundRect(RectF(36.5f, 22f, 41f, 38f), 1.5f, 1.5f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, bed, fillPaint, haloPaint)
    }

    private fun drawPawPrint(canvas: Canvas, fillPaint: Paint, haloPaint: Paint) {
        val paw = Path().apply {
            // Four upper toe pads
            addOval(RectF(9.5f, 15f, 16.5f, 24f), Path.Direction.CW)
            addOval(RectF(16.5f, 8.5f, 23.5f, 18.5f), Path.Direction.CW)
            addOval(RectF(24.5f, 8.5f, 31.5f, 18.5f), Path.Direction.CW)
            addOval(RectF(31.5f, 15f, 38.5f, 24f), Path.Direction.CW)
            // Main central metacarpal pad
            moveTo(24f, 22f)
            cubicTo(31f, 22f, 36.5f, 31f, 34.5f, 37.5f)
            cubicTo(33f, 40.5f, 27.5f, 39.5f, 24f, 38.5f)
            cubicTo(20.5f, 39.5f, 15f, 40.5f, 13.5f, 37.5f)
            cubicTo(11.5f, 31f, 17f, 22f, 24f, 22f)
            close()
        }
        drawHaloAndFill(canvas, paw, fillPaint, haloPaint)
    }

    private fun drawShoppingBag(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        fgColor: Int,
    ) {
        val bag = Path().apply {
            moveTo(11.5f, 17.5f)
            lineTo(36.5f, 17.5f)
            lineTo(38.5f, 38.5f)
            quadTo(38.5f, 41f, 36f, 41f)
            lineTo(12f, 41f)
            quadTo(9.5f, 41f, 9.5f, 38.5f)
            close()
        }
        val handle = Path().apply {
            moveTo(17.5f, 18.5f)
            cubicTo(17.5f, 8f, 30.5f, 8f, 30.5f, 18.5f)
        }
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = fgColor
            strokeWidth = 3.2f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(handle, haloPaint)
        canvas.drawPath(bag, haloPaint)
        canvas.drawPath(handle, handlePaint)
        canvas.drawPath(bag, fillPaint)
    }

    private fun drawTree(canvas: Canvas, fillPaint: Paint, haloPaint: Paint) {
        val tree = Path().apply {
            // Leafy cloud canopy
            addCircle(24f, 16f, 9f, Path.Direction.CW)
            addCircle(17.5f, 22.5f, 8f, Path.Direction.CW)
            addCircle(30.5f, 22.5f, 8f, Path.Direction.CW)
            // Trunk
            addRoundRect(RectF(21.5f, 27f, 26.5f, 42f), 1.5f, 1.5f, Path.Direction.CW)
        }
        drawHaloAndFill(canvas, tree, fillPaint, haloPaint)
    }

    private fun drawLandmarkDiamond(
        canvas: Canvas,
        fillPaint: Paint,
        haloPaint: Paint,
        innerPaint: Paint,
    ) {
        val diamond = Path().apply {
            moveTo(24f, 7f)
            lineTo(39f, 24f)
            lineTo(24f, 41f)
            lineTo(9f, 24f)
            close()
        }
        drawHaloAndFill(canvas, diamond, fillPaint, haloPaint)
        canvas.drawCircle(24f, 24f, 4.5f, innerPaint)
    }
}
