package org.jellyfin.androidtv.ui.shared.toolbar

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.ui.base.Icon
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AutomaticAuthenticateMethod
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.util.initialsOf
import org.koin.compose.koinInject

/**
 * Switches between the accounts that are signed in on this device without going through the sign-in screen. Only
 * accounts with a saved sign-in token show up, and picking one checks that the server still accepts it. Long-press an
 * account to forget it.
 */
@Composable
fun AccountSwitcherDialog(
	visible: Boolean,
	server: Server?,
	currentUserId: java.util.UUID?,
	onDismissRequest: () -> Unit,
) {
	val serverUserRepository = koinInject<ServerUserRepository>()
	val authenticationRepository = koinInject<AuthenticationRepository>()
	val sessionRepository = koinInject<SessionRepository>()
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val activity = LocalActivity.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var accounts by remember { mutableStateOf(emptyList<PrivateUser>()) }
	var busy by remember { mutableStateOf(false) }
	val firstFocus = remember { FocusRequester() }

	fun reload() {
		accounts = server?.let { serverUserRepository.getStoredServerUsers(it).filter { user -> user.accessToken != null } }.orEmpty()
	}

	LaunchedEffect(visible, server) { if (visible) reload() }
	LaunchedEffect(accounts.isNotEmpty(), visible) { if (visible && accounts.isNotEmpty()) runCatching { firstFocus.requestFocus() } }

	val server0 = server
	DialogBase(
		visible = visible,
		onDismissRequest = onDismissRequest,
	) {
		Column(
			modifier = Modifier
				.clip(RoundedCornerShape(28.dp))
				// The darkest color of the theme, not the gray of its panels
				.background(JellyfinTheme.colorScheme.background)
				.padding(horizontal = 56.dp, vertical = 44.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(
				text = stringResource(R.string.account_switch_title),
				fontSize = 30.sp,
				fontWeight = FontWeight.Bold,
				color = JellyfinTheme.colorScheme.onBackground,
			)
			Text(
				text = server0?.name.orEmpty(),
				fontSize = 16.sp,
				color = JellyfinTheme.colorScheme.listCaption,
			)

			Spacer(Modifier.height(24.dp))

			// Large round pictures to pick from, one after the other
			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(28.dp),
				contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
				modifier = Modifier.widthIn(max = 880.dp),
			) {
				itemsIndexed(accounts, key = { _, account -> account.id }) { index, account ->
					val isCurrent = account.id == currentUserId
					val first = if (isCurrent || (index == 0 && accounts.none { it.id == currentUserId })) Modifier.focusRequester(firstFocus) else Modifier

					AccountTile(
						modifier = first,
						name = account.name,
						imageUrl = server0?.let { authenticationRepository.getUserImageUrl(it, account) },
						current = isCurrent,
						busy = busy,
						onClick = {
							if (isCurrent || server0 == null) {
								onDismissRequest()
							} else {
								busy = true
								scope.launch {
									val state = runCatching {
										authenticationRepository.authenticate(server0, AutomaticAuthenticateMethod(account)).last()
									}.getOrNull()

									if (state == AuthenticatedState) {
										onDismissRequest()
										// Start the app over as the new account, like opening it fresh
										mediaManager.clearAudioQueue()
										navigationRepository.reset(Destinations.home, clearHistory = true)
										activity?.recreate()
									} else {
										Toast.makeText(
											context,
											context.getString(
												if (state == ServerUnavailableState || state == null) R.string.account_switch_unreachable
												else R.string.account_switch_failed,
												account.name,
											),
											Toast.LENGTH_LONG,
										).show()
										busy = false
									}
								}
							}
						},
						onLongClick = if (isCurrent) null else {
							{
								authenticationRepository.logout(account)
								Toast.makeText(context, context.getString(R.string.account_forgotten, account.name), Toast.LENGTH_SHORT).show()
								reload()
							}
						},
					)
				}

				item(key = "add") {
					AccountTile(
						name = stringResource(R.string.account_add),
						imageUrl = null,
						current = false,
						busy = busy,
						addTile = true,
						onClick = {
							// Signing in as someone else keeps this account saved, so it stays in the list
							mediaManager.clearAudioQueue()
							sessionRepository.destroyCurrentSession()
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						},
						onLongClick = null,
					)
				}
			}

			Spacer(Modifier.height(8.dp))

			Text(
				text = stringResource(R.string.account_switch_help),
				fontSize = 14.sp,
				color = JellyfinTheme.colorScheme.listCaption,
			)
		}
	}
}

/**
 * One account: a large round picture with the name below it. It grows and gets a ring while it has the focus, and the
 * account that is signed in now has a mark and its name in bold.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountTile(
	name: String,
	imageUrl: String?,
	current: Boolean,
	busy: Boolean,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)?,
	modifier: Modifier = Modifier,
	addTile: Boolean = false,
	avatarSize: androidx.compose.ui.unit.Dp = 116.dp,
) {
	var focused by remember { mutableStateOf(false) }
	val colors = JellyfinTheme.colorScheme
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(140), label = "accountTileScale")

	Column(
		modifier = modifier
			.width(avatarSize + 34.dp)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
				alpha = if (busy) 0.5f else 1f
			}
			.clip(RoundedCornerShape(20.dp))
			.onFocusChanged { focused = it.isFocused }
			.combinedClickable(enabled = !busy, onClick = onClick, onLongClick = onLongClick)
			.padding(8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(contentAlignment = Alignment.Center) {
			Box(
				modifier = Modifier
					.size(avatarSize)
					.border(width = if (focused) 4.dp else 0.dp, color = if (focused) colors.buttonFocused else Color.Transparent, shape = CircleShape)
					.padding(if (focused) 6.dp else 0.dp)
					.clip(CircleShape)
					.then(if (addTile) Modifier.border(3.dp, colors.onBackground.copy(alpha = 0.35f), CircleShape) else Modifier.background(colors.badge)),
				contentAlignment = Alignment.Center,
			) {
				if (addTile) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_user_add),
						contentDescription = null,
						modifier = Modifier.size(avatarSize * 0.38f),
						tint = colors.onBackground.copy(alpha = 0.8f),
					)
				} else {
					val initials = initialsOf(name)
					Text(
						text = initials,
						fontSize = (avatarSize.value * (if (initials.length > 1) 0.31f else 0.38f)).sp,
						fontWeight = FontWeight.Bold,
						color = colors.onBadge,
					)
					if (imageUrl != null) {
						Image(
							painter = rememberAsyncImagePainter(imageUrl),
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier.fillMaxSize(),
						)
					}
				}
			}

			if (current) {
				// The account that is signed in now
				Box(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.size(32.dp)
						.clip(CircleShape)
						.background(colors.buttonActive),
					contentAlignment = Alignment.Center,
				) {
					Text(text = "\u2713", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onButtonActive)
				}
			}
		}

		Text(
			text = name,
			fontSize = (avatarSize.value * 0.165f).sp,
			fontWeight = if (focused || current) FontWeight.Bold else FontWeight.Normal,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			color = if (focused) colors.onBackground else colors.listHeadline,
		)
		Text(
			text = if (current) stringResource(R.string.account_current) else "",
			fontSize = 14.sp,
			color = colors.listCaption,
			maxLines = 1,
		)
	}
}
