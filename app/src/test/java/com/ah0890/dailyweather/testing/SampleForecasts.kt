package com.ah0890.dailyweather.testing

import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.CurrentWeather
import com.ah0890.dailyweather.domain.model.DailyForecast
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Small, readable domain fixtures for tests that don't care about parsing. */
object SampleForecasts {

    val london = Coordinates(51.51, -0.13)

    fun forecast(
        coordinates: Coordinates = london,
        start: LocalDateTime = LocalDateTime.of(2026, 10, 10, 8, 0),
        hourlyRainChances: List<Int?> = listOf(10, 20, 60, 80),
    ): WeatherForecast = WeatherForecast(
        coordinates = coordinates,
        timeZone = ZoneId.of("Europe/London"),
        current = CurrentWeather(
            time = start,
            condition = WeatherCondition.PartlyCloudy,
            temperatureC = 12.0,
            feelsLikeC = 11.0,
            humidityPercent = 80,
            windSpeedKmh = 15.0,
            windDirectionDegrees = 230,
            precipitationMm = 0.0,
            isDay = true,
            uvIndex = 1.0,
            visibilityMeters = 20_000.0,
        ),
        hourly = hourlyRainChances.mapIndexed { i, chance ->
            HourlyForecast(
                time = start.plusHours(i.toLong()),
                condition = if ((chance ?: 0) >= 50) WeatherCondition.Rain else WeatherCondition.PartlyCloudy,
                temperatureC = 12.0 + i,
                precipitationProbability = chance,
                precipitationMm = if ((chance ?: 0) >= 50) 1.0 else 0.0,
                windSpeedKmh = 15.0,
                isDay = true,
            )
        },
        daily = listOf(
            DailyForecast(
                date = LocalDate.from(start),
                condition = WeatherCondition.Rain,
                maxTemperatureC = 16.0,
                minTemperatureC = 9.0,
                precipitationProbabilityMax = hourlyRainChances.filterNotNull().maxOrNull(),
                precipitationSumMm = 4.0,
                sunrise = start.withHour(7).withMinute(15),
                sunset = start.withHour(18).withMinute(20),
                uvIndexMax = 2.0,
                windSpeedMaxKmh = 25.0,
            ),
        ),
    )
}
