package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

/**
 * The gear of the player: a menu with the less used buttons (audio, chapters, bookmarks, speed, quality, zoom), so the
 * row of buttons stays short. Picking one does what pressing its button would.
 */
class SettingsMenuAction(
	context: Context,
	private val glue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, glue) {
	private var popup: PopupMenu? = null

	/** The label and the action behind each entry, set whenever the buttons are built. */
	var entries: List<Pair<String, CustomAction>> = emptyList()

	init {
		initializeWithIcon(R.drawable.ic_settings)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		if (entries.isEmpty()) return
		val fragment = videoPlayerAdapter.leanbackOverlayFragment

		fragment.setFading(false)
		removePopup()
		popup = PopupMenu(context, view, Gravity.END).apply {
			entries.forEachIndexed { index, (label, _) -> menu.add(0, index, index, label) }
			setOnMenuItemClickListener { entry ->
				// The picked action shows its own menu on the same button
				view.post { glue.onCustomActionClicked(entries[entry.itemId].second, view) }
				true
			}
			setOnDismissListener {
				fragment.setFading(true)
				popup = null
			}
		}
		popup?.show()
	}

	fun removePopup() {
		popup?.dismiss()
	}
}
