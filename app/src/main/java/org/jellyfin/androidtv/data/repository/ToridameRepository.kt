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
import kotlin.math.roundToInt

data class ExternalRating(val source: String, val value: Double?, val score: Double?, val votes: Long?) {
	val label: String get() = RATING_LABELS[source] ?: source

	/** The rating the way people know it from that site, or null when there is nothing to show. */
	val display: String?
		get() = when {
			source == "imdb" && value != null -> "%.1f".format(value)
			// Letterboxd counts in stars out of five
			source == "letterboxd" -> (score?.div(20) ?: value)?.let { "%.1f★".format(it) }
			else -> (score ?: value)?.let { "${it.roundToInt()}%" }
		}

	private companion object {
		val RATING_LABELS = mapOf(
			"imdb" to "IMDb",
			"tmdb" to "TMDb",
			"trakt" to "Trakt",
			"letterboxd" to "Letterboxd",
			"tomatoes" to "Rotten Tomatoes",
			"popcorn" to "Audience",
			"audience" to "Audience",
			"tomatoesaudience" to "Audience",
			"metacritic" to "Metacritic",
			"metacriticuser" to "Metacritic users",
			"rogerebert" to "Roger Ebert",
			"mal" to "MyAnimeList",
			"myanimelist" to "MyAnimeList",
		)
		val ORDER = RATING_LABELS.keys.toList()
	}

	fun order() = ORDER.indexOf(source).let { if (it < 0) ORDER.size else it }
}

data class ElsewhereProvider(val name: String, val kind: String)
data class Elsewhere(val country: String, val providers: List<ElsewhereProvider>)
data class Review(val author: String, val rating: Double?, val content: String, val date: String?)

data class HiddenEntry(val itemId: UUID, val name: String, val type: String, val seriesName: String?)
data class HiddenInfo(val count: Int, val limit: Int, val hasPin: Boolean)
data class PrivateStatus(val active: Boolean, val unlocked: Boolean)
data class PrivateLibrary(val id: UUID, val name: String)
data class ServerCustomization(val pauseScreenEnabled: Boolean, val features: Map<String, Boolean> = emptyMap())

/** What the server says about maintenance. Times are in epoch milliseconds. */
data class MaintenanceStatus(
	val active: Boolean,
	val startsAt: Long?,
	val endsAt: Long?,
	val message: String,
	val locksOutUsers: Boolean,
)

data class ActivityEntry(
	val userName: String,
	val itemId: UUID,
	val itemName: String,
	val itemType: String,
	val seriesId: UUID?,
	val seriesName: String?,
	val seasonNumber: Int?,
	val episodeNumber: Int?,
	val playedAt: Long,
)

data class ActivityFeed(val enabled: Boolean, val sharing: Boolean, val entries: List<ActivityEntry>)

/** Outcome of a call that may need a PIN. */
enum class PinResult { OK, WRONG_PIN, LOCKED_OUT, FAILED }

/**
 * The extra endpoints of the Toridame server: ratings from other sites, where to watch, reviews, hidden content and
 * private libraries, and what the administrator customized. The official server has none of them, so every call
 * there simply fails and the app shows nothing for it.
 */
