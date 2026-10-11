package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.CommuteAdvice
import com.ah0890.dailyweather.domain.model.CommuteLeg
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteRain
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatTemperature
import com.ah0890.dailyweather.presentation.weather.formatWindSpeed
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "Plan your day": conditions at the morning and evening departure times. */
@Composable
fun CommuteCard(summary: CommuteSummary) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(if (summary.isToday) R.string.commute_title_today else R.string.commute_title_tomorrow),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                summary.legs.forEach { LegColumn(it, timeFormatter, Modifier.weight(1f)) }
            }
            Text(
                text = stringResource(summary.advice.textRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (summary.advice == CommuteAdvice.Dry) FontWeight.Normal else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LegColumn(leg: CommuteLeg, timeFormatter: DateTimeFormatter, modifier: Modifier = Modifier) {
    val label = stringResource(if (leg.type == CommuteLegType.Morning) R.string.commute_morning else R.string.commute_evening)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.commute_leg_time, label, leg.departure.format(timeFormatter)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = leg.condition.emoji(leg.isDay),
                fontSize = 24.sp,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                text = stringResource(leg.rain.textRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (leg.rain == CommuteRain.Dry) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = buildString {
                append(formatTemperature(leg.temperatureC))
                leg.rainChance?.let { append(" · ").append(stringResource(R.string.commute_rain_chance, it)) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (leg.windy && leg.windSpeedKmh != null) {
            Text(
                text = stringResource(R.string.commute_windy, formatWindSpeed(leg.windSpeedKmh)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private val CommuteRain.textRes: Int
    get() = when (this) {
        CommuteRain.Dry -> R.string.commute_rain_dry
        CommuteRain.Possible -> R.string.commute_rain_possible
        CommuteRain.Likely -> R.string.commute_rain_likely
    }

private val CommuteAdvice.textRes: Int
    get() = when (this) {
        CommuteAdvice.RainLikely -> R.string.commute_advice_rain_likely
        CommuteAdvice.RainPossible -> R.string.commute_advice_rain_possible
        CommuteAdvice.Windy -> R.string.commute_advice_windy
        CommuteAdvice.Dry -> R.string.commute_advice_dry
    }
