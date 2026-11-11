package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * What the small chip on a started item shows.
 */
enum class WatchProgressChip(
	override val nameRes: Int,
) : PreferenceEnum {
	OFF(R.string.lbl_never),
	PERCENT(R.string.watch_progress_percent),
	WATCHED(R.string.watch_progress_watched),
	REMAINING(R.string.watch_progress_remaining),
}
