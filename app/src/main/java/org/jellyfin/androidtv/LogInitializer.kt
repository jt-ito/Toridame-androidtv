package org.jellyfin.androidtv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.startup.Initializer
import timber.log.Timber

private const val DEBUG_LOGGING_KEY = "pref_enable_debug"

// Held in a field: preference listeners are only kept as weak references
private val logSettingListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
	if (key == DEBUG_LOGGING_KEY) LogTree.verbose = preferences.getBoolean(DEBUG_LOGGING_KEY, false)
}

/** Writes everything in a debug build or with debug logging on, and only warnings and errors otherwise. */
class LogTree : Timber.DebugTree() {
	override fun isLoggable(tag: String?, priority: Int) = BuildConfig.DEBUG || verbose || priority >= Log.WARN

	companion object {
		@Volatile
		var verbose = false
	}
}

class LogInitializer : Initializer<Unit> {
	override fun create(context: Context) {
		// Enable improved logging for leaking resources
		// https://wh0.github.io/2020/08/12/closeguard.html
		if (BuildConfig.DEBUG) {
			try {
				Class.forName("dalvik.system.CloseGuard")
					.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
					.invoke(null, true)
			} catch (e: ReflectiveOperationException) {
				@Suppress("TooGenericExceptionThrown")
				throw RuntimeException(e)
			}
		}

		// Initialize the logging library. A release build only writes warnings and errors: every other line costs time,
		// and the app logs a lot (every seek, card and playback event). The debug logging setting brings it all back.
		val preferences = PreferenceManager.getDefaultSharedPreferences(context)
		LogTree.verbose = preferences.getBoolean(DEBUG_LOGGING_KEY, false)
		preferences.registerOnSharedPreferenceChangeListener(logSettingListener)
		Timber.plant(LogTree())
		Timber.i("Debug tree planted")
	}

	override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}
