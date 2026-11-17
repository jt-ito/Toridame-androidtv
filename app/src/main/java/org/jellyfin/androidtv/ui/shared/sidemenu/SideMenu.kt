package org.jellyfin.androidtv.ui.shared.sidemenu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.rememberQueueEntry
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioNowPlayingFragment
import org.jellyfin.androidtv.ui.playback.CustomPlaybackOverlayFragment
import org.jellyfin.androidtv.ui.playback.nextup.NextUpFragment
import org.jellyfin.androidtv.ui.playback.stillwatching.StillWatchingFragment
import org.jellyfin.androidtv.ui.search.SearchFragment
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.data.repository.rememberFeature
import org.jellyfin.androidtv.ui.shared.toolbar.AccountSwitcherDialog
import org.jellyfin.androidtv.ui.shared.toolbar.CatchUpDialog
import org.jellyfin.androidtv.ui.shared.toolbar.rememberRandomTitle
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.reflect.KClass
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.preference.PinnedRow
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.toPinnedRows
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import java.util.UUID

/** The width of the menu while it is folded away; the screens next to it are laid out beside this. */
val SideMenuCollapsedWidth = 72.dp

// The open menu is as wide as its longest label needs, within these limits
private val MinExpandedWidth = 180.dp
private val MaxExpandedWidth = 420.dp
private val ItemHeight = SideMenuCollapsedWidth - 16.dp
// With many pinned items the buttons shrink, down to this, and then the pinned ones scroll
private val MinItemHeight = 36.dp
private val ItemSpacing = 6.dp
// The menu's own padding, top and bottom, and room kept for the gaps between its groups
private val MenuPadding = 24.dp
private val GroupGaps = 32.dp

/** The screens that use the whole display, where the menu would only be in the way. */
private val screensWithoutMenu: Set<KClass<out androidx.fragment.app.Fragment>> = setOf(
	CustomPlaybackOverlayFragment::class,
	AudioNowPlayingFragment::class,
	NextUpFragment::class,
	StillWatchingFragment::class,
)

/**
 * Tells whether the side menu is shown on the screen that is open.
 */
@Composable
fun rememberSideMenuVisible(navigationRepository: NavigationRepository = koinInject()): Boolean {
	val destination by navigationRepository.currentDestination.collectAsState()
	return destination.fragment !in screensWithoutMenu
}

