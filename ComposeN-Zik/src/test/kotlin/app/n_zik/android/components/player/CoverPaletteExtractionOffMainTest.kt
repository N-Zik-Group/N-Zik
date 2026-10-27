package app.n_zik.android.components.player

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
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
class CoverPaletteExtractionOffMainTest {
    private fun cover(dominant: Int, accent: Int): Bitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(32 * 32) { if (it < 716) dominant else accent }
            setPixels(pixels, 0, 32, 0, 0, 32, 32)
        }

    @Test
    fun `one MEDIA quantization supplies both cover swatches and the exact reference palette`() = runBlocking {
        val callerThread = Thread.currentThread().name
        val fixtures = listOf(
            cover(Color.rgb(150, 110, 170), Color.rgb(100, 30, 200)),
            cover(Color.rgb(150, 148, 146), Color.rgb(200, 30, 30)),
            cover(Color.rgb(30, 120, 200), Color.rgb(30, 120, 200)),
        )
        for (bitmap in fixtures) for (isDark in listOf(false, true)) {
            var quantizations = 0
            var extractionThread: String? = null
            val result = requireNotNull(extractCoverPalette(bitmap, isDark) {
                quantizations++
                extractionThread = Thread.currentThread().name
                Palette.from(it).maximumColorCount(8).generate()
            })

            assertEquals(1, quantizations)
            assertNotEquals(callerThread, extractionThread)
            assertTrue(requireNotNull(extractionThread).startsWith("nzik-media-"))
            val reference = requireNotNull(dynamicColorPaletteOf(bitmap, isDark))
            assertEquals(reference, result.palette)
            assertEquals(reference, m3eDynamicColorPaletteOf(bitmap, isDark))
            assertEquals(result.colors, extractM3ECoverColors(bitmap, isDark))

            val swatches = Palette.from(bitmap).maximumColorCount(8).generate()
            val fallback = reference.accent.toArgb()
            assertEquals(
                M3ECoverColors(
                    swatches.getDominantColor(fallback),
                    swatches.getVibrantColor(fallback),
                    swatches.getLightVibrantColor(fallback),
                    swatches.getDarkVibrantColor(fallback),
                    swatches.getMutedColor(fallback),
                    swatches.getLightMutedColor(fallback),
                    swatches.getDarkMutedColor(fallback),
                ),
                result.colors,
            )
        }
    }

    @Test
    fun `neutral ramps and empty covers preserve their previous results with one quantization`() = runBlocking {
        for (color in listOf(Color.rgb(235, 233, 228), Color.rgb(58, 56, 54), Color.rgb(128, 126, 124))) {
            val bitmap = cover(color, color)
            for (isDark in listOf(false, true)) {
                var quantizations = 0
                val result = requireNotNull(extractCoverPalette(bitmap, isDark) {
                    quantizations++
                    Palette.from(it).maximumColorCount(8).generate()
                })
                assertEquals(1, quantizations)
                assertTrue(result.colors.allAchromatic)
                assertEquals(m3eDominantDynamicPaletteOf(result.colors, bitmap, isDark), result.palette)
            }
        }
        // Palette's default filter rejects pure white and black as well as this empty cover.
        for (color in listOf(Color.WHITE, Color.BLACK, Color.TRANSPARENT)) {
            val filtered = cover(color, color)
            for (isDark in listOf(false, true)) {
                assertNull(dynamicColorPaletteOf(filtered, isDark))
                var quantizations = 0
                assertNull(extractCoverPalette(filtered, isDark) {
                    quantizations++
                    Palette.from(it).maximumColorCount(8).generate()
                })
                assertEquals(1, quantizations)
            }
        }
    }
}
