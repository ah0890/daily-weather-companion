package com.ah0890.dailyweather.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ah0890.dailyweather.R
import java.time.Duration
import java.time.Instant

/** How long ago something happened, rounded down to the largest sensible unit. */
sealed interface RelativeAge {
    data object JustNow : RelativeAge
    data class Minutes(val count: Int) : RelativeAge
    data class Hours(val count: Int) : RelativeAge
    data class Days(val count: Int) : RelativeAge

    companion object {
        fun between(then: Instant, now: Instant): RelativeAge {
            val elapsed = Duration.between(then, now)
            return when {
                // Also covers a clock that moved backwards.
                elapsed < Duration.ofMinutes(1) -> JustNow
                elapsed < Duration.ofHours(1) -> Minutes(elapsed.toMinutes().toInt())
                elapsed < Duration.ofDays(1) -> Hours(elapsed.toHours().toInt())
                else -> Days(elapsed.toDays().toInt())
            }
        }
    }
}

@Composable
fun RelativeAge.text(): String = when (this) {
    RelativeAge.JustNow -> stringResource(R.string.age_just_now)
    is RelativeAge.Minutes -> pluralStringResource(R.plurals.age_minutes, count, count)
    is RelativeAge.Hours -> pluralStringResource(R.plurals.age_hours, count, count)
    is RelativeAge.Days -> pluralStringResource(R.plurals.age_days, count, count)
}
