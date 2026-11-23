package app.n_zik.android.components.theme

import android.content.SharedPreferences
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.components.player.ARTWORK_METADATA_GRACE_MS
import app.n_zik.android.components.player.ArtworkDisplayColors
import app.n_zik.android.components.player.ArtworkColorSnapshot
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val publishArtwork: ((ArtworkDisplayColors?, Boolean) -> Unit)? = null,
) {
    private data class Artwork(val url: String?, val isDark: Boolean)
    private var currentArtwork: Artwork? = null
    private var extractedArtwork: Artwork? = null
    private var extractedPalette: ColorPalette? = null
    private var extractedSnapshot: ArtworkColorSnapshot? = null
    private var publishedMode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
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

    fun refresh(
        url: String?,
        animate: Boolean = false,
        awaitArtworkMetadata: Boolean = false,
        shouldAnimate: () -> Boolean = { animate },
    ) {
        cancel()
        if (!isDynamic()) return
        val mode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
        val dark = mode == ColorPaletteMode.Dark || (mode == ColorPaletteMode.PitchBlack || mode == ColorPaletteMode.PureBlack) ||
            (mode == ColorPaletteMode.System && systemIsDark())
        val artwork = Artwork(url, dark)
        currentArtwork = artwork
        if (loadArtwork != null || artwork != extractedArtwork) {
            extractedArtwork = null
            extractedPalette = null
            extractedSnapshot = null
        }
        if (url.isNullOrBlank() || url.contains("ic_launcher_box")) {
            if (awaitArtworkMetadata) {
                job = scope.launch {
                    // Media3 may publish the artwork URI in a separate metadata event.
                    delay(ARTWORK_METADATA_GRACE_MS)
                    if (isDynamic()) publishPalette(dynamicArtworkFallback(dark), shouldAnimate())
                }
            } else {
                publishPalette(dynamicArtworkFallback(dark), shouldAnimate())
            }
            return
        }
        val version = request
        job = scope.launch {
            val (snapshot, raw) = try {
                val snapshot = withContext(extractionDispatcher) { loadArtwork?.invoke(url) }
                val raw = if (loadArtwork == null) withContext(extractionDispatcher) { loadPalette(url, dark) }
                    else snapshot?.display(dark, false)?.palette
                snapshot to raw
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("DynamicAppearanceOwner").e(e, "Error loading appearance")
                null to null
            }
            coroutineContext.ensureActive()
            if (version != request || !isDynamic()) return@launch
            val palette = raw ?: dynamicArtworkFallback(dark)
            extractedArtwork = artwork
            extractedPalette = palette
            extractedSnapshot = snapshot
            publishPalette(palette, shouldAnimate())
        }
    }

    private fun isDynamic() =
        preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic) == ColorPaletteName.Dynamic

    private fun publishPalette(raw: ColorPalette, animate: Boolean) {
        val currentArtwork = currentArtwork ?: return
        val keepDark = preferences.getBoolean(dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
        val display = extractedSnapshot?.display(currentArtwork.isDark, keepDark)
        val palette = display?.palette ?: raw.keepMonochromeColorsDark(keepDark)
        val current = appearance()
        val mode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
        val next = current.copy(colorPalette = palette, typography = current.typography.withColor(palette.text))
            .forPaletteMode(mode)
        systemBars(next.colorPalette.isDark)
        if (current != next || mode != publishedMode) {
            publishedMode = mode
            publish(next, animate)
        }
        publishArtwork?.invoke(display, animate)
        saveWidget(next.colorPalette)
    }
}
