package com.ah0890.dailyweather.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinatesTest {

    @Test
    fun `approximate rounds to two decimal places`() {
        val approx = Coordinates(48.856613, 2.352222).approximate()

        assertEquals(48.86, approx.latitude, 0.0)
        assertEquals(2.35, approx.longitude, 0.0)
    }

    @Test
    fun `approximate handles negative coordinates`() {
        val approx = Coordinates(-33.868820, -151.209296).approximate()

        assertEquals(-33.87, approx.latitude, 0.0)
        assertEquals(-151.21, approx.longitude, 0.0)
    }

    @Test
    fun `boundary values are accepted`() {
        Coordinates(90.0, 180.0)
        Coordinates(-90.0, -180.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude above 90 is rejected`() {
        Coordinates(90.01, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longitude below -180 is rejected`() {
        Coordinates(0.0, -180.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NaN is rejected`() {
        Coordinates(Double.NaN, 0.0)
    }
}
