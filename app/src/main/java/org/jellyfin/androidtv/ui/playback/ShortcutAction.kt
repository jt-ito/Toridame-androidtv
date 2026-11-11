package org.jellyfin.androidtv.ui.playback

import android.view.KeyEvent
import androidx.annotation.StringRes
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.preference.Preference

/** Remote buttons that do something in the player, each with a button that can be changed in the settings. */
enum class ShortcutAction(
	@StringRes val nameRes: Int,
	val preference: Preference<Int>,
) {
	BOOKMARK(R.string.shortcut_bookmark, UserPreferences.shortcutBookmark),
	CYCLE_SUBTITLES(R.string.shortcut_subtitles, UserPreferences.shortcutSubtitles),
	CYCLE_AUDIO(R.string.shortcut_audio, UserPreferences.shortcutAudio),
	CYCLE_ASPECT(R.string.shortcut_aspect, UserPreferences.shortcutAspect),
	SPEED_RESET(R.string.shortcut_speed, UserPreferences.shortcutSpeedReset),
}

object ShortcutKeys {
	/** Buttons the player uses itself, which can not be given to an action. */
	private val reserved = setOf(
		KeyEvent.KEYCODE_DPAD_UP,
		KeyEvent.KEYCODE_DPAD_DOWN,
		KeyEvent.KEYCODE_DPAD_LEFT,
		KeyEvent.KEYCODE_DPAD_RIGHT,
		KeyEvent.KEYCODE_DPAD_CENTER,
		KeyEvent.KEYCODE_ENTER,
		KeyEvent.KEYCODE_NUMPAD_ENTER,
		KeyEvent.KEYCODE_BACK,
		KeyEvent.KEYCODE_ESCAPE,
		KeyEvent.KEYCODE_HOME,
		KeyEvent.KEYCODE_MENU,
		KeyEvent.KEYCODE_MEDIA_PLAY,
		KeyEvent.KEYCODE_MEDIA_PAUSE,
		KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
		KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
		KeyEvent.KEYCODE_MEDIA_REWIND,
		KeyEvent.KEYCODE_MEDIA_NEXT,
		KeyEvent.KEYCODE_MEDIA_PREVIOUS,
		KeyEvent.KEYCODE_MEDIA_STOP,
		KeyEvent.KEYCODE_VOLUME_UP,
		KeyEvent.KEYCODE_VOLUME_DOWN,
		KeyEvent.KEYCODE_VOLUME_MUTE,
		KeyEvent.KEYCODE_POWER,
	)

	fun isAssignable(keyCode: Int) = keyCode !in reserved && keyCode != KeyEvent.KEYCODE_UNKNOWN

	/** A readable name for a button, like "Prog Red" or "0". */
	fun nameOf(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode)
		.removePrefix("KEYCODE_")
		.replace('_', ' ')
		.lowercase()
		.replaceFirstChar { it.uppercase() }
}
