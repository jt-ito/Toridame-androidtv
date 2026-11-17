package org.jellyfin.androidtv.ui.shared

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.PinResult
import org.jellyfin.androidtv.data.repository.ServerFeatures
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.android.ext.android.inject
import org.koin.java.KoinJavaComponent

private val HIDEABLE = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
private const val PIN_MEMORY_MS = 5 * 60 * 1000L

/** The PIN typed a moment ago, so a list can be managed without typing it for every step. */
private var rememberedPin: String? = null
private var rememberedUntil = 0L

/**
 * Asks for a PIN in a dialog that hides what is typed. Gives null when it was cancelled. The PIN is remembered for a
 * few minutes.
 */
suspend fun Context.askPin(title: String, useRemembered: Boolean = true): String? {
	val known = rememberedPin
	if (useRemembered && known != null && System.currentTimeMillis() < rememberedUntil) return known

	val answer = CompletableDeferred<String?>()
	val input = EditText(this).apply {
		inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
		hint = getString(R.string.hidden_pin)
	}

	AlertDialog.Builder(this)
		.setTitle(title)
		.setView(input)
		.setPositiveButton(android.R.string.ok) { _, _ -> answer.complete(input.text.toString().ifBlank { null }) }
		.setNegativeButton(android.R.string.cancel) { _, _ -> answer.complete(null) }
		.setOnCancelListener { answer.complete(null) }
		.show()

	val pin = answer.await()
	if (pin != null) {
		rememberedPin = pin
		rememberedUntil = System.currentTimeMillis() + PIN_MEMORY_MS
	}
	return pin
}

fun forgetRememberedPin() {
	rememberedPin = null
	rememberedUntil = 0L
}

/** Says what went wrong with a PIN, and forgets a PIN the server did not accept. */
fun Context.explain(result: PinResult) {
	if (result == PinResult.WRONG_PIN || result == PinResult.LOCKED_OUT) forgetRememberedPin()

	val text = when (result) {
		PinResult.WRONG_PIN -> R.string.hidden_wrong_pin
		PinResult.LOCKED_OUT -> R.string.hidden_locked_out
		PinResult.FAILED -> R.string.hidden_failed
		PinResult.OK -> return
	}
	Toast.makeText(this, getString(text), Toast.LENGTH_LONG).show()
}

object HiddenContentUi {
	/** Whether the page of this item offers to hide it from lists. */
	@JvmStatic
	fun canHide(item: BaseItemDto): Boolean {
		if (item.type !in HIDEABLE) return false
		val settings = KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)
		return settings[UserPreferences.hiddenContentButton] &&
			KoinJavaComponent.get<ServerFeatures>(ServerFeatures::class.java).isEnabled("hiddenContent")
	}

	/** Hides the item from lists (it can still be opened and played) and says so. */
	@JvmStatic
	fun hide(fragment: Fragment, item: BaseItemDto) {
		val repository by fragment.inject<ToridameRepository>()
		val context = fragment.requireContext()

		fragment.lifecycleScope.launch {
			val hidden = repository.hide(item.id)
			Toast.makeText(
				context,
				context.getString(if (hidden) R.string.hidden_done else R.string.hidden_failed, item.name.orEmpty()),
				Toast.LENGTH_LONG,
			).show()
		}
	}
}

/** The spoiler guard: unwatched episodes show no picture and no synopsis of their own. */
object SpoilerGuard {
	private fun enabled() = KoinJavaComponent.get<UserSettingPreferences>(UserSettingPreferences::class.java)[UserSettingPreferences.spoilerGuard]

	/** Episodes that were not watched and not started: their picture and synopsis can give away the story. */
	@JvmStatic
	fun covers(item: BaseItemDto?): Boolean = item != null &&
		item.type == BaseItemKind.EPISODE &&
		item.userData?.played != true &&
		(item.userData?.playbackPositionTicks ?: 0L) == 0L &&
		enabled()

	@JvmStatic
	fun overview(context: Context, item: BaseItemDto): String? =
		if (covers(item)) context.getString(R.string.spoiler_hidden) else item.overview
}
