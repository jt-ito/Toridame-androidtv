package org.jellyfin.androidtv.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.shared.toolbar.AccountTile

private val AvatarSize = 132.dp

/**
 * Who is watching: large round pictures in the middle of the screen, then the server they sign in to. Everything is drawn
 * with the colors of [JellyfinTheme], so another theme applies to it as it is.
 */
@Composable
fun ServerScreen(
	server: Server,
	users: List<User>,
	imageOf: (User) -> String?,
	notification: String?,
	disclaimer: String?,
	onUserClick: (User) -> Unit,
	onAddUser: () -> Unit,
	onChangeServer: () -> Unit,
	onSignOut: (PrivateUser) -> Unit,
	onRemove: (PrivateUser) -> Unit,
) {
	val colors = JellyfinTheme.colorScheme
	val firstFocus = remember { FocusRequester() }
	var menuFor by remember { mutableStateOf<PrivateUser?>(null) }

	// The account in the middle of the row has the focus, or "add account" when there is none
	val focusOnOpen = initialFocus(firstFocus, users.map { it.id })

	// The background is behind the whole screen (see StartupBackground), so the toolbar at the top has it too
	Box(modifier = Modifier.fillMaxSize().then(focusOnOpen)) {
		if (notification != null) {
			Text(
				text = notification,
				color = colors.onRecording,
				fontSize = 16.sp,
				textAlign = TextAlign.Center,
				modifier = Modifier
					.fillMaxWidth()
					.background(colors.recording)
					.padding(16.dp)
					.align(Alignment.TopCenter),
			)
		}

		Column(
			modifier = Modifier
				.align(Alignment.Center)
				.padding(horizontal = 48.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = stringResource(R.string.who_is_watching),
				fontSize = 38.sp,
				fontWeight = FontWeight.Medium,
				color = colors.onBackground,
			)

			Spacer(Modifier.height(32.dp))

			if (users.isEmpty()) {
				Text(
					text = stringResource(R.string.no_user_warning),
					fontSize = 18.sp,
					color = colors.listCaption,
					textAlign = TextAlign.Center,
				)
				Spacer(Modifier.height(24.dp))
			}

			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
				contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
				modifier = Modifier.widthIn(max = 1200.dp),
			) {
				items(users, key = { it.id }) { user ->
					AccountTile(
						modifier = if (users.indexOf(user) == users.size / 2) Modifier.focusRequester(firstFocus) else Modifier,
						name = user.name,
						imageUrl = imageOf(user),
						current = false,
						busy = false,
						avatarSize = AvatarSize,
						onClick = { onUserClick(user) },
						onLongClick = (user as? PrivateUser)?.let { { menuFor = it } },
					)
				}

				item(key = "add") {
					AccountTile(
						modifier = if (users.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
						name = stringResource(R.string.add_user),
						imageUrl = null,
						current = false,
						busy = false,
						addTile = true,
						avatarSize = AvatarSize,
						onClick = onAddUser,
						onLongClick = null,
					)
				}
			}

			Spacer(Modifier.height(24.dp))

			ServerChip(server = server, onClick = onChangeServer)

			if (!disclaimer.isNullOrBlank()) {
				Spacer(Modifier.height(16.dp))
				Text(
					text = disclaimer,
					fontSize = 14.sp,
					color = colors.listCaption,
					textAlign = TextAlign.Center,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.widthIn(max = 700.dp),
				)
			}
		}
	}

	val user = menuFor
	DialogBase(visible = user != null, onDismissRequest = { menuFor = null }) {
		if (user != null) {
			Column(
				modifier = Modifier
					.clip(RoundedCornerShape(24.dp))
					.background(colors.background)
					.padding(32.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(text = user.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

				if (user.accessToken != null) {
					Button(
						modifier = Modifier.fillMaxWidth(),
						onClick = {
							menuFor = null
							onSignOut(user)
						},
					) { Text(stringResource(R.string.lbl_sign_out)) }
				}

				Button(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						menuFor = null
						onRemove(user)
					},
				) { Text(stringResource(R.string.lbl_remove)) }
			}
		}
	}
}

/** The server the accounts are on; pressing it opens the choice of servers. */
@Composable
private fun ServerChip(server: Server, onClick: () -> Unit) {
	val colors = JellyfinTheme.colorScheme
	var focused by remember { mutableStateOf(false) }

	Row(
		modifier = Modifier
			.clip(RoundedCornerShape(50))
			.border(2.dp, if (focused) colors.buttonFocused else colors.onBackground.copy(alpha = 0.25f), RoundedCornerShape(50))
			.background(if (focused) colors.buttonFocused else Color.Transparent)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick)
			.padding(horizontal = 24.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		val content = if (focused) colors.onButtonFocused else colors.onBackground
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_house_edit),
			contentDescription = null,
			modifier = Modifier.size(24.dp),
			tint = content,
		)
		Text(text = server.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = content)
		Text(text = server.address, fontSize = 15.sp, color = content.copy(alpha = 0.7f))
		Text(text = server.version.orEmpty(), fontSize = 15.sp, color = content.copy(alpha = 0.7f))
	}
}
