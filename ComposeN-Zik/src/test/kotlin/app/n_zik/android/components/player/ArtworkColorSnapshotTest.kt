package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import app.n_zik.android.utils.coroutines.NzikDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArtworkColorSnapshotTest {
    private fun bitmap(color: Int) = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    // JUnit's synchronous entry point waits for the production MEDIA extraction.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `a colorless bitmap is normalized before Palette resizes it`() = runBlocking {
        val source = Bitmap.createBitmap(512, 256, Bitmap.Config.ALPHA_8).apply {
            eraseColor(android.graphics.Color.BLACK)
        }
        assertEquals(null, source.colorSpace)
        var analyzed: Bitmap? = null
        analyzeArtworkColors(source) { image, unfiltered ->
            analyzed = image
            assertNotNull(image.colorSpace)
            assertEquals(Bitmap.Config.ARGB_8888, image.config)
            assertEquals(128, image.width)
            assertEquals(64, image.height)
            Palette.from(image).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
        }
        assertTrue(requireNotNull(analyzed).isRecycled)
        assertFalse(source.isRecycled)
    }

    // JUnit's synchronous entry point waits for the production MEDIA extraction.
    @Test fun `one quantization preserves both original tones and derives dark neutral display roles`() = runBlocking {
        val fixtures = listOf(
            android.graphics.Color.rgb(235, 233, 228) to true,
            android.graphics.Color.rgb(56, 54, 52) to true,
            android.graphics.Color.rgb(30, 120, 200) to false,
        )
        for ((color, monochrome) in fixtures) {
            val source = bitmap(color)
            var calls = 0
            var thread = ""
            val raw = analyzeArtworkColors(source) { image, unfiltered ->
                calls++
                thread = Thread.currentThread().name
                Palette.from(image).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
            }
            assertEquals(1, calls)
            assertTrue(thread.startsWith("nzik-media-"))
            val shared = requireNotNull(SharedArtworkColors.fromBitmap(source))
            assertEquals(monochrome, shared.isMonochrome)
            for (dark in listOf(false, true)) {
                val original = shared.display(dark, keepDark = false)
                assertNotNull(original.palette)
                assertNotNull(original.colors)
                assertEquals(original, raw.display(dark, keepDark = false))
                val forced = raw.display(dark, true)
                if (raw.isMonochrome) {
                    assertTrue(forced.forceDark)
                    assertTrue(requireNotNull(forced.palette).isDark)
                    assertTrue(requireNotNull(forced.background).luminance() < .2f)
                    assertTrue(requireNotNull(forced.foreground).luminance() > .6f)
                    assertNotEquals(forced.background, forced.foreground)
                } else assertEquals(raw.display(dark, false), forced)
                assertEquals(original, raw.display(dark, keepDark = false))
            }
        }
    }

    // JUnit waits for the suspend production quantizer.
    @Test fun `transparent artwork has no filtered palette or swatches`() = runBlocking {
        val source = bitmap(android.graphics.Color.TRANSPARENT)
        val filterPasses = mutableListOf<Boolean>()
        val raw = analyzeArtworkColors(source) { image, unfiltered ->
            filterPasses += unfiltered
            Palette.from(image).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
        }
        assertEquals(listOf(false, true), filterPasses)
        assertEquals(null, raw.lightPalette)
        assertEquals(null, raw.darkPalette)
        assertEquals(null, raw.lightColors)
        assertEquals(null, raw.darkColors)
    }

    // JUnit's synchronous entry point waits for the production MEDIA extraction.
    @Test fun `extreme black has a neutral palette and black player surface in either brightness policy`() = runBlocking {
        for (color in listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK)) {
            var calls = 0
            val raw = analyzeArtworkColors(bitmap(color)) { image, unfiltered ->
                calls++
                Palette.from(image).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
            }
            assertEquals(2, calls)
            val recovered = requireNotNull(raw.display(false, false).dominant)
            val black = color == android.graphics.Color.BLACK
            if (black) assertEquals(Color.Black, recovered)
            else assertTrue(recovered.luminance() > .9f)
            assertEquals(listOf(recovered), raw.display(false, false).artworkSamples)
            for (dark in listOf(false, true)) {
                val normal = raw.display(dark, false)
                assertFalse(normal.forceDark)
                assertEquals(black, normal.isExtremeBlack)
                if (black) {
                    val palette = requireNotNull(normal.palette)
                    assertEquals(dark, palette.isDark)
                    assertEquals(palette.accent.red, palette.accent.blue, .001f)
                    assertEquals(Color.Black, normal.background)
                    assertEquals(Color.White, normal.foreground)
                } else {
                    assertEquals(null, normal.palette)
                }
                val display = raw.display(dark, true)
                assertEquals(listOf(recovered), display.artworkSamples)
                assertTrue(display.forceDark)
                assertTrue(requireNotNull(display.palette).isDark)
                assertNotEquals(display.foreground, display.background)
            }
            assertEquals(2, calls)
        }
    }
    // JUnit waits for concurrent production-dispatcher consumers and their shared MEDIA task.
    @Test fun `many consumers share actual bitmap loading and quantization across all policy combinations`() = runBlocking {
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        try {
            val repository = ArtworkColorRepository(scope)
            val key = ArtworkColorKey("default", "same-cover", "1", 0)
            val gate = CompletableDeferred<Unit>()
            val loads = AtomicInteger()
            val quantizations = AtomicInteger()
            val image = bitmap(0xffeBe9e4.toInt())
            val consumers = List(8) {
                async {
                    repository.load(key) {
                        loads.incrementAndGet()
                        gate.await()
                        analyzeArtworkColors(image) { source, unfiltered ->
                            quantizations.incrementAndGet()
                            Palette.from(source).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
                        }
                    }
                }
            }
            yield()
            gate.complete(Unit)
            consumers.awaitAll().forEach { snapshot ->
                for (dark in listOf(false, true)) for (enabled in listOf(false, true, false))
                    requireNotNull(snapshot).display(dark, enabled)
            }
            assertEquals(1, loads.get())
            assertEquals(1, quantizations.get())
        } finally { scope.cancel() }
    }
    // JUnit waits for the injected source analysis on the production MEDIA dispatcher.
    @Test fun `rare chromatic source swatch prevents monochrome forcing`() = runBlocking {
        val palette = Palette.from(listOf(
            Palette.Swatch(android.graphics.Color.GRAY, 1000),
            Palette.Swatch(android.graphics.Color.rgb(8, 0, 32), 1),
        ))
        val raw = analyzeArtworkColors(bitmap(android.graphics.Color.GRAY)) { _, _ -> palette }
        assertFalse(raw.isMonochrome)
        for (dark in listOf(false, true)) {
            val display = raw.display(dark, false)
            assertEquals(display, raw.display(dark, true))
            assertEquals(listOf(Color(android.graphics.Color.GRAY), Color(android.graphics.Color.rgb(8, 0, 32))), display.artworkSamples)
            assertEquals(coverPaletteFromSwatches(palette, dark)?.palette, raw.display(dark, false).palette)
        }
    }
}
