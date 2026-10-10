package com.ah0890.dailyweather.domain.model

import java.time.Instant

/** The last location that was successfully found, kept on the device between app launches. */
data class SavedLocation(
    val location: LocationData,
    val savedAt: Instant,
)
