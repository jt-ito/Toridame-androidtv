package org.jellyfin.androidtv.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text

/**
 * Entering the address of a server by hand, in the middle of the screen. Drawn with the colors of [JellyfinTheme].
 */
@Composable
fun ServerAddScreen(
	address: String,
	onAddressChange: (String) -> Unit,
	message: String?,
	busy: Boolean,
	onConnect: () -> Unit,
) {
	val colors = JellyfinTheme.colorScheme
	val fieldFocus = remember { FocusRequester() }

	val focusOnOpen = initialFocus(fieldFocus, Unit, enabled = !busy)

	Box(modifier = Modifier.fillMaxSize().then(focusOnOpen)) {
		Column(
			modifier = Modifier
				.align(Alignment.Center)
				.padding(horizontal = 48.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				text = stringResource(R.string.lbl_enter_server_address),
				fontSize = 38.sp,
				fontWeight = FontWeight.Medium,
				color = colors.onBackground,
			)
			Text(
				text = stringResource(R.string.lbl_valid_server_address),
				fontSize = 18.sp,
				color = colors.listCaption,
				textAlign = TextAlign.Center,
			)

			Spacer(Modifier.height(12.dp))

			StartupTextField(
				value = address,
				onValueChange = onAddressChange,
				enabled = !busy,
				keyboardType = KeyboardType.Uri,
				imeAction = ImeAction.Done,
				onImeAction = onConnect,
				modifier = Modifier.focusRequester(fieldFocus),
			)

			Spacer(Modifier.height(4.dp))

			StartupButton(
				text = stringResource(R.string.action_connect),
				onClick = onConnect,
				enabled = !busy,
				primary = true,
			)

			if (message != null) {
				Text(
					text = message,
					fontSize = 16.sp,
					color = colors.listCaption,
					textAlign = TextAlign.Center,
					modifier = Modifier.widthIn(max = 700.dp),
				)
			}
		}
	}
}
