package com.muhammadridha.fjalar.domain.model

enum class PlaceCategory {
    TRAIN_STATION,
    METRO_STATION,
    TRANSIT_STOP,
    LANDMARK,
    GENERAL,
}

data class SearchResultPlace(
    val id: String,
    val name: String,
    val coordinates: GeoCoordinates,
    val category: PlaceCategory,
    val detail: String? = null,
    val aliases: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Place id must not be blank" }
        require(name.isNotBlank()) { "Place name must not be blank" }
    }

    /**
     * Checks if a search query matches this place by name or aliases.
     * Blank query matches everything.
     */
    fun matchesQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (name.contains(trimmed, ignoreCase = true)) return true
        return aliases.any { it.contains(trimmed, ignoreCase = true) }
    }
}
