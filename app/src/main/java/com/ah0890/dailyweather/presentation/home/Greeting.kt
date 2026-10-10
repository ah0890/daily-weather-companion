package com.ah0890.dailyweather.presentation.home

import androidx.annotation.StringRes
import com.ah0890.dailyweather.R

/** Picks a time-of-day greeting for the given hour (0–23). */
@StringRes
fun greetingFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..16 -> R.string.home_greeting_afternoon
    else -> R.string.home_greeting_evening
}
