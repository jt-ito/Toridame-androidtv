package org.jellyfin.androidtv.ui.settings.screen.customization

import android.widget.Toast
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.HiddenEntry
import org.jellyfin.androidtv.data.repository.HiddenInfo
import org.jellyfin.androidtv.data.repository.PinResult
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.shared.askPin
import org.jellyfin.androidtv.ui.shared.explain
import org.jellyfin.androidtv.ui.shared.forgetRememberedPin
import org.koin.compose.koinInject

/**
 * The titles hidden from lists (the web client's hidden content), with the PIN that protects the list. Hidden titles
 * can still be opened and played; they just do not show up in lists.
 */
@Composable
fun SettingsHiddenContentScreen() {
	val repository = koinInject<ToridameRepository>()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var info by remember { mutableStateOf<HiddenInfo?>(null) }
	var entries by remember { mutableStateOf<List<HiddenEntry>?>(null) }

	suspend fun refreshInfo() {
		info = repository.hiddenInfo()
	}

	/** Loads the list, asking for the PIN when there is one. */
	suspend fun loadEntries() {
		val hasPin = repository.hiddenInfo()?.hasPin == true
		val pin = if (hasPin) context.askPin(context.getString(R.string.hidden_enter_pin)) ?: return else null

		val (result, list) = repository.hiddenList(pin)
		if (result != PinResult.OK) context.explain(result) else entries = list
		refreshInfo()
	}

	LaunchedEffect(Unit) { refreshInfo() }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.hidden_title)) },
				captionContent = {
					Text(
						info?.let { stringResource(R.string.hidden_count, it.count, it.limit) }
							?: stringResource(R.string.hidden_needs_server)
					)
				},
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.hidden_show_list)) },
				captionContent = { Text(stringResource(if (info?.hasPin == true) R.string.hidden_pin_protected else R.string.hidden_no_pin)) },
				onClick = { scope.launch { loadEntries() } },
				modifier = Modifier.focusKey("hidden_show"),
			)
		}

		items(entries.orEmpty()) { entry ->
			ListButton(
				headingContent = { Text(entry.name) },
				captionContent = { Text(listOfNotNull(entry.type, entry.seriesName).joinToString(" · ") + "  —  " + stringResource(R.string.hidden_select_to_show)) },
				onClick = {
					scope.launch {
						val pin = if (info?.hasPin == true) context.askPin(context.getString(R.string.hidden_enter_pin)) ?: return@launch else null
						val result = repository.unhide(entry.itemId, pin)
						if (result == PinResult.OK) entries = entries.orEmpty().filterNot { it.itemId == entry.itemId } else context.explain(result)
						refreshInfo()
					}
				},
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.hidden_show_all)) },
				onClick = {
					scope.launch {
						val pin = if (info?.hasPin == true) context.askPin(context.getString(R.string.hidden_enter_pin)) ?: return@launch else null
						val result = repository.unhideAll(pin)
						if (result == PinResult.OK) {
							entries = emptyList()
							Toast.makeText(context, context.getString(R.string.hidden_all_shown), Toast.LENGTH_SHORT).show()
						} else {
							context.explain(result)
						}
						refreshInfo()
					}
				},
				modifier = Modifier.focusKey("hidden_show_all"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(if (info?.hasPin == true) R.string.hidden_change_pin else R.string.hidden_set_pin)) },
				onClick = {
					scope.launch {
						val current = if (info?.hasPin == true) context.askPin(context.getString(R.string.hidden_current_pin), useRemembered = false) ?: return@launch else null
						val new = context.askPin(context.getString(R.string.hidden_new_pin), useRemembered = false) ?: return@launch
						val result = repository.setPin(new, current)
						if (result == PinResult.OK) {
							forgetRememberedPin()
							Toast.makeText(context, context.getString(R.string.hidden_pin_saved), Toast.LENGTH_SHORT).show()
						} else {
							context.explain(result)
						}
						refreshInfo()
					}
				},
				modifier = Modifier.focusKey("hidden_set_pin"),
			)
		}

		if (info?.hasPin == true) {
			item {
				ListButton(
					headingContent = { Text(stringResource(R.string.hidden_remove_pin)) },
					onClick = {
						scope.launch {
							val pin = context.askPin(context.getString(R.string.hidden_current_pin), useRemembered = false) ?: return@launch
							val result = repository.removePin(pin)
							if (result == PinResult.OK) {
								forgetRememberedPin()
								Toast.makeText(context, context.getString(R.string.hidden_pin_removed), Toast.LENGTH_SHORT).show()
							} else {
								context.explain(result)
							}
							refreshInfo()
						}
					},
					modifier = Modifier.focusKey("hidden_remove_pin"),
				)
			}
		}
	}
}
