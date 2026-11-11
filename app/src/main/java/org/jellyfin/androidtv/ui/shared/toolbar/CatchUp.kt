package org.jellyfin.androidtv.ui.shared.toolbar

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber

private const val BATCH = 60
private const val MAX_DISMISSED = 2000
private const val POSTER_HEIGHT = 520

/** Titles ruled out in Catch Up stay on this device, as on a shared screen someone else may want to see them. */
private class Dismissed(context: Context, userId: String) {
	private val preferences = context.getSharedPreferences("catch_up", Context.MODE_PRIVATE)
	private val key = "dismissed_$userId"

	fun all(): Set<String> = preferences.getStringSet(key, emptySet()).orEmpty()

	fun add(id: String) {
		val updated = (all() + id).toList().takeLast(MAX_DISMISSED).toSet()
		preferences.edit().putStringSet(key, updated).apply()
	}
}

/**
 * Catch Up: go through the titles that have not been watched yet and decide what to do with each: open it, skip it, rule
 * it out for good, put it on the watchlist, or mark it as watched.
 */
@Composable
fun CatchUpDialog(visible: Boolean, onDismissRequest: () -> Unit) {
	val api = koinInject<ApiClient>()
	val userRepository = koinInject<UserRepository>()
	val itemMutationRepository = koinInject<ItemMutationRepository>()
	val navigationRepository = koinInject<NavigationRepository>()
	val context = androidx.compose.ui.platform.LocalContext.current
	val scope = rememberCoroutineScope()
	val firstFocus = remember { FocusRequester() }

	var queue by remember { mutableStateOf<List<BaseItemDto>?>(null) }
	var index by remember { mutableIntStateOf(0) }
	val userId = userRepository.currentUser.value?.id?.toString().orEmpty()
	val dismissed = remember(userId) { Dismissed(context, userId) }

	LaunchedEffect(visible) {
		if (!visible) return@LaunchedEffect
		queue = null
		index = 0

		val skip = dismissed.all()
		queue = runCatching {
			api.itemsApi.getItems(
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				recursive = true,
				filters = setOf(ItemFilter.IS_UNPLAYED),
				isFavorite = false,
				sortBy = setOf(ItemSortBy.RANDOM),
				limit = BATCH,
				fields = setOf(ItemFields.OVERVIEW),
			).content.items.filterNot { it.id.toString() in skip }
		}.onFailure { Timber.w(it, "Could not load the titles for Catch Up") }.getOrDefault(emptyList())
	}

	val list = queue
	val current = list?.getOrNull(index)
	LaunchedEffect(current?.id) { if (current != null) runCatching { firstFocus.requestFocus() } }

	fun next() {
		if (list != null && index + 1 < list.size) index++ else onDismissRequest()
	}

	DialogBase(visible = visible, onDismissRequest = onDismissRequest) {
		Column(
			modifier = Modifier
				.width(900.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(JellyfinTheme.colorScheme.surface)
				.padding(28.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Text(text = stringResource(R.string.catch_up_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)

			when {
				list == null -> Text(stringResource(R.string.catch_up_loading))
				current == null -> Text(stringResource(R.string.catch_up_done))
				else -> {
					Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
						val posterUrl = current.itemImages[ImageType.PRIMARY]?.getUrl(api, maxHeight = POSTER_HEIGHT)
						if (posterUrl != null) {
							Image(
								painter = rememberAsyncImagePainter(posterUrl),
								contentDescription = null,
								contentScale = ContentScale.Crop,
								modifier = Modifier
									.height(300.dp)
									.width(200.dp)
									.clip(RoundedCornerShape(8.dp)),
							)
						}

						Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
							Text(text = current.name.orEmpty(), fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
							Text(
								text = listOfNotNull(current.productionYear?.toString(), current.type.name.lowercase().replaceFirstChar { it.uppercase() }).joinToString(" · "),
								fontSize = 16.sp,
							)
							Text(text = current.overview.orEmpty(), fontSize = 16.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
							Text(
								text = stringResource(R.string.catch_up_progress, index + 1, list.size),
								fontSize = 14.sp,
								color = JellyfinTheme.colorScheme.listCaption,
							)
						}
					}

					Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
						Button(
							modifier = Modifier.focusRequester(firstFocus),
							onClick = {
								onDismissRequest()
								navigationRepository.navigate(Destinations.itemDetails(current.id))
							},
						) { Text(stringResource(R.string.catch_up_open)) }

						Button(onClick = { next() }) { Text(stringResource(R.string.catch_up_skip)) }

						Button(onClick = {
							dismissed.add(current.id.toString())
							next()
						}) { Text(stringResource(R.string.catch_up_not_interested)) }

						Button(onClick = {
							scope.launch { itemMutationRepository.setFavorite(current.id, true) }
							next()
						}) { Text(stringResource(R.string.catch_up_watchlist)) }

						Button(onClick = {
							scope.launch { itemMutationRepository.setPlayed(current.id, true) }
							next()
						}) { Text(stringResource(R.string.catch_up_watched)) }
					}
				}
			}
		}
	}
}
