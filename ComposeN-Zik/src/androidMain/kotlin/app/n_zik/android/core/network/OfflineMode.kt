package app.n_zik.android.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Preferences key of the per-profile "full offline mode" toggle (burger menu). */
const val fullOfflineModeKey = "fullOfflineModeEnabled"

/**
 * Per-profile "full offline mode": a persistent, explicit choice that feeds the
 * effective online predicate (`NetworkGate.isOnline`, `NetworkQualityHelper`) so
 * all existing offline machinery activates as if the network were down — no new
 * gating mechanics, just the flag inserted into the primitives.
 */
object OfflineMode {

    /** Non-composable read of the mode flag (profile-scoped, default off). */
    fun enabled(context: Context): Boolean =
        context.preferences.getBoolean(fullOfflineModeKey, false)

    /** Live flow of the mode flag: emits on initial read and on every change. */
    fun enabledFlow(context: Context): Flow<Boolean> = callbackFlow {
        val prefs = context.preferences
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == fullOfflineModeKey) {
                trySend(prefs.getBoolean(fullOfflineModeKey, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(fullOfflineModeKey, false))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Live, profile-scoped read of the mode flag for composition (listener-backed, like [rememberPreference]). */
    @Composable
    fun rememberEnabled() = rememberPreference(fullOfflineModeKey, false)
}
