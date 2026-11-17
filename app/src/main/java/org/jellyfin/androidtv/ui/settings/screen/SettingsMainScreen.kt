package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.rememberFeature
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.Router
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

@Composable
fun SettingsMainScreen() {
	val router = LocalRouter.current

	val activityOn = rememberFeature("activityFeed")
	val badgesOn = rememberFeature("posterBadges")
	val hiddenOn = rememberFeature("hiddenContent")
	val privateOn = rememberFeature("privateLibraries")

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.settings)) },
				captionContent = { Text(stringResource(R.string.settings_description)) },
			)
		}

		group(R.string.settings_group_account)
		entry(router, R.drawable.ic_users, R.string.pref_login, Routes.AUTHENTICATION)
		if (activityOn) entry(router, R.drawable.ic_watch, R.string.activity_title, Routes.ACTIVITY)

		group(R.string.settings_group_app)
		entry(router, R.drawable.ic_adjust, R.string.pref_customization, Routes.CUSTOMIZATION)
		entry(router, R.drawable.ic_next, R.string.pref_playback, Routes.PLAYBACK)
		// TODO: Temporarily added to root - should be accessed via customization screen instead
		entry(router, R.drawable.ic_photos, R.string.pref_screensaver, Routes.CUSTOMIZATION_SCREENSAVER)
		entry(router, R.drawable.ic_control_select, R.string.pref_shortcuts, Routes.PLAYBACK_SHORTCUTS)

		group(R.string.settings_group_content)
		if (badgesOn) entry(router, R.drawable.ic_star, R.string.pref_badges, Routes.CUSTOMIZATION_BADGES)
		if (hiddenOn) entry(router, R.drawable.ic_visibility_off, R.string.hidden_title, Routes.CUSTOMIZATION_HIDDEN)
		if (privateOn) entry(router, R.drawable.ic_folder, R.string.private_title, Routes.CUSTOMIZATION_PRIVATE)

		group(R.string.settings_group_help)
		entry(router, R.drawable.ic_error, R.string.pref_telemetry_category, Routes.TELEMETRY)
		entry(router, R.drawable.ic_jellyfin, R.string.pref_about_title, Routes.ABOUT)
	}
}

/** A heading above a group of entries. */
private fun LazyListScope.group(title: Int) {
	item(key = "group-$title") {
		Spacer(Modifier.height(8.dp))
		ListSection(headingContent = { Text(stringResource(title)) })
	}
}

private fun LazyListScope.entry(router: Router, icon: Int, title: Int, route: String) {
	item(key = route) {
		ListButton(
			leadingContent = { Icon(painterResource(icon), contentDescription = null) },
			headingContent = { Text(stringResource(title)) },
			onClick = { router.push(route) },
			modifier = Modifier.focusKey(route),
		)
	}
}
