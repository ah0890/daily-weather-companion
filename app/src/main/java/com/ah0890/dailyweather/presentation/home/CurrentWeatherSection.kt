package com.ah0890.dailyweather.presentation.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.WeatherForecast
import com.ah0890.dailyweather.domain.usecase.RainAnalysis
import com.ah0890.dailyweather.presentation.components.RelativeAge
import com.ah0890.dailyweather.presentation.components.text
import com.ah0890.dailyweather.presentation.weather.HourlyForecastRow
import com.ah0890.dailyweather.presentation.weather.WeatherErrorCard
import com.ah0890.dailyweather.presentation.weather.compassDirection
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.formatTemperature
import com.ah0890.dailyweather.presentation.weather.formatVisibility
import com.ah0890.dailyweather.presentation.weather.formatWindSpeed
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.presentation.weather.mood
import com.ah0890.dailyweather.presentation.weather.uvLevelRes
import com.ah0890.dailyweather.ui.theme.LocalDarkTheme
import com.ah0890.dailyweather.ui.theme.WeatherBackdrop
import com.ah0890.dailyweather.ui.theme.WeatherMood
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Everything weather-related below the location header.
 *
 * @param edgePadding the screen's horizontal padding, so scrolling rows can reach the edges.
 */
@Composable
fun CurrentWeatherSection(state: WeatherUiState, edgePadding: Dp, onRetry: () -> Unit) {
    when (state) {
        WeatherUiState.Idle -> Unit
        WeatherUiState.Loading -> LoadingCard()
        is WeatherUiState.Error -> WeatherErrorCard(state.error, onRetry)
        is WeatherUiState.Loaded -> {
            CurrentWeatherCard(state.forecast, state.fetchedAt)
            if (state.refreshError != null) RefreshFailedNotice(state.fetchedAt)
            val outlook = remember(state.forecast) { RainAnalysis.outlookForRestOfToday(state.forecast) }
            TodayOutlookCard(outlook, now = state.forecast.current.time)
            HourlyForecastRow(
                hours = state.forecast.upcomingHours(),
                title = stringResource(R.string.hourly_title),
                edgePadding = edgePadding,
                firstIsNow = true,
            )
            WeatherDetails(state.forecast)
        }
    }
}

@Composable
private fun CurrentWeatherCard(forecast: WeatherForecast, fetchedAt: Instant) {
    val current = forecast.current
    val today = forecast.today
    val backdrop = WeatherBackdrop.forMood(current.condition.mood(current.isDay), LocalDarkTheme.current)
    val conditionLabel = stringResource(current.condition.labelRes)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backdrop.brush)
            .padding(24.dp)
            // Read as one sentence by screen readers instead of separate fragments.
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTemperature(current.temperatureC),
                    style = MaterialTheme.typography.displayLarge,
                    color = backdrop.content,
                )
                Text(
                    text = conditionLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = backdrop.content,
                )
            }
            Text(
                text = current.condition.emoji(current.isDay),
                fontSize = 64.sp,
                // Decorative: the condition is already spelled out next to it.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.weather_feels_like, formatTemperature(current.feelsLikeC)),
            style = MaterialTheme.typography.bodyLarge,
            color = backdrop.content,
        )
        if (today != null) {
            val high = formatTemperature(today.maxTemperatureC)
            val low = formatTemperature(today.minTemperatureC)
            val description = stringResource(R.string.weather_high_low_description, high, low)
            Text(
                text = stringResource(R.string.weather_high_low, high, low),
                style = MaterialTheme.typography.titleMedium,
                color = backdrop.content,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.weather_updated, RelativeAge.between(fetchedAt, Instant.now()).text()),
            style = MaterialTheme.typography.labelMedium,
            color = backdrop.content.copy(alpha = 0.8f),
        )
    }
}

private data class Detail(@StringRes val label: Int, val value: String)

/** Secondary readings, deliberately smaller than the main card. Missing values are omitted. */
@Composable
private fun WeatherDetails(forecast: WeatherForecast) {
    val current = forecast.current
    val today = forecast.today
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    val details = buildList {
        current.humidityPercent?.let { add(Detail(R.string.weather_humidity, "$it%")) }
        current.windSpeedKmh?.let { speed ->
            val direction = current.windDirectionDegrees?.let { " ${compassDirection(it)}" }.orEmpty()
            add(Detail(R.string.weather_wind, formatWindSpeed(speed) + direction))
        }
        current.uvIndex?.let { uv ->
            add(Detail(R.string.weather_uv, stringResource(R.string.uv_value, uv.roundToInt(), stringResource(uvLevelRes(uv)))))
        }
        current.visibilityMeters?.let { add(Detail(R.string.weather_visibility, formatVisibility(it))) }
        today?.sunrise?.let { add(Detail(R.string.weather_sunrise, it.format(timeFormatter))) }
        today?.sunset?.let { add(Detail(R.string.weather_sunset, it.format(timeFormatter))) }
    }
    if (details.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.weather_details_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        details.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { DetailTile(it, Modifier.weight(1f)) }
                // Keep tile widths equal on a short last row.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DetailTile(detail: Detail, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(detail.label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detail.value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    val backdrop = WeatherBackdrop.forMood(WeatherMood.Cloudy, LocalDarkTheme.current)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backdrop.brush)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = backdrop.content)
            Text(
                text = stringResource(R.string.weather_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = backdrop.content,
            )
        }
    }
}

@Composable
private fun RefreshFailedNotice(fetchedAt: Instant) {
    val time = remember(fetchedAt) {
        fetchedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.weather_refresh_failed, time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }
}
