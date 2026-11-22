package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.PlaceCategory

/**
 * Best-effort category refinement for geocoders that return little more than a name.
 * Provider typing always wins when it already identifies rail, metro, or a landmark.
 */
object PlaceCategoryHeuristics {

    private val METRO_KEYWORDS = listOf(
        "metro",
        "mrt",
        "subway",
        "underground",
        "u-bahn",
        "ubahn",
        "metropolitana",
        "metropolitano",
    )

    private val RAIL_KEYWORDS = listOf(
        "station",
        "stasiun",
        "bahnhof",
        "hauptbahnhof",
        "hbf",
        "gare",
        "stazione",
        "estación",
        "estacion",
        "railway",
    )

    fun refine(name: String, providerCategory: PlaceCategory): PlaceCategory {
        when (providerCategory) {
            PlaceCategory.METRO_STATION,
            PlaceCategory.TRAIN_STATION,
            PlaceCategory.LANDMARK,
            -> return providerCategory
            PlaceCategory.TRANSIT_STOP,
            PlaceCategory.GENERAL,
            -> Unit
        }

        if (METRO_KEYWORDS.any { name.contains(it, ignoreCase = true) }) {
            return PlaceCategory.METRO_STATION
        }
        if (RAIL_KEYWORDS.any { name.contains(it, ignoreCase = true) }) {
            return PlaceCategory.TRAIN_STATION
        }
        return providerCategory
    }
}
