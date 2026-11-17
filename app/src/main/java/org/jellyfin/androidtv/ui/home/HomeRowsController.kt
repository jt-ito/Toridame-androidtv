package org.jellyfin.androidtv.ui.home

import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Row
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.toPinnedRows
import org.jellyfin.androidtv.preference.toThumbKeys
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.data.model.AppNotification
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Loads the rows of the home screen and reacts to what happens to them (focus, clicks, refreshes). It draws nothing:
 * [HomeRows] shows what is in [rows]. The rows still come from the same adapters the other screens use, so every
 * kind of row keeps working until those screens have been moved over too.
 */
class HomeRowsController(
	private val activity: FragmentActivity,
	private val owner: LifecycleOwner,
) : KoinComponent, AudioEventListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()

	private val lifecycle get() = owner.lifecycle
	private val scope get() = owner.lifecycleScope
	private val helper by lazy { HomeFragmentHelper(activity, userRepository, userPreferences) }

	/** The rows, in the order they are shown. */
	val rows = MutableObjectAdapter<Row>(ListRowPresenter())

	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true
	private var serverEventRefresh: Job? = null

	/** The title that has the focus, for the header above the rows. */
	private val _focusedItem = MutableStateFlow<BaseItemDto?>(null)
	val focusedItem: StateFlow<BaseItemDto?> = _focusedItem

	/** How many titles the focused library holds and what was added to it last, for the header and the backdrop. */
	private val libraryPreviews = mutableMapOf<UUID, LibraryPreview>()
	private val _libraryPreview = MutableStateFlow<LibraryPreview?>(null)
	val libraryPreview: StateFlow<LibraryPreview?> = _libraryPreview
	private var libraryPreviewJob: Job? = null
	private var libraryPreviewFor: UUID? = null
	private var focusedLibraryId: UUID? = null

	private val notificationsRow by lazy { NotificationsHomeFragmentRow(scope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(scope, playbackManager, mediaManager) }

	fun start() {
		scope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// With the key the row has in the "thumbnails" setting
			val rowDefinitions = mutableListOf<Pair<String, HomeFragmentRow>>()
			if (!isActive) return@launch

			for (section in userSettingPreferences.activeHomesections) when (section) {
				HomeSectionType.LATEST_MEDIA -> rowDefinitions.add(section.serializedName to helper.loadRecentlyAdded(userViewsRepository.views.first()))
				HomeSectionType.LIBRARY_TILES_SMALL -> rowDefinitions.add(section.serializedName to HomeFragmentViewsRow(small = false))
				HomeSectionType.LIBRARY_BUTTONS -> rowDefinitions.add(section.serializedName to HomeFragmentViewsRow(small = true))
				HomeSectionType.RESUME -> rowDefinitions.add(section.serializedName to helper.loadResumeVideo())
				HomeSectionType.RESUME_NEXT_UP -> rowDefinitions.add(section.serializedName to helper.loadResumeNextUp())
				HomeSectionType.FAVORITES -> rowDefinitions.add(section.serializedName to helper.loadFavorites())
				HomeSectionType.RESUME_AUDIO -> rowDefinitions.add(section.serializedName to helper.loadResumeAudio())
				HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
				HomeSectionType.ACTIVE_RECORDINGS -> rowDefinitions.add(section.serializedName to helper.loadLatestLiveTvRecordings())
				HomeSectionType.NEXT_UP -> rowDefinitions.add(section.serializedName to helper.loadNextUp())
				HomeSectionType.LIVE_TV -> if (currentUser.policy?.enableLiveTvAccess == true) {
					rowDefinitions.add(section.serializedName to HomeFragmentLiveTVRow(activity, userRepository))
					rowDefinitions.add(section.serializedName to helper.loadOnNow())
				}

				HomeSectionType.NONE -> Unit
			}

			for (pinned in userSettingPreferences[UserSettingPreferences.pinnedRows].toPinnedRows()) {
				rowDefinitions.add(pinned.key to HomeFragmentPinnedRow(pinned))
			}

			withContext(Dispatchers.Main) {
				val cardPresenter = CardPresenter()
				val thumbPresenter = CardPresenter(true, ImageType.THUMB, THUMB_ROW_HEIGHT)
				val thumbKeys = userSettingPreferences[UserSettingPreferences.thumbRows].toThumbKeys()

				notificationsRow.addToRowsAdapter(activity, cardPresenter, rows)
				nowPlaying.addToRowsAdapter(activity, cardPresenter, rows)
				for ((key, row) in rowDefinitions) row.addToRowsAdapter(activity, if (key in thumbKeys) thumbPresenter else cardPresenter, rows)

				// Wire up Live TV sibling rows so the On Now row removes the buttons row when empty
				for (i in 0 until rows.size()) {
					val listRow = rows.get(i) as? ListRow ?: continue
					val itemAdapter = listRow.adapter as? ItemRowAdapter ?: continue
					if (itemAdapter.queryType == QueryType.LiveTvProgram && i > 0) {
						rows.get(i - 1)?.let { itemAdapter.setSiblingRow(it) }
					}
				}
			}
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
					else -> Unit
				}
			}.launchIn(scope)

		scope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRowsAfterServerEvent() }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRowsAfterServerEvent() }
					.launchIn(this)
			}
		}

		mediaManager.addAudioEventListener(this)
	}

	fun onResume() {
		// React to deletion
		val item = currentItem
		val row = currentRow
		if (row != null && item?.baseItem != null && item.baseItem?.id == dataRefreshService.lastDeletedItemId) {
			(row.adapter as ItemRowAdapter).remove(item)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			// Re-retrieve anything that needs it, but not at once so the screen can settle first
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		nowPlaying.update(activity, rows)
	}

	fun onDestroy() {
		mediaManager.removeAudioEventListener(this)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity.isFinishing) return
		nowPlaying.update(activity, rows)
	}

	/** The menu key (or a long press on a card) opens the context menu of the focused title. */
	fun onMenuKey(): Boolean = keyProcessor.handleKey(android.view.KeyEvent.KEYCODE_MENU, currentItem, activity)

	fun onItemClicked(item: Any?, row: ListRow) {
		when (item) {
			is GridButton -> when (item.id) {
				LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvGuide)
				LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
				LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
				LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
			}

			is AppNotification -> notificationsRow.onItemClicked(null, item, null, null)

			is BaseRowItem -> {
				@Suppress("UNCHECKED_CAST")
				itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, activity)
			}
		}
	}

	fun onItemFocused(item: Any?, row: ListRow) {
		_focusedItem.value = (item as? BaseRowItem)?.baseItem

		if (item !is BaseRowItem) {
			currentItem = null
			backgroundService.clearBackgrounds()
			return
		}

		currentItem = item
		currentRow = row

		val itemRowAdapter = row.adapter as? ItemRowAdapter
		itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

		backgroundService.setBackground(item.baseItem)

		// A library has no backdrop of its own: borrow the one of what was added to it last
		val focused = item.baseItem
		if (focused?.type == BaseItemKind.USER_VIEW || focused?.type == BaseItemKind.COLLECTION_FOLDER) {
			focusedLibraryId = focused.id
			showLibraryPreview(focused)
		} else {
			focusedLibraryId = null
			_libraryPreview.value = null
		}
	}

	/**
	 * Library scans and other devices that are playing send a burst of events. Reloading every row for each of
	 * them keeps the server and the TV busy for as long as the burst lasts, so wait until it has been quiet.
	 */
	private fun refreshRowsAfterServerEvent() {
		serverEventRefresh?.cancel()
		serverEventRefresh = scope.launch {
			delay(SERVER_EVENT_QUIET_PERIOD)
			refreshRows(force = true, delayed = false)
		}
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		scope.launch(Dispatchers.IO) {
			if (delayed) delay(1.5.seconds)

			repeat(rows.size()) { i ->
				val rowAdapter = (rows[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(activity)}")
		adapter.refreshItem(api, owner, item)
	}

	private fun showLibraryPreview(library: BaseItemDto) {
		libraryPreviews[library.id]?.let {
			libraryPreviewJob?.cancel()
			libraryPreviewFor = null
			applyLibraryPreview(it)
			return
		}

		// The selection is reported again and again while the page loads: let the lookup that is on its way finish
		if (libraryPreviewFor == library.id && libraryPreviewJob?.isActive == true) return

		libraryPreviewJob?.cancel()
		libraryPreviewFor = library.id
		_libraryPreview.value = null
		libraryPreviewJob = scope.launch {
			try {
				val result = runCatching {
					withContext(Dispatchers.IO) {
						api.itemsApi.getItems(
							parentId = library.id,
							recursive = true,
							includeItemTypes = when (library.collectionType) {
								CollectionType.MOVIES -> setOf(BaseItemKind.MOVIE)
								CollectionType.TVSHOWS -> setOf(BaseItemKind.SERIES)
								CollectionType.MUSIC -> setOf(BaseItemKind.MUSIC_ALBUM)
								CollectionType.HOMEVIDEOS -> setOf(BaseItemKind.VIDEO)
								CollectionType.BOXSETS -> setOf(BaseItemKind.BOX_SET)
								else -> null
							},
							sortBy = setOf(ItemSortBy.DATE_CREATED),
							sortOrder = setOf(SortOrder.DESCENDING),
							limit = LIBRARY_PREVIEW_ITEMS,
							enableTotalRecordCount = true,
						).content
					}
				}.onFailure { Timber.w(it, "Could not look up the library") }.getOrNull() ?: return@launch

				val preview = LibraryPreview(library.id, result.totalRecordCount, result.items)
				libraryPreviews[library.id] = preview
				applyLibraryPreview(preview)
			} finally {
				if (libraryPreviewFor == library.id) libraryPreviewFor = null
			}
		}
	}

	private fun applyLibraryPreview(preview: LibraryPreview) {
		// The focus may have moved on to something else while it was loading
		if (focusedLibraryId != preview.libraryId) return

		_libraryPreview.value = preview
		preview.latest.firstOrNull()?.let { backgroundService.setBackground(it) }
	}

	private companion object {
		val SERVER_EVENT_QUIET_PERIOD = 3.seconds
		const val LIBRARY_PREVIEW_ITEMS = 3
		const val THUMB_ROW_HEIGHT = 130
	}
}

/** A library: how many titles it holds, and the ones added to it last. */
data class LibraryPreview(
	val libraryId: UUID,
	val count: Int,
	val latest: List<BaseItemDto>,
)
