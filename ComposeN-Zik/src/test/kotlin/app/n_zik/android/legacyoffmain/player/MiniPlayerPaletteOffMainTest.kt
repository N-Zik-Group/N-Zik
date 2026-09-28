package app.n_zik.android.legacyoffmain.player

import android.graphics.Bitmap
import android.graphics.Color
import app.it.fast4x.rimusic.ui.screens.player.computeMiniPlayerPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
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
 * Issue #606 H10 (Lot 2, AD-4 tier 2 legacy in-place patch) -- `MiniPlayer.kt`'s
 * `LaunchedEffect(mediaItem.mediaId)` used to run `dynamicColorPaletteOf(bitmap, ...)`
 * (CPU-bound `Palette` extraction) inline, on whatever dispatcher that effect resumes on (Main,
 * since it follows `getBitmapFromUrl`'s suspension). That call is now extracted, unchanged, to
 * [computeMiniPlayerPalette] (declared `internal` in `MiniPlayer.kt`, `app.it.fast4x.rimusic.*`,
 * legacy) and dispatched via `withContext(NzikDispatchers.MEDIA)`. The palette is built from
 * the dominant swatch via `m3eDynamicColorPaletteOf` (shared M3E cover extraction, RiPlay-based:
 * capped 8 palette, dominant swatch, achromatic neutralization -- spec
 * `spec-fix-palette-extractor-riplay-reference`).
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per the
 * gh-606 Lot 2 spec's "no new file under app.it.fast4x.rimusic.*" boundary; `internal` visibility
 * is module-scoped in Kotlin, not package-scoped, so it still resolves [computeMiniPlayerPalette]
 * from here without needing to share its package.
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch -- the same technique
 * `DynamicColorPaletteOffMainTest` (Lot 1, M6) uses for the same underlying legacy function.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MiniPlayerPaletteOffMainTest {

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    @Test
    fun `computeMiniPlayerPalette returns the same dominant-based result as m3eDynamicColorPaletteOf directly`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(200, 60, 60))

        // For a colored cover the app's palette is the RiPlay reference (dominant-based); on a
        // solid bitmap the dominant and vibrant swatches coincide, so the reference is exact.
        val reference = dynamicColorPaletteOf(bitmap, false)

        val direct = m3eDynamicColorPaletteOf(bitmap, false)
        val extracted = computeMiniPlayerPalette(bitmap, false)

        assertNotNull("expected a palette from the RiPlay reference", reference)
        assertEquals(reference, extracted)
        assertEquals(direct, extracted)
    }

    @Test
    fun `computeMiniPlayerPalette dispatched to MEDIA runs on a nzik-media thread, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(30, 120, 200))
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            computeMiniPlayerPalette(bitmap, true)
            Thread.currentThread().name
        }

        assertNotEquals(
            "computeMiniPlayerPalette must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }
}
