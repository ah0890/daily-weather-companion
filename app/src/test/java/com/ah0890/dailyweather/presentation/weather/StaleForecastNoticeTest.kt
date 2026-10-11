package com.ah0890.dailyweather.presentation.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StaleForecastNoticeTest {

    private val zone = ZoneId.of("Europe/London")
    private val now = Instant.parse("2026-10-10T12:00:00Z") // 13:00 in London

    @Test
    fun `same local day is today`() {
        assertEquals(UpdatedDay.Today, updatedDay(Instant.parse("2026-10-09T23:30:00Z"), now, zone)) // 00:30 local
    }

    @Test
    fun `previous local day is yesterday`() {
        assertEquals(UpdatedDay.Yesterday, updatedDay(Instant.parse("2026-10-09T22:30:00Z"), now, zone)) // 23:30 local
    }

    @Test
    fun `older days are earlier`() {
        assertEquals(UpdatedDay.Earlier, updatedDay(Instant.parse("2026-10-07T08:00:00Z"), now, zone))
    }

    @Test
    fun `forecasts over an hour old are outdated`() {
        assertFalse(isOutdated(now.minusSeconds(3600), now))
        assertTrue(isOutdated(now.minusSeconds(3601), now))
    }
}
