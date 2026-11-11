package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.Bookmark
import org.jellyfin.androidtv.data.repository.BookmarkRepository
import org.jellyfin.androidtv.ui.playback.PlaybackBookmarks
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.formatTicks
import org.koin.android.ext.android.inject

private const val ADD_ID = -1
private const val DELETE_ID_BASE = 1_000_000
private const val TICKS_PER_SECOND = 10_000_000L
private const val MILLISECONDS_PER_SECOND = 1000L

/**
 * Bookmarks of the video being watched: add one, jump to one, or remove one.
 */
class BookmarkAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_bookmark)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val fragment = videoPlayerAdapter.leanbackOverlayFragment
		val item = playbackController.currentlyPlayingItem ?: return
		val repository by fragment.inject<BookmarkRepository>()

		fragment.lifecycleScope.launch {
			val bookmarks = repository.list(item.id)

			fragment.setFading(false)
			removePopup()
			popup = PopupMenu(context, view, Gravity.END).apply {
				menu.add(0, ADD_ID, 0, context.getString(R.string.bookmark_add_here))

				bookmarks.forEachIndexed { index, bookmark ->
					menu.add(0, index, index + 1, describe(context, bookmark))
				}

				// Removing is a second step so that the first press on a bookmark always goes to it
				bookmarks.forEachIndexed { index, bookmark ->
					menu.add(1, DELETE_ID_BASE + index, bookmarks.size + index + 1, context.getString(R.string.bookmark_remove, describe(context, bookmark)))
				}

				setOnMenuItemClickListener { entry ->
					when {
						entry.itemId == ADD_ID -> PlaybackBookmarks.addHere(playbackController)
						entry.itemId >= DELETE_ID_BASE -> {
							val bookmark = bookmarks[entry.itemId - DELETE_ID_BASE]
							fragment.lifecycleScope.launch {
								val removed = repository.remove(item.id, bookmark.seconds)
								if (removed) videoPlayerAdapter.leanbackOverlayFragment.playerGlue.refreshBookmarkMarkers()
								Toast.makeText(
									context,
									context.getString(if (removed) R.string.bookmark_removed else R.string.bookmark_failed),
									Toast.LENGTH_SHORT
								).show()
							}
						}

						else -> playbackController.seek(bookmarks[entry.itemId].seconds * MILLISECONDS_PER_SECOND)
					}
					true
				}
				setOnDismissListener {
					fragment.setFading(true)
					popup = null
				}
			}
			popup?.show()
		}
	}

	private fun describe(context: Context, bookmark: Bookmark): String {
		val time = formatTicks(bookmark.seconds * TICKS_PER_SECOND)
		return if (bookmark.label.isNullOrBlank()) time else "$time  ${bookmark.label}"
	}

	fun removePopup() {
		popup?.dismiss()
	}
}
