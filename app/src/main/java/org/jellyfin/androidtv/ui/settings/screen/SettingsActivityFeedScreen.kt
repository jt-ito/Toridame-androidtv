package org.jellyfin.androidtv.ui.settings.screen

import android.text.format.DateUtils
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ActivityEntry
import org.jellyfin.androidtv.data.repository.ActivityFeed
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

/** What the people of the household recently watched, when an administrator allows it. */
@Composable
fun SettingsActivityFeedScreen() {
	val repository = koinInject<ToridameRepository>()
	val navigationRepository = koinInject<NavigationRepository>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val scope = rememberCoroutineScope()

	var feed by remember { mutableStateOf<ActivityFeed?>(null) }
	var failed by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		feed = repository.activityFeed()
		failed = feed == null
	}

	val current = feed

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.activity_title)) },
				captionContent = {
					Text(
						when {
							failed -> stringResource(R.string.hidden_needs_server)
							current == null -> stringResource(R.string.catch_up_loading)
							!current.enabled -> stringResource(R.string.activity_off)
							else -> stringResource(R.string.activity_help)
						}
					)
				},
			)
		}

		if (current != null && current.enabled) {
			if (current.entries.isEmpty()) {
				item { ListSection(headingContent = { Text(stringResource(R.string.activity_empty)) }) }
			}

			items(current.entries) { entry ->
				ListButton(
					headingContent = { Text(stringResource(R.string.activity_watched, entry.userName, describe(entry))) },
					captionContent = {
						Text(DateUtils.getRelativeTimeSpanString(entry.playedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
					},
					onClick = {
						settingsViewModel.hide()
						navigationRepository.navigate(Destinations.itemDetails(entry.seriesId ?: entry.itemId))
					},
				)
			}

			item {
				ListButton(
					headingContent = { Text(stringResource(if (current.sharing) R.string.activity_stop_sharing else R.string.activity_start_sharing)) },
					captionContent = { Text(stringResource(if (current.sharing) R.string.activity_sharing_on else R.string.activity_sharing_off)) },
					onClick = {
						scope.launch {
							if (repository.setActivitySharing(!current.sharing)) feed = repository.activityFeed()
						}
					},
					modifier = Modifier.focusKey("activity_sharing"),
				)
			}
		}
	}
}

/** The title of what was watched: the movie, or the series with its season and episode. */
private fun describe(entry: ActivityEntry): String {
	if (entry.itemType != "Episode") return entry.itemName

	val code = if (entry.seasonNumber != null && entry.episodeNumber != null) "S${entry.seasonNumber}:E${entry.episodeNumber}" else null
	return listOfNotNull(entry.seriesName, code, entry.itemName).filter { it.isNotBlank() }.joinToString(" · ")
}
