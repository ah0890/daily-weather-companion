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
 * @property pollen highest counts per type in the next 24 hours; empty where the provider has no
 *   pollen data (Open-Meteo covers Europe only).
 */
data class AirQuality(
    val time: LocalDateTime,
    val usAqi: Int,
    val readings: List<PollutantReading>,
    val peakUsAqi: Int? = null,
    val peakTime: LocalDateTime? = null,
    val pollen: List<PollenReading> = emptyList(),
) {
    /** The most significant pollen type, or null where no pollen data exists. */
    val highestPollen: PollenReading?
        get() = pollen.maxWithOrNull(compareBy<PollenReading> { it.level }.thenBy { it.grainsPerCubicMetre })

    val category: AqiCategory get() = AqiCategory.of(usAqi)

    /** The pollutant with the highest sub-index, i.e. the one that sets the AQI. */
    val mainPollutant: Pollutant?
        get() = readings.filter { it.index != null }.maxByOrNull { it.index!! }?.pollutant
}

enum class PollenType { Tree, Grass, Weed }

enum class PollenLevel { Low, Moderate, High, VeryHigh }

/** Highest pollen count of one type in the next 24 hours, in grains/m³. */
data class PollenReading(val type: PollenType, val grainsPerCubicMetre: Double) {
    val level: PollenLevel get() = PollenLevels.of(type, grainsPerCubicMetre)
}

/**
 * Pollen levels using the US National Allergy Bureau scale (grains/m³). Trees produce far more
 * pollen than grasses, so each type has its own bands.
 */
object PollenLevels {
    // Lower bounds of Moderate, High and Very high.
    private val BANDS = mapOf(
        PollenType.Tree to Triple(15.0, 90.0, 1500.0),
        PollenType.Grass to Triple(5.0, 20.0, 200.0),
        PollenType.Weed to Triple(10.0, 50.0, 500.0),
    )

    fun of(type: PollenType, grains: Double): PollenLevel {
        val (moderate, high, veryHigh) = BANDS.getValue(type)
        return when {
            grains >= veryHigh -> PollenLevel.VeryHigh
            grains >= high -> PollenLevel.High
            grains >= moderate -> PollenLevel.Moderate
            else -> PollenLevel.Low
        }
    }
}
