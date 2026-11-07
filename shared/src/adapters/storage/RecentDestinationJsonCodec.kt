package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import kotlin.time.Instant

/**
 * Lightweight, pure-Kotlin JSON codec for [RecentDestination].
 * Handles serialization and defensive deserialization without reflection or third-party dependencies.
 */
object RecentDestinationJsonCodec {

    fun toJson(destination: RecentDestination): String = buildString {
        append("{")
        append("\"id\":").append(escapeJsonString(destination.id)).append(",")
        append("\"name\":").append(escapeJsonString(destination.name)).append(",")
        append("\"coordinates\":{")
        append("\"latitude\":").append(destination.coordinates.latitude).append(",")
        append("\"longitude\":").append(destination.coordinates.longitude)
        append("},")
        append("\"timestamp\":").append(destination.timestamp.toEpochMilliseconds()).append(",")
        append("\"category\":").append(escapeJsonString(destination.category.name))
        append("}")
    }

    fun fromJson(json: String): RecentDestination? {
        return runCatching {
            val root = JsonParser.parse(json) as? JsonValue.JsonObject ?: return null
            decodeDestination(root)
        }.getOrNull()
    }

    fun toJsonList(destinations: Collection<RecentDestination>): String = buildString {
        append("[")
        destinations.forEachIndexed { index, destination ->
            if (index > 0) append(",")
            append(toJson(destination))
        }
        append("]")
    }

    fun fromJsonList(json: String): List<RecentDestination> {
        return runCatching {
            val root = JsonParser.parse(json) as? JsonValue.JsonArray ?: return emptyList()
            root.elements.mapNotNull { element ->
                if (element is JsonValue.JsonObject) {
                    decodeDestination(element)
                } else null
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeDestination(obj: JsonValue.JsonObject): RecentDestination? {
        return runCatching {
            val id = obj.getString("id")?.takeIf { it.isNotBlank() } ?: return null
            val name = obj.getString("name")?.takeIf { it.isNotBlank() } ?: return null

            val coordsObj = obj.getObject("coordinates")
            val lat = coordsObj?.getDouble("latitude") ?: obj.getDouble("latitude") ?: return null
            val lon = coordsObj?.getDouble("longitude") ?: obj.getDouble("longitude") ?: return null
            val coordinates = GeoCoordinates(latitude = lat, longitude = lon)

            val timestamp = when (val tsVal = obj.properties["timestamp"]) {
                is JsonValue.JsonNumber -> Instant.fromEpochMilliseconds(tsVal.value.toLong())
                is JsonValue.JsonString -> {
                    runCatching { Instant.parse(tsVal.value) }
                        .getOrNull()
                        ?: tsVal.value.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
                }
                else -> null
            } ?: return null

            val category = obj.getString("category")?.let { catStr ->
                runCatching { PlaceCategory.valueOf(catStr) }.getOrNull()
            } ?: PlaceCategory.GENERAL

            RecentDestination(
                id = id,
                name = name,
                coordinates = coordinates,
                timestamp = timestamp,
                category = category,
            )
        }.getOrNull()
    }

    private fun escapeJsonString(value: String): String = buildString {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code in 0x00..0x1F) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}
