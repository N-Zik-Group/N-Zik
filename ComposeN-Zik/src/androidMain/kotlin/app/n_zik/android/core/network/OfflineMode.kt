package app.n_zik.android.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Preferences key of the per-profile "full offline mode" toggle (burger menu). */
const val fullOfflineModeKey = "fullOfflineModeEnabled"

/** Profile-owned offline policy shared by phone and automotive clients. */
object OfflineMode {

    /** Non-composable read of the mode flag (profile-scoped, default off). */
    fun enabled(context: Context): Boolean =
        context.preferences.getBoolean(fullOfflineModeKey, false)

    /** Live flow of the mode flag: emits on initial read and on every change. */
    fun enabledFlow(context: Context): Flow<Boolean> = callbackFlow {
        val profilePrefs = context.profilePreferences
        val lock = Any()
        var prefs: SharedPreferences? = null
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == fullOfflineModeKey) {
                synchronized(lock) {
                    if (prefs != null) trySend(enabled(context))
                }
            }
        }

        fun bindActivePreferences() {
            prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            prefs = context.preferences.also { it.registerOnSharedPreferenceChangeListener(listener) }
            trySend(enabled(context))
        }

        val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == activeProfileKey) {
                synchronized(lock) {
                    if (prefs != null) bindActivePreferences()
                }
            }
        }

        synchronized(lock) {
            profilePrefs.registerOnSharedPreferenceChangeListener(profileListener)
            bindActivePreferences()
        }
        awaitClose {
            synchronized(lock) {
                profilePrefs.unregisterOnSharedPreferenceChangeListener(profileListener)
                prefs?.unregisterOnSharedPreferenceChangeListener(listener)
                prefs = null
            }
        }
    }.distinctUntilChanged()

    /** Live, profile-scoped read of the mode flag for composition (listener-backed, like [rememberPreference]). */
    @Composable
    fun rememberEnabled() = rememberPreference(fullOfflineModeKey, false)
}
