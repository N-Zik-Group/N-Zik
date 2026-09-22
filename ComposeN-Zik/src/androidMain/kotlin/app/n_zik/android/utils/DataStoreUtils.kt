package app.n_zik.android.utils

import android.content.Context

/**
 * Minimal port of the Cubic Music "DataStoreUtils" (GPL-3.0, original license header in
 * the Rewind package) backed by plain SharedPreferences instead of Preferences DataStore,
 * so no new dependency is required.
 */
object DataStoreUtils {

    // Key constants
    const val KEY_USERNAME = "username"

    private const val PREFS_NAME = "app_settings"

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Read a string preference. Plain SharedPreferences read, safe to call from a
     * coroutine on any dispatcher.
     */
    fun getString(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun saveString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }
}
