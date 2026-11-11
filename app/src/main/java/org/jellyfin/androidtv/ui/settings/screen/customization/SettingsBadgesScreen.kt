package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

/** Small tags on the cards: picture size, age rating and score. Each one can be switched on its own. */
@Composable
fun SettingsBadgesScreen() {
	val userPreferences = koinInject<UserPreferences>()

	var enabled by rememberPreference(userPreferences, UserPreferences.badgesEnabled)
	var quality by rememberPreference(userPreferences, UserPreferences.badgesQuality)
	var ageRating by rememberPreference(userPreferences, UserPreferences.badgesAgeRating)
	var score by rememberPreference(userPreferences, UserPreferences.badgesScore)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_badges)) },
				captionContent = { Text(stringResource(R.string.pref_badges_description)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_badges_show)) },
				trailingContent = { Checkbox(checked = enabled) },
				onClick = { enabled = !enabled },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_badges_quality)) },
				captionContent = { Text(stringResource(R.string.pref_badges_quality_description)) },
				trailingContent = { Checkbox(checked = quality) },
				enabled = enabled,
				onClick = { quality = !quality },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_badges_age_rating)) },
				trailingContent = { Checkbox(checked = ageRating) },
				enabled = enabled,
				onClick = { ageRating = !ageRating },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_badges_score)) },
				trailingContent = { Checkbox(checked = score) },
				enabled = enabled,
				onClick = { score = !score },
			)
		}
	}
}
