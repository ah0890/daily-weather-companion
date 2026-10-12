package com.ah0890.dailyweather.presentation.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.AirQuality
import com.ah0890.dailyweather.domain.model.AqiCategory
import com.ah0890.dailyweather.domain.model.Pollutant
import com.ah0890.dailyweather.presentation.weather.rememberHourFormatter
import com.ah0890.dailyweather.ui.theme.cardContainer
import java.util.Locale

/** "🌫️ Air quality: Moderate" up front; pollutant details only when asked for. */
@Composable
fun AirQualityCard(state: AirQualityUiState) {
    when (state) {
        AirQualityUiState.Idle, AirQualityUiState.Loading -> return
        AirQualityUiState.Unavailable -> CardShell { Line("🌫️ " + stringResource(R.string.air_unavailable)) }
        is AirQualityUiState.Available -> Details(state.airQuality)
    }
}

@Composable
private fun Details(air: AirQuality) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hours = rememberHourFormatter()
    val category = stringResource(air.category.labelRes)
    val toggle = stringResource(if (expanded) R.string.air_hide_details else R.string.air_show_details)

    CardShell(onClick = { expanded = !expanded }, onClickLabel = toggle) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = "${air.category.dot} " + stringResource(R.string.air_title, category),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(
                    stringResource(R.string.air_index, air.usAqi),
                    air.mainPollutant?.let { stringResource(R.string.air_main_pollutant, stringResource(it.labelRes)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val peak = air.peakUsAqi
        val peakTime = air.peakTime
        if (peak != null && peakTime != null && AqiCategory.of(peak) > air.category) {
            Line(stringResource(R.string.air_peak, stringResource(AqiCategory.of(peak).labelRes), peakTime.format(hours)))
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                air.readings.forEach { reading ->
                    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(
                            text = stringResource(reading.pollutant.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val amount = reading.concentration?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "–"
                        Text(
                            text = reading.index?.let { stringResource(R.string.air_reading, amount, it) }
                                ?: stringResource(R.string.air_reading_no_index, amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.air_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CardShell(onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = (if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

@Composable
private fun Line(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** Colour dot for the EPA category; the category name is always written next to it. */
private val AqiCategory.dot: String
    get() = when (this) {
        AqiCategory.Good -> "🟢"
        AqiCategory.Moderate -> "🟡"
        AqiCategory.UnhealthyForSensitiveGroups -> "🟠"
        AqiCategory.Unhealthy -> "🔴"
        AqiCategory.VeryUnhealthy -> "🟣"
        AqiCategory.Hazardous -> "🟤"
    }

@get:StringRes
private val AqiCategory.labelRes: Int
    get() = when (this) {
        AqiCategory.Good -> R.string.aqi_good
        AqiCategory.Moderate -> R.string.aqi_moderate
        AqiCategory.UnhealthyForSensitiveGroups -> R.string.aqi_sensitive
        AqiCategory.Unhealthy -> R.string.aqi_unhealthy
        AqiCategory.VeryUnhealthy -> R.string.aqi_very_unhealthy
        AqiCategory.Hazardous -> R.string.aqi_hazardous
    }

@get:StringRes
private val Pollutant.labelRes: Int
    get() = when (this) {
        Pollutant.Pm25 -> R.string.pollutant_pm25
        Pollutant.Pm10 -> R.string.pollutant_pm10
        Pollutant.Ozone -> R.string.pollutant_ozone
        Pollutant.NitrogenDioxide -> R.string.pollutant_no2
        Pollutant.SulphurDioxide -> R.string.pollutant_so2
        Pollutant.CarbonMonoxide -> R.string.pollutant_co
    }
