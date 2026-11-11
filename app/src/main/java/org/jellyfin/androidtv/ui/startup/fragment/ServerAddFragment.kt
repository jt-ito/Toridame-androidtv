package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.ui.startup.ServerAddScreen
import org.jellyfin.androidtv.ui.startup.ServerAddViewModel
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.androidtv.util.getSummary
import org.koin.androidx.viewmodel.ext.android.viewModel

class ServerAddFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ADDRESS = "server_address"
	}

	private val startupViewModel: ServerAddViewModel by viewModel()

	private val serverAddressArgument get() = arguments?.getString(ARG_SERVER_ADDRESS)?.ifBlank { null }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = content {
		var address by remember { mutableStateOf(serverAddressArgument.orEmpty()) }
		var emptyWarning by remember { mutableStateOf(false) }
		val state by startupViewModel.state.collectAsState()

		val submit = {
			if (address.isNotBlank()) {
				emptyWarning = false
				startupViewModel.addServer(address)
			} else {
				emptyWarning = true
			}
		}

		// An address that was handed over is tried right away
		LaunchedEffect(Unit) {
			if (serverAddressArgument != null) submit()
		}

		LaunchedEffect(state) {
			val connected = state as? ConnectedState ?: return@LaunchedEffect
			parentFragmentManager.commit {
				// Open server view
				replace<StartupToolbarFragment>(R.id.content_view)
				add<ServerFragment>(
					R.id.content_view,
					null,
					createBundle {
						putString(ServerFragment.ARG_SERVER_ID, connected.id.toString())
					}
				)
			}
		}

		val current = state
		ServerAddScreen(
			address = address,
			onAddressChange = { address = it },
			message = when {
				current is ConnectingState -> getString(R.string.server_connecting, current.address)
				current is UnableToConnectState -> getString(
					R.string.server_connection_failed_candidates,
					current.addressCandidates
						.map { "${it.key} - ${it.value.getSummary(requireContext())}" }
						.joinToString(prefix = "\n", separator = "\n")
				)
				emptyWarning -> getString(R.string.server_field_empty)
				else -> null
			},
			busy = current is ConnectingState,
			onConnect = submit,
		)
	}
}
