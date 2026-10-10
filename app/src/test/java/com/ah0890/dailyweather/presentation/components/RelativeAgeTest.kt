package com.ah0890.dailyweather.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RelativeAgeTest {

    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private fun ageAt(secondsAgo: Long) = RelativeAge.between(now.minusSeconds(secondsAgo), now)

    @Test
    fun `under a minute is just now`() {
        assertEquals(RelativeAge.JustNow, ageAt(0))
        assertEquals(RelativeAge.JustNow, ageAt(59))
    }

    @Test
    fun `future timestamps from clock changes are just now`() {
        assertEquals(RelativeAge.JustNow, ageAt(-3600))
    }

    @Test
    fun `minutes, hours and days round down`() {
        assertEquals(RelativeAge.Minutes(1), ageAt(60))
        assertEquals(RelativeAge.Minutes(59), ageAt(3599))
        assertEquals(RelativeAge.Hours(1), ageAt(3600))
        assertEquals(RelativeAge.Hours(23), ageAt(86_399))
        assertEquals(RelativeAge.Days(1), ageAt(86_400))
        assertEquals(RelativeAge.Days(3), ageAt(3 * 86_400 + 5_000))
    }
}
