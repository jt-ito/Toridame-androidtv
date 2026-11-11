package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.androidtv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.shared.SpoilerGuard
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val HEADER_HEIGHT = 168.dp
private val TITLE_SLOT = 60.dp
private val SUBTITLE_SLOT = 26.dp
private val DETAILS_SLOT = 22.dp
private const val LOGO_HEIGHT = 64
private const val TICKS_PER_MINUTE = 600_000_000L
private const val MINUTES_PER_HOUR = 60
private const val OVERVIEW_LINES = 3

/**
 * What the focused title is, above the rows: its logo (or its name), the year, rating and length, and the start of its
 * synopsis. Every line has a place of its own with a fixed height, whether it has anything to show or not, so nothing
 * moves when the focus goes from one title to the next. While nothing is focused yet it greets the user, and a library
 * shows its name and what kind of library it is.
 */
@Composable
fun HomeHeader(
	item: BaseItemDto?,
	greetingName: String?,
	libraryPreview: LibraryPreview? = null,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(HEADER_HEIGHT)
			.padding(start = 56.dp, end = 56.dp, top = 4.dp),
		contentAlignment = Alignment.TopStart,
	) {
		val isEpisode = item?.type == BaseItemKind.EPISODE
		val isLibrary = item?.type == BaseItemKind.USER_VIEW || item?.type == BaseItemKind.COLLECTION_FOLDER
		val title = when {
			item == null -> greetingName?.let { stringResource(R.string.home_header_welcome, it) }
			isEpisode || item.type == BaseItemKind.SEASON -> item.seriesName ?: item.name
			else -> item.name
		}
		val logoUrl = item?.let { (it.itemImages[ImageType.LOGO] ?: it.parentImages[ImageType.LOGO])?.getUrl(api, maxHeight = LOGO_HEIGHT * 2) }

		val subtitle = when {
			item == null -> null
			isEpisode -> listOfNotNull(
				if (item.parentIndexNumber != null && item.indexNumber != null) "S${item.parentIndexNumber}:E${item.indexNumber}" else null,
				item.name,
			).joinToString(" · ")

			item.type == BaseItemKind.SEASON -> item.name
			isLibrary -> collectionLabel(item.collectionType)?.let { stringResource(it) }
			else -> null
		}
		val preview = libraryPreview?.takeIf { isLibrary && it.libraryId == item?.id }
		val details = when {
			item == null -> ""
			isLibrary -> preview?.let { stringResource(R.string.home_header_titles, it.count) }.orEmpty()
			else -> quickDetails(item)
		}
		val overview = when {
			item == null -> null
			isLibrary -> preview?.latest?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }
				?.let { stringResource(R.string.home_header_latest, it.joinToString(", ")) }

			SpoilerGuard.covers(item) -> null
			else -> item.overview
		}

		Column(modifier = Modifier.fillMaxWidth(0.62f)) {
			// The name or logo
			Box(modifier = Modifier.height(TITLE_SLOT), contentAlignment = Alignment.CenterStart) {
				val painter = rememberAsyncImagePainter(logoUrl)
				val state by painter.state.collectAsState()

				when {
					logoUrl != null && state is AsyncImagePainter.State.Success -> Image(
						painter = painter,
						contentDescription = title,
						contentScale = ContentScale.Fit,
						alignment = Alignment.CenterStart,
						modifier = Modifier.height(LOGO_HEIGHT.dp),
					)

					// A logo that is on its way: show nothing rather than the name, which would then swap for the logo
					logoUrl != null && state !is AsyncImagePainter.State.Error -> Unit

					else -> Text(
						text = title.orEmpty(),
						fontSize = 30.sp,
						fontWeight = FontWeight.Bold,
						color = Color.White,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Box(modifier = Modifier.height(SUBTITLE_SLOT), contentAlignment = Alignment.CenterStart) {
				if (!subtitle.isNullOrBlank()) Text(text = subtitle, fontSize = 18.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}

			Box(modifier = Modifier.height(DETAILS_SLOT), contentAlignment = Alignment.CenterStart) {
				if (details.isNotBlank()) Text(text = details, fontSize = 15.sp, color = Color(0xCCFFFFFF), maxLines = 1)
			}

			// Always three lines high, so a short synopsis leaves the space it would have used
			Text(
				text = overview.orEmpty(),
				fontSize = 14.sp,
				color = Color(0xB3FFFFFF),
				minLines = OVERVIEW_LINES,
				maxLines = OVERVIEW_LINES,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

private fun collectionLabel(type: CollectionType?): Int? = when (type) {
	CollectionType.MOVIES -> R.string.home_header_movies
	CollectionType.TVSHOWS -> R.string.home_header_shows
	CollectionType.MUSIC -> R.string.home_header_music
	CollectionType.MUSICVIDEOS -> R.string.home_header_music_videos
	CollectionType.HOMEVIDEOS -> R.string.home_header_home_videos
	CollectionType.BOOKS -> R.string.home_header_books
	CollectionType.PLAYLISTS -> R.string.home_header_playlists
	CollectionType.BOXSETS -> R.string.home_header_collections
	CollectionType.LIVETV -> R.string.home_header_live_tv
	else -> null
}

/** "2021 · PG-13 · 1h 42m · ★ 7.8" */
private fun quickDetails(item: BaseItemDto): String = listOfNotNull(
	item.productionYear?.toString(),
	item.officialRating?.takeIf { it.isNotBlank() },
	if (item.type == BaseItemKind.SERIES) item.childCount?.takeIf { it > 0 }?.let { "$it ${if (it == 1) "season" else "seasons"}" } else null,
	item.runTimeTicks?.takeIf { it > 0 && item.type != BaseItemKind.SERIES }?.let { ticks ->
		val minutes = (ticks / TICKS_PER_MINUTE).toInt()
		if (minutes >= MINUTES_PER_HOUR) "${minutes / MINUTES_PER_HOUR}h ${minutes % MINUTES_PER_HOUR}m" else "${minutes}m"
	},
	item.communityRating?.takeIf { it > 0f }?.let { "★ %.1f".format(it) },
).joinToString(" · ")
