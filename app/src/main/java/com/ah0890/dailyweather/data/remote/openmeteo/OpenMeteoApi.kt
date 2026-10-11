package com.ah0890.dailyweather.data.remote.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = FORECAST_DAYS,
    ): ForecastResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val FORECAST_DAYS = 7

        const val CURRENT_FIELDS = "temperature_2m,apparent_temperature,relative_humidity_2m,is_day," +
            "precipitation,weather_code,wind_speed_10m,wind_direction_10m,uv_index,visibility"
        const val HOURLY_FIELDS = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
            "weather_code,wind_speed_10m,is_day"
        const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max,wind_speed_10m_max"
    }
}
