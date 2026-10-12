package com.ah0890.dailyweather.data.remote

import java.io.IOException

/** The weather provider answered, but the response is missing data the app cannot do without. */
class MalformedWeatherDataException(reason: String) : IOException("Malformed weather data: $reason")
