package org.jellyfin.androidtv.ui.settings.screen.playback

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.playback.ShortcutAction
import org.jellyfin.androidtv.ui.playback.ShortcutKeys
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

/**
 * Pick the remote button for each extra player action: select an action, then press the button to give it. A button
 * that another action uses is swapped over to this one.
 */
@Composable
fun SettingsPlaybackShortcutsScreen() {
	val userPreferences = koinInject<UserPreferences>()
	val context = LocalContext.current

	// Read through a counter so the list updates after a change
	var version by remember { mutableIntStateOf(0) }
	var capturing by remember { mutableStateOf<ShortcutAction?>(null) }

	fun assign(action: ShortcutAction, keyCode: Int) {
		val previous = userPreferences[action.preference]
		val other = ShortcutAction.entries.firstOrNull { it != action && userPreferences[it.preference] == keyCode }
		userPreferences[action.preference] = keyCode
		// The action that had this button gets the one that was freed
		if (other != null) userPreferences[other.preference] = previous
		version++
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_shortcuts)) },
				captionContent = { Text(stringResource(R.string.pref_shortcuts_caption)) },
			)
		}

		items(ShortcutAction.entries) { action ->
			// Depends on the counter, so it reads the new button after a change
			val keyName = remember(version) { ShortcutKeys.nameOf(userPreferences[action.preference]) }
			ListButton(
				headingContent = { Text(stringResource(action.nameRes)) },
				captionContent = { Text(keyName) },
				onClick = { capturing = action },
				modifier = Modifier.focusKey("shortcut_${action.name}"),
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_shortcuts_reset)) },
				onClick = {
					ShortcutAction.entries.forEach { userPreferences[it.preference] = it.preference.defaultValue }
					version++
				},
				modifier = Modifier.focusKey("shortcut_reset"),
			)
		}
	}

	val target = capturing
	val focusRequester = remember { FocusRequester() }
	DialogBase(
		visible = target != null,
		onDismissRequest = { capturing = null },
	) {
		LaunchedEffect(target) { runCatching { focusRequester.requestFocus() } }

		Column(
			modifier = Modifier
				.width(480.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(JellyfinTheme.colorScheme.surface)
				.padding(32.dp)
				.focusRequester(focusRequester)
				.focusable()
				.onPreviewKeyEvent { event ->
					if (target == null || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
					val keyCode = event.key.nativeKeyCode

					when {
						keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE -> {
							capturing = null
							true
						}

						ShortcutKeys.isAssignable(keyCode) -> {
							assign(target, keyCode)
							capturing = null
							true
						}

						else -> {
							Toast.makeText(context, context.getString(R.string.pref_shortcuts_reserved), Toast.LENGTH_SHORT).show()
							true
						}
					}
				},
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				text = target?.let { stringResource(it.nameRes) }.orEmpty(),
				fontSize = 22.sp,
				fontWeight = FontWeight.Bold,
			)
			Text(text = stringResource(R.string.pref_shortcuts_press), fontSize = 18.sp)
			Text(text = stringResource(R.string.pref_shortcuts_back_cancels), fontSize = 14.sp, color = JellyfinTheme.colorScheme.listCaption)
		}
	}
}
