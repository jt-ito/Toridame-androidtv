package org.jellyfin.androidtv.ui.startup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerAdditionState
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase

private val TileSize = 120.dp

/**
 * Choosing the server: round tiles in the middle of the screen, the saved servers first, then the ones found on the
 * network. Everything is drawn with the colors of [JellyfinTheme], so another theme applies to it as it is.
 */
@Composable
fun SelectServerScreen(
	stored: List<Server>,
	discovered: List<Pair<Server, ServerAdditionState?>>,
	notifications: List<String>,
	appVersion: String,
	onStoredClick: (Server) -> Unit,
	onRemove: (Server) -> Unit,
	onDiscoveredClick: (Server) -> Unit,
	onEnterAddress: () -> Unit,
) {
	val colors = JellyfinTheme.colorScheme
	val firstFocus = remember { FocusRequester() }
	var menuFor by remember { mutableStateOf<Server?>(null) }

	// The saved server in the middle of the row has the focus, or "add server" when there is none
	val focusOnOpen = initialFocus(firstFocus, stored.map { it.id })

	Box(modifier = Modifier.fillMaxSize().then(focusOnOpen)) {
		Column(
			modifier = Modifier
				.align(Alignment.TopCenter)
				.widthIn(max = 700.dp)
				.padding(top = 8.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			for (message in notifications) {
				Text(
					text = message,
					color = colors.onBackground,
					fontSize = 15.sp,
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.background(colors.onBackground.copy(alpha = 0.12f))
						.padding(12.dp),
				)
			}
		}

		Column(
			modifier = Modifier
				.align(Alignment.Center)
				.padding(horizontal = 48.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = if (stored.isEmpty()) stringResource(R.string.welcome_title) else stringResource(R.string.select_server_title),
				fontSize = 38.sp,
				fontWeight = FontWeight.Medium,
				color = colors.onBackground,
			)

			if (stored.isEmpty()) {
				Spacer(Modifier.height(8.dp))
				Text(
					text = stringResource(R.string.welcome_content),
					fontSize = 18.sp,
					color = colors.listCaption,
					textAlign = TextAlign.Center,
				)
			}

			Spacer(Modifier.height(16.dp))

			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
				contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
				modifier = Modifier.widthIn(max = 1200.dp),
			) {
				items(stored, key = { it.id }) { server ->
					ServerTile(
						modifier = if (stored.indexOf(server) == stored.size / 2) Modifier.focusRequester(firstFocus) else Modifier,
						name = server.name,
						caption = server.address,
						onClick = { onStoredClick(server) },
						onLongClick = { menuFor = server },
					)
				}

				item(key = "add") {
					ServerTile(
						modifier = if (stored.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
						name = stringResource(R.string.connect_manually_by_address),
						caption = null,
						addTile = true,
						onClick = onEnterAddress,
					)
				}
			}

			Spacer(Modifier.height(8.dp))

			Text(
				text = stringResource(R.string.discovered_servers_title),
				fontSize = 18.sp,
				fontWeight = FontWeight.Medium,
				color = colors.listCaption,
			)

			if (discovered.isEmpty()) {
				Spacer(Modifier.height(8.dp))
				Text(
					text = stringResource(R.string.discovered_servers_empty),
					fontSize = 15.sp,
					color = colors.listCaption,
					textAlign = TextAlign.Center,
				)
			} else {
				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
					contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
					modifier = Modifier.widthIn(max = 1200.dp),
				) {
					items(discovered, key = { it.first.id }) { (server, state) ->
						ServerTile(
							name = server.name,
							caption = server.address,
							state = state,
							onClick = { onDiscoveredClick(server) },
						)
					}
				}
			}
		}

		Text(
			text = appVersion,
			fontSize = 13.sp,
			color = colors.listCaption,
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(end = 48.dp, bottom = 16.dp),
		)
	}

	val server = menuFor
	DialogBase(visible = server != null, onDismissRequest = { menuFor = null }) {
		if (server != null) {
			Column(
				modifier = Modifier
					.clip(RoundedCornerShape(24.dp))
					.background(colors.background)
					.padding(32.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(text = server.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
				Button(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						menuFor = null
						onRemove(server)
					},
				) { Text(stringResource(R.string.lbl_remove)) }
			}
		}
	}
}

/**
 * One server: a round tile with the name and the address below it. It grows and gets a ring while it has the focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerTile(
	name: String,
	caption: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
	state: ServerAdditionState? = null,
	addTile: Boolean = false,
	size: Dp = TileSize,
) {
	var focused by remember { mutableStateOf(false) }
	val colors = JellyfinTheme.colorScheme
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(140), label = "serverTileScale")
	val connecting = state is ConnectingState
	val failed = state is UnableToConnectState

	Column(
		modifier = modifier
			.width(size + 60.dp)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
				alpha = if (connecting) 0.6f else 1f
			}
			.clip(RoundedCornerShape(20.dp))
			.onFocusChanged { focused = it.isFocused }
			.combinedClickable(enabled = !connecting, onClick = onClick, onLongClick = onLongClick)
			.padding(8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Box(
			modifier = Modifier
				.size(size)
				.border(
					width = if (focused) 4.dp else if (addTile || failed) 3.dp else 0.dp,
					color = when {
						focused -> colors.buttonFocused
						failed -> colors.recording
						else -> colors.onBackground.copy(alpha = 0.35f)
					},
					shape = CircleShape,
				)
				.padding(if (focused) 6.dp else 0.dp)
				.clip(CircleShape)
				.then(if (addTile) Modifier else Modifier.background(colors.badge)),
			contentAlignment = Alignment.Center,
		) {
			when {
				connecting -> CircularProgressIndicator(modifier = Modifier.size(size * 0.4f))
				else -> Icon(
					imageVector = ImageVector.vectorResource(if (addTile) R.drawable.ic_add else R.drawable.ic_house),
					contentDescription = null,
					modifier = Modifier.size(size * 0.4f),
					tint = if (addTile) colors.onBackground.copy(alpha = 0.8f) else colors.onBadge,
				)
			}
		}

		Text(
			text = name,
			fontSize = 19.sp,
			fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
			color = if (focused) colors.onBackground else colors.listHeadline,
			maxLines = 2,
			textAlign = TextAlign.Center,
			overflow = TextOverflow.Ellipsis,
		)
		if (caption != null) {
			Text(
				text = caption,
				fontSize = 13.sp,
				color = colors.listCaption,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}
