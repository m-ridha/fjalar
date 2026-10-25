package com.muhammadridha.fjalar

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformIosTest {

    @Test
    fun testExample() {
        assertTrue(Greeting().greet().contains("iOS"), "Check iOS is mentioned")
    }
}
