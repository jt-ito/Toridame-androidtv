package org.jellyfin.androidtv.ui.playback.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import androidx.leanback.widget.SeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.BookmarkRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID
import java.util.function.LongSupplier

private const val MARKER_COLOR = 0xFFFFB300.toInt()
private const val MARKER_WIDTH_DP = 3f
private const val CHAPTER_COLOR = 0xB3FFFFFF.toInt()
private const val CHAPTER_WIDTH_DP = 2f
private const val CHAPTER_MIN_GAP_DP = 6f
private const val LABEL_TEXT_SP = 15f
private const val LABEL_GAP_DP = 10f
private const val LABEL_SHADOW_DP = 3f
private const val MILLISECONDS_PER_SECOND = 1000f
private const val TICKS_PER_MILLISECOND = 10_000L

/** A chapter of the video: where it starts, in milliseconds, and its name. */
private data class Chapter(val startMs: Long, val name: String?)

/**
 * Small ticks on the seek bar, drawn on top of it so the bar itself stays as Leanback makes it: thin white ones where
 * the chapters start, amber ones at the places the viewer bookmarked. The name of the chapter that the bar is at
 * shows just above it.
 */
class BookmarkMarkers(private val repository: BookmarkRepository) {
	private var bar: View? = null
	private var duration: LongSupplier = LongSupplier { 0L }
	private var seconds: List<Int> = emptyList()
	private var chapters: List<Chapter> = emptyList()

	private val bookmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARKER_COLOR }
	private val chapterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHAPTER_COLOR }
	private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

	private val drawable = object : Drawable() {
		override fun draw(canvas: Canvas) {
			val view = bar ?: return
			val total = duration.asLong
			if (total <= 0L) return

			val density = view.resources.displayMetrics.density
			val left = view.paddingLeft.toFloat()
			val width = (view.width - view.paddingLeft - view.paddingRight).toFloat()

			// Where each chapter starts, leaving out the ones that would sit on top of each other
			var lastX = Float.NEGATIVE_INFINITY
			val chapterHalf = CHAPTER_WIDTH_DP * density / 2f
			for (chapter in chapters) {
				if (chapter.startMs <= 0L) continue
				val x = left + (chapter.startMs.toFloat() / total).coerceIn(0f, 1f) * width
				if (x - lastX < CHAPTER_MIN_GAP_DP * density) continue
				lastX = x
				canvas.drawRect(x - chapterHalf, 0f, x + chapterHalf, view.height.toFloat(), chapterPaint)
			}

			val bookmarkHalf = MARKER_WIDTH_DP * density / 2f
			for (second in seconds) {
				val x = left + (second * MILLISECONDS_PER_SECOND / total).coerceIn(0f, 1f) * width
				canvas.drawRect(x - bookmarkHalf, 0f, x + bookmarkHalf, view.height.toFloat(), bookmarkPaint)
			}

			drawChapterName(canvas, view, total, left, width, density)
		}

		private fun drawChapterName(canvas: Canvas, view: View, total: Long, left: Float, width: Float, density: Float) {
			if (chapters.isEmpty()) return

			val seekBar = view as? SeekBar ?: return
			val max = seekBar.max
			if (max <= 0) return

			val fraction = (seekBar.progress.toFloat() / max).coerceIn(0f, 1f)
			val position = (fraction * total).toLong()
			val name = chapters.lastOrNull { it.startMs <= position }?.name?.takeIf { it.isNotBlank() } ?: return

			labelPaint.textSize = LABEL_TEXT_SP * view.resources.displayMetrics.scaledDensity
			labelPaint.setShadowLayer(LABEL_SHADOW_DP * density, 0f, 0f, 0xFF000000.toInt())

			val textWidth = labelPaint.measureText(name)
			val x = (left + fraction * width - textWidth / 2f).coerceIn(0f, maxOf(0f, view.width - textWidth))
			canvas.drawText(name, x, -LABEL_GAP_DP * density, labelPaint)
		}

		override fun setAlpha(alpha: Int) = Unit
		override fun setColorFilter(colorFilter: ColorFilter?) = Unit

		@Deprecated("Required by Drawable")
		override fun getOpacity() = PixelFormat.TRANSLUCENT
	}

	/** Draws on the given seek bar from now on. [totalDuration] gives the length of the video in milliseconds. */
	fun attach(seekBar: View, totalDuration: LongSupplier) {
		bar?.overlay?.remove(drawable)
		bar = seekBar
		duration = totalDuration

		seekBar.overlay.add(drawable)
		seekBar.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
			drawable.setBounds(0, 0, view.width, view.height)
			view.invalidate()
		}
		drawable.setBounds(0, 0, seekBar.width, seekBar.height)
	}

	/** The chapters of the video, or none (for live TV, or a video without chapters). */
	fun setChapters(item: BaseItemDto?) {
		chapters = item?.chapters.orEmpty()
			.map { Chapter(it.startPositionTicks / TICKS_PER_MILLISECOND, it.name) }
			.sortedBy { it.startMs }
		bar?.invalidate()
	}

	/** Loads the bookmarks of a video and shows them. Without the Toridame server the list stays empty. */
	fun refresh(scope: CoroutineScope, itemId: UUID) {
		scope.launch {
			seconds = repository.list(itemId).map { it.seconds }
			bar?.invalidate()
		}
	}

	fun clear() {
		seconds = emptyList()
		chapters = emptyList()
		bar?.invalidate()
	}
}
