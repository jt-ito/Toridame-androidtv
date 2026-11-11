package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * An episode card in the episode panel of the player. The first line is always the season and episode number, which
 * is short enough to stay put; the second line holds the names, which scroll when they are too long for the card.
 */
class EpisodePanelRowItem(
	item: BaseItemDto,
) : BaseItemDtoBaseRowItem(item, false, true) {
	override fun getCardName(context: Context): String? {
		val season = baseItem?.parentIndexNumber
		val episode = baseItem?.indexNumber
		return when {
			season != null && episode != null -> "S$season:E$episode"
			episode != null -> "E$episode"
			else -> baseItem?.name
		}
	}

	override fun getSubText(context: Context): String? {
		// Movies and other videos have no season and episode, so the name is the title already
		if (baseItem?.indexNumber == null) return baseItem?.productionYear?.toString()

		return listOfNotNull(baseItem?.name, baseItem?.seriesName)
			.filter { it.isNotBlank() }
			.joinToString("  ·  ")
	}
}
