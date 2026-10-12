package com.ah0890.dailyweather.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.domain.model.TravelRating
import com.ah0890.dailyweather.domain.model.UmbrellaAdvice
import com.ah0890.dailyweather.domain.model.WeatherCondition
import com.ah0890.dailyweather.domain.model.WeatherHazard
import com.ah0890.dailyweather.domain.usecase.RainSummary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** One scrolling screen: temperature, rain chance and timing, the next trip and any severe alert. */
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { WearApp() }
        }
    }
}

@Composable
private fun WearApp(viewModel: WearViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.load() }
    LaunchedEffect(Unit) { viewModel.load() }

    val lines = (state as? WearUiState.Loaded)?.let { summaryItems(it.summary) }.orEmpty()
    val title = stringResource(R.string.wear_title)
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item { Line(title, size = 14) }
        when (val s = state) {
            WearUiState.Loading -> item { Line(stringResource(R.string.wear_loading)) }
            WearUiState.NeedsPermission -> {
                item { Line(stringResource(R.string.wear_permission)) }
                item {
                    Button(onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                        Text(stringResource(R.string.wear_allow))
                    }
                }
            }
            WearUiState.NoLocation, WearUiState.NoConnection -> {
                item {
                    Line(stringResource(if (s == WearUiState.NoLocation) R.string.wear_no_location else R.string.wear_no_connection))
                }
                item { Button(onClick = viewModel::load) { Text(stringResource(R.string.wear_retry)) } }
            }
            is WearUiState.Loaded -> lines.forEach { (text, size) -> item { Line(text, size) } }
        }
    }
}

/** Text and font size for each line of the summary. */
@Composable
private fun summaryItems(summary: WatchSummary): List<Pair<String, Int>> {
    val hours = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    return buildList {
        add("${summary.condition.emoji(summary.isDay)} ${summary.temperatureC.roundToInt()}°" to 30)
        summary.rainChance?.let { add(stringResource(R.string.wear_rain, it) to 16) }
        add(
            when (val rain = summary.rain) {
                is RainSummary.NowUntil -> stringResource(R.string.wear_rain_now_until, rain.until.format(hours))
                is RainSummary.Between -> stringResource(R.string.wear_rain_between, rain.start.format(hours), rain.end.format(hours))
                is RainSummary.ChanceAround -> stringResource(R.string.wear_rain_around, rain.time.format(hours))
                is RainSummary.LightRain, RainSummary.Low -> stringResource(R.string.wear_rain_low)
            } to 14,
        )
        if (summary.umbrella == UmbrellaAdvice.Recommended) add(stringResource(R.string.wear_umbrella) to 14)
        summary.nextTrip?.let { trip ->
            val leg = stringResource(if (trip.leg.type == CommuteLegType.Morning) R.string.wear_morning else R.string.wear_evening)
            val verdict = stringResource(
                when (trip.rating) {
                    TravelRating.Favorable -> R.string.wear_commute_good
                    TravelRating.Caution -> R.string.wear_commute_caution
                    TravelRating.Unfavorable -> R.string.wear_commute_bad
                },
            )
            add("$leg · ${trip.leg.departure.format(hours)}\n$verdict" to 14)
        }
        summary.alert?.let { add(stringResource(R.string.wear_alert, stringResource(it.hazard.wearLabel)) to 14) }
        add(stringResource(R.string.wear_note) to 11)
    }
}

@Composable
private fun Line(text: String, size: Int = 14) {
    Text(text = text, fontSize = size.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

private val WeatherHazard.wearLabel: Int
    get() = when (this) {
        WeatherHazard.Thunderstorm -> R.string.wear_hazard_thunderstorm
        WeatherHazard.Gale -> R.string.wear_hazard_gale
        WeatherHazard.DenseFog -> R.string.wear_hazard_fog
        WeatherHazard.ExtremeHeat -> R.string.wear_hazard_heat
        WeatherHazard.Wintry -> R.string.wear_hazard_wintry
        else -> R.string.wear_hazard_heavy_rain
    }

/** Same icons as the phone app; night never shows a sun. */
private fun WeatherCondition.emoji(isDay: Boolean): String = when (this) {
    WeatherCondition.Clear -> if (isDay) "☀️" else "🌙"
    WeatherCondition.MainlyClear -> if (isDay) "🌤️" else "🌙"
    WeatherCondition.PartlyCloudy -> if (isDay) "⛅" else "☁️"
    WeatherCondition.Overcast -> "☁️"
    WeatherCondition.Fog -> "🌫️"
    WeatherCondition.Drizzle, WeatherCondition.RainShowers -> if (isDay) "🌦️" else "🌧️"
    WeatherCondition.FreezingDrizzle, WeatherCondition.Rain, WeatherCondition.FreezingRain -> "🌧️"
    WeatherCondition.Snow, WeatherCondition.SnowShowers -> "🌨️"
    WeatherCondition.Thunderstorm -> "⛈️"
    WeatherCondition.Unknown -> "🌡️"
}
