package app.n_zik.android.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.n_zik.android.components.player.m3eDynamicColorPaletteOf
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 M4 — `NZikWidgetManager.updateWidgets()` now wraps palette extraction and bitmap
 * work (scaling, rounding, circling) in `withContext(NzikDispatchers.MEDIA)` instead of running
 * inline on whatever dispatcher the caller used (`Dispatchers.Main` for the idle widget path via
 * the `AppWidgetProvider`s, raw `Dispatchers.IO` for the playing path via
 * `PlayerServiceModern.updateWidgets()`). `extractPalette` (widened from `private` to
 * `internal` for testability) now delegates its fallback to the app's shared M3E extraction
 * `m3eDynamicColorPaletteOf` (RiPlay-based, achromatic neutralization included), so this test
 * asserts both the offload (the same bitmap yields the same palette whether called directly or
 * dispatched to MEDIA, and the dispatched call actually runs on a `nzik-media-*` thread) and
 * the app/widget palette parity of the fallback path.
 *
 * Robolectric is required (not a plain JVM unit test) because [NZikWidgetManager.extractPalette]
 * reads `Context.resources.configuration` and `Context.getSharedPreferences`, which need a real
 * Android environment to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NZikWidgetManagerExtractPaletteOffMainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Records which thread called [getSharedPreferences] on this context, then delegates to the
     * real base context. `extractPalette` reads shared prefs synchronously as its first `Context`
     * access, so wrapping the context passed into the real, untouched production entry point
     * (`updateWidgets`) with this observes the actual thread `updateWidgets`' own
     * `withContext(NzikDispatchers.MEDIA)` block dispatches to -- unlike calling `extractPalette`
     * directly under a test-written `withContext`, which would stay green even if production
     * dropped its offload.
     */
    private class ThreadProbingContext(base: Context) : ContextWrapper(base) {
        @Volatile
        var sharedPreferencesThreadName: String? = null

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            sharedPreferencesThreadName = Thread.currentThread().name
            return super.getSharedPreferences(name, mode)
        }
    }

    @Test
    fun `updateWidgets dispatches its internal work to a nzik-media thread, not the caller's thread`() = runBlocking {
        val probeContext = ThreadProbingContext(context)
        val callerThreadName = Thread.currentThread().name
        val bitmap = solidBitmap(Color.rgb(50, 150, 250))

        // No widgets registered under Robolectric -> both internal `updateAppWidget` loops are
        // skipped, but palette extraction (and thus the `getSharedPreferences` probe) runs
        // unconditionally before that check, from inside the real `withContext(MEDIA)` block.
        NZikWidgetManager.updateWidgets(
            context = probeContext,
            title = "Title",
            artist = "Artist",
            artworkBitmap = bitmap,
            isPlaying = false,
            isLiked = false,
        )

        val threadName = probeContext.sharedPreferencesThreadName
        assertTrue("expected extractPalette's getSharedPreferences call to have been observed", threadName != null)
        assertNotEquals(
            "updateWidgets' internal work must not run on the caller's (UI-simulating) thread",
            callerThreadName,
            threadName
        )
        assertTrue(
            "expected nzik-media-* but was $threadName",
            threadName!!.startsWith("nzik-media-")
        )
    }

    @Test
    fun `extractPalette dispatched to MEDIA returns the same result as calling it directly`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(180, 90, 40))

        val direct = NZikWidgetManager.extractPalette(context, bitmap)
        val offloaded = withContext(NzikDispatchers.MEDIA) { NZikWidgetManager.extractPalette(context, bitmap) }

        assertEquals(direct, offloaded)
    }

    @Test
    fun `extractPalette runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(20, 140, 210))
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            NZikWidgetManager.extractPalette(context, bitmap)
            Thread.currentThread().name
        }

        assertNotEquals(
            "extractPalette must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }

    @Test
    fun `extractPalette dispatched to MEDIA with null bitmap returns the default palette, same as direct call`() = runBlocking {
        val direct = NZikWidgetManager.extractPalette(context, null)
        val offloaded = withContext(NzikDispatchers.MEDIA) { NZikWidgetManager.extractPalette(context, null) }

        assertEquals(direct, offloaded)
    }

    /**
     * App/widget parity (acceptance criterion 3): with no palette saved by the app (fresh
     * Robolectric prefs), `extractPalette` must take the fallback path and return exactly the
     * app's shared M3E extraction `m3eDynamicColorPaletteOf` for the same bitmap — RiPlay-based
     * (capped 8 palette, dominant swatch) including the achromatic neutralization.
     *
     * Uses the same solid blue cover as [app.n_zik.android.components.player.CoverPaletteExtractorTest]:
     * in this Robolectric environment androidx.palette yields no swatch for solid warm-color
     * bitmaps (dominantSwatch is null), so the shared extraction would return null here — a
     * test-environment artifact, not a product regression (the app and widget both call the
     * same function, which is what this parity assertion covers).
     */
    @Test
    fun `extractPalette fallback equals the app m3eDynamicColorPaletteOf for the same bitmap`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(30, 120, 200))
        val isSystemInDarkMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val expected = m3eDynamicColorPaletteOf(bitmap, isSystemInDarkMode)
        assertNotNull("expected a non-null palette from the app's shared extraction", expected)

        // No `widget_palette_*` prefs under Robolectric -> `extractPalette` takes the fallback.
        val actual = NZikWidgetManager.extractPalette(context, bitmap)

        assertEquals(
            "the widget fallback must be the app's shared M3E extraction for the same cover",
            expected,
            actual,
        )
    }

    /**
     * Arithmetic mean of the three RGB channels, normalized to [0, 1] — a cheap tone check for
     * the pure ARGB helpers below. NOT a WCAG/perceptual luminance (no gamma correction).
     */
    private fun relativeLuminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3f / 255f
    }

    /** A synthetic gray ramp pinned per-field, so the ARGB helpers can be asserted on tone. */
    private fun syntheticPalette(toneIsDark: Boolean): ColorPalette {
        fun gray(lightness: Float) = androidx.compose.ui.graphics.Color.hsl(0f, 0f, lightness)
        return ColorPalette(
            background0 = gray(if (toneIsDark) 0.1f else 0.925f),
            background1 = gray(if (toneIsDark) 0.15f else 0.9f),
            background2 = gray(if (toneIsDark) 0.2f else 0.85f),
            background3 = gray(if (toneIsDark) 0.3f else 0.955f),
            background4 = gray(if (toneIsDark) 0.2f else 0.955f),
            accent = gray(0.5f),
            onAccent = if (toneIsDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
            text = gray(if (toneIsDark) 0.88f else 0.12f),
            textSecondary = gray(if (toneIsDark) 0.65f else 0.4f),
            textDisabled = gray(if (toneIsDark) 0.4f else 0.65f),
            isDark = toneIsDark,
            iconButtonPlayer = gray(if (toneIsDark) 0.88f else 0.12f),
        )
    }

    @Test
    fun `widgetTextArgb and widgetIconTintArgb follow the effective palette tone, not the system theme`() {
        val lightTone = syntheticPalette(toneIsDark = false)
        val darkTone = syntheticPalette(toneIsDark = true)

        // The pure helpers resolve exactly the palette's own fields.
        assertEquals(lightTone.text.toArgb(), widgetTextArgb(lightTone))
        assertEquals(darkTone.text.toArgb(), widgetTextArgb(darkTone))
        assertEquals(lightTone.iconButtonPlayer.toArgb(), widgetIconTintArgb(lightTone))
        assertEquals(darkTone.iconButtonPlayer.toArgb(), widgetIconTintArgb(darkTone))

        // A light tone must yield dark ARGBs and a dark tone light ARGBs -- no hardcoded
        // black/white keyed on isSystemInDarkMode (spec-achromatic-ramp-luminance-cap).
        assertTrue("light-tone text must be a dark ARGB", relativeLuminance(widgetTextArgb(lightTone)) < 0.5f)
        assertTrue("dark-tone text must be a light ARGB", relativeLuminance(widgetTextArgb(darkTone)) > 0.5f)
        assertTrue("light-tone icon tint must be a dark ARGB", relativeLuminance(widgetIconTintArgb(lightTone)) < 0.5f)
        assertTrue("dark-tone icon tint must be a light ARGB", relativeLuminance(widgetIconTintArgb(darkTone)) > 0.5f)
    }
}
