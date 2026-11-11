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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PendingQuickConnectState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.UnavailableQuickConnectState
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.ui.startup.LoginMethod
import org.jellyfin.androidtv.ui.startup.UserLoginScreen
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class UserLoginFragment : Fragment() {
	companion object {
		const val ARG_USERNAME = "user_name"
		const val ARG_SERVER_ID = "server_id"
		const val ARG_SKIP_QUICKCONNECT = "skip_quickconnect"
		const val TAG_LOGIN_METHOD = "login_method"
	}

	private val userLoginViewModel: UserLoginViewModel by activityViewModel()

	private val usernameArgument get() = arguments?.getString(ARG_USERNAME)?.ifBlank { null }
	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }
	private val skipQuickConnect get() = arguments?.getBoolean(ARG_SKIP_QUICKCONNECT) ?: false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		userLoginViewModel.forcedUsername = usernameArgument
		userLoginViewModel.setServer(serverIdArgument?.toUUIDOrNull())
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = content {
		val server by userLoginViewModel.server.collectAsState()
		val quickConnectState by userLoginViewModel.quickConnectState.collectAsState()
		val loginState by userLoginViewModel.loginState.collectAsState()
		val lifecycle = LocalLifecycleOwner.current.lifecycle

		var method by remember { mutableStateOf(if (skipQuickConnect) LoginMethod.Credentials else LoginMethod.QuickConnect) }
		var username by remember { mutableStateOf(userLoginViewModel.forcedUsername.orEmpty()) }
		var password by remember { mutableStateOf("") }
		var emptyUsername by remember { mutableStateOf(false) }

		LaunchedEffect(Unit) { userLoginViewModel.clearLoginState() }

		// A Quick Connect code is asked for while that is how to sign in, and the screen is showing
		LaunchedEffect(method) {
			if (method == LoginMethod.QuickConnect) {
				lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { userLoginViewModel.initiateQuickconnect() }
			}
		}

		// Quick Connect that is not available leaves the password
		LaunchedEffect(quickConnectState) {
			if (quickConnectState == UnavailableQuickConnectState) method = LoginMethod.Credentials
		}

		val message = when (val state = loginState) {
			is ServerVersionNotSupported -> getString(
				R.string.server_issue_outdated_version,
				state.server.version,
				ServerRepository.recommendedServerVersion.toString()
			)
			AuthenticatingState -> getString(R.string.login_authenticating)
			RequireSignInState -> getString(R.string.login_invalid_credentials)
			ServerUnavailableState,
			is ApiClientErrorLoginState -> getString(R.string.login_server_unavailable)
			// Nothing to say, because the activity will respond to the new session
			AuthenticatedState,
			null -> if (emptyUsername) getString(R.string.login_username_field_empty) else null
		}

		UserLoginScreen(
			serverName = server?.name ?: "Jellyfin",
			method = method,
			quickConnectCode = (quickConnectState as? PendingQuickConnectState)?.code?.formatCode(),
			quickConnectAvailable = quickConnectState != UnavailableQuickConnectState,
			username = username,
			onUsernameChange = { username = it },
			usernameLocked = userLoginViewModel.forcedUsername != null,
			password = password,
			onPasswordChange = { password = it },
			message = message,
			onSignIn = {
				if (username.isNotBlank()) {
					emptyUsername = false
					userLoginViewModel.login(username, password)
				} else {
					emptyUsername = true
				}
			},
			onMethodChange = { method = it },
			onCancel = { parentFragmentManager.popBackStack() },
		)
	}

	/**
	 * Add space after every 3 characters so "420420" becomes "420 420".
	 */
	private fun String.formatCode() = buildString {
		@Suppress("MagicNumber")
		val interval = 3
		this@formatCode.forEachIndexed { index, character ->
			if (index != 0 && index % interval == 0) append(" ")
			append(character)
		}
	}
}
