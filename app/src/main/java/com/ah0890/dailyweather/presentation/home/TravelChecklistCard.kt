package com.ah0890.dailyweather.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.ChecklistEntry
import com.ah0890.dailyweather.domain.model.ChecklistItem
import com.ah0890.dailyweather.domain.model.ChecklistLevel
import com.ah0890.dailyweather.domain.model.CommuteLegType
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.ui.theme.cardContainer
import java.time.LocalDate

/** A short list of what to take along. Not shown when there is nothing worth mentioning. */
@Composable
fun TravelChecklistCard(entries: List<ChecklistEntry>, date: LocalDate, today: LocalDate) {
    if (entries.isEmpty()) return
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = dayTitle(date, today, R.string.checklist_title_today, R.string.checklist_title_tomorrow, R.string.checklist_title_on),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            entries.forEach { ChecklistRow(it) }
        }
    }
}

@Composable
private fun ChecklistRow(entry: ChecklistEntry) {
    val value = if (entry.item == ChecklistItem.AlternativeTransport) {
        stringResource(
            when {
                CommuteLegType.Morning in entry.legs && CommuteLegType.Evening in entry.legs -> R.string.checklist_car_both
                CommuteLegType.Morning in entry.legs -> R.string.checklist_car_morning
                else -> R.string.checklist_car_return
            },
        )
    } else {
        stringResource(entry.level.labelRes)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = entry.item.emoji,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .clearAndSetSemantics { },
        )
        Text(
            text = stringResource(entry.item.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.level == ChecklistLevel.Recommended) FontWeight.SemiBold else FontWeight.Normal,
            color = if (entry.level == ChecklistLevel.Recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
