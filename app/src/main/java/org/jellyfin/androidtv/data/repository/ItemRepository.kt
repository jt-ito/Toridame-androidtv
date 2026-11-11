package org.jellyfin.androidtv.data.repository

import org.jellyfin.sdk.model.api.ItemFields

object ItemRepository {
	val itemFields = setOf(
		ItemFields.CAN_DELETE,
		ItemFields.CHANNEL_INFO,
		ItemFields.CHAPTERS,
		ItemFields.CHILD_COUNT,
		ItemFields.CUMULATIVE_RUN_TIME_TICKS,
		ItemFields.DATE_CREATED,
		ItemFields.DISPLAY_PREFERENCES_ID,
		ItemFields.GENRES,
		ItemFields.ITEM_COUNTS,
		ItemFields.MEDIA_SOURCE_COUNT,
		ItemFields.MEDIA_SOURCES,
		ItemFields.MEDIA_STREAMS,
		ItemFields.OVERVIEW,
		ItemFields.PATH,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
		ItemFields.TAGLINES,
		ItemFields.TRICKPLAY,
	)

	// Lighter field set for home screen rows and for video lists and grids (movie and series
	// libraries, seasons, next up, similar items...) - excludes heavy fields like MediaSources,
	// MediaStreams, Chapters, Trickplay that aren't needed for display. On a library with hundreds
	// of titles these made every page of the grid several times bigger for the server to build and
	// for the TV to parse. Full item data is fetched when user selects an item.
	private val baseBrowseFields = setOf(
		ItemFields.CAN_DELETE,
		ItemFields.CHILD_COUNT,
		ItemFields.DATE_CREATED,
		ItemFields.GENRES,
		ItemFields.OVERVIEW,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
	)

	// The picture size is two numbers per item, only asked for when the picture size badge is on
	private val sizeFields = setOf(ItemFields.WIDTH, ItemFields.HEIGHT)

	val browseFields: Set<ItemFields>
		get() {
			val preferences = org.koin.java.KoinJavaComponent.get<org.jellyfin.androidtv.preference.UserPreferences>(
				org.jellyfin.androidtv.preference.UserPreferences::class.java
			)
			val sizeBadge = preferences[org.jellyfin.androidtv.preference.UserPreferences.badgesEnabled] &&
				preferences[org.jellyfin.androidtv.preference.UserPreferences.badgesQuality]
			return if (sizeBadge) baseBrowseFields + sizeFields else baseBrowseFields
		}
}
