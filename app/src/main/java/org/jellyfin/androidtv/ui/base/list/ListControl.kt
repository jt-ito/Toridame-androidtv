package org.jellyfin.androidtv.ui.base.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes

object ListControlDefaults {
	@ReadOnlyComposable
	@Composable
	fun colors(
		containerColor: Color = JellyfinTheme.colorScheme.listButton,
		// A light veil over the panel, with the ring of the theme for the focus drawn around it
		focusedContainerColor: Color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.12f),
	) = ListControlColors(
		containerColor = containerColor,
		focusedContainerColor = focusedContainerColor,
	)
}

@Composable
fun ListControl(
	headingContent: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	interactionSource: InteractionSource? = null,
	colors: ListControlColors = ListControlDefaults.colors(),
	overlineContent: (@Composable () -> Unit)? = null,
	captionContent: (@Composable () -> Unit)? = null,
	leadingContent: (@Composable () -> Unit)? = null,
	trailingContent: (@Composable () -> Unit)? = null,
	footerContent: (@Composable () -> Unit)? = null,
) {
	@Suppress("NAME_SHADOWING")
	val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val pressed by interactionSource.collectIsPressedAsState()

	val backgroundColor = when {
		!enabled -> colors.containerColor
		pressed -> colors.focusedContainerColor
		focused -> colors.focusedContainerColor
		else -> colors.containerColor
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.background(backgroundColor, LocalShapes.current.large)
			.then(if (focused && enabled) Modifier.border(3.dp, JellyfinTheme.colorScheme.buttonFocused, LocalShapes.current.large) else Modifier)
			.clip(LocalShapes.current.large)
			.alpha(if (enabled) 1f else 0.4f),
	) {
		ListItemContent(
			headingContent = headingContent,
			overlineContent = overlineContent,
			captionContent = captionContent,
			leadingContent = leadingContent,
			trailingContent = trailingContent,
			footerContent = footerContent,
			headingStyle = JellyfinTheme.typography.listHeadline
				.copy(color = JellyfinTheme.colorScheme.listHeadline),
		)
	}
}
