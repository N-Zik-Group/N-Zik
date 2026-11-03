package app.n_zik.android.components.player

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.components.theme.DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** Source changes load once; brightness and mode changes only derive display roles. */
@Composable
fun rememberArtworkDisplayColors(
    uri: String?,
    isDark: Boolean,
    revision: String? = null,
): ArtworkDisplayColors? {
    val context = LocalContext.current.applicationContext
    var profile by remember(context) { mutableStateOf(getActiveProfile(context)) }
    DisposableEffect(context) {
        val prefs = context.profilePreferences
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> profile = getActiveProfile(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        profile = getActiveProfile(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val preferences = remember(context, profile) { context.preferences }
    var keepDark by remember(preferences) { mutableStateOf(preferences.getBoolean(
        dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == dynamicThemeRespectBrightnessKey || key == null) keepDark = preferences.getBoolean(
                dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // Close the initial read/register window without waiting for another preference write.
        keepDark = preferences.getBoolean(dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val generation by SharedArtworkColors.generation.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    var raw by remember(uri, profile, revision, generation) { mutableStateOf<ArtworkColorSnapshot?>(null) }
    LaunchedEffect(uri, profile, revision, generation) {
        try {
            raw = SharedArtworkColors.load(context, uri, revision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ArtworkColors").w(error, "Unable to analyze artwork")
        }
    }
    return remember(raw, isDark, keepDark) { raw?.display(isDark, keepDark) }
}
