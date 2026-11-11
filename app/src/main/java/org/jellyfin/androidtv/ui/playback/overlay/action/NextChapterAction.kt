package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.View
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

private const val INDICATOR_MS = 1500

/** Jumps to the start of the next chapter, so the chapter list is not needed for that. */
class NextChapterAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	init {
		initializeWithIcon(R.drawable.ic_next_chapter)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		// Goes through the controller so that quick presses go on through the chapters and none is lost to a seek in progress
		val name = playbackController.jumpToNextChapter() ?: return
		videoPlayerAdapter.masterOverlayFragment.showChapterIndicator(name, INDICATOR_MS)
	}
}
