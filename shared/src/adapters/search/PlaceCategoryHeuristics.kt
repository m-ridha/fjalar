package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.PlaceCategory

/**
 * Best-effort category refinement for geocoders that return little more than a name.
 * Provider typing always wins when it already identifies rail, metro, bus, or a landmark.
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

    private val BUS_KEYWORDS = listOf(
        "bus stop",
        "bus station",
        "bus terminal",
        "bus interchange",
        "terminal bus",
        "halte",
        "busway",
        "transjakarta",
        "bushaltestelle",
        "arrêt de bus",
        "arret de bus",
        "parada de bus",
        "バス停",
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
            PlaceCategory.BUS_STOP,
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
        if (BUS_KEYWORDS.any { name.contains(it, ignoreCase = true) }) {
            return PlaceCategory.BUS_STOP
        }
        if (RAIL_KEYWORDS.any { name.contains(it, ignoreCase = true) }) {
            return PlaceCategory.TRAIN_STATION
        }
        return providerCategory
    }
}