/**
 * The menu at the side of the screen: the account, home, search and the rest. It is folded down to icons while the
 * focus is somewhere else, and opens up while it holds the focus, so it can be reached from anywhere by going left.
 * Everything is drawn with the colors of [JellyfinTheme], so a different theme applies to it as it is.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SideMenu(
	modifier: Modifier = Modifier,
	/** Moves the focus back to the screen next to the menu. */
	onLeave: () -> Unit = {},
) {
	val navigationRepository = koinInject<NavigationRepository>()
	val userRepository = koinInject<UserRepository>()
	val sessionRepository = koinInject<SessionRepository>()
	val serverRepository = koinInject<ServerRepository>()
	val playbackManager = koinInject<PlaybackManager>()
	val api = koinInject<ApiClient>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()

	val destination by navigationRepository.currentDestination.collectAsState()
	val currentServer by serverRepository.currentServer.collectAsState()
	// Keeps the picture while signing out, by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }
	val playing by rememberQueueEntry(playbackManager)

	val randomTitle = rememberRandomTitle()
	val accountSwitcherOn = rememberFeature("accountSwitcher")
	val catchUpOn = rememberFeature("catchUp")
	var showAccountSwitcher by remember { mutableStateOf(false) }
	var showCatchUp by remember { mutableStateOf(false) }
	var expanded by remember { mutableStateOf(false) }

	// What was pinned in the settings; read again whenever the settings close
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val itemLauncher = koinInject<ItemLauncher>()
	val scope = rememberCoroutineScope()
	val settingsVisible by settingsViewModel.visible.collectAsState()
	val pins = remember(settingsVisible) { userSettingPreferences[UserSettingPreferences.sidePins].toPinnedRows() }
	val openPin: (PinnedRow) -> Unit = { pin ->
		scope.launch {
			val item = runCatching {
				withContext(Dispatchers.IO) { api.userLibraryApi.getItem(itemId = UUID.fromString(pin.id)).content }
			}.getOrNull() ?: return@launch
			if (pin.kind == PinnedRow.COLLECTION) navigationRepository.navigate(Destinations.collectionBrowser(item))
			else itemLauncher.launchUserView(item)
		}
	}

	val onHome = destination.fragment == HomeFragment::class
	val onSearch = destination.fragment == SearchFragment::class

	// The focus goes to the item of the screen that is open when the menu is entered, not to whichever is nearest
	val homeFocus = remember { FocusRequester() }
	val searchFocus = remember { FocusRequester() }

	// Hands the focus to the screen, which closes the menu; if the screen does not take it the menu stays as it is
	val leave = { onLeave() }

	// Back from the menu goes back to what was on screen, instead of leaving the app
	BackHandler(expanded) { leave() }

	// Once another screen has opened (a random title, a search...), the menu closes if the screen did not take the focus
	LaunchedEffect(destination) {
		if (expanded) {
			delay(500)
			if (expanded) leave()
		}
	}

	AccountSwitcherDialog(
		visible = showAccountSwitcher,
		server = currentServer,
		currentUserId = sessionRepository.currentSession.value?.userId,
		onDismissRequest = { showAccountSwitcher = false },
	)
	CatchUpDialog(visible = showCatchUp, onDismissRequest = { showCatchUp = false })

	// Grows to fit the longest label (a long account name, a longer translation) and no further
	val labelTexts = listOfNotNull(
		currentUser?.name,
		stringResource(R.string.lbl_home),
		stringResource(R.string.lbl_search),
		if (randomTitle != null) stringResource(R.string.random_title) else null,
		stringResource(R.string.catch_up_title),
		if (playing != null) stringResource(R.string.lbl_now_playing) else null,
		stringResource(R.string.lbl_settings),
	)
	val textMeasurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val expandedWidth = remember(labelTexts, density) {
		val widest = labelTexts.maxOf { textMeasurer.measure(it, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)).size.width }
		// Label, plus the icon, the gaps and the padding around them, and a little room to breathe
		(with(density) { widest.toDp() } + 120.dp).coerceIn(MinExpandedWidth, MaxExpandedWidth)
	}
	val width by animateDpAsState(if (expanded) expandedWidth else SideMenuCollapsedWidth, tween(180), label = "sideMenuWidth")
	val backdrop by animateFloatAsState(if (expanded) 1f else 0f, tween(180), label = "sideMenuBackdrop")
	val labels by animateFloatAsState(if (expanded) 1f else 0f, tween(if (expanded) 220 else 90), label = "sideMenuLabels")

	// Takes up only the folded width; the open menu is drawn over the screen instead of pushing it aside
	BoxWithConstraints(
		modifier = modifier
			.width(SideMenuCollapsedWidth)
			.fillMaxHeight()
			// Open, it is drawn over the screen next to it, not under it
			.zIndex(10f),
	) {
		// The buttons shrink as more of them are added, so that they all keep fitting
		val itemCount = 4 + (if (randomTitle != null) 1 else 0) + (if (catchUpOn) 1 else 0) + (if (playing != null) 1 else 0) + pins.size
		val available = maxHeight - MenuPadding * 2 - GroupGaps
		val itemHeight = ((available - ItemSpacing * (itemCount - 1)) / itemCount).coerceIn(MinItemHeight, ItemHeight)

		// See-through: only a soft shade of the theme's dark color behind the labels, fading out to the right
		val shade = JellyfinTheme.colorScheme.background
		Column(
			modifier = Modifier
				.wrapContentWidth(align = Alignment.Start, unbounded = true)
				.width(width)
				.fillMaxHeight()
				.background(
					Brush.horizontalGradient(
						0f to shade.copy(alpha = 0.7f * backdrop),
						0.6f to shade.copy(alpha = 0.45f * backdrop),
						1f to Color.Transparent,
					)
				)
				.focusProperties {
					enter = {
						when {
							onHome -> homeFocus
							onSearch -> searchFocus
							else -> FocusRequester.Default
						}
					}
				}
				.onFocusChanged { expanded = it.hasFocus }
				// Right goes back to the screen, which closes the menu
				.onPreviewKeyEvent {
					if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) {
						leave()
						true
					} else {
						false
					}
				}
				.focusGroup()
				.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			// The account comes first, as the way to be somebody else
			SideMenuItem(
				itemHeight = itemHeight,
				label = currentUser?.name.orEmpty(),
				labelAlpha = labels,
				active = false,
				onClick = { if (accountSwitcherOn) showAccountSwitcher = true },
				icon = {
					ProfilePicture(
						url = userImage,
						name = currentUser?.name,
						contentDescription = stringResource(R.string.lbl_switch_user),
						modifier = Modifier
							.size((itemHeight * 0.64f).coerceIn(26.dp, 36.dp))
							.clip(CircleShape)
							.background(JellyfinTheme.colorScheme.badge),
					)
				},
			)

			// With pinned items the space is theirs; without, the main items sit in the middle
			if (pins.isEmpty()) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(16.dp))

			SideMenuItem(
				itemHeight = itemHeight,
				label = stringResource(R.string.lbl_home),
				icon = R.drawable.ic_house,
				labelAlpha = labels,
				active = onHome,
				modifier = Modifier.focusRequester(homeFocus),
				onClick = { if (!onHome) navigationRepository.navigate(Destinations.home, replace = true) },
			)
			SideMenuItem(
				itemHeight = itemHeight,
				label = stringResource(R.string.lbl_search),
				icon = R.drawable.ic_search,
				labelAlpha = labels,
				active = onSearch,
				modifier = Modifier.focusRequester(searchFocus),
				onClick = { if (!onSearch) navigationRepository.navigate(Destinations.search()) },
			)
			if (randomTitle != null) {
				SideMenuItem(
					itemHeight = itemHeight,
					label = stringResource(R.string.random_title),
					icon = R.drawable.ic_casino,
					labelAlpha = labels,
					active = false,
					onClick = randomTitle,
				)
			}
			if (catchUpOn) {
				SideMenuItem(
					itemHeight = itemHeight,
					label = stringResource(R.string.catch_up_title),
					icon = R.drawable.ic_catch_up,
					labelAlpha = labels,
					active = false,
					onClick = { showCatchUp = true },
				)
			}
			if (playing != null) {
				SideMenuItem(
					itemHeight = itemHeight,
					label = stringResource(R.string.lbl_now_playing),
					icon = R.drawable.ic_music_album,
					labelAlpha = labels,
					active = false,
					onClick = { navigationRepository.navigate(Destinations.nowPlaying) },
				)
			}

			// What was pinned in the settings; this part scrolls once even the smallest buttons do not fit
			if (pins.isNotEmpty()) {
				Column(
					modifier = Modifier
						.weight(1f)
						.padding(top = 16.dp)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(ItemSpacing),
				) {
					for (pin in pins) {
						SideMenuItem(
							itemHeight = itemHeight,
							label = pin.name,
							icon = pinIcon(pin),
							labelAlpha = labels,
							active = false,
							onClick = { openPin(pin) },
						)
					}
				}
			} else {
				Spacer(Modifier.weight(1f))
			}

			SideMenuItem(
				itemHeight = itemHeight,
				label = stringResource(R.string.lbl_settings),
				icon = R.drawable.ic_settings,
				labelAlpha = labels,
				active = false,
				onClick = { settingsViewModel.show() },
			)
		}
	}
}

