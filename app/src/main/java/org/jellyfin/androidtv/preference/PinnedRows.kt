package org.jellyfin.androidtv.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A collection or a genre that has a row of its own on the home screen, or a library or collection pinned to the side
 * menu. [kind] is [COLLECTION], [GENRE] or [LIBRARY].
 */
@Serializable
data class PinnedRow(
	val kind: String,
	/** The id of the collection; for a genre its name. */
	val id: String,
	val name: String,
	/** For the side menu: the kind of library, which picks the icon. */
	val type: String = "",
) {
	/** What the row is called in the "thumbnails" setting. */
	val key get() = "pinned:$kind:$id"

	companion object {
		const val COLLECTION = "collection"
		const val GENRE = "genre"
		const val LIBRARY = "library"
	}
}

fun String.toPinnedRows(): List<PinnedRow> =
	runCatching { Json.decodeFromString<List<PinnedRow>>(this) }.getOrDefault(emptyList())

fun List<PinnedRow>.toPreferenceValue(): String = Json.encodeToString(this)

/** The rows that show thumbnails instead of posters are kept as a comma separated list of their keys. */
fun String.toThumbKeys(): Set<String> = split(',').filter { it.isNotBlank() }.toSet()

fun Set<String>.toPreferenceValue(): String = joinToString(",")
