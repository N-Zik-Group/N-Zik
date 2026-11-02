package app.n_zik.android.components.player

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey

/** An absent landscape override follows portrait without materializing a preference. */
@Composable
fun rememberPlayerLandscapeArtworkSize(): MutableState<Float> {
    val preferences = LocalContext.current.preferences
    val state = remember(preferences) { LandscapeArtworkSizeState(preferences) }
    DisposableEffect(preferences, state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == thumbnailSizeDpKey || key == thumbnailSizeLDpKey) {
                state.refresh()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.refresh()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

private class LandscapeArtworkSizeState(private val preferences: SharedPreferences) : MutableState<Float> {
    private val observed = mutableStateOf(read())

    private fun read(): Float = preferences.getFloat(
        thumbnailSizeLDpKey,
        preferences.getFloat(thumbnailSizeDpKey, 90f),
    )

    fun refresh() { observed.value = read() }

    override var value: Float
        get() = observed.value
        set(value) {
            preferences.edit().putFloat(thumbnailSizeLDpKey, value).apply()
            refresh()
        }

    override fun component1(): Float = value
    override fun component2(): (Float) -> Unit = { value = it }
}
