package app.n_zik.android.components.theme

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.getEnum
import app.n_zik.android.components.player.ArtworkColorSnapshot
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Owns current-artwork extraction and reversible live brightness updates. */
internal class DynamicAppearanceOwner(
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    private val systemIsDark: () -> Boolean,
    private val loadPalette: suspend (String, Boolean) -> ColorPalette?,
    private val appearance: () -> Appearance,
    private val publish: (Appearance, Boolean) -> Unit,
    private val systemBars: (Boolean) -> Unit,
    private val saveWidget: (ColorPalette) -> Unit,
    private val extractionDispatcher: CoroutineDispatcher = NzikDispatchers.DATA,
    private val loadArtwork: (suspend (String) -> ArtworkColorSnapshot?)? = null,
) {
    private data class Artwork(val url: String?, val isDark: Boolean)
    private var currentArtwork: Artwork? = null
    private var extractedArtwork: Artwork? = null
    private var extractedPalette: ColorPalette? = null
    private var extractedSnapshot: ArtworkColorSnapshot? = null
    private var job: Job? = null
    private var request = 0L
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == dynamicThemeRespectBrightnessKey && isDynamic()) {
            val current = currentArtwork
            if (current != null && current == extractedArtwork) {
                cancel()
                extractedPalette?.let { publishPalette(it, true) }
            }
            // Pending extraction reads the latest preference when it publishes.
        }
    }

    fun start() = preferences.registerOnSharedPreferenceChangeListener(listener)

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        cancel()
    }

    fun cancel() {
        request++
        job?.cancel()
        job = null
    }

    fun refresh(url: String?, animate: Boolean = false) {
        cancel()
        if (!isDynamic()) return
        val mode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
        val dark = mode == ColorPaletteMode.Dark || mode == ColorPaletteMode.PitchBlack ||
            (mode == ColorPaletteMode.System && systemIsDark())
        val artwork = Artwork(url, dark)
        currentArtwork = artwork
        if (loadArtwork != null || artwork != extractedArtwork) {
            extractedArtwork = null
            extractedPalette = null
            extractedSnapshot = null
        }
        if (url.isNullOrEmpty() || url.contains("ic_launcher_box")) {
            publishPalette(fallback(dark), animate)
            return
        }
        val version = request
        job = scope.launch {
            try {
                val snapshot = withContext(extractionDispatcher) { loadArtwork?.invoke(url) }
                val raw = if (loadArtwork == null) withContext(extractionDispatcher) { loadPalette(url, dark) }
                    else snapshot?.display(dark, false)?.palette
                coroutineContext.ensureActive()
                if (version != request || !isDynamic()) return@launch
                extractedArtwork = artwork
                extractedPalette = raw ?: fallback(dark)
                extractedSnapshot = snapshot
                publishPalette(extractedPalette ?: fallback(dark), animate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("DynamicAppearanceOwner").e(e, "Error loading appearance")
            }
        }
    }

    private fun isDynamic() =
        preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic) == ColorPaletteName.Dynamic

    private fun fallback(dark: Boolean) =
        dynamicColorPaletteOf(Color(0.54509807f, 0.36078432f, 0.9647059f), dark)

    private fun publishPalette(raw: ColorPalette, animate: Boolean) {
        val currentArtwork = currentArtwork ?: return
        val keepDark = preferences.getBoolean(dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
        val palette = extractedSnapshot?.display(currentArtwork.isDark, keepDark)?.palette
            ?: raw.keepMonochromeColorsDark(keepDark)
        val current = appearance()
        val next = current.copy(colorPalette = palette, typography = current.typography.withColor(palette.text))
            .forPaletteMode(preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark))
        systemBars(next.colorPalette.isDark)
        if (current != next) publish(next, animate)
        saveWidget(next.colorPalette)
    }
}
