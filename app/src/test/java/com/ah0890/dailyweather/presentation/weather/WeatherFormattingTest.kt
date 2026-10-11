package com.ah0890.dailyweather.presentation.weather

import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.ui.theme.WeatherMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherFormattingTest {

    @Test
    fun `temperatures round to whole degrees`() {
        assertEquals("22°", formatTemperature(21.5))
        assertEquals("21°", formatTemperature(21.4))
        assertEquals("-3°", formatTemperature(-2.6))
    }

    @Test
    fun `negative zero is shown as zero`() {
        assertEquals("0°", formatTemperature(-0.4))
    }

    @Test
    fun `wind speed is whole kilometres per hour`() {
        assertEquals("15 km/h", formatWindSpeed(14.8))
    }

    @Test
    fun `compass points cover the full circle`() {
        assertEquals("N", compassDirection(0))
        assertEquals("N", compassDirection(350))
        assertEquals("NE", compassDirection(45))
        assertEquals("SW", compassDirection(230))
        assertEquals("W", compassDirection(270))
        assertEquals("N", compassDirection(360))
        assertEquals("NW", compassDirection(-45))
    }

    @Test
    fun `visibility is capped and uses sensible precision`() {
        assertEquals("10+ km", formatVisibility(24_140.0))
        assertEquals("3 km", formatVisibility(3_440.0))
        assertEquals("0.4 km".replace('.', decimalSeparator()), formatVisibility(400.0))
    }

    @Test
    fun `uv index categories follow WHO bands`() {
        assertEquals(R.string.uv_low, uvLevelRes(0.0))
        assertEquals(R.string.uv_low, uvLevelRes(2.9))
        assertEquals(R.string.uv_moderate, uvLevelRes(3.0))
        assertEquals(R.string.uv_high, uvLevelRes(6.0))
        assertEquals(R.string.uv_very_high, uvLevelRes(8.0))
        assertEquals(R.string.uv_extreme, uvLevelRes(11.0))
    }

    @Test
    fun `clear skies are warm by day and cool at night`() {
        assertEquals(WeatherMood.Sunny, WeatherCondition.Clear.mood(isDay = true))
        assertEquals(WeatherMood.Clear, WeatherCondition.Clear.mood(isDay = false))
        assertNotEquals(WeatherCondition.Clear.emoji(isDay = true), WeatherCondition.Clear.emoji(isDay = false))
    }

    @Test
    fun `wet conditions use the rain or storm moods`() {
        assertEquals(WeatherMood.Rain, WeatherCondition.Drizzle.mood(isDay = true))
        assertEquals(WeatherMood.Rain, WeatherCondition.RainShowers.mood(isDay = false))
        assertEquals(WeatherMood.Storm, WeatherCondition.Thunderstorm.mood(isDay = true))
        assertEquals(WeatherMood.Snow, WeatherCondition.SnowShowers.mood(isDay = true))
    }

    @Test
    fun `every condition has its own label`() {
        val labels = WeatherCondition.entries.map { it.labelRes }
        assertEquals(labels.size, labels.toSet().size)
    }

    private fun decimalSeparator(): Char =
        java.text.DecimalFormatSymbols.getInstance().decimalSeparator
}
