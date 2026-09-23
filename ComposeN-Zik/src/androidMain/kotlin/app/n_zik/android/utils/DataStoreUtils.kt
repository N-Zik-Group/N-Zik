package app.n_zik.android.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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

    // Rewind toggles (spec GH-275): all default to `true` on read, so an existing install
    // keeps the current behavior until the user flips something (zero regression)

    /** Master switch: gates the home entries, the menu item and the monthly reminder. */
    const val KEY_REWIND_ENABLED = "rewind_enabled"

    /** Yearly recap entries on the Rewind home. */
    const val KEY_REWIND_YEARLY_ENABLED = "rewind_yearly_enabled"

    /** Month grid on the Rewind home. */
    const val KEY_REWIND_MONTHLY_ENABLED = "rewind_monthly_enabled"

    /** All-time entry on the Rewind home. */
    const val KEY_REWIND_GLOBAL_ENABLED = "rewind_global_enabled"

    /** Monthly reminder notification; also gated on [KEY_REWIND_MONTHLY_ENABLED]. */
    const val KEY_REWIND_MONTHLY_NOTIF_ENABLED = "rewind_monthly_notif_enabled"

    /** Yearly reminder notification; inert until the yearly worker lands (roadmap item 2). */
    const val KEY_REWIND_YEARLY_NOTIF_ENABLED = "rewind_yearly_notif_enabled"

    private const val PREFS_NAME = "app_settings"

    // Internal (not private) so the file-top-level rememberDataStoreBooleanPreference
    // can reach the same SharedPreferences file
    internal fun prefs(context: Context): android.content.SharedPreferences =
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

/**
 * Builds a SharedPreferences listener reporting every same-process write to [key] with its
 * new value (spec GH-275). Internal so the real-time behavior is unit-testable without a
 * composition; [rememberDataStoreBooleanPreference] registers it for the lifetime of the
 * composing composable.
 */
internal fun createBooleanPreferenceListener(
    context: Context,
    key: String,
    default: Boolean,
    onChange: (Boolean) -> Unit
): SharedPreferences.OnSharedPreferenceChangeListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) {
            onChange(DataStoreUtils.prefs(context).getBoolean(key, default))
        }
    }

/**
 * Live version of [DataStoreUtils.getBoolean] (spec GH-275) for compositions that outlive
 * the screen writing the preference — the app header stays composed across the whole
 * session, so a one-shot `remember { getBoolean(...) }` read would never refresh. Seeds
 * the value once, then follows every same-process write through a SharedPreferences
 * listener — the same pattern as the legacy `rememberPreference` on the "preferences" file.
 */
@Composable
fun rememberDataStoreBooleanPreference(key: String, default: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = DataStoreUtils.prefs(context)
    val state = remember { mutableStateOf(prefs.getBoolean(key, default)) }
    val listener = remember(prefs, key, default) {
        createBooleanPreferenceListener(context, key, default) { newValue ->
            if (state.value != newValue) {
                state.value = newValue
            }
        }
    }
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}
