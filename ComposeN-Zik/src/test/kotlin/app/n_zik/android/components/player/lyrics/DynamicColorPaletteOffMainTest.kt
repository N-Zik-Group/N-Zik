package app.n_zik.android.components.player.lyrics

import android.graphics.Bitmap
import android.graphics.Color
import app.n_zik.android.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 M6 — `LyricsScreen.kt`'s `LaunchedEffect(bitmapCover, lightTheme)` now wraps
 * `dynamicColorPaletteOf(...)` in `withContext(NzikDispatchers.MEDIA)` instead of running it
 * inline on the composition/Main dispatcher. `dynamicColorPaletteOf`
 * (`app.n_zik.android.ui.styling.ColorPalette.kt`, legacy READ-ONLY) is untouched and wraps
 * `Palette.from(bitmap).generate()`, which has no UI-thread dependency, so the offload must
 * change neither its result nor correctness for the same bitmap — only which thread runs it.
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DynamicColorPaletteOffMainTest {

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    @Test
    fun `dynamicColorPaletteOf dispatched to MEDIA returns the same result as calling it directly`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(200, 60, 60))

        val direct = dynamicColorPaletteOf(bitmap, false)
        val offloaded = withContext(NzikDispatchers.MEDIA) { dynamicColorPaletteOf(bitmap, false) }

        assertEquals(direct, offloaded)
    }

    @Test
    fun `dynamicColorPaletteOf runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(30, 120, 200))
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            dynamicColorPaletteOf(bitmap, false)
            Thread.currentThread().name
        }

        assertNotEquals(
            "dynamicColorPaletteOf must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }
}
