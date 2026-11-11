package org.jellyfin.androidtv.util

private const val MAX_INITIALS = 2

/**
 * The letters for a profile picture without an image: the first letter of the first word, and of the second word when
 * there is one. Never more than two, whatever the length of the name.
 */
fun initialsOf(name: String?): String = name.orEmpty()
	.split(Regex("\\s+"))
	.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
	.take(MAX_INITIALS)
	.joinToString("")
	.uppercase()
