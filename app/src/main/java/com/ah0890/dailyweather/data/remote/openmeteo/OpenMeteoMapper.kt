package com.ah0890.dailyweather.data.remote.openmeteo

import com.ah0890.dailyweather.data.remote.MalformedWeatherDataException
import com.ah0890.dailyweather.domain.model.Coordinates
import com.ah0890.dailyweather.domain.model.CurrentWeather
import com.ah0890.dailyweather.domain.model.DailyForecast
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Converts an Open-Meteo response into the domain model.
 *
 * Individual hours or days with missing essentials are skipped; the whole response is rejected
 * with [MalformedWeatherDataException] only when the current conditions or an entire series
 * are unusable.
 */
internal fun ForecastResponseDto.toDomain(coordinates: Coordinates): WeatherForecast {
    val current = current?.toDomain() ?: malformed("current conditions missing")
    val hourly = hourly?.toDomain().orEmpty().ifEmpty { malformed("no usable hourly data") }
    val daily = daily?.toDomain().orEmpty().ifEmpty { malformed("no usable daily data") }

    return WeatherForecast(
        coordinates = coordinates,
        timeZone = resolveZone(timezone, utcOffsetSeconds),
        current = current,
        hourly = hourly,
        daily = daily,
    )
}

/** Maps a WMO weather interpretation code (as used by Open-Meteo) to a [WeatherCondition]. */
internal fun wmoCodeToCondition(code: Int?): WeatherCondition = when (code) {
    0 -> WeatherCondition.Clear
    1 -> WeatherCondition.MainlyClear
    2 -> WeatherCondition.PartlyCloudy
    3 -> WeatherCondition.Overcast
    45, 48 -> WeatherCondition.Fog
    51, 53, 55 -> WeatherCondition.Drizzle
    56, 57 -> WeatherCondition.FreezingDrizzle
    61, 63, 65 -> WeatherCondition.Rain
    66, 67 -> WeatherCondition.FreezingRain
    71, 73, 75, 77 -> WeatherCondition.Snow
    80, 81, 82 -> WeatherCondition.RainShowers
    85, 86 -> WeatherCondition.SnowShowers
    95, 96, 99 -> WeatherCondition.Thunderstorm
    else -> WeatherCondition.Unknown
}

private fun CurrentDto.toDomain(): CurrentWeather? {
    val time = parseDateTime(time) ?: return null
    val temperature = temperature ?: return null
    return CurrentWeather(
        time = time,
        condition = wmoCodeToCondition(weatherCode),
        temperatureC = temperature,
        feelsLikeC = apparentTemperature ?: temperature,
        humidityPercent = relativeHumidity?.coerceIn(0, 100),
        windSpeedKmh = windSpeed,
        windDirectionDegrees = windDirection,
        precipitationMm = precipitation,
        isDay = isDay != 0,
        uvIndex = uvIndex,
        visibilityMeters = visibility,
    )
}

private fun HourlyDto.toDomain(): List<HourlyForecast> =
    time.orEmpty().mapIndexedNotNull { i, rawTime ->
        val time = parseDateTime(rawTime) ?: return@mapIndexedNotNull null
        val temperature = temperature?.getOrNull(i) ?: return@mapIndexedNotNull null
        HourlyForecast(
            time = time,
            condition = wmoCodeToCondition(weatherCode?.getOrNull(i)),
            temperatureC = temperature,
            precipitationProbability = precipitationProbability?.getOrNull(i)?.coerceIn(0, 100),
            precipitationMm = precipitation?.getOrNull(i),
            windSpeedKmh = windSpeed?.getOrNull(i),
            isDay = isDay?.getOrNull(i) != 0,
            feelsLikeC = apparentTemperature?.getOrNull(i),
        )
    }

private fun DailyDto.toDomain(): List<DailyForecast> =
    time.orEmpty().mapIndexedNotNull { i, rawDate ->
        val date = parseDate(rawDate) ?: return@mapIndexedNotNull null
        val max = temperatureMax?.getOrNull(i) ?: return@mapIndexedNotNull null
        val min = temperatureMin?.getOrNull(i) ?: return@mapIndexedNotNull null
        DailyForecast(
            date = date,
            condition = wmoCodeToCondition(weatherCode?.getOrNull(i)),
            maxTemperatureC = max,
            minTemperatureC = min,
            precipitationProbabilityMax = precipitationProbabilityMax?.getOrNull(i)?.coerceIn(0, 100),
            precipitationSumMm = precipitationSum?.getOrNull(i),
            sunrise = parseDateTime(sunrise?.getOrNull(i)),
            sunset = parseDateTime(sunset?.getOrNull(i)),
            uvIndexMax = uvIndexMax?.getOrNull(i),
            windSpeedMaxKmh = windSpeedMax?.getOrNull(i),
        )
    }

private fun resolveZone(timezone: String?, utcOffsetSeconds: Int?): ZoneId =
    timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: utcOffsetSeconds?.let { runCatching { ZoneOffset.ofTotalSeconds(it) }.getOrNull() }
        ?: ZoneOffset.UTC

private fun parseDateTime(value: String?): LocalDateTime? =
    value?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

private fun parseDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun malformed(reason: String): Nothing = throw MalformedWeatherDataException(reason)
