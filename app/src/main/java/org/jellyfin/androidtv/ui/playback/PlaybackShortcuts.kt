package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.BookmarkRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ZoomMode
import org.koin.java.KoinJavaComponent
import org.jellyfin.androidtv.util.formatTicks
import org.jellyfin.sdk.model.api.MediaStreamType
import org.koin.android.ext.android.inject

private const val TICKS_PER_MILLISECOND = 10_000L
private const val MILLISECONDS_PER_SECOND = 1000

/**
 * Adding bookmarks while watching.
 */
object PlaybackBookmarks {
	@JvmStatic
	fun addHere(controller: PlaybackController) {
		val fragment = controller.fragment ?: return
		val item = controller.currentlyPlayingItem ?: return
		val seconds = (controller.currentPosition / MILLISECONDS_PER_SECOND).toInt()
		val repository by fragment.inject<BookmarkRepository>()

		fragment.lifecycleScope.launch {
			val added = repository.add(item.id, seconds)
			val text = if (added) {
				fragment.getString(R.string.bookmark_added, formatTicks(seconds * MILLISECONDS_PER_SECOND * TICKS_PER_MILLISECOND))
			} else {
				fragment.getString(R.string.bookmark_failed)
			}
			Toast.makeText(fragment.requireContext(), text, Toast.LENGTH_SHORT).show()
			if (added) controller.fragment?.leanbackOverlayFragment?.playerGlue?.refreshBookmarkMarkers()
		}
	}
}

/**
 * Remote control buttons that change playback without opening a menu: the colour buttons, and the dedicated
 * bookmark button some remotes have.
 *
 * - Red: add a bookmark here
 * - Green: next subtitle track (or off)
 * - Yellow: next audio track
 * - Blue: next picture size
 * - 0: back to normal speed
 */
object PlaybackShortcuts {
	@JvmStatic
	fun handle(keyCode: Int, controller: PlaybackController?): Boolean {
		if (controller == null || controller.isLiveTv) return false
		val fragment = controller.fragment ?: return false
		val context = fragment.context ?: return false

		// The dedicated bookmark button some remotes have always adds a bookmark
		if (keyCode == KeyEvent.KEYCODE_BOOKMARK) {
			PlaybackBookmarks.addHere(controller)
			return true
		}

		val preferences = KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)
		val action = ShortcutAction.entries.firstOrNull { preferences[it.preference] == keyCode } ?: return false

		return when (action) {
			ShortcutAction.BOOKMARK -> {
				PlaybackBookmarks.addHere(controller)
				true
			}

			ShortcutAction.CYCLE_SUBTITLES -> cycleSubtitles(controller, context)
			ShortcutAction.CYCLE_AUDIO -> cycleAudio(controller, context)
			ShortcutAction.CYCLE_ASPECT -> cycleZoom(controller, context)
			ShortcutAction.SPEED_RESET -> {
				VideoSpeedController(controller).resetSpeedToDefault()
				toast(context, context.getString(R.string.shortcut_speed_reset))
				true
			}
		}
	}

	private fun cycleSubtitles(controller: PlaybackController, context: Context): Boolean {
		val streams = controller.currentMediaSource?.mediaStreams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE }
		if (streams.isEmpty()) return false

		// Off comes first, then every track
		val choices = listOf(-1) + streams.map { it.index }
		val next = choices[(choices.indexOf(controller.subtitleStreamIndex) + 1) % choices.size]
		controller.setSubtitleIndex(next)

		val name = streams.firstOrNull { it.index == next }?.displayTitle ?: context.getString(R.string.lbl_none)
		toast(context, context.getString(R.string.lbl_subtitle_track) + ": " + name)
		return true
	}

	private fun cycleAudio(controller: PlaybackController, context: Context): Boolean {
		val streams = controller.currentMediaSource?.mediaStreams.orEmpty().filter { it.type == MediaStreamType.AUDIO }
		if (streams.size < 2) return false

		val choices = streams.map { it.index }
		val next = choices[(choices.indexOf(controller.audioStreamIndex) + 1) % choices.size]
		controller.switchAudioStream(next)

		toast(context, context.getString(R.string.lbl_audio_track) + ": " + streams.first { it.index == next }.displayTitle)
		return true
	}

	private fun cycleZoom(controller: PlaybackController, context: Context): Boolean {
		val modes = listOf(ZoomMode.FIT, ZoomMode.AUTO_CROP, ZoomMode.STRETCH)
		val next = modes[(modes.indexOf(controller.zoomMode) + 1) % modes.size]
		controller.setZoom(next)

		toast(
			context,
			context.getString(
				when (next) {
					ZoomMode.AUTO_CROP -> R.string.lbl_auto_crop
					ZoomMode.STRETCH -> R.string.lbl_stretch
					else -> R.string.lbl_fit
				}
			)
		)
		return true
	}

	private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
