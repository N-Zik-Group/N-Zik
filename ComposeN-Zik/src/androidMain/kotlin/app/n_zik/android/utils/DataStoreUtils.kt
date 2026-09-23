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

    /** First-launch onboarding flag; the onboarding flow only runs while it is false. */
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    /**
     * Current onboarding step (the `OnboardingStep` enum name); persisted on every step
     * change so a process restart (post-import restart, process death) resumes at that
     * step instead of replaying the flow. Cleared when the flow completes.
     */
    const val KEY_ONBOARDING_STEP = "onboarding_step"

    /**
     * Runtime permissions whose system dialog was already shown on the onboarding
     * permissions screen (comma-joined permission names); lets the
     * PERMANENTLY_DENIED mapping survive activity recreation.
     */
    const val KEY_ONBOARDING_PERMISSIONS_REQUESTED = "onboarding_permissions_requested"

    /** Source of the display name shown on the Rewind slides (see [resolveDisplayName]). */
    const val KEY_DISPLAY_NAME_SOURCE = "display_name_source"

    // Display name sources (values stored under [KEY_DISPLAY_NAME_SOURCE])
    const val DISPLAY_NAME_SOURCE_CUSTOM = "custom"
    const val DISPLAY_NAME_SOURCE_YOUTUBE = "youtube"

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

    /**
     * Read a boolean preference. Plain SharedPreferences read, safe to call
     * from a coroutine on any dispatcher.
     */
    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    fun saveBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }
}
