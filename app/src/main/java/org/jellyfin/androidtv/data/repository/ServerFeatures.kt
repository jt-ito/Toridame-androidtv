package org.jellyfin.androidtv.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

/**
 * Which Toridame features the administrator of the server switched on (Features in the dashboard of the server). A
 * feature counts as on until the server says otherwise, so a server without the setting changes nothing.
 */
class ServerFeatures(
	private val repository: ToridameRepository,
) {
	private val state = MutableStateFlow<Map<String, Boolean>>(emptyMap())
	val features: StateFlow<Map<String, Boolean>> = state

	fun isEnabled(key: String): Boolean = state.value[key] != false

	/** Asks the server again; called when the app comes to the front. */
	suspend fun refresh() {
		state.value = repository.customization().features
	}
}

/** Whether a feature is switched on, and follows the answer of the server once it arrives. */
@Composable
fun rememberFeature(key: String): Boolean {
	val features = koinInject<ServerFeatures>()
	val map by features.features.collectAsState()
	return map[key] != false
}
