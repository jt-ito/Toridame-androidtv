package org.jellyfin.androidtv.ui.settings.screen.customization.subtitle

import org.jellyfin.androidtv.R

/**
 * A one-press subtitle look. The values are the ones the subtitle settings screen edits.
 */
enum class SubtitlePreset(
	val nameRes: Int,
	val textSize: Float,
	val textWeight: Int,
	val textColor: Long,
	val backgroundColor: Long,
	val strokeColor: Long,
) {
	CLASSIC(R.string.subtitle_preset_classic, 24f, 400, 0xFFFFFFFF, 0x00FFFFFF, 0xFF000000),
	YELLOW(R.string.subtitle_preset_yellow, 24f, 700, 0xFFFFFF00, 0x00FFFFFF, 0xFF000000),
	HIGH_CONTRAST(R.string.subtitle_preset_high_contrast, 32f, 700, 0xFFFFFFFF, 0xFF000000, 0x00000000),
	LARGE_PRINT(R.string.subtitle_preset_large_print, 40f, 700, 0xFFFFFFFF, 0x00FFFFFF, 0xFF000000),
	CINEMA(R.string.subtitle_preset_cinema, 18f, 400, 0xFFD3D3D3, 0x00FFFFFF, 0xFF000000),
}
