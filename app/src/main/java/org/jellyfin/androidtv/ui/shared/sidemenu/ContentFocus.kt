package org.jellyfin.androidtv.ui.shared.sidemenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester

/**
 * Where the side menu sends the focus when it is left to the right. A screen that is built with Compose says where its
 * focus belongs, as asking the view it sits in does not reach into it; the other screens are given the focus as a view.
 */
object ContentFocus {
	@Volatile
	var handler: (() -> Unit)? = null
}

/** Makes [requester] the place the side menu hands the focus to, while this screen is shown. */
@Composable
fun ProvideContentFocus(requester: FocusRequester) {
	DisposableEffect(requester) {
		val handler = { runCatching { requester.requestFocus() }; Unit }
		ContentFocus.handler = handler
		onDispose { if (ContentFocus.handler === handler) ContentFocus.handler = null }
	}
}
