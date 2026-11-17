package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.PinnedRow
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/** A row for one collection (its titles by name) or one genre (the newest movies and series in it). */
class HomeFragmentPinnedRow(private val pinned: PinnedRow) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val query = when (pinned.kind) {
			PinnedRow.COLLECTION -> GetItemsRequest(
				parentId = UUID.fromString(pinned.id),
				sortBy = setOf(ItemSortBy.SORT_NAME),
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
				limit = ITEM_LIMIT,
			)

			else -> GetItemsRequest(
				genres = setOf(pinned.id),
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				recursive = true,
				sortBy = setOf(ItemSortBy.DATE_CREATED),
				sortOrder = setOf(SortOrder.DESCENDING),
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
				limit = ITEM_LIMIT,
			)
		}

		HomeFragmentBrowseRowDefRow(
			// The chunk size is how many titles the loader asks for
			BrowseRowDef(pinned.name, query, ITEM_LIMIT, false, true, arrayOf(ChangeTriggerType.LibraryUpdated))
		).addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	private companion object {
		const val ITEM_LIMIT = 50
	}
}
