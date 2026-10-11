package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.TodayOutlook
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.domain.usecase.RainSummary
import com.ah0890.dailyweather.presentation.weather.formatPrecipitation
import com.ah0890.dailyweather.presentation.weather.rememberHourFormatter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** "Today's outlook": the umbrella decision first, then the rain facts behind it. */
@Composable
fun TodayOutlookCard(outlook: TodayOutlook, now: LocalDateTime) {
    val advice = outlook.recommendation.umbrella
    val rain = outlook.rain
    val hourFormatter = rememberHourFormatter()
    val (container, content) = adviceColors(advice)

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.outlook_title),
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = 0.8f),
                modifier = Modifier.semantics { heading() },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = advice.emoji,
                    fontSize = 36.sp,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clearAndSetSemantics { },
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(advice.headlineRes), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = rainSummaryText(RainAnalysis.summary(rain, now), hourFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            HorizontalDivider(color = content.copy(alpha = 0.15f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Fact(
                    label = stringResource(R.string.outlook_rain_chance),
                    value = rain.maxProbability?.let { "$it%" } ?: "–",
                    modifier = Modifier.weight(1f),
                )
                Fact(
                    label = stringResource(R.string.outlook_rain_amount),
                    value = rain.totalMm?.let { formatPrecipitation(it) } ?: "–",
                    modifier = Modifier.weight(1f),
                )
                Fact(
                    label = stringResource(R.string.outlook_rain_times),
                    value = rain.periods.takeIf { it.isNotEmpty() }
                        ?.map { timeRange(it.start, it.end, hourFormatter) }
                        ?.joinToString(", ")
                        ?: stringResource(R.string.outlook_rain_times_none),
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.75f),
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun rainSummaryText(summary: RainSummary, formatter: DateTimeFormatter): String = when (summary) {
    is RainSummary.NowUntil -> stringResource(R.string.rain_now_until, summary.until.format(formatter))
    is RainSummary.Between ->
        stringResource(R.string.rain_between, summary.start.format(formatter), summary.end.format(formatter))
    is RainSummary.ChanceAround ->
        stringResource(R.string.rain_chance_around, summary.percent, summary.time.format(formatter))
    is RainSummary.LightRain -> stringResource(R.string.rain_light, formatPrecipitation(summary.totalMm))
    RainSummary.Low -> stringResource(R.string.rain_low)
}

@Composable
private fun timeRange(start: LocalDateTime, end: LocalDateTime, formatter: DateTimeFormatter): String =
    stringResource(R.string.time_range, start.format(formatter), end.format(formatter))

/** Stronger colour the more an umbrella is needed; the headline text always states it too. */
@Composable
private fun adviceColors(advice: UmbrellaAdvice): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (advice) {
        UmbrellaAdvice.Recommended -> primaryContainer to onPrimaryContainer
        UmbrellaAdvice.Consider -> secondaryContainer to onSecondaryContainer
        UmbrellaAdvice.NotNeeded -> surfaceContainerLowest to onSurface
    }
}

private val UmbrellaAdvice.emoji: String
    get() = when (this) {
        UmbrellaAdvice.Recommended -> "☂️"
        UmbrellaAdvice.Consider -> "🌂"
        UmbrellaAdvice.NotNeeded -> "🌤️"
    }

private val UmbrellaAdvice.headlineRes: Int
    get() = when (this) {
        UmbrellaAdvice.Recommended -> R.string.umbrella_recommended
        UmbrellaAdvice.Consider -> R.string.umbrella_consider
        UmbrellaAdvice.NotNeeded -> R.string.umbrella_not_needed
    }
