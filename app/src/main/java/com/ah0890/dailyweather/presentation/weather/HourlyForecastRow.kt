package com.ah0890.dailyweather.presentation.weather

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.HourlyForecast
import java.time.format.DateTimeFormatter

/** Rain chance at or above which an hour is highlighted. */
private const val NOTABLE_RAIN_CHANCE = 50

/**
 * Horizontally scrolling timeline of hours, focused on rain chance.
 *
 * @param edgePadding the parent's horizontal padding; the row scrolls under it to the screen edge.
 * @param firstIsNow label the first entry "Now" instead of its time.
 */
@Composable
fun HourlyForecastRow(
    hours: List<HourlyForecast>,
    title: String,
    edgePadding: Dp,
    firstIsNow: Boolean = false,
) {
    if (hours.isEmpty()) return
    val hourFormatter = rememberHourFormatter()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Bleed to the screen edges while keeping the first card aligned with the content.
            contentPadding = PaddingValues(horizontal = edgePadding),
            modifier = Modifier.bleed(edgePadding),
        ) {
            items(hours, key = { it.time }) { hour ->
                val label = if (firstIsNow && hour == hours.first()) {
                    stringResource(R.string.hourly_now)
                } else {
                    hour.time.format(hourFormatter)
                }
                HourCell(hour, label)
            }
        }
    }
}

@Composable
private fun HourCell(hour: HourlyForecast, timeLabel: String) {
    val chance = hour.precipitationProbability
    val notable = (chance ?: 0) >= NOTABLE_RAIN_CHANCE
    val condition = stringResource(hour.condition.labelRes)
    val description = buildString {
        append(timeLabel).append(", ").append(condition).append(", ").append(temperatureText(hour.temperatureC))
        if (chance != null) append(", ").append(stringResource(R.string.hourly_rain_chance_description, chance))
        hour.windSpeedKmh?.let { append(", ").append(stringResource(R.string.hourly_wind_description, formatWindSpeed(it))) }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (notable) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        modifier = Modifier
            .width(72.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(text = hour.condition.emoji(hour.isDay), fontSize = 24.sp)
            Text(
                text = temperatureText(hour.temperatureC),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RainBar(chance)
            Text(
                text = chance?.let { "$it%" } ?: "–",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (notable) FontWeight.Bold else FontWeight.Medium,
                color = if (notable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            val amount = hour.precipitationMm?.takeIf { it >= 0.1 }?.let { formatPrecipitation(it) }
            Text(
                // Keeps cells the same height whether or not rain is expected.
                text = amount ?: " ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            hour.windSpeedKmh?.let {
                Text(
                    text = formatWindSpeed(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Small vertical gauge of rain chance; the percentage is always printed beside it too. */
@Composable
private fun RainBar(chance: Int?) {
    val fraction = (chance ?: 0) / 100f
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Widens the element by [horizontal] on each side, cancelling out the parent's padding. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra * 2,
            maxWidth = constraints.maxWidth + extra * 2,
        ),
    )
    layout(placeable.width - extra * 2, placeable.height) { placeable.place(-extra, 0) }
}

/** "9 AM" or "09" style hour labels, following the device's 12/24-hour preference. */
@Composable
fun rememberHourFormatter(): DateTimeFormatter {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "j"), locale)
    }
}
