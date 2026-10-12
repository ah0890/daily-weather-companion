package com.ah0890.dailyweather.presentation.forecast

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.DailyForecast
import com.ah0890.dailyweather.domain.model.HourlyForecast
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.usecase.CommuteStatus
import com.ah0890.dailyweather.domain.usecase.DayCommute
import com.ah0890.dailyweather.domain.usecase.WorkWeek
import com.ah0890.dailyweather.domain.usecase.WorkWeekAnalysis
import com.ah0890.dailyweather.presentation.components.LocalUserPreferences
import com.ah0890.dailyweather.presentation.home.HomeViewModel
import com.ah0890.dailyweather.presentation.home.WeatherUiState
import com.ah0890.dailyweather.presentation.weather.HourlyForecastRow
import com.ah0890.dailyweather.presentation.weather.StaleForecastNotice
import com.ah0890.dailyweather.presentation.weather.WeatherErrorCard
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatPrecipitation
import com.ah0890.dailyweather.presentation.weather.formatWindSpeed
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.presentation.weather.temperatureText
import com.ah0890.dailyweather.presentation.weather.uvLevelRes
import com.ah0890.dailyweather.ui.theme.cardContainer
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt

private val ScreenPadding = 20.dp
private val CardPadding = 16.dp

/** Shares the Today screen's ViewModel, so switching tabs never triggers another download. */
@Composable
fun ForecastRoute(viewModel: HomeViewModel) {
    val location by viewModel.locationState.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshWeatherIfStale()
        onPauseOrDispose { }
    }

    ForecastScreen(
        locationName = location.location?.location?.name,
        weather = weather,
        onRefresh = viewModel::refreshWeather,
    )
}

