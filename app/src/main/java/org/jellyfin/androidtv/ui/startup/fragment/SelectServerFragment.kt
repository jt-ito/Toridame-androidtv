package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ServerAdditionState
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.startup.SelectServerScreen
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.androidtv.util.getSummary
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.compose.koinInject
import java.util.UUID

class SelectServerFragment : Fragment() {
	private val startupViewModel: StartupViewModel by activityViewModel()

	// What the discovered servers are doing (connecting, could not connect), by server
	private var discoveryStates by mutableStateOf<Map<UUID, ServerAdditionState>>(emptyMap())

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = content {
		val stored by startupViewModel.storedServers.collectAsState()
		val discovered by startupViewModel.discoveredServers.collectAsState()
		val notificationsRepository = koinInject<NotificationsRepository>()
		val notifications by notificationsRepository.notifications.collectAsState()

		SelectServerScreen(
			stored = stored,
			discovered = discovered.map { it to discoveryStates[it.id] },
			notifications = notifications.filter { it.public }.map { it.message },
			appVersion = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}",
			onStoredClick = { server ->
				requireActivity()
					.supportFragmentManager
					.commit {
						replace<StartupToolbarFragment>(R.id.content_view)
						add<ServerFragment>(
							R.id.content_view,
							null,
							createBundle {
								putString(ServerFragment.ARG_SERVER_ID, server.id.toString())
							}
						)
						addToBackStack(null)
					}
			},
			onRemove = { server -> startupViewModel.deleteServer(server.id) },
			onDiscoveredClick = { server ->
				startupViewModel.addServer(server.address).onEach { state ->
					if (state is ConnectedState) {
						parentFragmentManager.commit {
							replace<StartupToolbarFragment>(R.id.content_view)
							add<ServerFragment>(
								R.id.content_view,
								null,
								createBundle {
									putString(ServerFragment.ARG_SERVER_ID, state.id.toString())
								}
							)
						}
					} else {
						discoveryStates = discoveryStates + (server.id to state)

						// Show error as toast
						if (state is UnableToConnectState) {
							Toast.makeText(
								requireContext(),
								getString(
									R.string.server_connection_failed_candidates,
									state.addressCandidates
										.map { "${it.key} ${it.value.getSummary(requireContext())}" }
										.joinToString(prefix = "\n", separator = "\n")
								),
								Toast.LENGTH_LONG,
							).show()
						}
					}
				}.launchIn(lifecycleScope)
			},
			onEnterAddress = {
				parentFragmentManager.commit {
					addToBackStack(null)
					replace<ServerAddFragment>(R.id.content_view)
				}
			},
		)
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		startupViewModel.loadDiscoveryServers()
	}
}
