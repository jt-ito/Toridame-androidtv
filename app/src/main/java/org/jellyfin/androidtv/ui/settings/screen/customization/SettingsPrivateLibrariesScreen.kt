package org.jellyfin.androidtv.ui.settings.screen.customization

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
import org.jellyfin.androidtv.data.repository.PinResult
import org.jellyfin.androidtv.data.repository.PrivateLibrary
import org.jellyfin.androidtv.data.repository.PrivateStatus
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.shared.askPin
import org.jellyfin.androidtv.ui.shared.explain
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.koin.compose.koinInject

/**
 * Libraries hidden behind the PIN of the hidden content. While locked they are gone everywhere for this user;
 * entering the PIN shows them again. The server does the hiding, this only talks to it.
 */
@Composable
fun SettingsPrivateLibrariesScreen() {
	val repository = koinInject<ToridameRepository>()
	val api = koinInject<ApiClient>()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var status by remember { mutableStateOf<PrivateStatus?>(null) }
	// Every library with whether it is private; only filled after the PIN was given
	var libraries by remember { mutableStateOf<List<Pair<PrivateLibrary, Boolean>>?>(null) }

	suspend fun refresh() {
		status = repository.privateStatus()
	}

	LaunchedEffect(Unit) { refresh() }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.private_title)) },
				captionContent = {
					Text(
						when {
							status == null -> stringResource(R.string.hidden_needs_server)
							status?.active != true -> stringResource(R.string.private_none)
							status?.unlocked == true -> stringResource(R.string.private_unlocked)
							else -> stringResource(R.string.private_locked)
						}
					)
				},
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(if (status?.unlocked == true) R.string.private_lock else R.string.private_unlock)) },
				onClick = {
					scope.launch {
						if (status?.unlocked == true) {
							repository.lockPrivate()
						} else {
							val pin = context.askPin(context.getString(R.string.hidden_enter_pin)) ?: return@launch
							val result = repository.unlockPrivate(pin)
							if (result != PinResult.OK) context.explain(result)
						}
						refresh()
					}
				},
				modifier = Modifier.focusKey("private_toggle"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.private_manage)) },
				captionContent = { Text(stringResource(R.string.private_manage_caption)) },
				onClick = {
					scope.launch {
						val pin = context.askPin(context.getString(R.string.hidden_enter_pin)) ?: return@launch
						val (result, privateOnes) = repository.privateLibraries(pin)
						if (result != PinResult.OK) {
							context.explain(result)
							return@launch
						}

						val visible = runCatching { api.userViewsApi.getUserViews().content.items }.getOrDefault(emptyList())
							.map { PrivateLibrary(it.id, it.name.orEmpty()) }
						val privateIds = privateOnes.map { it.id }.toSet()
						libraries = (visible + privateOnes).distinctBy { it.id }.map { it to (it.id in privateIds) }
					}
				},
				modifier = Modifier.focusKey("private_manage"),
			)
		}

		items(libraries.orEmpty()) { (library, isPrivate) ->
			ListButton(
				headingContent = { Text(library.name) },
				captionContent = { Text(stringResource(if (isPrivate) R.string.private_is_private else R.string.private_is_public)) },
				onClick = {
					scope.launch {
						val pin = context.askPin(context.getString(R.string.hidden_enter_pin)) ?: return@launch
						val result = if (isPrivate) repository.makeLibraryPublic(library.id, pin) else repository.makeLibraryPrivate(library.id, pin)
						if (result == PinResult.OK) {
							libraries = libraries.orEmpty().map { (lib, value) -> if (lib.id == library.id) lib to !isPrivate else lib to value }
						} else {
							context.explain(result)
						}
						refresh()
					}
				},
			)
		}
	}
}
