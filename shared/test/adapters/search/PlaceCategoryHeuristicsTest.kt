package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.PlaceCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceCategoryHeuristicsTest {

    @Test
    fun metroNamesPromoteTransitStopsToMetro() {
        assertEquals(
            PlaceCategory.METRO_STATION,
            PlaceCategoryHeuristics.refine("Shibuya Metro", PlaceCategory.TRANSIT_STOP),
        )
    }

    @Test
    fun metroNamesPromoteGeneralToMetro() {
        assertEquals(
            PlaceCategory.METRO_STATION,
            PlaceCategoryHeuristics.refine("MRT Blok M", PlaceCategory.GENERAL),
        )
    }

    @Test
    fun railNamesPromoteGeneralToTrainStation() {
        assertEquals(
            PlaceCategory.TRAIN_STATION,
            PlaceCategoryHeuristics.refine("Berlin Hauptbahnhof", PlaceCategory.GENERAL),
        )
    }

    @Test
    fun railNamesPromoteTransitStopsToTrainStation() {
        assertEquals(
            PlaceCategory.TRAIN_STATION,
            PlaceCategoryHeuristics.refine("Tokyo Station", PlaceCategory.TRANSIT_STOP),
        )
    }

    @Test
    fun providerRailAndMetroCategoriesAreNotOverridden() {
        assertEquals(
            PlaceCategory.TRAIN_STATION,
            PlaceCategoryHeuristics.refine("Metro Cafe", PlaceCategory.TRAIN_STATION),
        )
    }

    @Test
    fun landmarksAreNotReclassified() {
        assertEquals(
            PlaceCategory.LANDMARK,
            PlaceCategoryHeuristics.refine("Station Monument", PlaceCategory.LANDMARK),
        )
    }

    @Test
    fun unrelatedNamesKeepProviderCategory() {
        assertEquals(
            PlaceCategory.GENERAL,
            PlaceCategoryHeuristics.refine("Blue Bottle Coffee", PlaceCategory.GENERAL),
        )
    }

    @Test
    fun busNamesPromoteTransitStopsAndGeneralToBusStop() {
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Halte TransJakarta Harmoni", PlaceCategory.TRANSIT_STOP),
        )
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Shinjuku Bus Station", PlaceCategory.GENERAL),
        )
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Central Bus Stop", PlaceCategory.GENERAL),
        )
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Harmoni Central Busway", PlaceCategory.TRANSIT_STOP),
        )
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Bedok Bus Interchange", PlaceCategory.GENERAL),
        )
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Arret de Bus Opera", PlaceCategory.GENERAL),
        )
    }

    @Test
    fun compoundBusAndMetroNamesPreferBusStop() {
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Metro Bus Station", PlaceCategory.TRANSIT_STOP),
        )
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Halte MRT Lebak Bulus", PlaceCategory.TRANSIT_STOP),
        )
    }

    @Test
    fun providerBusStopCategoryIsNotOverridden() {
        assertEquals(
            PlaceCategory.BUS_STOP,
            PlaceCategoryHeuristics.refine("Stasiun Bus Terminal", PlaceCategory.BUS_STOP),
        )
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(
            PlaceCategory.TRAIN_STATION,
            PlaceCategoryHeuristics.refine("sTaSiUn", PlaceCategory.GENERAL),
        )
    }
}
