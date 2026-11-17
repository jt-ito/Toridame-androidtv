package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.popover.Popover
import org.jellyfin.androidtv.ui.base.popover.PopoverMenu
import org.jellyfin.androidtv.ui.base.popover.PopoverMenuCheckboxItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsDialog
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.routes
import java.util.UUID

fun BrowseGridFragment.createSettingsVisibility() = MutableStateFlow(false)

fun BrowseGridFragment.addSettings(
	view: ComposeView,
	itemId: UUID,
	displayPreferencesId: String,
	visible: MutableStateFlow<Boolean>,
) {
	view.setContent {
		val isVisible by visible.collectAsState(false)

		ProvideRouter(
			routes,
			Routes.LIBRARIES_DISPLAY,
			mapOf("itemId" to itemId.toString(), "displayPreferencesId" to displayPreferencesId)
		) {
			SettingsDialog(
				visible = isVisible,
				onDismissRequest = {
					visible.value = false
					onResume()
				}
			) {
				SettingsRouterContent()
			}
		}
	}
}

/** One line of the sort menu. */
data class SortMenuItem(val id: Int, val label: String, val selected: Boolean)

/** What the sort menu shows: whether it is open, and its lines. */
class SortMenuState {
	val visible = MutableStateFlow(false)
	val items = MutableStateFlow<List<SortMenuItem>>(emptyList())
}

/**
 * The menu that opens from the sort button, drawn with the colors and shapes of the theme like the rest of the app.
 * The view is as small as it gets and sits under the buttons; the menu opens from its right edge.
 */
fun BrowseGridFragment.addSortMenu(view: ComposeView, state: SortMenuState, onSelect: (Int) -> Unit, onLeft: () -> Unit) {
	view.setContent {
		JellyfinTheme {
			val visible by state.visible.collectAsState()
			val items by state.items.collectAsState()

			Box(modifier = Modifier.size(1.dp)) {
				Popover(
					expanded = visible,
					onDismissRequest = { state.visible.value = false },
					alignment = Alignment.BottomEnd,
					shape = RoundedCornerShape(16.dp),
				) {
					// The focus starts on what is chosen now
					val selectedFocus = remember { FocusRequester() }
					LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

					PopoverMenu(
						modifier = Modifier
							// Left closes the menu and goes on to the titles
							.onPreviewKeyEvent {
								if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
									state.visible.value = false
									onLeft()
									true
								} else {
									false
								}
							}
							.widthIn(min = 280.dp)
							.heightIn(max = 330.dp)
							.verticalScroll(rememberScrollState()),
						paddingValues = PaddingValues(8.dp),
					) {
						items.forEachIndexed { index, item ->
							PopoverMenuCheckboxItem(
								selected = item.selected,
								onClick = {
									state.visible.value = false
									onSelect(item.id)
								},
								modifier = (if (item.selected) Modifier.focusRequester(selectedFocus) else Modifier)
									// Up from the first line closes the menu too
									.then(
										if (index == 0) Modifier.onPreviewKeyEvent {
											if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
												state.visible.value = false
												true
											} else {
												false
											}
										} else Modifier
									),
							) {
								Text(text = item.label)
							}
						}
					}
				}
			}
		}
	}
}