@Composable
fun ForecastScreen(
    locationName: String?,
    weather: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only one day is expanded at a time; remembered across rotation.
    var expandedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val preferences = LocalUserPreferences.current
    val loaded = weather as? WeatherUiState.Loaded
    val week = remember(loaded?.forecast, preferences.commuteTimes, preferences.transportMode, preferences.workdays) {
        loaded?.let { WorkWeekAnalysis.analyse(it.forecast, preferences.commuteTimes, preferences.transportMode, preferences.workdays) }
            ?: WorkWeek(emptyMap(), best = null, riskiest = null)
    }

    PullToRefreshBox(
        isRefreshing = (weather as? WeatherUiState.Loaded)?.isRefreshing == true,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { Header(locationName) }

            when (weather) {
                WeatherUiState.Idle -> item(key = "idle") {
                    Text(
                        text = stringResource(R.string.forecast_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WeatherUiState.Loading -> item(key = "loading") { Loading() }
                is WeatherUiState.Error -> item(key = "error") { WeatherErrorCard(weather.error, onRefresh) }
                is WeatherUiState.Loaded -> {
                    val forecast = weather.forecast
                    val today = forecast.current.time.toLocalDate()
                    val span = TemperatureSpan.of(forecast.daily)
                    item(key = "stale") { StaleForecastNotice(weather.fetchedAt, weather.refreshError) }
                    if (week.best != null || week.riskiest != null) {
                        item(key = "week") { WeekPlanCard(week) }
                    }
                    items(forecast.daily, key = { it.date.toString() }) { day ->
                        val key = day.date.toString()
                        DayCard(
                            day = day,
                            label = DayLabel.of(day.date, today),
                            span = span,
                            hours = forecast.hoursOn(day.date),
                            commute = week.days[day.date],
                            expanded = expandedDate == key,
                            onToggle = { expandedDate = if (expandedDate == key) null else key },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(locationName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.forecast_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (locationName != null) {
            Text(
                text = locationName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Best and highest-risk commute days, with a reminder that later days are less certain. */
@Composable
private fun WeekPlanCard(week: WorkWeek) {
    val locale = LocalLocale.current.platformLocale
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.week_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            week.best?.let {
                Text(
                    text = stringResource(R.string.week_best, it.dayOfWeek.getDisplayName(TextStyle.FULL, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            week.riskiest?.let {
                Text(
                    text = stringResource(R.string.week_riskiest, it.dayOfWeek.getDisplayName(TextStyle.FULL, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.week_uncertainty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun commuteStatusText(status: CommuteStatus): String = stringResource(
    when (status) {
        CommuteStatus.Good -> R.string.week_status_good
        CommuteStatus.TakeCare -> R.string.week_status_take_care
        CommuteStatus.CarryUmbrella ->
            if (LocalUserPreferences.current.transportMode == TransportMode.Motorcycle) R.string.week_status_rain_gear else R.string.week_status_umbrella
        CommuteStatus.HighRainRisk -> R.string.week_status_high_rain
        CommuteStatus.Difficult -> R.string.week_status_difficult
    },
)

private val CommuteStatus.dot: String
    get() = when (this) {
        CommuteStatus.Good -> "🟢"
        CommuteStatus.TakeCare, CommuteStatus.CarryUmbrella -> "🟡"
        CommuteStatus.HighRainRisk, CommuteStatus.Difficult -> "🔴"
    }

@Composable
private fun Loading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 24.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(
            text = stringResource(R.string.forecast_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DayCard(
    day: DailyForecast,
    label: DayLabel,
    span: TemperatureSpan?,
    hours: List<HourlyForecast>,
    commute: DayCommute?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val shortDate = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    }
    val longDate = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    val name = when (label) {
        DayLabel.Today -> stringResource(R.string.forecast_today)
        DayLabel.Tomorrow -> stringResource(R.string.forecast_tomorrow)
        is DayLabel.Weekday -> label.day.getDisplayName(TextStyle.SHORT, locale)
    }
    val high = temperatureText(day.maxTemperatureC)
    val low = temperatureText(day.minTemperatureC)
    val condition = stringResource(day.condition.labelRes)
    val chance = day.precipitationProbabilityMax

    val commuteText = commute?.let { commuteStatusText(it.status) }
    val description = buildString {
        if (label !is DayLabel.Weekday) append(name).append(", ")
        append(day.date.format(longDate)).append(", ").append(condition)
        if (chance != null) append(", ").append(stringResource(R.string.hourly_rain_chance_description, chance))
        append(", ").append(stringResource(R.string.weather_high_low_description, high, low))
        if (commuteText != null) append(", ").append(commuteText)
    }
    val toggleLabel = stringResource(if (expanded) R.string.forecast_hide_details else R.string.forecast_show_details)
    val stateText = stringResource(if (expanded) R.string.forecast_expanded else R.string.forecast_collapsed)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = toggleLabel, onClick = onToggle)
                .clearAndSetSemantics {
                    contentDescription = description
                    stateDescription = stateText
                }
                .padding(horizontal = CardPadding, vertical = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    text = day.date.format(shortDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (commute != null && commuteText != null) {
                    Text(
                        text = commute.status.dot + " " + commuteText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
            Text(text = day.condition.emoji(isDay = true), fontSize = 24.sp)
            Text(
                text = chance?.let { "$it%" } ?: "–",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            Text(
                text = low,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 36.dp),
            )
            if (span != null) RangeBar(day, span, Modifier.width(56.dp))
            Text(
                text = high,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 36.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            DayDetails(day, condition, hours)
        }
    }
}

/** This day's low–high range drawn against the whole week's range. */
@Composable
private fun RangeBar(day: DailyForecast, span: TemperatureSpan, modifier: Modifier = Modifier) {
    val start = span.fractionOf(day.minTemperatureC)
    val end = span.fractionOf(day.maxTemperatureC).coerceAtLeast(start)
    // Weights must be positive; tiny values keep the layout valid for edge positions.
    val minWeight = 0.0001f
    Row(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Spacer(Modifier.weight(start.coerceAtLeast(minWeight)))
        Box(
            Modifier
                .weight((end - start).coerceAtLeast(0.08f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                    ),
                ),
        )
        Spacer(Modifier.weight((1f - end).coerceAtLeast(minWeight)))
    }
}

@Composable
private fun DayDetails(day: DailyForecast, condition: String, hours: List<HourlyForecast>) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val details = buildList {
        add(stringResource(R.string.forecast_condition) to condition)
        day.precipitationProbabilityMax?.let { add(stringResource(R.string.forecast_rain_chance) to "$it%") }
        day.precipitationSumMm?.let { add(stringResource(R.string.forecast_rain_amount) to formatPrecipitation(it)) }
        day.windSpeedMaxKmh?.let { add(stringResource(R.string.forecast_max_wind) to formatWindSpeed(it)) }
        day.uvIndexMax?.let {
            add(stringResource(R.string.forecast_max_uv) to stringResource(R.string.uv_value, it.roundToInt(), stringResource(uvLevelRes(it))))
        }
        day.sunrise?.let { add(stringResource(R.string.weather_sunrise) to it.format(timeFormatter)) }
        day.sunset?.let { add(stringResource(R.string.weather_sunset) to it.format(timeFormatter)) }
    }

    Column(
        modifier = Modifier.padding(start = CardPadding, end = CardPadding, bottom = CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        details.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        HourlyForecastRow(
            hours = hours,
            title = stringResource(R.string.forecast_hour_by_hour),
            edgePadding = CardPadding,
        )
    }
}
