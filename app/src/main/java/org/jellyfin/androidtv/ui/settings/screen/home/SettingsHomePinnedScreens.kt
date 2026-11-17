package org.jellyfin.androidtv.ui.settings.screen.home

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
import org.jellyfin.androidtv.preference.toThumbKeys
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject

/** The rows of the home screen that can show thumbnails instead of posters, with the name each has in the settings. */
val thumbnailRowOptions = listOf(
	"resume" to R.string.home_section_resume,
	"resumenextup" to R.string.home_section_resume_next_up,
	"nextup" to R.string.home_section_next_up,
	"latestmedia" to R.string.home_section_latest_media,
	"favorites" to R.string.home_section_favorites,
)

/** One switch per row, for thumbnails instead of posters. */
@Composable
fun ThumbnailSwitch(key: String, label: String) {
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var thumbRows by rememberPreference(userSettingPreferences, UserSettingPreferences.thumbRows)
	val checked = key in thumbRows.toThumbKeys()

	ListButton(
		headingContent = { Text(label) },
		trailingContent = { Checkbox(checked = checked) },
		onClick = {
			val keys = thumbRows.toThumbKeys()
			thumbRows = (if (checked) keys - key else keys + key).toPreferenceValue()
		},
		modifier = Modifier.focusKey("thumbs_$key"),
	)
}

/** The collections and genres that have a row of their own on the home screen. */
@Composable
fun SettingsHomePinnedScreen() {
	val router = LocalRouter.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var pinnedValue by rememberPreference(userSettingPreferences, UserSettingPreferences.pinnedRows)
	val pinned = pinnedValue.toPinnedRows()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_prefs).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_pinned_title)) },
				captionContent = { Text(stringResource(R.string.home_pinned_caption)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_pinned_add_collection)) },
				onClick = { router.push(Routes.HOME_PINNED_ADD, mapOf("kind" to PinnedRow.COLLECTION)) },
				modifier = Modifier.focusKey("pinned_add_collection"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.home_pinned_add_genre)) },
				onClick = { router.push(Routes.HOME_PINNED_ADD, mapOf("kind" to PinnedRow.GENRE)) },
				modifier = Modifier.focusKey("pinned_add_genre"),
			)
		}

		items(pinned, key = { it.key }) { row ->
			ThumbnailSwitch(row.key, row.name)

			ListButton(
				headingContent = { Text(stringResource(R.string.home_pinned_remove, row.name)) },
				onClick = { pinnedValue = (pinned - row).toPreferenceValue() },
				modifier = Modifier.focusKey("pinned_remove_${row.key}"),
			)
		}
	}
}

/** Picks a collection or a genre from the server to give a row. */
@Composable
fun SettingsHomePinnedAddScreen(kind: String) {
	val router = LocalRouter.current
	val api = koinInject<ApiClient>()
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var pinnedValue by rememberPreference(userSettingPreferences, UserSettingPreferences.pinnedRows)

	var options by remember { mutableStateOf<List<PinnedRow>?>(null) }
	LaunchedEffect(kind) {
		options = withContext(Dispatchers.IO) {
			runCatching {
				if (kind == PinnedRow.COLLECTION) {
					api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.BOX_SET),
						recursive = true,
						sortBy = setOf(ItemSortBy.SORT_NAME),
					).content.items.map { PinnedRow(kind, it.id.toString(), it.name.orEmpty()) }
				} else {
					api.genresApi.getGenres(sortBy = setOf(ItemSortBy.SORT_NAME)).content.items
						.map { PinnedRow(kind, it.name.orEmpty(), it.name.orEmpty()) }
				}
			}.getOrDefault(emptyList())
		}
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_pinned_title).uppercase()) },
				headingContent = {
					Text(
						stringResource(
							if (kind == PinnedRow.COLLECTION) R.string.home_pinned_add_collection else R.string.home_pinned_add_genre
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
					modifier = Modifier.focusKey("pinned_option_${option.key}"),
				)
			}
		}
	}
}