class ToridameRepository(
	private val api: ApiClient,
) {
	private class Response(val code: Int, val body: String)

	// ---- Item page

	suspend fun externalRatings(itemId: UUID): List<ExternalRating> = runCatching {
		val array = JSONArray(getJson("Items/$itemId/ExternalRatings") ?: return emptyList())
		(0 until array.length()).map { array.getJSONObject(it) }.map {
			ExternalRating(
				source = it.optString("Source"),
				value = it.optDoubleOrNull("Value"),
				score = it.optDoubleOrNull("Score"),
				votes = if (it.isNull("Votes")) null else it.optLong("Votes"),
			)
		}.filter { it.display != null }.sortedBy { it.order() }
	}.onFailure { Timber.d(it, "No external ratings") }.getOrDefault(emptyList())

	suspend fun elsewhere(itemId: UUID): Elsewhere? = runCatching {
		val json = JSONObject(getJson("Items/$itemId/Elsewhere") ?: return null)
		val providers = listOf("Stream" to "stream", "Free" to "free", "Rent" to "rent", "Buy" to "buy").flatMap { (key, kind) ->
			val array = json.optJSONArray(key) ?: return@flatMap emptyList()
			(0 until array.length()).map { ElsewhereProvider(array.getJSONObject(it).optString("Name"), kind) }
		}
		if (providers.isEmpty()) null else Elsewhere(json.optString("Country"), providers)
	}.onFailure { Timber.d(it, "No elsewhere") }.getOrNull()

	suspend fun reviews(itemId: UUID): List<Review> = runCatching {
		val array = JSONArray(getJson("Items/$itemId/Reviews") ?: return emptyList())
		(0 until array.length()).map { array.getJSONObject(it) }.map {
			Review(
				author = it.optString("Author"),
				rating = it.optDoubleOrNull("Rating"),
				content = it.optString("Content"),
				date = it.optString("Date").takeIf { date -> date.isNotBlank() && date != "null" },
			)
		}
	}.onFailure { Timber.d(it, "No reviews") }.getOrDefault(emptyList())

	suspend fun customization(): ServerCustomization = runCatching {
		val json = JSONObject(getJson("Customization") ?: return ServerCustomization(false))
		val features = json.optJSONObject("Features")?.let { list -> list.keys().asSequence().associateWith { list.optBoolean(it, true) } }.orEmpty()
		ServerCustomization(pauseScreenEnabled = json.optBoolean("PauseScreenEnabled"), features = features)
	}.getOrDefault(ServerCustomization(false))

	// ---- Maintenance

	/** Needs no sign in on the server, so it also works when nobody is signed in. Null when the server could not be asked. */
	suspend fun maintenanceStatus(): MaintenanceStatus? = runCatching {
		val json = JSONObject(getJson("System/Maintenance") ?: return null)
		MaintenanceStatus(
			active = json.optBoolean("Active"),
			startsAt = parseTime(json.optString("StartsAt")),
			endsAt = parseTime(json.optString("EndsAt")),
			message = json.optString("Message").takeIf { it != "null" }.orEmpty(),
			locksOutUsers = json.optBoolean("LocksOutUsers"),
		)
	}.getOrNull()

	// ---- Household activity

	suspend fun activityFeed(): ActivityFeed? = runCatching {
		val json = JSONObject(getJson("Activity/Recent") ?: return null)
		val array = json.optJSONArray("Entries") ?: JSONArray()
		val entries = (0 until array.length()).map { array.getJSONObject(it) }.mapNotNull { entry ->
			val itemId = runCatching { UUID.fromString(entry.optString("ItemId")) }.getOrNull() ?: return@mapNotNull null
			ActivityEntry(
				userName = entry.optString("UserName"),
				itemId = itemId,
				itemName = entry.optString("ItemName"),
				itemType = entry.optString("ItemType"),
				seriesId = runCatching { UUID.fromString(entry.optString("SeriesId")) }.getOrNull(),
				seriesName = entry.optString("SeriesName").takeIf { it.isNotBlank() && it != "null" },
				seasonNumber = if (entry.isNull("SeasonNumber")) null else entry.optInt("SeasonNumber"),
				episodeNumber = if (entry.isNull("EpisodeNumber")) null else entry.optInt("EpisodeNumber"),
				playedAt = parseTime(entry.optString("PlayedAt")) ?: return@mapNotNull null,
			)
		}
		ActivityFeed(json.optBoolean("Enabled"), json.optBoolean("Sharing", true), entries)
	}.getOrNull()

	suspend fun setActivitySharing(share: Boolean): Boolean = send("POST", "Activity/Sharing?share=$share").code in OK_RANGE

	private fun parseTime(text: String?): Long? {
		if (text.isNullOrBlank() || text == "null") return null

		return runCatching { java.time.Instant.parse(text).toEpochMilli() }
			.recoverCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }
			.recoverCatching { java.time.LocalDateTime.parse(text).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }
			.getOrNull()
	}

	// ---- Hidden content

	suspend fun hiddenInfo(): HiddenInfo? = runCatching {
		val json = JSONObject(getJson("UserItems/Hidden/Info") ?: return null)
		HiddenInfo(json.optInt("Count"), json.optInt("Limit"), json.optBoolean("HasPin"))
	}.getOrNull()

	/** The hidden titles; needs the PIN when one is set. Null when the server refused (see [lastPinResult]). */
	suspend fun hiddenList(pin: String?): Pair<PinResult, List<HiddenEntry>> {
		val response = send("GET", "UserItems/Hidden", pin = pin)
		val result = response.pinResult()
		if (result != PinResult.OK) return result to emptyList()

		val array = JSONArray(response.body.ifBlank { "[]" })
		return PinResult.OK to (0 until array.length()).map { array.getJSONObject(it) }.mapNotNull {
			val id = runCatching { UUID.fromString(it.optString("ItemId")) }.getOrNull() ?: return@mapNotNull null
			HiddenEntry(
				itemId = id,
				name = it.optString("Name"),
				type = it.optString("Type"),
				seriesName = it.optString("SeriesName").takeIf { name -> name.isNotBlank() && name != "null" },
			)
		}
	}

	suspend fun hide(itemId: UUID): Boolean = send("POST", "UserItems/$itemId/Hidden").code in OK_RANGE

	suspend fun unhide(itemId: UUID, pin: String?): PinResult = send("DELETE", "UserItems/$itemId/Hidden", pin = pin).pinResult()

	suspend fun unhideAll(pin: String?): PinResult = send("DELETE", "UserItems/Hidden", pin = pin).pinResult()

	suspend fun setPin(newPin: String, currentPin: String?): PinResult {
		val json = JSONObject().put("NewPin", newPin).apply { if (currentPin != null) put("CurrentPin", currentPin) }
		return send("POST", "UserItems/Hidden/Pin", body = json.toString()).pinResult()
	}

	suspend fun removePin(pin: String): PinResult = send("DELETE", "UserItems/Hidden/Pin", pin = pin).pinResult()

	// ---- Private libraries

	suspend fun privateStatus(): PrivateStatus? = runCatching {
		val json = JSONObject(getJson("UserLibraries/Private/Status") ?: return null)
		PrivateStatus(json.optBoolean("Active"), json.optBoolean("Unlocked"))
	}.getOrNull()

	suspend fun unlockPrivate(pin: String): PinResult =
		send("POST", "UserLibraries/Private/Unlock", body = JSONObject().put("Pin", pin).toString()).pinResult()

	suspend fun lockPrivate(): Boolean = send("POST", "UserLibraries/Private/Lock").code in OK_RANGE

	suspend fun privateLibraries(pin: String): Pair<PinResult, List<PrivateLibrary>> {
		val response = send("GET", "UserLibraries/Private", pin = pin)
		val result = response.pinResult()
		if (result != PinResult.OK) return result to emptyList()

		val array = JSONArray(response.body.ifBlank { "[]" })
		return PinResult.OK to (0 until array.length()).map { array.getJSONObject(it) }.mapNotNull {
			val id = runCatching { UUID.fromString(it.optString("Id")) }.getOrNull() ?: return@mapNotNull null
			PrivateLibrary(id, it.optString("Name"))
		}
	}

	suspend fun makeLibraryPrivate(libraryId: UUID, pin: String): PinResult = send("POST", "UserLibraries/Private/$libraryId", pin = pin).pinResult()

	suspend fun makeLibraryPublic(libraryId: UUID, pin: String): PinResult = send("DELETE", "UserLibraries/Private/$libraryId", pin = pin).pinResult()

	// ---- Plumbing

	private fun Response.pinResult() = when (code) {
		in OK_RANGE -> PinResult.OK
		HTTP_FORBIDDEN -> PinResult.WRONG_PIN
		HTTP_TOO_MANY -> PinResult.LOCKED_OUT
		else -> PinResult.FAILED
	}

	private fun JSONObject.optDoubleOrNull(name: String) = if (isNull(name) || !has(name)) null else optDouble(name)

	/** The answer text, or null when the server had nothing (no content) or refused. */
	private suspend fun getJson(path: String): String? = send("GET", path).takeIf { it.code in OK_RANGE && it.code != HTTP_NO_CONTENT }
		?.body?.takeIf { it.isNotBlank() }

	private suspend fun send(method: String, path: String, body: String? = null, pin: String? = null): Response = withContext(Dispatchers.IO) {
		val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext Response(0, "")
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
			if (pin != null) connection.setRequestProperty("X-Hidden-Content-Pin", pin)
			if (body != null) {
				connection.doOutput = true
				connection.setRequestProperty("Content-Type", "application/json")
				connection.outputStream.use { it.write(body.toByteArray()) }
			}

			val code = connection.responseCode
			val text = if (code in OK_RANGE) connection.inputStream.bufferedReader().use { it.readText() } else ""
			Response(code, text)
		} catch (err: java.io.IOException) {
			Timber.d(err, "Request to the server failed: $path")
			Response(0, "")
		} finally {
			connection.disconnect()
		}
	}

	private companion object {
		const val TIMEOUT_MS = 10_000
		const val HTTP_NO_CONTENT = 204
		const val HTTP_FORBIDDEN = 403
		const val HTTP_TOO_MANY = 429
		val OK_RANGE = 200..299
	}
}
