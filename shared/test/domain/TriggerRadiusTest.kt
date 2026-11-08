package com.muhammadridha.fjalar.domain

import com.muhammadridha.fjalar.domain.model.TriggerRadius
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TriggerRadiusTest {
    @Test
    fun fiftyMetersIsTheMinimumRadius() {
        assertEquals(50, TriggerRadius.MIN_METERS)
        assertEquals(50, TriggerRadius(50).meters)
        assertFailsWith<IllegalArgumentException> { TriggerRadius(49) }
    }

    @Test
    fun newAlarmsDefaultToOneHundredMeters() {
        assertEquals(100, TriggerRadius.DEFAULT.meters)
    }

    @Test
    fun presetsOfferSmallerRadiusChoices() {
        assertEquals(listOf(50, 100, 200, 300, 500, 1_000, 2_000, 3_000), TriggerRadius.PRESETS.map { it.meters })
    }

    @Test
    fun existingTenKilometerRadiusRemainsValid() {
        assertEquals(10_000, TriggerRadius.MAX_METERS)
        assertEquals(10_000, TriggerRadius(10_000).meters)
        assertFailsWith<IllegalArgumentException> { TriggerRadius(10_001) }
    }
}
