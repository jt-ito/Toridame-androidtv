package org.jellyfin.androidtv.util

/**
 * How strict an age rating is, used to colour its badge.
 */
enum class RatingTone { ALL, TEEN, MATURE }

private val knownTones = mapOf(
	"G" to RatingTone.ALL, "TV-G" to RatingTone.ALL, "TV-Y" to RatingTone.ALL, "TV-Y7" to RatingTone.ALL,
	"TV-Y7-FV" to RatingTone.ALL, "U" to RatingTone.ALL, "L" to RatingTone.ALL, "ALL" to RatingTone.ALL,
	"PG" to RatingTone.TEEN, "TV-PG" to RatingTone.TEEN, "PG-13" to RatingTone.TEEN, "TV-14" to RatingTone.TEEN,
	"12" to RatingTone.TEEN, "12A" to RatingTone.TEEN,
	"R" to RatingTone.MATURE, "TV-MA" to RatingTone.MATURE, "NC-17" to RatingTone.MATURE, "X" to RatingTone.MATURE,
)

private val trailingAge = Regex("""(\d{1,2})\+?$""")

private const val CHILD_AGE = 8
private const val TEEN_AGE = 16

/**
 * The tone of an age rating, or null when it is not a rating that is known or an age (no colour is used then).
 */
fun getRatingTone(rating: String?): RatingTone? {
	val text = rating?.trim()?.uppercase().orEmpty()
	if (text.isEmpty()) return null
	knownTones[text]?.let { return it }

	// Ratings that are an age, like "16", "FSK-12" or "18+"
	val years = trailingAge.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
	return when {
		years < CHILD_AGE -> RatingTone.ALL
		years < TEEN_AGE -> RatingTone.TEEN
		else -> RatingTone.MATURE
	}
}
