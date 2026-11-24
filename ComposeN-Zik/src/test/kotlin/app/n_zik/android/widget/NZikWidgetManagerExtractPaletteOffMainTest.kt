package app.n_zik.android.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.ui.styling.ColorPalette
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private class ThreadProbingContext(base: Context) : ContextWrapper(base) {
        @Volatile
        var sharedPreferencesThreadName: String? = null

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            sharedPreferencesThreadName = Thread.currentThread().name
            return super.getSharedPreferences(name, mode)
        }
    }

    @Test
    fun `updateWidgets skips palette work if no widget is installed`() = runBlocking {
        val probeContext = ThreadProbingContext(context)
        NZikWidgetManager.updateWidgets(
            context = probeContext,
            title = "Title",
            artist = "Artist",
            artworkBitmap = solidBitmap(Color.BLUE),
            isPlaying = false,
            likeState = null,
        )
        assertNull(probeContext.sharedPreferencesThreadName)
    }

    @Test
    fun `updateWidgets dispatches its internal work to a nzik-media thread, not the caller's thread`() = runBlocking {
        val probeContext = ThreadProbingContext(context)
        val callerThreadName = Thread.currentThread().name
        val bitmap = solidBitmap(Color.rgb(50, 150, 250))

        // Exercise the render path without binding launcher RemoteViews in this test.
        mockkObject(WidgetAppearance)
        try {
            every { WidgetAppearance.hasWidgets(probeContext) } returns true
            NZikWidgetManager.updateWidgets(
                context = probeContext,
                title = "Title",
                artist = "Artist",
                artworkBitmap = bitmap,
                isPlaying = false,
                likeState = null,
            )
        } finally {
            unmockkObject(WidgetAppearance)
        }

        val threadName = probeContext.sharedPreferencesThreadName
        assertTrue("expected the palette/profile preferences access to have been observed", threadName != null)
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
