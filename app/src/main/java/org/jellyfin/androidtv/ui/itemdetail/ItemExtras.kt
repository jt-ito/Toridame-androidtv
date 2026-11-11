package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.android.ext.android.inject

private const val RATINGS_ID = 9101L
private const val ELSEWHERE_ID = 9102L
private const val REVIEWS_ID = 9103L
private const val CARD_WIDTH_DP = 190
private const val WIDE_CARD_WIDTH_DP = 380
private const val CARD_PADDING_DP = 14
private const val CARD_RADIUS_DP = 10
private const val BODY_LINES = 5
private const val CARD_COLOR = 0x66FFFFFF
private const val CARD_FOCUSED_COLOR = 0xCCFFFFFF.toInt()

/** A small card with a title, a line of text, and optionally a longer text that opens in full when selected. */
data class ExtraInfoCard(
	val title: String,
	val body: String,
	val detail: String? = null,
	val wide: Boolean = false,
	val fullText: String? = null,
)

class ExtraInfoCardPresenter : Presenter() {
	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val context = parent.context
		val density = context.resources.displayMetrics.density
		val padding = (CARD_PADDING_DP * density).toInt()

		val background = GradientDrawable().apply {
			cornerRadius = CARD_RADIUS_DP * density
			setColor(CARD_COLOR)
		}

		val card = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, padding, padding, padding)
			this.background = background
			isFocusable = true
			isFocusableInTouchMode = true
			setOnFocusChangeListener { _, focused ->
				background.setColor(if (focused) CARD_FOCUSED_COLOR else CARD_COLOR)
				children().forEach { it.setTextColor(if (focused) Color.BLACK else Color.WHITE) }
			}
		}

		listOf(TITLE to 14f, BODY to 22f, DETAIL to 13f).forEach { (tag, size) ->
			card.addView(
				TextView(context).apply {
					this.tag = tag
					textSize = size
					setTextColor(Color.WHITE)
					if (tag == TITLE) setTypeface(typeface, android.graphics.Typeface.BOLD)
				}
			)
		}

		return ViewHolder(card)
	}

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		val card = item as? ExtraInfoCard ?: return
		val view = viewHolder.view as LinearLayout
		val density = view.resources.displayMetrics.density
		val widthDp = if (card.wide) WIDE_CARD_WIDTH_DP else CARD_WIDTH_DP

		view.layoutParams = ViewGroup.MarginLayoutParams((widthDp * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

		fun text(tag: String) = view.findViewWithTag<TextView>(tag)
		text(TITLE).text = card.title
		text(BODY).apply {
			text = card.body
			if (card.wide) {
				textSize = 15f
				maxLines = BODY_LINES
				ellipsize = TextUtils.TruncateAt.END
			}
		}
		text(DETAIL).apply {
			text = card.detail.orEmpty()
			visibility = if (card.detail.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
		}
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit

	private fun LinearLayout.children() = (0 until childCount).map { getChildAt(it) as TextView }

	private companion object {
		const val TITLE = "title"
		const val BODY = "body"
		const val DETAIL = "detail"
	}
}

/**
 * Rows on the page of a movie or series that come from the Toridame server: ratings from other sites, where to
 * watch, and reviews. A row only shows up when the server has something for it.
 */
object ItemExtras {
	@JvmStatic
	fun load(fragment: Fragment, item: BaseItemDto, adapter: MutableObjectAdapter<Row>) {
		if (item.type != BaseItemKind.MOVIE && item.type != BaseItemKind.SERIES) return

		val repository by fragment.inject<ToridameRepository>()
		val context = fragment.requireContext()

		fragment.lifecycleScope.launch {
			val ratings = async { repository.externalRatings(item.id) }
			val elsewhere = async { repository.elsewhere(item.id) }
			val reviews = async { repository.reviews(item.id) }

			// A page that was refreshed in the meantime starts again, so what an older request adds goes first
			replaceRow(
				adapter,
				RATINGS_ID,
				context.getString(R.string.extras_ratings),
				ratings.await().map { ExtraInfoCard(it.label, it.display.orEmpty()) },
			)

			val where = elsewhere.await()
			replaceRow(
				adapter,
				ELSEWHERE_ID,
				if (where == null) "" else context.getString(R.string.extras_elsewhere, where.country),
				where?.providers.orEmpty().map { provider ->
					ExtraInfoCard(
						title = context.getString(
							when (provider.kind) {
								"stream" -> R.string.extras_stream
								"free" -> R.string.extras_free
								"rent" -> R.string.extras_rent
								else -> R.string.extras_buy
							}
						),
						body = provider.name,
					)
				},
			)

			val list = reviews.await()
			replaceRow(
				adapter,
				REVIEWS_ID,
				context.getString(R.string.extras_reviews, list.size),
				list.map { review ->
					val rating = review.rating?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) "${it.toInt()}/10" else "%.1f/10".format(it) }
					ExtraInfoCard(
						title = listOfNotNull(review.author.ifBlank { context.getString(R.string.extras_anonymous) }, rating).joinToString(" · "),
						body = review.content,
						detail = review.date,
						wide = true,
						fullText = review.content,
					)
				},
			)
		}
	}

	private fun replaceRow(adapter: MutableObjectAdapter<Row>, id: Long, title: String, cards: List<ExtraInfoCard>) {
		// Take away what an earlier load put there
		for (index in adapter.size() - 1 downTo 0) {
			val row = adapter.get(index)
			if (row is ListRow && row.headerItem?.id == id) adapter.removeAt(index)
		}

		if (cards.isEmpty()) return

		val cardAdapter = ArrayObjectAdapter(ExtraInfoCardPresenter())
		cards.forEach { cardAdapter.add(it) }
		adapter.add(ListRow(HeaderItem(id, title), cardAdapter))
	}

	/** Shows a long text, such as a review, in full. */
	@JvmStatic
	fun showFullText(context: Context, card: ExtraInfoCard) {
		val text = card.fullText ?: return
		AlertDialog.Builder(context)
			.setTitle(card.title)
			.setMessage(text)
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}
}
