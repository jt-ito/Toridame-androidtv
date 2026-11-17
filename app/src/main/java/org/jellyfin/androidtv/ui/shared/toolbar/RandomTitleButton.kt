package org.jellyfin.androidtv.ui.shared.toolbar

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.rememberFeature
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber

/**
 * Gives what opens the page of a random movie or series, like the random button of the web client; null when it was
 * switched off there. It follows the account's settings from the web client: limited to what was picked there (movies,
 * series, or both; unwatched only unless that was switched off).
 */
@Composable
fun rememberRandomTitle(): (() -> Unit)? {
	val settings = koinInject<UserSettingPreferences>()
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var busy by remember { mutableStateOf(false) }

	val featureOn = rememberFeature("randomButton")
	if (settings[UserSettingPreferences.randomButton] == "false" || !featureOn) return null

	return {
		if (!busy) {
			busy = true
			scope.launch {
				val type = settings[UserSettingPreferences.randomType]
				val unwatchedOnly = settings[UserSettingPreferences.randomUnwatched] != "false"

				val item = runCatching {
					api.itemsApi.getItems(
						recursive = true,
						includeItemTypes = when (type) {
							"Movie" -> setOf(BaseItemKind.MOVIE)
							"Series" -> setOf(BaseItemKind.SERIES)
							else -> setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
						},
						sortBy = setOf(ItemSortBy.RANDOM),
						isPlayed = if (unwatchedOnly) false else null,
						limit = 1,
					).content.items.firstOrNull()
				}.onFailure { Timber.w(it, "Could not pick a random title") }.getOrNull()

				busy = false
				if (item != null) navigationRepository.navigate(Destinations.itemDetails(item.id))
				else Toast.makeText(context, context.getString(R.string.random_nothing_found), Toast.LENGTH_SHORT).show()
			}
		}
	}
}
