package com.ah0890.dailyweather.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ah0890.dailyweather.R
import com.ah0890.dailyweather.domain.model.Place
import com.ah0890.dailyweather.domain.model.PlaceSuggestion
import com.ah0890.dailyweather.domain.model.PlaceType
import com.ah0890.dailyweather.domain.model.Places
import com.ah0890.dailyweather.presentation.weather.emoji
import com.ah0890.dailyweather.presentation.weather.labelRes
import com.ah0890.dailyweather.presentation.weather.title

class PlacesActions(
    val search: (String) -> Unit = {},
    val addSuggestion: (String, PlaceType, PlaceSuggestion) -> Unit = { _, _, _ -> },
    val addCurrentLocation: (String, PlaceType) -> Unit = { _, _ -> },
    val resetAdd: () -> Unit = {},
    val update: (String, String, PlaceType) -> Unit = { _, _, _ -> },
    val delete: (String) -> Unit = {},
    val setHome: (String?) -> Unit = {},
    val setOffice: (String?) -> Unit = {},
    val setDefault: (String?) -> Unit = {},
)

/** List of saved places with their roles, plus the add and edit dialogs. */
@Composable
fun PlacesSectionContent(places: Places, addState: AddPlaceState, actions: PlacesActions) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    if (places.places.isEmpty()) {
        Text(
            text = stringResource(R.string.places_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
    places.places.forEachIndexed { index, place ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PlaceRow(place, places, onClick = { editingId = place.id })
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (places.places.size < Places.MAX_PLACES) {
        TextButton(
            onClick = {
                actions.resetAdd()
                adding = true
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.places_add), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Text(
            text = pluralStringResource(R.plurals.places_full, Places.MAX_PLACES, Places.MAX_PLACES),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
    Text(
        text = stringResource(R.string.places_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    )

    places.find(editingId)?.let { place ->
        EditPlaceDialog(place, places, actions, onDismiss = { editingId = null })
    }
    if (adding) {
        val defaultType = when {
            places.home == null -> PlaceType.Home
            places.office == null -> PlaceType.Office
            else -> PlaceType.Other
        }
        AddPlaceDialog(
            state = addState,
            defaultType = defaultType,
            actions = actions,
            onDismiss = {
                adding = false
                actions.resetAdd()
            },
        )
    }
}

@Composable
private fun PlaceRow(place: Place, places: Places, onClick: () -> Unit) {
    val roles = listOfNotNull(
        R.string.places_role_home.takeIf { place.id == places.homeId },
        R.string.places_role_office.takeIf { place.id == places.officeId },
        R.string.places_role_default.takeIf { place.id == places.defaultId },
    ).map { stringResource(it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = place.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        val summary = (listOfNotNull(place.placeName) + roles).joinToString(" · ")
        if (summary.isNotEmpty()) {
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TypeChips(selected: PlaceType, onSelect: (PlaceType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaceType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.emoji + " " + stringResource(type.labelRes)) },
            )
        }
    }
}

@Composable
private fun EditPlaceDialog(place: Place, places: Places, actions: PlacesActions, onDismiss: () -> Unit) {
    var name by rememberSaveable(place.id) { mutableStateOf(place.name) }
    var type by rememberSaveable(place.id) { mutableStateOf(place.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.places_edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Places.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.places_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                place.placeName?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.places_type), style = MaterialTheme.typography.labelLarge)
                // Scrolls sideways on narrow screens instead of squeezing the chips.
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) { TypeChips(type) { type = it } }
                RoleSwitch(R.string.places_set_home, R.string.places_set_home_summary, place.id == places.homeId) {
                    actions.setHome(if (it) place.id else null)
                }
                RoleSwitch(R.string.places_set_office, R.string.places_set_office_summary, place.id == places.officeId) {
                    actions.setOffice(if (it) place.id else null)
                }
                RoleSwitch(R.string.places_set_default, R.string.places_set_default_summary, place.id == places.defaultId) {
                    actions.setDefault(if (it) place.id else null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.update(place.id, name, type)
                onDismiss()
            }) { Text(stringResource(R.string.places_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    actions.delete(place.id)
                    onDismiss()
                }) { Text(stringResource(R.string.places_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.places_cancel)) }
            }
        },
    )
}

@Composable
private fun RoleSwitch(title: Int, summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AddPlaceDialog(
    state: AddPlaceState,
    defaultType: PlaceType,
    actions: PlacesActions,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(defaultType) }
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val typeLabel = stringResource(type.labelRes)
    val finalName = name.ifBlank { typeLabel }

    LaunchedEffect(state) { if (state == AddPlaceState.Added) onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.places_add_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.places_type), style = MaterialTheme.typography.labelLarge)
                // Scrolls sideways on narrow screens instead of squeezing the chips.
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) { TypeChips(type) { type = it } }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Places.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.places_name)) },
                    placeholder = { Text(typeLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { actions.addCurrentLocation(finalName, type) },
                    enabled = state != AddPlaceState.Locating,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.places_use_current)) }
                Text(stringResource(R.string.places_or_search), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    placeholder = { Text(stringResource(R.string.places_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.search(query) }),
                    trailingIcon = {
                        IconButton(onClick = { actions.search(query) }) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.places_search))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AddStatus(state, onPick = { actions.addSuggestion(finalName, type, it) })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.places_cancel)) }
        },
    )
}

@Composable
private fun AddStatus(state: AddPlaceState, onPick: (PlaceSuggestion) -> Unit) {
    when (state) {
        AddPlaceState.Idle, AddPlaceState.Added -> Unit
        AddPlaceState.Searching -> Progress(R.string.places_searching)
        AddPlaceState.Locating -> Progress(R.string.places_locating)
        is AddPlaceState.Results ->
            if (state.suggestions.isEmpty()) {
                Message(R.string.places_no_results)
            } else {
                Column {
                    state.suggestions.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { onPick(suggestion) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(suggestion.name, style = MaterialTheme.typography.bodyLarge)
                            suggestion.region?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        is AddPlaceState.SearchFailed -> Message(R.string.places_search_failed, error = true)
        AddPlaceState.LocationFailed -> Message(R.string.places_location_failed, error = true)
        AddPlaceState.SaveFailed -> Message(R.string.places_save_failed, error = true)
        AddPlaceState.Full -> Text(
            text = pluralStringResource(R.plurals.places_full, Places.MAX_PLACES, Places.MAX_PLACES),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Progress(text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Message(text: Int, error: Boolean = false) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
