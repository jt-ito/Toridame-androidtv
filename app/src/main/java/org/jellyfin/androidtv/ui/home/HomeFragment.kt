package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.auth.repository.UserRepository
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.androidtv.preference.UserPreferences
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.collectAsState
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.leanback.widget.ListRowPresenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.koin.android.ext.android.inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userRepository by inject<UserRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		LaunchedEffect(rowsFocusRequester) { rowsFocusRequester.requestFocus() }

		// The buttons that used to be along the top are in the side menu now
		Column(modifier = Modifier.padding(top = 27.dp)) {
			// The leanback code has its own awful focus handling that doesn't work properly with Compose view inteop to workaround this
			// issue we add custom behavior that only allows focus exit when the current selected row is the first one. Additionally when
			// we do switch the focus, we reset the leanback state so it won't cause weird behavior when focus is regained
			var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }

			// What the focused title is, above the rows; a moment after the focus settles, so moving quickly does not redraw it
			if (userPreferences[UserPreferences.homeHeader]) {
				val focusedFlow = remember(rowsSupportFragment) {
					rowsSupportFragment?.focusedItem?.debounce(HEADER_DEBOUNCE_MS) ?: flowOf(null)
				}
				// Keeps showing the last title when the focus goes up to the buttons, instead of going blank
				val focusedItem by remember(focusedFlow) { focusedFlow.filterNotNull() }.collectAsState(null)
				val previewFlow = remember(rowsSupportFragment) { rowsSupportFragment?.libraryPreview ?: flowOf(null) }
				val libraryPreview by previewFlow.collectAsState(null)
				HomeHeader(item = focusedItem, greetingName = userRepository.currentUser.value?.name, libraryPreview = libraryPreview)
			}

			AndroidFragment<HomeRowsFragment>(
				modifier = Modifier
					.focusGroup()
					.focusRequester(rowsFocusRequester)
					.focusProperties {
						onExit = {
							val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
							// Left goes to the side menu, but only from the first item of a row: leanback can ask for the focus to
							// leave while it is still scrolling along a row, which must not open the menu
							if (requestedFocusDirection == FocusDirection.Left) {
								val positionInRow = rowsSupportFragment?.let { rows ->
									(rows.getRowViewHolder(rows.selectedPosition) as? ListRowPresenter.ViewHolder)?.gridView?.selectedPosition
								}
								if (positionInRow != null && positionInRow > 0) cancelFocusChange()
							} else if (requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected) {
								cancelFocusChange()
							} else {
								rowsSupportFragment?.selectedPosition = 0
								rowsSupportFragment?.verticalGridView?.clearFocus()
							}
						}
					}
					.fillMaxSize(),
				maxLifecycle = Lifecycle.State.RESUMED,
				onUpdate = { fragment ->
					rowsSupportFragment = fragment
				}
			)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}

private const val HEADER_DEBOUNCE_MS = 120L
