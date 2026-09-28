package app.n_zik.android.legacyoffmain.player

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.screens.player.computePlayerDynamicPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.components.player.M3ECoverColors
import app.n_zik.android.components.player.extractM3ECoverColors
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
 * Issue #606 H10 (Lot 2, AD-4 tier 2 legacy in-place patch) -- `Player.kt`'s cover-swipe
 * `LaunchedEffect(currentSwipedMediaItem.mediaId, updateBrush)` used to run
 * `dynamicColorPaletteOf(bitmap, ...)`, `Palette.from(bitmap).generate()`, and 6
 * `Palette.get*Color` extractions inline, on whatever dispatcher that effect resumes on (Main).
 * That whole CPU-bound sequence is now extracted, unchanged, to [computePlayerDynamicPalette]
 * (declared `internal` in `Player.kt`, `app.it.fast4x.rimusic.*`, legacy) and dispatched in one
 * `withContext(NzikDispatchers.MEDIA)` call. The extraction and the local dynamic palette now
 * delegate to the shared M3E cover extraction (`extractM3ECoverColors` +
 * `m3eDominantDynamicPaletteOf`, `app.n_zik.android.components.player`) -- RiPlay-based: capped
 * 8 palette, **dominant** swatch, achromatic neutralization -- the same dominant-based
 * construction the app uses (`m3eDynamicColorPaletteOf`), so the player stays in parity with
 * the app (spec `spec-fix-palette-extractor-riplay-reference`, OQ1=A/OQ2=B).
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per the
 * gh-606 Lot 2 spec's "no new file under app.it.fast4x.rimusic.*" boundary; `internal` visibility
 * is module-scoped in Kotlin, not package-scoped, so it still resolves
 * [computePlayerDynamicPalette] from here without needing to share its package.
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch -- the same technique
 * `NZikWidgetManagerExtractPaletteOffMainTest` (Lot 1, M4) uses for an equivalent palette
 * extraction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerDynamicPaletteOffMainTest {

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    @Test
    fun `computePlayerDynamicPalette matches the dominant-based RiPlay reference and the shared M3E extractor's swatches`() = runBlocking {
        // Solid blue bitmap: every swatch is the same color, so the expected values below are
        // exact (no get*Color fallback is used).
        val bitmap = solidBitmap(Color.rgb(30, 120, 200))
        val fallback = DefaultDarkColorPalette

        // A solid bitmap always yields a dominant swatch; the test's premise otherwise.
        val reference = dynamicColorPaletteOf(bitmap, false)!!
        val swatchPalette = Palette.from(bitmap).maximumColorCount(8).generate()
        val dominant = swatchPalette.getDominantColor(reference.accent.toArgb())
        val dominantHsl = FloatArray(3).apply { colorToHSL(dominant, this) }
        val expectedPalette = dynamicColorPaletteOf(dominantHsl, false)

        val result = computePlayerDynamicPalette(bitmap, false, fallback)

        assertEquals(expectedPalette, result.palette)
        assertEquals(
            "the player's local palette must be the RiPlay reference (dominant-based)",
            reference,
            result.palette,
        )
        assertEquals(
            "swatches must be identical to the shared M3E extractor (same fallback accent)",
            extractM3ECoverColors(bitmap, false),
            M3ECoverColors(
                result.dominant,
                result.vibrant,
                result.lightVibrant,
                result.darkVibrant,
                result.muted,
                result.lightMuted,
                result.darkMuted,
            )
        )
    }

    @Test
    fun `computePlayerDynamicPalette dispatched to MEDIA runs on a nzik-media thread, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(30, 120, 200))
        val fallback = DefaultDarkColorPalette
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            computePlayerDynamicPalette(bitmap, true, fallback)
            Thread.currentThread().name
        }

        assertNotEquals(
            "computePlayerDynamicPalette must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }
}
