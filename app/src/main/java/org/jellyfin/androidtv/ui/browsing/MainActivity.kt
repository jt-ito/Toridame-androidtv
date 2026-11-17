package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.BaseGridView
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import org.jellyfin.androidtv.ui.shared.toolbar.ToolbarClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideLocalInteractionTracker
import org.jellyfin.androidtv.ui.composable.compat.AppNavigationHost
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.shared.MaintenanceOverlay
import org.jellyfin.androidtv.ui.shared.sidemenu.ContentFocus
import org.jellyfin.androidtv.ui.shared.sidemenu.SideMenu
import org.jellyfin.androidtv.ui.shared.sidemenu.rememberSideMenuVisible
import org.jellyfin.androidtv.ui.settings.compat.MainActivitySettings
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.data.repository.ServerFeatures
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : FragmentActivity() {
	private companion object {
		// How long the focus has to have stayed where it is before pressing left opens the side menu
		const val SIDE_MENU_FOCUS_STILL_MS = 250L
	}

	private val navigationRepository by inject<NavigationRepository>()
	private val serverFeatures by inject<ServerFeatures>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val interactionTrackerViewModel by viewModel<InteractionTrackerViewModel>()
	private val workManager by inject<WorkManager>()

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		if (!validateAuthentication()) return

		interactionTrackerViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		if (savedInstanceState == null && navigationRepository.canGoBack) navigationRepository.reset(clearHistory = true)

		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach {
				interactionTrackerViewModel.notifyInteraction(canCancel = false, userInitiated = false)
			}.launchIn(lifecycleScope)

		window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, _ -> lastFocusChange = SystemClock.uptimeMillis() }

		setContent {
			JellyfinTheme {
				ProvideLocalInteractionTracker(
					interactionTracker = { interactionTrackerViewModel.notifyInteraction(false, userInitiated = true) }
				) {
					AppBackground()
					// The menu at the side, on every screen but the ones that fill the display (the player)
					val menuVisible = rememberSideMenuVisible(navigationRepository)
					Box(modifier = Modifier.fillMaxSize()) {
						Row(modifier = Modifier.fillMaxSize()) {
							if (menuVisible) SideMenu(onLeave = ::focusContent)

							AppNavigationHost(
								modifier = Modifier
									.weight(1f)
									.fillMaxSize()
									.focusProperties {
										// Left goes to the menu from the screen only when it is meant to (see opensSideMenu)
										onExit = {
											if (requestedFocusDirection == FocusDirection.Left && !opensSideMenu()) cancelFocusChange()
										}
									},
								navigationRepository = navigationRepository,
							)
						}

						if (menuVisible) {
							ToolbarClock(
								modifier = Modifier
									.align(Alignment.TopEnd)
									.padding(top = 27.dp, end = 48.dp),
							)
						}
					}
					InAppScreensaver()
					MaintenanceOverlay()
					MainActivitySettings()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		if (!validateAuthentication()) return

		applyTheme()

		// What the administrator switched on may have changed since the app was last in front
		lifecycleScope.launch(Dispatchers.IO) { serverFeatures.refresh() }

		interactionTrackerViewModel.activityPaused = false
	}

	private var lastFocusChange = 0L
	private var leftKeyIsRepeat = false

	/**
	 * Whether pressing left, with nothing more to the left on the screen, opens the side menu. Lists and grids can ask
	 * for the focus to leave while they are still scrolling along (the next item is not there yet), which would open the
	 * menu in the middle of a row. So it opens for a fresh press only, not for a key that is held down, and only once the
	 * focus has been still for a moment.
	 */
	private fun opensSideMenu(): Boolean =
		!leftKeyIsRepeat &&
			SystemClock.uptimeMillis() - lastFocusChange >= SIDE_MENU_FOCUS_STILL_MS &&
			!screenHasMoreOnTheLeft()

	/**
	 * Whether the view with the focus has something more to its left on the screen, which pressing left has to go to
	 * before the side menu is reached. Found the way the view system finds it itself.
	 */
	private fun screenHasMoreOnTheLeft(): Boolean {
		val focused = currentFocus ?: return false
		val host = findScreenHost(window.decorView) ?: return false

		var parent = focused.parent
		while (parent != null && parent !== host) parent = parent.parent
		if (parent == null) return false

		return FocusFinder.getInstance().findNextFocus(host, focused, View.FOCUS_LEFT) != null
	}

	private fun findScreenHost(view: View): ViewGroup? {
		if (view is DestinationFragmentView) return view
		val group = view as? ViewGroup ?: return null
		for (i in 0 until group.childCount) findScreenHost(group.getChildAt(i))?.let { return it }
		return null
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
			leftKeyIsRepeat = event.repeatCount > 0
		}

		return super.dispatchKeyEvent(event)
	}

	// Puts the focus on the screen that is open, from the side menu
	private fun focusContent() {
		// A screen built with Compose knows better where its focus belongs
		ContentFocus.handler?.let {
			it()
			return
		}

		fun find(view: View): View? {
			if (view is DestinationFragmentView) return view
			val group = view as? ViewGroup ?: return null
			for (i in 0 until group.childCount) find(group.getChildAt(i))?.let { return it }
			return null
		}

		// The rows of a screen built with leanback (the home page) are found and given the focus directly, as asking
		// the view they sit in does not reach them
		fun findGrid(view: View): View? {
			if (view is BaseGridView && view.childCount > 0) return view
			val group = view as? ViewGroup ?: return null
			for (i in 0 until group.childCount) findGrid(group.getChildAt(i))?.let { return it }
			return null
		}

		val host = find(window.decorView) ?: return
		if (findGrid(host)?.requestFocus() != true) host.requestFocus()
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	override fun onPause() {
		super.onPause()

		interactionTrackerViewModel.activityPaused = true
	}

	override fun onStop() {
		super.onStop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		lifecycleScope.launch(Dispatchers.IO) {
			Timber.i("MainActivity stopped")
			sessionRepository.restoreSession(destroyOnly = true)
		}
	}

	// Forward key events to fragments

	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean = supportFragmentManager.fragments
		.any { it.onKeyEvent(keyCode, event) }

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)
}
