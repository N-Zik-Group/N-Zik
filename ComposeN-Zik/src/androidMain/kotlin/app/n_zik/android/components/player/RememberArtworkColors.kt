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
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.components.theme.DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import timber.log.Timber

internal const val ARTWORK_METADATA_GRACE_MS = 350L

/** Source changes load once; brightness and mode changes only derive display roles. */
@Composable
fun rememberArtworkDisplayColors(
    uri: String?,
    isDark: Boolean,
    revision: String? = null,
    awaitArtworkMetadata: Boolean = false,
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
    var raw by remember(profile) { mutableStateOf<ArtworkColorSnapshot?>(null) }
    val missingUri = uri.isNullOrBlank() || uri == "null"
    LaunchedEffect(uri, profile, revision, generation, awaitArtworkMetadata) {
        if (missingUri && awaitArtworkMetadata && raw != null) delay(ARTWORK_METADATA_GRACE_MS)
        try {
            val loaded = SharedArtworkColors.load(context, uri, revision)
            currentCoroutineContext().ensureActive()
            raw = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            raw = null
            Timber.tag("ArtworkColors").w(error, "Unable to analyze artwork")
        }
    }
    return remember(missingUri, awaitArtworkMetadata, raw, isDark, keepDark) {
        raw?.takeUnless { missingUri && !awaitArtworkMetadata }?.display(isDark, keepDark)
    }
}
