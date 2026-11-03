package com.muhammadridha.fjalar.domain.model

data class GeoCoordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude must be within -90..90, was $latitude" }
        require(longitude in -180.0..180.0) { "longitude must be within -180..180, was $longitude" }
    }

}

data class TriggerRadius(val meters: Int) {
    init {
        require(meters in MIN_METERS..MAX_METERS) { "radius must be within $MIN_METERS..$MAX_METERS m, was $meters" }
    }

    companion object {
        const val MIN_METERS = 50
        const val MAX_METERS = 10_000
        val DEFAULT = TriggerRadius(100)
        val PRESETS: List<TriggerRadius> = listOf(50, 100, 200, 300, 500, 1_000).map(::TriggerRadius)
    }
}
