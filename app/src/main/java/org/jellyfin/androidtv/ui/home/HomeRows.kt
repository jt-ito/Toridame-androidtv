package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.AppNotification
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CardViewHolderContent

private val ROW_START_PADDING = 56.dp
private val CARD_SPACING = 4.dp
private val FOCUS_PADDING = 4.dp
private val ROW_SPACING = 12.dp
private val ROW_HEADER_SLOT = 44.dp

/** Brings the focused card to just below the top of the list, so the row that has the focus is always the first one. */
@OptIn(ExperimentalFoundationApi::class)
private class RowToTopSpec(private val headerPx: Float) : BringIntoViewSpec {
	override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - headerPx
}

/**
 * The rows of the home screen: a list of rows, each one a list of cards. Which rows there are and what is in them comes
 * from [controller]; this only draws them and tells the controller what is focused and clicked.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeRows(
	controller: HomeRowsController,
	modifier: Modifier = Modifier,
) {
	val rows by controller.rows.snapshotState()
	val listState = rememberLazyListState()
	val density = LocalDensity.current
	val defaultSpec = LocalBringIntoViewSpec.current
	val rowToTop = remember(density) { RowToTopSpec(with(density) { ROW_HEADER_SLOT.toPx() }) }

	// The rows come in one by one, and start out empty: focus the first card as soon as there is one, but only once
	val requester = remember { FocusRequester() }
	var hasFocused by remember { mutableStateOf(false) }
	var loadedRows by remember { mutableIntStateOf(0) }
	LaunchedEffect(loadedRows) {
		if (loadedRows > 0 && !hasFocused) {
			listState.scrollToItem(0)
			hasFocused = runCatching { requester.requestFocus() }.getOrDefault(false)
		}
	}

	CompositionLocalProvider(LocalBringIntoViewSpec provides rowToTop) {
		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(bottom = 64.dp),
			verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
			modifier = modifier
				.onPreviewKeyEvent {
					if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) controller.onMenuKey() else false
				}
				.focusRequester(requester)
				.focusRestorer()
				.focusGroup(),
		) {
			itemsIndexed(rows, key = { _, row -> System.identityHashCode(row) }) { _, row ->
				if (row is ListRow) {
					// Sideways scrolling needs the usual behaviour again
					CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
						HomeRow(controller, row, onLoaded = { loadedRows++ })
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeRow(controller: HomeRowsController, row: ListRow, onLoaded: () -> Unit) {
	val items by row.adapter.snapshotState()
	LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) onLoaded() }
	val presenter = remember(row) { row.adapter.presenterSelector.getPresenter(null) as? CardPresenter }

	Column {
		Box(modifier = Modifier.padding(start = ROW_START_PADDING), contentAlignment = Alignment.CenterStart) {
			row.headerItem?.name?.let {
				Text(
					text = it,
					fontSize = 20.sp,
					fontWeight = FontWeight.SemiBold,
					color = JellyfinTheme.colorScheme.onBackground,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(bottom = 8.dp),
				)
			}
		}

		LazyRow(
			contentPadding = PaddingValues(horizontal = ROW_START_PADDING - FOCUS_PADDING),
			horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
			modifier = Modifier.focusRestorer(),
		) {
			itemsIndexed(items) { _, item ->
				HomeRowItem(controller, row, item, presenter)
			}
		}
	}
}

@Composable
private fun HomeRowItem(controller: HomeRowsController, row: ListRow, item: Any?, presenter: CardPresenter?) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) FOCUS_SCALE else 1f, label = "HomeCardScale")
	val shape = JellyfinTheme.shapes.medium

	Box(
		modifier = Modifier
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.then(if (focused) Modifier.border(2.dp, JellyfinTheme.colorScheme.buttonFocused, shape) else Modifier)
			.onFocusChanged {
				focused = it.isFocused
				if (it.isFocused) controller.onItemFocused(item, row)
			}
			.combinedClickable(
				// The focus border is the highlight: the default one is a square wash over the whole item
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = { controller.onItemClicked(item, row) },
				onLongClick = { controller.onMenuKey() },
			)
			// Room between the border and what is inside it
			.padding(FOCUS_PADDING)
	) {
		when (item) {
			is BaseRowItem -> {
				// The row says how its cards look: the same card as everywhere else in the app
				val cards = presenter ?: DEFAULT_PRESENTER
				CardViewHolderContent(
					item = item,
					focused = focused,
					showInfo = cards.showInfo,
					imageType = cards.imageType,
					staticHeight = cards.staticHeight,
					uniformAspect = cards.uniformAspect,
				)
			}

			is GridButton -> GridButtonCard(item)
			is AppNotification -> NotificationCard(item)
		}
	}
}

@Composable
private fun GridButtonCard(button: GridButton) {
	Box(
		modifier = Modifier
			.width(110.dp)
			.clip(JellyfinTheme.shapes.small)
			.background(colorResource(R.color.button_default_normal_background))
	) {
		if (button.imageRes != null) {
			Image(
				painter = painterResource(button.imageRes),
				contentDescription = button.text,
				contentScale = ContentScale.Crop,
				modifier = Modifier.size(110.dp),
			)
		}

		Text(
			text = button.text,
			fontSize = 12.sp,
			color = colorResource(R.color.button_default_normal_text),
			modifier = Modifier
				.padding(15.dp, 10.dp)
				.align(if (button.imageRes != null) Alignment.BottomStart else Alignment.CenterStart),
		)
	}
}

@Composable
private fun NotificationCard(notification: AppNotification) {
	Box(
		modifier = Modifier
			.size(280.dp, 90.dp)
			.clip(JellyfinTheme.shapes.small)
			.background(JellyfinTheme.colorScheme.surface)
			.padding(12.dp)
	) {
		Text(
			text = notification.message,
			color = JellyfinTheme.colorScheme.onBackground,
			maxLines = 3,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/** What is in the adapter, as state that changes whenever the adapter does. */
@Composable
private fun ObjectAdapter.snapshotState(): State<List<Any?>> {
	val state = remember(this) { mutableStateOf(snapshot()) }

	DisposableEffect(this) {
		val observer = object : ObjectAdapter.DataObserver() {
			override fun onChanged() {
				state.value = snapshot()
			}
		}
		registerObserver(observer)
		state.value = snapshot()
		onDispose { unregisterObserver(observer) }
	}

	return state
}

private fun ObjectAdapter.snapshot(): List<Any?> = List(size()) { get(it) }

private const val FOCUS_SCALE = 1.06f
private val DEFAULT_PRESENTER = CardPresenter()
