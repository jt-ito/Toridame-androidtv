package org.jellyfin.androidtv.ui.playback.overlay

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.shared.SpoilerGuard
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.java.KoinJavaComponent

private const val DELAY_MS = 5000L
private const val CHECK_MS = 500L
private const val POSTER_HEIGHT = 400

/**
 * A paused video shows the poster, title and synopsis at the side of the picture, when an administrator has switched it
 * on (the web client's pause screen). It never takes a key press, and goes away when playback goes on.
 */
class PauseScreen(
	private val parent: ViewGroup,
	private val controller: () -> PlaybackController?,
) {
	private var item by mutableStateOf<BaseItemDto?>(null)
	private var enabled = false
	private var pausedSince = 0L
	private val handler = Handler(Looper.getMainLooper())
	private var scope: CoroutineScope? = null

	private val view = ComposeView(parent.context).apply {
		visibility = View.GONE
		isFocusable = false
		setContent { JellyfinTheme { PauseScreenContent(item) } }
	}

	private val check = object : Runnable {
		override fun run() {
			val paused = controller()?.isPaused == true
			if (paused && enabled) {
				if (pausedSince == 0L) pausedSince = System.currentTimeMillis()
				if (view.visibility != View.VISIBLE && System.currentTimeMillis() - pausedSince >= DELAY_MS) {
					item = controller()?.currentlyPlayingItem
					view.visibility = View.VISIBLE
				}
			} else {
				pausedSince = 0L
				if (view.visibility == View.VISIBLE) view.visibility = View.GONE
			}
			handler.postDelayed(this, CHECK_MS)
		}
	}

	init {
		parent.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
	}

	fun start() {
		scope?.cancel()
		val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
		scope = newScope
		newScope.launch {
			enabled = KoinJavaComponent.get<ToridameRepository>(ToridameRepository::class.java).customization().pauseScreenEnabled
		}

		handler.removeCallbacks(check)
		handler.post(check)
	}

	fun stop() {
		handler.removeCallbacks(check)
		scope?.cancel()
		scope = null
		pausedSince = 0L
		view.visibility = View.GONE
	}
}

@androidx.compose.runtime.Composable
private fun PauseScreenContent(item: BaseItemDto?) {
	if (item == null) return
	val api = org.koin.compose.koinInject<ApiClient>()

	// A poster of the series for an episode
	val posterImage = if (item.type == BaseItemKind.EPISODE) {
		item.parentImages[ImageType.PRIMARY] ?: item.seriesPrimaryImage
	} else {
		item.itemImages[ImageType.PRIMARY]
	}
	val posterUrl = posterImage?.getUrl(api, maxHeight = POSTER_HEIGHT)

	androidx.compose.foundation.layout.Box(
		modifier = Modifier
			.fillMaxWidth()
			.fillMaxHeight(),
		contentAlignment = Alignment.CenterEnd,
	) {
		Row(
			modifier = Modifier
				.fillMaxHeight()
				.width(620.dp)
				.background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xDD000000))))
				.padding(start = 90.dp, end = 56.dp, top = 56.dp, bottom = 56.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(24.dp),
		) {
			if (posterUrl != null) {
				Image(
					painter = rememberAsyncImagePainter(posterUrl),
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier
						.height(220.dp)
						.width(150.dp)
						.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
				)
			}

			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					text = item.seriesName ?: item.name.orEmpty(),
					fontSize = 28.sp,
					fontWeight = FontWeight.Bold,
					color = Color.White,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)

				val subtitle = listOfNotNull(
					if (item.type == BaseItemKind.EPISODE && item.parentIndexNumber != null && item.indexNumber != null) "S${item.parentIndexNumber} E${item.indexNumber}" else null,
					if (item.type == BaseItemKind.EPISODE) item.name else null,
					item.productionYear?.toString(),
				).joinToString(" · ")
				if (subtitle.isNotBlank()) Text(text = subtitle, fontSize = 18.sp, color = Color(0xCCFFFFFF))

				item.overview?.takeIf { it.isNotBlank() && !SpoilerGuard.covers(item) }?.let {
					Text(text = it, fontSize = 16.sp, color = Color(0xCCFFFFFF), maxLines = 8, overflow = TextOverflow.Ellipsis)
				}
			}
		}
	}
}
