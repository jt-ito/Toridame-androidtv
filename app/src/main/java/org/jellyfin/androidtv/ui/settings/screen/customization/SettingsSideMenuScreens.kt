package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.PinnedRow
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.toPinnedRows
import org.jellyfin.androidtv.preference.toPreferenceValue
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject

/** The libraries and collections pinned to the side menu. */
@Composable
fun SettingsSideMenuScreen() {
	val router = LocalRouter.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var pinnedValue by rememberPreference(userSettingPreferences, UserSettingPreferences.sidePins)
	val pinned = pinnedValue.toPinnedRows()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.side_menu_title)) },
				captionContent = { Text(stringResource(R.string.side_menu_caption)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.side_menu_add_library)) },
				onClick = { router.push(Routes.SIDE_MENU_ADD, mapOf("kind" to PinnedRow.LIBRARY)) },
				modifier = Modifier.focusKey("side_add_library"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.side_menu_add_collection)) },
				onClick = { router.push(Routes.SIDE_MENU_ADD, mapOf("kind" to PinnedRow.COLLECTION)) },
				modifier = Modifier.focusKey("side_add_collection"),
			)
		}

		items(pinned, key = { it.key }) { pin ->
			ListButton(
				headingContent = { Text(stringResource(R.string.home_pinned_remove, pin.name)) },
				onClick = { pinnedValue = (pinned - pin).toPreferenceValue() },
				modifier = Modifier.focusKey("side_remove_${pin.key}"),
			)
		}
	}
}

/** Picks a library or a collection from the server to pin to the side menu. */
@Composable
fun SettingsSideMenuAddScreen(kind: String) {
	val router = LocalRouter.current
	val api = koinInject<ApiClient>()
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var pinnedValue by rememberPreference(userSettingPreferences, UserSettingPreferences.sidePins)

	var options by remember { mutableStateOf<List<PinnedRow>?>(null) }
	LaunchedEffect(kind) {
		options = withContext(Dispatchers.IO) {
			runCatching {
				if (kind == PinnedRow.LIBRARY) {
					api.userViewsApi.getUserViews().content.items
						.map { PinnedRow(kind, it.id.toString(), it.name.orEmpty(), it.collectionType?.serialName.orEmpty()) }
				} else {
					api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.BOX_SET),
						recursive = true,
						sortBy = setOf(ItemSortBy.SORT_NAME),
					).content.items.map { PinnedRow(kind, it.id.toString(), it.name.orEmpty()) }
				}
			}.getOrDefault(emptyList())
		}
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.side_menu_title).uppercase()) },
				headingContent = {
					Text(
						stringResource(
							if (kind == PinnedRow.LIBRARY) R.string.side_menu_add_library else R.string.side_menu_add_collection
						)
					)
				},
			)
		}

		val list = options
		if (list == null) {
			item { ListMessage { Text(stringResource(R.string.home_pinned_loading)) } }
		} else {
			val taken = pinnedValue.toPinnedRows().map { it.key }.toSet()
			val available = list.filter { it.key !in taken }

			if (available.isEmpty()) item { ListMessage { Text(stringResource(R.string.home_pinned_none)) } }

			items(available, key = { it.key }) { option ->
				ListButton(
					headingContent = { Text(option.name) },
					onClick = {
						pinnedValue = (pinnedValue.toPinnedRows() + option).toPreferenceValue()
						router.back()
					},
					modifier = Modifier.focusKey("side_option_${option.key}"),
				)
			}
		}
	}
}
