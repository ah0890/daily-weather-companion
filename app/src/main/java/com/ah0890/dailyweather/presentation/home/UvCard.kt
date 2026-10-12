package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.UvCategory
import com.ah0890.dailyweather.domain.model.UvOutlook
import com.ah0890.dailyweather.presentation.weather.dot
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.presentation.weather.rememberHourFormatter
import com.ah0890.dailyweather.ui.theme.cardContainer
import kotlin.math.roundToInt

/**
 * "UV status": today's peak category and when it is high. Stands out when it reaches the
 * user's UV alert level; the advice stays practical, never medical.
 */
@Composable
fun UvCard(uv: UvOutlook, highlighted: Boolean) {
    val hours = rememberHourFormatter()
    val level = stringResource(uv.category.labelRes)
    val index = uv.peakIndex.roundToInt()
    val (container, content) = with(MaterialTheme.colorScheme) {
        if (highlighted) tertiaryContainer to onTertiaryContainer else cardContainer to onSurface
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = uv.category.dot + " " + (
                    uv.peakTime?.let { stringResource(R.string.uv_status, level, index, it.format(hours)) }
                        ?: stringResource(R.string.uv_status_no_time, level, index)
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            val from = uv.highFrom
            val until = uv.highUntil
            if (from != null && until != null) {
                Text(
                    text = stringResource(R.string.uv_high_hours, from.format(hours), until.format(hours)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uv.category >= UvCategory.Moderate) {
                Text(text = stringResource(R.string.uv_tip), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
