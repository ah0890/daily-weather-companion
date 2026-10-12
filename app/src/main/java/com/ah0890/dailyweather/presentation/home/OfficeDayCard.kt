package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.CommuteSummary
import com.ah0890.dailyweather.domain.model.LegAdvice
import com.ah0890.dailyweather.domain.model.OfficeWeatherScore
import com.ah0890.dailyweather.domain.model.ScoreBand
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.presentation.weather.dot
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatWindSpeed
import com.ah0890.dailyweather.presentation.weather.headlineRes
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.presentation.weather.rememberHourFormatter
import com.ah0890.dailyweather.presentation.weather.temperatureText
import com.ah0890.dailyweather.presentation.weather.textRes
import com.ah0890.dailyweather.ui.theme.cardContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.format.FormatStyle

/**
 * "Office day": the score, then each trip with a verdict for the user's way of travelling, the
 * facts behind it and practical tips.
 */
@Composable
fun OfficeDayCard(
    summary: CommuteSummary,
    advice: List<LegAdvice>,
    score: OfficeWeatherScore,
    mode: TransportMode,
    today: LocalDate,
    onChangeTransport: () -> Unit,
    homeName: String? = null,
    officeName: String? = null,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayTitle(summary.date, today, R.string.office_day_title_today, R.string.office_day_title_tomorrow, R.string.office_day_title_on),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                ScoreChip(score)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.office_day_transport, mode.emoji, stringResource(mode.labelRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChangeTransport) { Text(stringResource(R.string.office_day_change_transport)) }
            }
            advice.forEachIndexed { index, legAdvice ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LegSection(legAdvice, homeName, officeName)
            }
            Text(
                text = stringResource(R.string.score_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreChip(score: OfficeWeatherScore) {
    val band = stringResource(score.band.labelRes)
    val description = stringResource(R.string.score_description, score.value, band)
    val (container, content) = with(MaterialTheme.colorScheme) {
        when (score.band) {
            ScoreBand.Good -> primaryContainer to onPrimaryContainer
            ScoreBand.Moderate -> secondaryContainer to onSecondaryContainer
            ScoreBand.Poor -> errorContainer to onErrorContainer
        }
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = stringResource(R.string.score_value, score.value, band),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LegSection(advice: LegAdvice, homeName: String?, officeName: String?) {
    val leg = advice.leg
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val hourFormatter = rememberHourFormatter()
    val morning = leg.type == CommuteLegType.Morning

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(if (morning) R.string.office_day_morning else R.string.office_day_evening),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    R.string.office_day_route_time,
                    route(morning, homeName ?: stringResource(R.string.place_type_home), officeName ?: stringResource(R.string.place_type_office)),
                    leg.departure.format(timeFormatter),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(text = advice.mode.emoji, fontSize = 26.sp, modifier = Modifier.clearAndSetSemantics { })
            Column {
                Text(
                    text = "${advice.rating.dot} " + stringResource(headlineRes(advice.mode, advice.rating)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (advice.rating == TravelRating.Favorable) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildList {
                        add(leg.condition.emoji(leg.isDay) + " " + temperatureText(leg.temperatureC))
                        leg.rainChance?.let { add(stringResource(R.string.office_day_rain, it)) }
                        leg.windSpeedKmh?.let { wind ->
                            val gusts = leg.windGustsKmh?.takeIf { it >= wind + 10 }
                                ?.let { " (" + stringResource(R.string.office_day_gusts, formatWindSpeed(it)) + ")" }
                                .orEmpty()
                            add(stringResource(R.string.office_day_wind, formatWindSpeed(wind)) + gusts)
                        }
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val peak = leg.rainPeakTime
        if (peak != null && (leg.rainChance ?: 0) >= RainAnalysis.UMBRELLA_CONSIDER_PERCENT) {
            Text(
                text = stringResource(R.string.office_day_rain_peak, peak.format(hourFormatter)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Rain is already covered by the rain chance and tips; list the other concerns.
        advice.concerns.filter { it !in RAIN }.forEach { hazard ->
            Text(
                text = hazard.emoji + " " + stringResource(hazard.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        advice.tips.forEach { tip ->
            Text(
                text = stringResource(tip.textRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "Home → Office" in the morning, "Office → Home" in the evening. */
@Composable
private fun route(morning: Boolean, home: String, office: String): String =
    if (morning) stringResource(R.string.office_day_route, home, office) else stringResource(R.string.office_day_route, office, home)

/** "Office day", "Tomorrow's office day" or "Office day · Monday". */
@Composable
fun dayTitle(date: LocalDate, today: LocalDate, todayRes: Int, tomorrowRes: Int, onDayRes: Int): String = when (date) {
    today -> stringResource(todayRes)
    today.plusDays(1) -> stringResource(tomorrowRes)
    else -> stringResource(onDayRes, date.dayOfWeek.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale))
}

private val RAIN = setOf(WeatherHazard.RainLikely, WeatherHazard.RainPossible)
