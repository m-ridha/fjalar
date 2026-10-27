package com.muhammadridha.fjalar.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateNavigationTypeTest {
    @Test
    fun coordinateRouteValuesPreservePrecisionAcrossHemispheres() {
        for (coordinate in listOf(-179.999999999, -6.2088123456, 0.000000001, 89.999999999)) {
            val routeValue = CoordinateNavType.serializeAsValue(coordinate)
            assertEquals(coordinate, CoordinateNavType.parseValue(routeValue))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            CoordinateNavType.parseValue("NaN")
        }
    }

}
