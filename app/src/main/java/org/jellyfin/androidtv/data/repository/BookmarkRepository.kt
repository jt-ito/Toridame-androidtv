package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * A spot in a video the user marked.
 */
data class Bookmark(
	val seconds: Int,
	val label: String?,
)

/**
 * Bookmarks live on the Toridame server (the official server has no such feature, so on that server every call
 * fails and no bookmarks are shown).
 */
class BookmarkRepository(
	private val api: ApiClient,
) {
	suspend fun list(itemId: UUID): List<Bookmark> = runCatching {
		val body = request("GET", "Items/$itemId/Bookmarks") ?: return emptyList()
		val array = JSONArray(body)
		(0 until array.length()).map { index ->
			val entry = array.getJSONObject(index)
			Bookmark(
				seconds = entry.optInt("Seconds"),
				label = entry.optString("Label").takeIf { it.isNotBlank() && it != "null" },
			)
		}.sortedBy { it.seconds }
	}.onFailure { Timber.w(it, "Could not load bookmarks") }.getOrDefault(emptyList())

	suspend fun add(itemId: UUID, seconds: Int, label: String? = null): Boolean = runCatching {
		val json = JSONObject().put("Seconds", seconds).apply { if (label != null) put("Label", label) }
		request("POST", "Items/$itemId/Bookmarks", json.toString()) != null
	}.onFailure { Timber.w(it, "Could not add bookmark") }.getOrDefault(false)

	suspend fun remove(itemId: UUID, seconds: Int): Boolean = runCatching {
		request("DELETE", "Items/$itemId/Bookmarks?seconds=$seconds") != null
	}.onFailure { Timber.w(it, "Could not remove bookmark") }.getOrDefault(false)

	/** Returns the response text (empty for no content), or null when the server did not accept the request. */
	private suspend fun request(method: String, path: String, body: String? = null): String? = withContext(Dispatchers.IO) {
		val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext null
		val connection = URL("$baseUrl/$path").openConnection() as HttpURLConnection
		try {
			connection.requestMethod = method
			connection.connectTimeout = TIMEOUT_MS
			connection.readTimeout = TIMEOUT_MS
			connection.setRequestProperty(
				"Authorization",
				AuthorizationHeaderBuilder.buildHeader(
					api.clientInfo.name,
					api.clientInfo.version,
					api.deviceInfo.id,
					api.deviceInfo.name,
					api.accessToken,
				)
			)
			if (body != null) {
				connection.doOutput = true
				connection.setRequestProperty("Content-Type", "application/json")
				connection.outputStream.use { it.write(body.toByteArray()) }
			}

			if (connection.responseCode !in HTTP_OK_RANGE) return@withContext null
			connection.inputStream.bufferedReader().use { it.readText() }
		} finally {
			connection.disconnect()
		}
	}

	private companion object {
		const val TIMEOUT_MS = 10_000
		val HTTP_OK_RANGE = 200..299
	}
}
