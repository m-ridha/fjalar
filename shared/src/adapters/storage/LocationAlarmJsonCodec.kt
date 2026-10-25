package com.muhammadridha.fjalar.adapters.storage

import com.muhammadridha.fjalar.domain.model.AlarmSchedule
import com.muhammadridha.fjalar.domain.model.AlarmSoundConfig
import com.muhammadridha.fjalar.domain.model.AlarmState
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.LocationAlarm
import com.muhammadridha.fjalar.domain.model.ScheduleType
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlin.time.Instant

/**
 * Lightweight, pure-Kotlin JSON codec for [LocationAlarm].
 * Handles serialization and defensive deserialization without reflection or third-party dependencies.
 */
object LocationAlarmJsonCodec {

    fun toJson(alarm: LocationAlarm): String = buildString {
        append("{")
        append("\"id\":").append(escapeJsonString(alarm.id)).append(",")
        append("\"label\":").append(escapeJsonString(alarm.label)).append(",")
        append("\"coordinates\":{")
        append("\"latitude\":").append(alarm.coordinates.latitude).append(",")
        append("\"longitude\":").append(alarm.coordinates.longitude)
        append("},")
        append("\"radius\":{")
        append("\"meters\":").append(alarm.radius.meters)
        append("},")
        append("\"schedule\":{")
        append("\"type\":").append(escapeJsonString(alarm.schedule.type.name))
        if (alarm.schedule.startMinuteOfDay != null) {
            append(",\"startMinuteOfDay\":").append(alarm.schedule.startMinuteOfDay)
        }
        if (alarm.schedule.endMinuteOfDay != null) {
            append(",\"endMinuteOfDay\":").append(alarm.schedule.endMinuteOfDay)
        }
        append(",\"daysOfWeekMask\":").append(alarm.schedule.daysOfWeekMask)
        append("},")
        append("\"soundConfig\":{")
        append("\"soundEnabled\":").append(alarm.soundConfig.soundEnabled).append(",")
        append("\"vibrateEnabled\":").append(alarm.soundConfig.vibrateEnabled).append(",")
        append("\"volume\":").append(alarm.soundConfig.volume)
        append("},")
        append("\"state\":").append(escapeJsonString(alarm.state.name))
        if (alarm.addressLabel != null) {
            append(",\"addressLabel\":").append(escapeJsonString(alarm.addressLabel))
        }
        if (alarm.suppressedUntil != null) {
            append(",\"suppressedUntil\":").append(escapeJsonString(alarm.suppressedUntil.toString()))
        }
        append("}")
    }

    fun fromJson(json: String): LocationAlarm? {
        return runCatching {
            val root = JsonParser.parse(json) as? JsonValue.JsonObject ?: return null
            decodeAlarm(root)
        }.getOrNull()
    }

    fun toJsonList(alarms: Collection<LocationAlarm>): String = buildString {
        append("[")
        alarms.forEachIndexed { index, alarm ->
            if (index > 0) append(",")
            append(toJson(alarm))
        }
        append("]")
    }

    fun fromJsonList(json: String): List<LocationAlarm> {
        val root = JsonParser.parse(json) as? JsonValue.JsonArray ?: return emptyList()
        return root.elements.mapNotNull { element ->
            if (element is JsonValue.JsonObject) {
                decodeAlarm(element)
            } else null
        }
    }

    fun parseStringList(json: String): List<String> {
        val root = JsonParser.parse(json) as? JsonValue.JsonArray ?: return emptyList()
        return root.elements.mapNotNull { (it as? JsonValue.JsonString)?.value }
    }

