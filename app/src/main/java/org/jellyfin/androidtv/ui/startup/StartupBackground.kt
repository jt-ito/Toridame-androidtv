package org.jellyfin.androidtv.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.jellyfin.androidtv.ui.base.JellyfinTheme

/**
 * What is behind every screen before signing in, the toolbar at the top included: darker than the theme's background,
 * which fades up a little toward the bottom. It is made from the theme's colors, so another theme applies to it.
 */
@Composable
fun StartupBackground() {
	val background = JellyfinTheme.colorScheme.background

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Brush.verticalGradient(listOf(lerp(background, Color.Black, 0.8f), lerp(background, Color.Black, 0.4f)))),
	)
}
