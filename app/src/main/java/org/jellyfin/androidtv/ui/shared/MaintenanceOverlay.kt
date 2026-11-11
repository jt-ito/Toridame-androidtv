package org.jellyfin.androidtv.ui.shared

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.MaintenanceStatus
import org.jellyfin.androidtv.data.repository.ToridameRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent

private const val POLL_MS = 30_000L
private const val BANNER_MS = 20_000L
private const val SECOND_MS = 1000L
private const val HOUR_MS = 60 * 60 * 1000L
private const val UPCOMING_NOTICE_MS = HOUR_MS
private const val PLAYBACK_REMINDER_MS = 30 * 60 * 1000L
private const val MIN_REPOLL_MS = 3_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

/** What is worth telling people about right now: maintenance going on, or starting soon. */
data class RelevantMaintenance(val active: Boolean, val targetMs: Long?) {
	val key get() = "${if (active) "active" else "upcoming"}-${targetMs ?: ""}"
}

fun MaintenanceStatus?.relevant(now: Long, noticeMs: Long = UPCOMING_NOTICE_MS): RelevantMaintenance? {
	if (this == null) return null
	if (active) return RelevantMaintenance(true, endsAt)

	val start = startsAt
	return if (start != null && start > now && start - now <= noticeMs) RelevantMaintenance(false, start) else null
}

/** m:ss, or h:mm:ss from an hour up. Nothing is shown below zero. */
fun formatRemaining(ms: Long): String {
	val total = maxOf(0L, (ms + SECOND_MS - 1) / SECOND_MS)
	val hours = total / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
	val minutes = (total / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
	val seconds = total % SECONDS_PER_MINUTE
	return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** What the server last said about maintenance, kept for the whole app. */
class MaintenanceState(private val repository: ToridameRepository) {
	val status = MutableStateFlow<MaintenanceStatus?>(null)
	var lastPolled = 0L
		private set
	var remindedKey: String? = null

	suspend fun poll() {
		lastPolled = System.currentTimeMillis()
		status.value = repository.maintenanceStatus()
	}
}

private fun describe(context: Context, status: MaintenanceStatus?, relevant: RelevantMaintenance, now: Long, isAdmin: Boolean, onLockScreen: Boolean): String {
	val parts = mutableListOf<String>()

	if (!relevant.active) {
		parts += context.getString(R.string.maintenance_starts_in, formatRemaining((relevant.targetMs ?: now) - now))
	} else {
		if (!onLockScreen) parts += context.getString(R.string.maintenance_in_progress)
		relevant.targetMs?.let { parts += context.getString(R.string.maintenance_back_in, formatRemaining(it - now)) }
	}

	status?.message?.takeIf { it.isNotBlank() }?.let { parts += it }
	if (status?.locksOutUsers == true) {
		parts += context.getString(if (isAdmin || onLockScreen) R.string.maintenance_admins_still_in else R.string.maintenance_admins_only)
	}

	return parts.joinToString(if (onLockScreen) "\n" else "  ·  ")
}

/** A reminder for when something starts playing shortly before or during maintenance; once for each maintenance. */
object MaintenanceReminder {
	@JvmStatic
	fun text(context: Context): String? {
		val state = KoinJavaComponent.get<MaintenanceState>(MaintenanceState::class.java)
		val now = System.currentTimeMillis()
		val status = state.status.value
		val relevant = status.relevant(now, PLAYBACK_REMINDER_MS) ?: return null

		if (relevant.key == state.remindedKey) return null
		state.remindedKey = relevant.key

		val isAdmin = KoinJavaComponent.get<UserRepository>(UserRepository::class.java).currentUser.value?.policy?.isAdministrator == true
		return describe(context, status, relevant, now, isAdmin, onLockScreen = false)
	}
}

/**
 * Tells people about maintenance: a notice with a live countdown at the top of the screen (for a short while whenever
 * it comes up or changes, as a notice that stays would cover the picture), and, when users are locked out, a screen in
 * place of the app.
 */
@Composable
fun MaintenanceOverlay() {
	val state = koinInject<MaintenanceState>()
	val userRepository = koinInject<UserRepository>()
	val sessionRepository = koinInject<SessionRepository>()
	val mediaManager = koinInject<MediaManager>()
	val context = LocalContext.current
	val activity = LocalActivity.current

	val status by state.status.collectAsState()
	val user by userRepository.currentUser.collectAsState()
	val isAdmin = user?.policy?.isAdministrator == true

	LaunchedEffect(Unit) {
		while (true) {
			state.poll()
			delay(POLL_MS)
		}
	}

	// Redraws every second so the countdown moves between polls
	val now by produceState(System.currentTimeMillis()) {
		while (true) {
			delay(SECOND_MS)
			value = System.currentTimeMillis()
		}
	}

	val relevant = status.relevant(now)
	val locked = relevant?.active == true && status?.locksOutUsers == true && user != null && !isAdmin

	// The end has passed but the server has not said so yet: ask again rather than show a zero
	LaunchedEffect(relevant?.active == true && (relevant.targetMs ?: Long.MAX_VALUE) <= now) {
		if (relevant?.active == true && (relevant.targetMs ?: Long.MAX_VALUE) <= now && now - state.lastPolled > MIN_REPOLL_MS) state.poll()
	}

	// The notice shows for a while whenever the maintenance comes up or its time changes
	var bannerVisible by remember { mutableStateOf(false) }
	LaunchedEffect(relevant?.key) {
		if (relevant != null && !locked) {
			bannerVisible = true
			delay(BANNER_MS)
		}
		bannerVisible = false
	}

	if (bannerVisible && relevant != null && !locked) {
		Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
			Text(
				text = describe(context, status, relevant, now, isAdmin, onLockScreen = false),
				fontSize = 18.sp,
				color = Color.White,
				modifier = Modifier
					.clip(RoundedCornerShape(24.dp))
					.background(if (relevant.active) Color(0xE6B3261E) else Color(0xE6333333))
					.padding(horizontal = 24.dp, vertical = 10.dp),
			)
		}
	}

	val signOutFocus = remember { FocusRequester() }
	LaunchedEffect(locked) { if (locked) runCatching { signOutFocus.requestFocus() } }

	DialogBase(visible = locked, onDismissRequest = {}) {
		Column(
			modifier = Modifier
				.width(620.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(JellyfinTheme.colorScheme.surface)
				.padding(32.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Text(text = stringResource(R.string.maintenance_in_progress), fontSize = 26.sp, fontWeight = FontWeight.Bold)
			if (relevant != null) {
				Text(
					text = describe(context, status, relevant, now, isAdmin, onLockScreen = true),
					fontSize = 18.sp,
					textAlign = TextAlign.Center,
				)
			}
			Button(
				modifier = Modifier.focusRequester(signOutFocus),
				onClick = {
					mediaManager.clearAudioQueue()
					sessionRepository.destroyCurrentSession()
					activity?.startActivity(ActivityDestinations.startup(activity))
					activity?.finishAfterTransition()
				},
			) { Text(stringResource(R.string.maintenance_switch_user), fontSize = 18.sp) }
		}
	}
}
