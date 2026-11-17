package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.android.ext.android.inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userRepository by inject<UserRepository>()

	private val rows by lazy { HomeRowsController(requireActivity(), this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		rows.start()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		// The buttons that used to be along the top are in the side menu now
		Column(modifier = Modifier.padding(top = 27.dp)) {
			// What the focused title is, above the rows; a moment after the focus settles, so moving quickly does not redraw it
			if (userPreferences[UserPreferences.homeHeader]) {
				val focusedFlow = remember { rows.focusedItem.debounce(HEADER_DEBOUNCE_MS) }
				// Keeps showing the last title when the focus goes up to the buttons, instead of going blank
				val focusedItem by remember(focusedFlow) { focusedFlow.filterNotNull() }.collectAsState(null)
				val libraryPreview by rows.libraryPreview.collectAsState()
				HomeHeader(item = focusedItem, greetingName = userRepository.currentUser.value?.name, libraryPreview = libraryPreview)
			}

			HomeRows(controller = rows, modifier = Modifier.fillMaxSize())
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

	override fun onResume() {
		super.onResume()
		rows.onResume()
	}

	override fun onDestroy() {
		super.onDestroy()
		rows.onDestroy()
	}
}

private const val HEADER_DEBOUNCE_MS = 120L
