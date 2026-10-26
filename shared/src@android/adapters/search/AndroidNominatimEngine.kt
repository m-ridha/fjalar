package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Android implementation of [OnlineGeocodingEngine] backed by OpenStreetMap Nominatim.
 * Uses Android platform standard library (HttpURLConnection, org.json) with zero external networking dependencies.
 */
class AndroidNominatimEngine(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : OnlineGeocodingEngine {

    override suspend fun geocode(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates?,
        limit: Int,
    ): List<SearchResultPlace> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH || limit <= 0) {
            return@withContext emptyList()
        }

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val langCode = locale.tag
            val urlString = "$baseUrl/search?q=$encodedQuery&format=json&addressdetails=1&limit=$limit"

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Language", langCode)
                setRequestProperty("Accept", "application/json")
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    return@withContext emptyList()
                }

                val responseBody = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }

                parseNominatimResponse(responseBody, limit)
            } finally {
                connection.disconnect()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseNominatimResponse(jsonText: String, limit: Int): List<SearchResultPlace> {
        val array = JSONArray(jsonText)
        val results = mutableListOf<SearchResultPlace>()

        for (i in 0 until array.length()) {
            if (results.size >= limit) break
            val obj = array.optJSONObject(i) ?: continue

            val placeId = obj.optLong("place_id", -1L).takeIf { it != -1L }?.toString()
                ?: obj.optString("osm_id", "").takeIf { it.isNotEmpty() }
                ?: "osm_$i"

            val displayName = obj.optString("display_name", "").trim()
            if (displayName.isEmpty()) continue

            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                continue
            }

            val categoryClass = obj.optString("class", "")
            val type = obj.optString("type", "")
            val category = resolveCategory(categoryClass, type)

            // Extract primary name (first token of display_name) and detail (subsequent tokens)
            val parts = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val name = parts.firstOrNull() ?: displayName
            val detail = if (parts.size > 1) {
                parts.drop(1).take(3).joinToString(", ")
            } else {
                null
            }

            results.add(
                SearchResultPlace(
                    id = "osm_$placeId",
                    name = name,
                    coordinates = GeoCoordinates(latitude = lat, longitude = lon),
                    category = category,
                    detail = detail,
                )
            )
        }

        return results
    }

    private fun resolveCategory(categoryClass: String, type: String): PlaceCategory = when {
        type in listOf("subway", "subway_entrance", "metro_station") -> PlaceCategory.METRO_STATION
        categoryClass == "railway" || type in listOf("station", "halt") -> PlaceCategory.TRAIN_STATION
        categoryClass in listOf("aeroway", "bus_station") ||
            type in listOf("aerodrome", "bus_stop", "platform", "tram_stop", "ferry_terminal") -> PlaceCategory.TRANSIT_STOP
        categoryClass in listOf("tourism", "historic", "attraction") -> PlaceCategory.LANDMARK
        else -> PlaceCategory.GENERAL
    }

    companion object {
        const val MIN_QUERY_LENGTH: Int = 3
        const val DEFAULT_BASE_URL: String = "https://nominatim.openstreetmap.org"
        const val DEFAULT_USER_AGENT: String = "Fjalar/1.0 (wake-me-up-there)"
        const val DEFAULT_TIMEOUT_MS: Int = 5000
    }
}
