package com.muhammadridha.fjalar.adapters.search

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.SearchResultPlace

/**
 * Interface contract for platform or network-based geocoding engines behind
 * [PlaceSearchAdapter]. Implementations receive only the typed query, the app locale,
 * and a coarse proximity center; ranking and distance filtering stay in the adapter.
 */
interface OnlineGeocodingEngine {
    suspend fun geocode(
        query: String,
        locale: AppLocale,
        proximity: GeoCoordinates? = null,
        limit: Int = 5,
    ): List<SearchResultPlace>
}
