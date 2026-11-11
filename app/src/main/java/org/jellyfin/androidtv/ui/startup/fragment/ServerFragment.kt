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
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.startup.ServerScreen
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ServerFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ID = "server_id"
	}

	private val startupViewModel: StartupViewModel by activityViewModel()
	private val markdownRenderer: MarkdownRenderer by inject()
	private val authenticationRepository: AuthenticationRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private var serverState by mutableStateOf<Server?>(null)

	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val server = serverIdArgument?.let(startupViewModel::getServer)

		if (server == null) {
			navigateFragment<SelectServerFragment>(keepToolbar = true, keepHistory = false)
			return null
		}

		serverState = server

		val signIn = { user: User ->
			startupViewModel.authenticate(server, user).onEach { state ->
				when (state) {
					// Ignored states
					AuthenticatingState -> Unit
					AuthenticatedState -> Unit
					// Actions
					RequireSignInState -> navigateFragment<UserLoginFragment>(createBundle {
						putString(UserLoginFragment.ARG_SERVER_ID, server.id.toString())
						putString(UserLoginFragment.ARG_USERNAME, user.name)
					})
					// Errors
					ServerUnavailableState,
					is ApiClientErrorLoginState -> Toast.makeText(context, R.string.server_connection_failed, Toast.LENGTH_LONG).show()

					is ServerVersionNotSupported -> Toast.makeText(
						context,
						getString(
							R.string.server_issue_outdated_version,
							state.server.version,
							ServerRepository.recommendedServerVersion.toString()
						),
						Toast.LENGTH_LONG
					).show()
				}
			}.launchIn(lifecycleScope)
			Unit
		}

		startupViewModel.loadUsers(server)

		onServerChange(server)

		val view = content {
			val current = serverState
			if (current != null) {
				val users by startupViewModel.users.collectAsState()
				val notification = when {
					!current.versionSupported -> getString(
						R.string.server_unsupported_notification,
						current.version,
						ServerRepository.recommendedServerVersion.toString(),
					)
					!current.setupCompleted -> getString(R.string.server_setup_incomplete)
					else -> null
				}

				ServerScreen(
					server = current,
					users = users,
					imageOf = { user -> startupViewModel.getUserImage(current, user) },
					notification = notification,
					disclaimer = current.loginDisclaimer?.let { markdownRenderer.toMarkdownSpanned(it).toString() },
					onUserClick = signIn,
					onAddUser = {
						navigateFragment<UserLoginFragment>(
							args = createBundle {
								putString(UserLoginFragment.ARG_SERVER_ID, current.id.toString())
								putString(UserLoginFragment.ARG_USERNAME, null)
							}
						)
					},
					onChangeServer = { navigateFragment<SelectServerFragment>(keepToolbar = true) },
					onSignOut = { user -> authenticationRepository.logout(user) },
					onRemove = { user ->
						serverUserRepository.deleteStoredUser(user)
						startupViewModel.loadUsers(current)
					},
				)
			}
		}

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				val updated = startupViewModel.updateServer(server)
				if (updated) startupViewModel.getServer(server.id)?.let(::onServerChange)
			}
		}

		return view
	}

	private fun onServerChange(server: Server) {
		serverState = server
	}

	private inline fun <reified F : Fragment> navigateFragment(
		args: Bundle = createBundle(),
		keepToolbar: Boolean = false,
		keepHistory: Boolean = true,
	) {
		requireActivity()
			.supportFragmentManager
			.commit {
				if (keepToolbar) {
					replace<StartupToolbarFragment>(R.id.content_view)
					add<F>(R.id.content_view, null, args)
				} else {
					replace<F>(R.id.content_view, null, args)
				}

				if (keepHistory) addToBackStack(null)
			}
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		backgroundService.clearBackgrounds()

		val server = serverIdArgument?.let(startupViewModel::getServer)
		if (server != null) startupViewModel.loadUsers(server)
		else navigateFragment<SelectServerFragment>(keepToolbar = true)
	}
}
