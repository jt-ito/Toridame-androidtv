package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

private const val MAX_COLLECTIONS = 200
private const val COLLECTION_CACHE_MS = 5 * 60 * 1000L
private const val VERSION_BASE = 1_000_000
private val NON_MOVIE_FOLDERS = setOf(BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_ROOT_FOLDER, BaseItemKind.AGGREGATE_FOLDER)

/** A list of videos that can be shown in the panel at the bottom of the player. */
private data class VideoGroup(val name: String, val items: List<BaseItemDto>)

private class CollectionMembers(val id: UUID, val name: String, val kind: BaseItemKind, val memberIds: Set<UUID>)

private var collectionCache: Pair<Long, List<CollectionMembers>>? = null

/**
 * What else there is to watch, as cards with their pictures in the panel at the bottom of the player, to jump to
 * another one without leaving the player:
 *  - episodes: the season being watched, and the other seasons;
 *  - movies and other videos: the play queue, the other videos in the same folder, and the collections and playlists
 *    that hold it, plus switching to another version of the video from where it is now.
 */
class EpisodeListAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_episode_list)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val current = playbackController.currentlyPlayingItem ?: return
		if (current.type == BaseItemKind.EPISODE && current.seriesId != null) {
			showSeason(videoPlayerAdapter, context, view, current, current.seasonId)
		} else {
			showVideoMenu(playbackController, videoPlayerAdapter, context, view, current)
		}
	}

	// ---- Episodes

	private fun showSeason(
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
		current: BaseItemDto,
		seasonId: UUID?,
	) {
		val seriesId = current.seriesId ?: return
		val fragment = videoPlayerAdapter.leanbackOverlayFragment
		val api by fragment.inject<ApiClient>()

		fragment.lifecycleScope.launch {
			val episodes = runCatching {
				api.tvShowsApi.getEpisodes(
					seriesId = seriesId,
					seasonId = seasonId,
					isMissing = false,
					fields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW),
				).content.items
			}.onFailure { Timber.w(it, "Could not load the episodes") }.getOrDefault(emptyList())
			if (episodes.isEmpty()) return@launch

			val title = episodes.first().seasonName ?: context.getString(R.string.episodes_list)
			videoPlayerAdapter.leanbackOverlayFragment.hideOverlay()
			videoPlayerAdapter.masterOverlayFragment.showEpisodePanel(
				title,
				episodes,
				episodes.indexOfFirst { it.id == current.id },
				context.getString(R.string.episodes_other_seasons),
			) { showSeasons(videoPlayerAdapter, context, view, current) }
		}
	}

	/** The seasons to pick from, shown on top of the player. */
	private fun showSeasons(
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
		current: BaseItemDto,
	) {
		val seriesId = current.seriesId ?: return
		val fragment = videoPlayerAdapter.leanbackOverlayFragment
		val api by fragment.inject<ApiClient>()

		fragment.lifecycleScope.launch {
			val seasons = runCatching {
				api.tvShowsApi.getSeasons(seriesId = seriesId, isMissing = false).content.items
			}.onFailure { Timber.w(it, "Could not load the seasons") }.getOrDefault(emptyList())

			removePopup()
			popup = PopupMenu(context, view, Gravity.END).apply {
				seasons.forEachIndexed { index, season ->
					menu.add(0, index, index, season.name ?: context.getString(R.string.lbl_seasons))
				}

				setOnMenuItemClickListener { entry ->
					showSeason(videoPlayerAdapter, context, view, current, seasons[entry.itemId].id)
					true
				}
				setOnDismissListener { popup = null }
			}
			popup?.show()
		}
	}

	// ---- Movies and other videos

	private fun showVideoMenu(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
		current: BaseItemDto,
	) {
		val fragment = videoPlayerAdapter.leanbackOverlayFragment
		val api by fragment.inject<ApiClient>()

		fragment.lifecycleScope.launch {
			val groups = runCatching { loadGroups(api, context, playbackController, current) }
				.onFailure { Timber.w(it, "Could not load the lists") }
				.getOrDefault(emptyList())
			val versions = current.mediaSources.orEmpty().takeIf { it.size > 1 }.orEmpty()

			if (groups.isEmpty() && versions.isEmpty()) {
				Toast.makeText(context, context.getString(R.string.episodes_nothing_else), Toast.LENGTH_SHORT).show()
				return@launch
			}

			// A single list needs no menu
			if (groups.size == 1 && versions.isEmpty()) {
				showGroup(playbackController, videoPlayerAdapter, context, view, current, groups.first())
				return@launch
			}

			removePopup()
			popup = PopupMenu(context, view, Gravity.END).apply {
				groups.forEachIndexed { index, group -> menu.add(0, index, index, group.name) }
				val currentSource = playbackController.currentMediaSource?.id
				versions.forEachIndexed { index, source ->
					val label = (source.name ?: context.getString(R.string.lbl_version)) + if (source.id == currentSource) "  ✓" else ""
					menu.add(1, VERSION_BASE + index, groups.size + index, context.getString(R.string.episodes_version, label))
				}

				setOnMenuItemClickListener { entry ->
					if (entry.itemId >= VERSION_BASE) {
						val source = versions[entry.itemId - VERSION_BASE]
						if (source.id != currentSource) playbackController.playVersion(source.id)
					} else {
						showGroup(playbackController, videoPlayerAdapter, context, view, current, groups[entry.itemId])
					}
					true
				}
				setOnDismissListener { popup = null }
			}
			popup?.show()
		}
	}

	private fun showGroup(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
		current: BaseItemDto,
		group: VideoGroup,
	) {
		videoPlayerAdapter.leanbackOverlayFragment.hideOverlay()
		videoPlayerAdapter.masterOverlayFragment.showEpisodePanel(
			group.name,
			group.items,
			group.items.indexOfFirst { it.id == current.id },
			context.getString(R.string.episodes_other_lists),
		) {
			// Back to the choice of lists
			handleClickAction(playbackController, videoPlayerAdapter, context, view)
		}
	}

	private suspend fun loadGroups(
		api: ApiClient,
		context: Context,
		playbackController: PlaybackController,
		current: BaseItemDto,
	): List<VideoGroup> {
		val groups = mutableListOf<VideoGroup>()
		val fields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW)

		// What is queued to play
		val queue = playbackController.queue.orEmpty()
		if (queue.size > 1) groups += VideoGroup(context.getString(R.string.episodes_queue), queue)

		// The other videos in the same folder
		val parentId = current.parentId
		if (parentId != null) {
			val parent = api.itemsApi.getItems(ids = setOf(parentId)).content.items.firstOrNull()
			if (parent != null && parent.type !in NON_MOVIE_FOLDERS) {
				val siblings = api.itemsApi.getItems(
					parentId = parentId,
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.VIDEO),
					sortBy = setOf(ItemSortBy.SORT_NAME),
					fields = fields,
				).content.items
				if (siblings.size > 1) groups += VideoGroup(context.getString(R.string.episodes_folder, parent.name.orEmpty()), siblings)
			}
		}

		// The collections and playlists that hold it
		for (collection in loadCollections(api).filter { current.id in it.memberIds }) {
			val items = api.itemsApi.getItems(
				parentId = collection.id,
				sortBy = if (collection.kind == BaseItemKind.BOX_SET) setOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME) else null,
				fields = fields,
			).content.items
			if (items.size > 1) groups += VideoGroup(collection.name, items)
		}

		return groups
	}

	/** Every collection and playlist with the ids of what is in it, kept for a few minutes as this is a lot of requests. */
	private suspend fun loadCollections(api: ApiClient): List<CollectionMembers> {
		collectionCache?.let { (at, cached) -> if (System.currentTimeMillis() - at < COLLECTION_CACHE_MS) return cached }

		val all = api.itemsApi.getItems(
			includeItemTypes = setOf(BaseItemKind.BOX_SET, BaseItemKind.PLAYLIST),
			recursive = true,
			sortBy = setOf(ItemSortBy.SORT_NAME),
			limit = MAX_COLLECTIONS,
			enableImages = false,
			enableUserData = false,
		).content.items

		val collections = coroutineScope {
			all.map { collection ->
				async {
					runCatching {
						val members = api.itemsApi.getItems(
							parentId = collection.id,
							enableImages = false,
							enableUserData = false,
						).content.items
						CollectionMembers(collection.id, collection.name.orEmpty(), collection.type, members.map { it.id }.toSet())
					}.getOrNull()
				}
			}.awaitAll().filterNotNull()
		}

		collectionCache = System.currentTimeMillis() to collections
		return collections
	}

	fun removePopup() {
		popup?.dismiss()
	}
}
