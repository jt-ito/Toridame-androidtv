package org.jellyfin.androidtv.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text

/**
 * A rounded button for the screens before signing in: an outline that fills in while it has the focus. The main action
 * of a screen is filled all the time.
 */
@Composable
fun StartupButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	primary: Boolean = false,
) {
	val colors = JellyfinTheme.colorScheme
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(50)
	val content = if (focused) colors.onButtonFocused else if (primary) colors.onButtonActive else colors.onBackground

	Box(
		modifier = modifier
			.alpha(if (enabled) 1f else 0.4f)
			.clip(shape)
			.background(
				when {
					focused -> colors.buttonFocused
					primary -> colors.buttonActive
					else -> Color.Transparent
				}
			)
			.border(2.dp, if (focused || primary) Color.Transparent else colors.onBackground.copy(alpha = 0.3f), shape)
			.onFocusChanged { focused = it.isFocused }
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = 32.dp, vertical = 14.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(text = text, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = content)
	}
}

/**
 * A text field for the screens before signing in: a dark rounded box with an outline that lights up while it has the focus.
 */
@Composable
fun StartupTextField(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	password: Boolean = false,
	keyboardType: KeyboardType = KeyboardType.Text,
	imeAction: ImeAction = ImeAction.Done,
	onImeAction: () -> Unit = {},
) {
	val colors = JellyfinTheme.colorScheme
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(16.dp)

	BasicTextField(
		value = value,
		onValueChange = onValueChange,
		enabled = enabled,
		singleLine = true,
		textStyle = TextStyle(color = colors.onBackground, fontSize = 22.sp),
		cursorBrush = SolidColor(colors.onBackground),
		visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
		keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
		keyboardActions = KeyboardActions(onAny = { onImeAction() }),
		modifier = modifier
			.widthIn(min = 360.dp, max = 560.dp)
			.fillMaxWidth()
			.alpha(if (enabled) 1f else 0.5f)
			.clip(shape)
			.background(colors.onBackground.copy(alpha = if (focused) 0.14f else 0.08f))
			.border(if (focused) 3.dp else 2.dp, if (focused) colors.buttonFocused else colors.onBackground.copy(alpha = 0.25f), shape)
			.onFocusChanged { focused = it.isFocused },
		decorationBox = { field ->
			Box(modifier = Modifier.height(60.dp).padding(horizontal = 20.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
				field()
			}
		},
	)
}

/**
 * Puts the focus on [requester] when a screen before signing in opens. The toolbar at the top takes the focus first, so
 * it is asked for again a moment later, but only while nothing on the screen has the focus: asking again once somebody
 * has started to move around would pull them back. The view the screen is in is given the focus as well, otherwise the
 * first press of a direction is spent on moving the focus into the screen and is undone again.
 *
 * Put the returned modifier on the root of the screen.
 */
@Composable
fun initialFocus(requester: FocusRequester, key: Any?, enabled: Boolean = true): Modifier {
	var screenHasFocus by remember { mutableStateOf(false) }
	val view = LocalView.current

	LaunchedEffect(key, enabled) {
		if (!enabled) return@LaunchedEffect

		repeat(4) { attempt ->
			delay(if (attempt == 0) 250 else 400)
			if (attempt == 0 || !screenHasFocus) {
				view.requestFocus()
				runCatching { requester.requestFocus() }
			}
		}
	}

	return Modifier.onFocusChanged { screenHasFocus = it.hasFocus }
}
