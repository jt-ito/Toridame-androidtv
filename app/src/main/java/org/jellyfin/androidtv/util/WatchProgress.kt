package org.jellyfin.androidtv.util

import org.jellyfin.androidtv.preference.constant.WatchProgressChip

private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

/**
 * "1:02:03" or "2:03" for a number of ticks.
 */
fun formatTicks(ticks: Long): String {
	val total = (ticks.coerceAtLeast(0) / TICKS_PER_SECOND).toInt()
	val hours = total / SECONDS_PER_HOUR
	val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
	val seconds = total % SECONDS_PER_MINUTE

	return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
	else "%d:%02d".format(minutes, seconds)
}

/**
 * The text of the progress chip on a card, or null when there is nothing started to show.
 */
fun formatWatchProgress(mode: WatchProgressChip, positionTicks: Long?, runtimeTicks: Long?): String? {
	if (mode == WatchProgressChip.OFF) return null
	if (positionTicks == null || positionTicks <= 0 || runtimeTicks == null || runtimeTicks <= 0) return null

	val position = minOf(positionTicks, runtimeTicks)
	return when (mode) {
		WatchProgressChip.WATCHED -> formatTicks(position)
		WatchProgressChip.REMAINING -> formatTicks(runtimeTicks - position)
		else -> "${Math.round(position * 100.0 / runtimeTicks)}%"
	}
}
