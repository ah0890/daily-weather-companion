package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteRain
import com.ah0890.dailyweather.domain.model.Impact
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.TravelTip
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherHazard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TravelAdvisorTest {

    private fun leg(vararg hazards: WeatherHazard) = CommuteLeg(
        type = CommuteLegType.Evening,
        departure = LocalDateTime.of(2026, 10, 12, 17, 0),
        condition = WeatherCondition.Overcast,
        isDay = true,
        temperatureC = 20.0,
        rainChance = 10,
        windSpeedKmh = 10.0,
        rain = CommuteRain.Dry,
        windy = false,
        hazards = hazards.toSet(),
    )

    private fun advise(mode: TransportMode, vararg hazards: WeatherHazard) = TravelAdvisor.advise(leg(*hazards), mode)

    @Test
    fun `no hazards is favourable for every mode, with no tips`() {
        TransportMode.entries.forEach { mode ->
            val advice = advise(mode)
            assertEquals(mode.name, TravelRating.Favorable, advice.rating)
            assertTrue(mode.name, advice.tips.isEmpty())
            assertTrue(mode.name, advice.concerns.isEmpty())
        }
    }

    @Test
    fun `motorcycle - likely rain is unfavourable and suggests the car and rain gear`() {
        val advice = advise(TransportMode.Motorcycle, WeatherHazard.RainLikely)

        assertEquals(TravelRating.Unfavorable, advice.rating)
        assertEquals(listOf(TravelTip.ConsiderAlternative, TravelTip.RainGear), advice.tips)
    }

    @Test
    fun `motorcycle - possible rain only needs care and rain gear`() {
        val advice = advise(TransportMode.Motorcycle, WeatherHazard.RainPossible)

        assertEquals(TravelRating.Caution, advice.rating)
        assertEquals(listOf(TravelTip.RainGear), advice.tips)
    }

    @Test
    fun `motorcycle - strong wind is a caution, a gale is unfavourable`() {
        assertEquals(TravelRating.Caution, advise(TransportMode.Motorcycle, WeatherHazard.StrongWind).rating)
        assertTrue(TravelTip.RideCarefully in advise(TransportMode.Motorcycle, WeatherHazard.StrongWind).tips)
        assertEquals(TravelRating.Unfavorable, advise(TransportMode.Motorcycle, WeatherHazard.Gale).rating)
    }

    @Test
    fun `motorcycle - visibility, heat and thunderstorms`() {
        assertEquals(TravelRating.Caution, advise(TransportMode.Motorcycle, WeatherHazard.PoorVisibility).rating)
        assertEquals(TravelRating.Unfavorable, advise(TransportMode.Motorcycle, WeatherHazard.DenseFog).rating)
        assertEquals(TravelRating.Unfavorable, advise(TransportMode.Motorcycle, WeatherHazard.Thunderstorm).rating)
        assertEquals(listOf(TravelTip.CarryWater), advise(TransportMode.Motorcycle, WeatherHazard.Heat).tips)
    }

    @Test
    fun `car - ordinary rain does not change the rating but an umbrella is suggested`() {
        val advice = advise(TransportMode.Car, WeatherHazard.RainLikely)

        assertEquals(TravelRating.Favorable, advice.rating)
        assertEquals(listOf(TravelTip.Umbrella), advice.tips)
    }

    @Test
    fun `car - heavy rain and fog call for extra time`() {
        val heavy = advise(TransportMode.Car, WeatherHazard.HeavyRain)
        assertEquals(TravelRating.Caution, heavy.rating)
        assertTrue(TravelTip.AllowExtraTime in heavy.tips)

        val fog = advise(TransportMode.Car, WeatherHazard.DenseFog)
        assertEquals(TravelRating.Unfavorable, fog.rating)
        assertTrue(TravelTip.AllowExtraTime in fog.tips)
    }

    @Test
    fun `car - wind and heat matter little`() {
        assertEquals(TravelRating.Favorable, advise(TransportMode.Car, WeatherHazard.StrongWind, WeatherHazard.Heat).rating)
        assertFalse(TravelTip.CarryWater in advise(TransportMode.Car, WeatherHazard.Heat).tips)
    }

    @Test
    fun `walking - heat, uv, wind and rain`() {
        val hot = advise(TransportMode.Walking, WeatherHazard.Heat, WeatherHazard.HighUv)
        assertEquals(TravelRating.Caution, hot.rating)
        assertEquals(listOf(TravelTip.CarryWater, TravelTip.SunProtection), hot.tips)

        assertEquals(TravelRating.Unfavorable, advise(TransportMode.Walking, WeatherHazard.ExtremeHeat).rating)
        assertEquals(listOf(TravelTip.ConsiderUmbrella), advise(TransportMode.Walking, WeatherHazard.RainPossible).tips)
        assertEquals(TravelRating.Caution, advise(TransportMode.Walking, WeatherHazard.StrongWind).rating)
    }

    @Test
    fun `public transport - severe weather may mean delays`() {
        val stormy = advise(TransportMode.PublicTransport, WeatherHazard.Thunderstorm)

        assertEquals(TravelRating.Caution, stormy.rating)
        assertEquals(listOf(TravelTip.Umbrella, TravelTip.AllowExtraTime), stormy.tips)
        assertEquals(TravelRating.Favorable, advise(TransportMode.PublicTransport, WeatherHazard.RainPossible).rating)
    }

    @Test
    fun `concerns are only the hazards relevant to the mode, most serious first`() {
        val advice = advise(TransportMode.Car, WeatherHazard.HighUv, WeatherHazard.PoorVisibility, WeatherHazard.DenseFog)

        assertEquals(listOf(WeatherHazard.DenseFog, WeatherHazard.PoorVisibility), advice.concerns)
    }

    @Test
    fun `every hazard has an impact for every mode`() {
        WeatherHazard.entries.forEach { hazard ->
            TransportMode.entries.forEach { mode -> TravelAdvisor.impact(hazard, mode) }
        }
        assertEquals(Impact.None, TravelAdvisor.impact(WeatherHazard.HighUv, TransportMode.Motorcycle))
    }
}
