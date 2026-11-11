package org.jellyfin.androidtv.util.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.DeviceProfile

/**
 * What this device can play, worked out by asking its decoders. That is slow and was done again each time something was
 * played, but the answer only changes with a setting or with the server version, so it is kept until one of them does.
 */
object DeviceProfileCache {
	private val lock = Any()
	private var cached: DeviceProfile? = null
	private var cachedFor: String? = null
	private var listening = false

	// Held in a field: preference listeners are only kept as weak references
	private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
		synchronized(lock) { cached = null }
	}

	@JvmStatic
	fun get(context: Context, userPreferences: UserPreferences, serverVersion: ServerVersion): DeviceProfile = synchronized(lock) {
		if (!listening) {
			PreferenceManager.getDefaultSharedPreferences(context.applicationContext).registerOnSharedPreferenceChangeListener(settingsListener)
			listening = true
		}

		val key = serverVersion.toString()
		cached?.takeIf { cachedFor == key } ?: createDeviceProfile(context, userPreferences, serverVersion).also {
			cached = it
			cachedFor = key
		}
	}
}
