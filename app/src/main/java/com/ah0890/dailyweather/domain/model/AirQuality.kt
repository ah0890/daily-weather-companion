package com.ah0890.dailyweather.domain.model

import java.time.LocalDateTime

/** Pollutants that make up the US AQI. */
enum class Pollutant { Pm25, Pm10, Ozone, NitrogenDioxide, SulphurDioxide, CarbonMonoxide }

/**
 * One pollutant's reading.
 *
 * @property concentration in µg/m³, or null if the provider gave none.
 * @property index this pollutant's US AQI sub-index, or null if unknown.
 */
data class PollutantReading(val pollutant: Pollutant, val concentration: Double?, val index: Int?)

/** US EPA AQI categories, from 0–50 (good) to 301+ (hazardous). */
enum class AqiCategory {
    Good,
    Moderate,
    UnhealthyForSensitiveGroups,
    Unhealthy,
    VeryUnhealthy,
    Hazardous;

    companion object {
        fun of(aqi: Int): AqiCategory = when {
            aqi <= 50 -> Good
            aqi <= 100 -> Moderate
            aqi <= 150 -> UnhealthyForSensitiveGroups
            aqi <= 200 -> Unhealthy
            aqi <= 300 -> VeryUnhealthy
            else -> Hazardous
        }
    }
}

/**
 * Air quality at a place (CAMS model data via Open-Meteo).
 *
 * @property usAqi current US Air Quality Index.
 * @property peakUsAqi highest hourly AQI in the next 24 hours, with [peakTime], when known.
 */
data class AirQuality(
    val time: LocalDateTime,
    val usAqi: Int,
    val readings: List<PollutantReading>,
    val peakUsAqi: Int? = null,
    val peakTime: LocalDateTime? = null,
) {
    val category: AqiCategory get() = AqiCategory.of(usAqi)

    /** The pollutant with the highest sub-index, i.e. the one that sets the AQI. */
    val mainPollutant: Pollutant?
        get() = readings.filter { it.index != null }.maxByOrNull { it.index!! }?.pollutant
}
