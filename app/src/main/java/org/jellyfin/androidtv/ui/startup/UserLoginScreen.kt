package org.jellyfin.androidtv.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text

/** How somebody signs in. */
enum class LoginMethod { QuickConnect, Credentials }

/**
 * Signing in, in the middle of the screen: with a Quick Connect code from another device, or with a name and a
 * password. Drawn with the colors of [JellyfinTheme].
 */
@Composable
fun UserLoginScreen(
	serverName: String,
	method: LoginMethod,
	quickConnectCode: String?,
	quickConnectAvailable: Boolean,
	username: String,
	onUsernameChange: (String) -> Unit,
	usernameLocked: Boolean,
	password: String,
	onPasswordChange: (String) -> Unit,
	message: String?,
	onSignIn: () -> Unit,
	onMethodChange: (LoginMethod) -> Unit,
	onCancel: () -> Unit,
) {
	val colors = JellyfinTheme.colorScheme
	val firstFocus = remember { FocusRequester() }
	val passwordFocus = remember { FocusRequester() }
	val buttonFocus = remember { FocusRequester() }

	// Quick Connect has no field, so the first button has the focus there
	val focusOnOpen = initialFocus(
		requester = when {
			method == LoginMethod.QuickConnect -> buttonFocus
			usernameLocked -> passwordFocus
			else -> firstFocus
		},
		key = method,
	)

	Box(modifier = Modifier.fillMaxSize().then(focusOnOpen)) {
		Column(
			modifier = Modifier
				.align(Alignment.Center)
				.padding(horizontal = 48.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Text(
				text = stringResource(R.string.lbl_sign_in),
				fontSize = 38.sp,
				fontWeight = FontWeight.Medium,
				color = colors.onBackground,
			)
			Text(
				text = stringResource(R.string.login_connect_to, serverName),
				fontSize = 18.sp,
				color = colors.listCaption,
			)

			Spacer(Modifier.height(18.dp))

			when (method) {
				LoginMethod.QuickConnect -> {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(10.dp),
						modifier = Modifier.widthIn(max = 720.dp),
					) {
						for (step in listOf(R.string.login_quickconnect_step_1, R.string.login_quickconnect_step_2, R.string.login_quickconnect_step_3)) {
							Text(text = stringResource(step), fontSize = 20.sp, color = colors.onBackground, textAlign = TextAlign.Center)
						}
					}

					Spacer(Modifier.height(10.dp))

					if (quickConnectCode != null) {
						Text(
							text = quickConnectCode,
							fontSize = 76.sp,
							fontWeight = FontWeight.Light,
							color = colors.onBackground,
						)
					} else {
						Box(modifier = Modifier.height(96.dp), contentAlignment = Alignment.Center) {
							CircularProgressIndicator(modifier = Modifier.size(40.dp))
						}
					}
				}

				LoginMethod.Credentials -> {
					Text(text = stringResource(R.string.input_username), fontSize = 16.sp, color = colors.listCaption)
					StartupTextField(
						value = username,
						onValueChange = onUsernameChange,
						enabled = !usernameLocked,
						imeAction = ImeAction.Next,
						modifier = Modifier.focusRequester(firstFocus),
					)

					Spacer(Modifier.height(4.dp))

					Text(text = stringResource(R.string.input_password), fontSize = 16.sp, color = colors.listCaption)
					StartupTextField(
						value = password,
						onValueChange = onPasswordChange,
						password = true,
						imeAction = ImeAction.Done,
						onImeAction = onSignIn,
						modifier = Modifier.focusRequester(passwordFocus),
					)

					Spacer(Modifier.height(8.dp))

					StartupButton(text = stringResource(R.string.action_login), onClick = onSignIn, primary = true)
				}
			}

			if (message != null) {
				Text(
					text = message,
					fontSize = 16.sp,
					color = colors.listCaption,
					textAlign = TextAlign.Center,
					modifier = Modifier.widthIn(max = 700.dp),
				)
			}

			Spacer(Modifier.height(18.dp))

			Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
				if (method == LoginMethod.QuickConnect) {
					StartupButton(
						text = stringResource(R.string.action_use_password),
						onClick = { onMethodChange(LoginMethod.Credentials) },
						modifier = Modifier.focusRequester(buttonFocus),
					)
				} else {
					StartupButton(
						text = stringResource(R.string.action_use_quickconnect),
						onClick = { onMethodChange(LoginMethod.QuickConnect) },
						enabled = quickConnectAvailable,
					)
				}
				StartupButton(text = stringResource(R.string.btn_cancel), onClick = onCancel)
			}
		}
	}
}