@Composable
private fun SideMenuItem(
	itemHeight: Dp,
	label: String,
	icon: Int,
	labelAlpha: Float,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) = SideMenuItem(
	modifier = modifier,
	itemHeight = itemHeight,
	label = label,
	labelAlpha = labelAlpha,
	active = active,
	onClick = onClick,
	icon = { tint ->
		Icon(
			imageVector = ImageVector.vectorResource(icon),
			contentDescription = label,
			modifier = Modifier.size(26.dp),
			tint = tint,
		)
	},
)

@Composable
private fun SideMenuItem(
	itemHeight: Dp,
	label: String,
	labelAlpha: Float,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	icon: @Composable (tint: Color) -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val colors = JellyfinTheme.colorScheme
	val container by animateColorAsState(
		if (focused) colors.buttonFocused else Color.Transparent,
		tween(120),
		label = "sideMenuItemContainer",
	)
	val content = when {
		focused -> colors.onButtonFocused
		// The screen that is open stands out in the accent color; the rest is muted
		active -> colors.badge
		else -> colors.onBackground.copy(alpha = 0.7f)
	}

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(itemHeight)
			// Fills the folded width exactly, which makes it a circle there, and a pill when it is open
			.clip(RoundedCornerShape(itemHeight / 2))
			.background(container)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick)
			.padding(horizontal = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		// The icon always sits in the same place, so only the labels move as the menu opens
		Box(modifier = Modifier.width(IconSlot), contentAlignment = Alignment.Center) {
			icon(content)
		}

		Text(
			text = label,
			color = content,
			fontSize = if (itemHeight < SmallItemHeight) 16.sp else 20.sp,
			fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal,
			maxLines = 1,
			softWrap = false,
			overflow = TextOverflow.Clip,
			modifier = Modifier
				.weight(1f)
				.alpha(labelAlpha),
		)
	}
}

private val IconSlot: Dp = 40.dp
private val SmallItemHeight = 46.dp

/** The icon shrinks along with the button, but stays big enough to recognise. */
private fun iconSize(itemHeight: Dp): Dp = (itemHeight * 0.46f).coerceIn(18.dp, 26.dp)

/** The icon of a pinned library or collection, by the kind of library it is. */
private fun pinIcon(pin: PinnedRow): Int = when {
	pin.kind == PinnedRow.COLLECTION -> R.drawable.ic_grid
	pin.type.equals("movies", true) || pin.type.equals("boxsets", true) -> R.drawable.ic_clapperboard
	pin.type.equals("tvshows", true) || pin.type.equals("livetv", true) -> R.drawable.ic_tv
	pin.type.equals("music", true) -> R.drawable.ic_music_album
	pin.type.equals("photos", true) || pin.type.equals("homevideos", true) -> R.drawable.ic_photos
	else -> R.drawable.ic_folder
}