    private fun decodeAlarm(obj: JsonValue.JsonObject): LocationAlarm? {
        return runCatching {
            val id = obj.getString("id")?.takeIf { it.isNotBlank() } ?: return null
            val label = obj.getString("label")?.takeIf { it.isNotBlank() } ?: return null

            val coordsObj = obj.getObject("coordinates")
            val lat = coordsObj?.getDouble("latitude") ?: obj.getDouble("latitude") ?: return null
            val lon = coordsObj?.getDouble("longitude") ?: obj.getDouble("longitude") ?: return null
            val coordinates = GeoCoordinates(latitude = lat, longitude = lon)

            val meters = obj.getObject("radius")?.getInt("meters") ?: obj.getInt("radius") ?: return null
            val radius = TriggerRadius(meters = meters)

            val schedObj = obj.getObject("schedule") ?: obj
            val typeStr = schedObj.getString("type") ?: ScheduleType.ONE_TIME.name
            val scheduleType = ScheduleType.valueOf(typeStr)
            val schedule = if (scheduleType == ScheduleType.SCHEDULED) {
                val startMin = schedObj.getInt("startMinuteOfDay") ?: return null
                val endMin = schedObj.getInt("endMinuteOfDay") ?: return null
                val mask = schedObj.getInt("daysOfWeekMask") ?: return null
                AlarmSchedule(
                    type = scheduleType,
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    daysOfWeekMask = mask,
                )
            } else {
                AlarmSchedule.ONE_TIME
            }

            val soundObj = obj.getObject("soundConfig")
            val soundConfig = if (soundObj != null) {
                AlarmSoundConfig(
                    soundEnabled = soundObj.getBoolean("soundEnabled") ?: true,
                    vibrateEnabled = soundObj.getBoolean("vibrateEnabled") ?: true,
                    volume = soundObj.getFloat("volume") ?: 1f,
                )
            } else {
                AlarmSoundConfig()
            }

            val stateStr = obj.getString("state")
            val state = stateStr?.let { AlarmState.valueOf(it) } ?: AlarmState.DISABLED

            val addressLabel = obj.getString("addressLabel")

            val suppressedUntil = when (val suppressedVal = obj.properties["suppressedUntil"]) {
                is JsonValue.JsonString -> Instant.parse(suppressedVal.value)
                is JsonValue.JsonNumber -> Instant.fromEpochMilliseconds(suppressedVal.value.toLong())
                else -> null
            }

            LocationAlarm(
                id = id,
                label = label,
                coordinates = coordinates,
                radius = radius,
                schedule = schedule,
                soundConfig = soundConfig,
                state = state,
                addressLabel = addressLabel,
                suppressedUntil = suppressedUntil,
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

/**
 * Lightweight JSON parser AST and recursive-descent parser.
 */
internal sealed interface JsonValue {
    data class JsonObject(val properties: Map<String, JsonValue>) : JsonValue {
        fun getString(key: String): String? = (properties[key] as? JsonString)?.value
        fun getDouble(key: String): Double? = (properties[key] as? JsonNumber)?.value
        fun getInt(key: String): Int? = getDouble(key)?.toInt()
        fun getFloat(key: String): Float? = getDouble(key)?.toFloat()
        fun getBoolean(key: String): Boolean? = (properties[key] as? JsonBoolean)?.value
        fun getObject(key: String): JsonObject? = properties[key] as? JsonObject
        fun getArray(key: String): JsonArray? = properties[key] as? JsonArray
    }

    data class JsonArray(val elements: List<JsonValue>) : JsonValue
    data class JsonString(val value: String) : JsonValue
    data class JsonNumber(val value: Double) : JsonValue
    data class JsonBoolean(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

internal class JsonParser(private val text: String) {
    private var index = 0

    fun parseValue(): JsonValue? {
        skipWhitespace()
        if (isAtEnd()) return null
        return when (val c = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> null
        }
    }

    fun skipWhitespace() {
        while (!isAtEnd() && text[index].isWhitespace()) {
            index++
        }
    }

    fun isAtEnd(): Boolean = index >= text.length

    private fun peek(): Char = text[index]

    private fun advance(): Char = text[index++]

    private fun parseObject(): JsonValue.JsonObject? {
        if (advance() != '{') return null
        val map = mutableMapOf<String, JsonValue>()
        skipWhitespace()
        if (!isAtEnd() && peek() == '}') {
            advance()
            return JsonValue.JsonObject(map)
        }
        while (!isAtEnd()) {
            skipWhitespace()
            val key = parseRawString() ?: return null
            skipWhitespace()
            if (isAtEnd() || advance() != ':') return null
            val value = parseValue() ?: return null
            map[key] = value
            skipWhitespace()
            if (isAtEnd()) return null
            when (peek()) {
                ',' -> {
                    advance()
                    skipWhitespace()
                    if (!isAtEnd() && peek() == '}') {
                        advance()
                        break
                    }
                }
                '}' -> {
                    advance()
                    break
                }
                else -> return null
            }
        }
        return JsonValue.JsonObject(map)
    }

    private fun parseArray(): JsonValue.JsonArray? {
        if (advance() != '[') return null
        val list = mutableListOf<JsonValue>()
        skipWhitespace()
        if (!isAtEnd() && peek() == ']') {
            advance()
            return JsonValue.JsonArray(list)
        }
        while (!isAtEnd()) {
            val value = parseValue() ?: return null
            list.add(value)
            skipWhitespace()
            if (isAtEnd()) return null
            when (peek()) {
                ',' -> {
                    advance()
                    skipWhitespace()
                    if (!isAtEnd() && peek() == ']') {
                        advance()
                        break
                    }
                }
                ']' -> {
                    advance()
                    break
                }
                else -> return null
            }
        }
        return JsonValue.JsonArray(list)
    }

    private fun parseString(): JsonValue.JsonString? {
        val s = parseRawString() ?: return null
        return JsonValue.JsonString(s)
    }

    private fun parseRawString(): String? {
        if (isAtEnd() || advance() != '"') return null
        val sb = StringBuilder()
        while (!isAtEnd()) {
            val c = advance()
            if (c == '"') return sb.toString()
            if (c == '\\') {
                if (isAtEnd()) return null
                val esc = advance()
                when (esc) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (index + 4 > text.length) return null
                        val hex = text.substring(index, index + 4)
                        index += 4
                        val code = hex.toIntOrNull(16) ?: return null
                        sb.append(code.toChar())
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
        }
        return null
    }

    private fun parseNumber(): JsonValue.JsonNumber? {
        val start = index
        if (!isAtEnd() && peek() == '-') index++
        while (!isAtEnd() && (peek().isDigit() || peek() == '.' || peek() == 'e' || peek() == 'E' || peek() == '+' || peek() == '-')) {
            index++
        }
        val numStr = text.substring(start, index)
        val num = numStr.toDoubleOrNull() ?: return null
        return JsonValue.JsonNumber(num)
    }

    private fun parseBoolean(): JsonValue.JsonBoolean? {
        return if (text.startsWith("true", index)) {
            index += 4
            JsonValue.JsonBoolean(true)
        } else if (text.startsWith("false", index)) {
            index += 5
            JsonValue.JsonBoolean(false)
        } else {
            null
        }
    }

    private fun parseNull(): JsonValue.JsonNull? {
        return if (text.startsWith("null", index)) {
            index += 4
            JsonValue.JsonNull
        } else {
            null
        }
    }

    companion object {
        fun parse(text: String): JsonValue? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val parser = JsonParser(trimmed)
            val result = parser.parseValue() ?: return null
            parser.skipWhitespace()
            if (!parser.isAtEnd()) return null
            return result
        }
    }
}
