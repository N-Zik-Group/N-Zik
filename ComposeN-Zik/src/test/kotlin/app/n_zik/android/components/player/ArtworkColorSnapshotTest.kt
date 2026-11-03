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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArtworkColorSnapshotTest {
    private fun bitmap(color: Int) = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    // JUnit's synchronous entry point waits for the production MEDIA extraction.
    @Test fun `one quantization preserves both original tones and derives dark neutral display roles`() = runBlocking {
        for (color in listOf(0xffebE9e4.toInt(), 0xff383634.toInt(), 0xff1e78c8.toInt())) {
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
            for (dark in listOf(false, true)) {
                val original = requireNotNull(extractCoverPalette(source, dark))
                assertEquals(original.palette, raw.display(dark, false).palette)
                assertEquals(original.colors, raw.display(dark, false).colors)
                val forced = raw.display(dark, true)
                if (raw.isMonochrome) {
                    assertTrue(forced.forceDark)
                    assertTrue(requireNotNull(forced.palette).isDark)
                    assertTrue(requireNotNull(forced.background).luminance() < .2f)
                    assertTrue(requireNotNull(forced.foreground).luminance() > .6f)
                    assertNotEquals(forced.background, forced.foreground)
                } else assertEquals(raw.display(dark, false), forced)
                assertEquals(original.palette, raw.display(dark, false).palette)
            }
        }
    }

    // JUnit's synchronous entry point waits for the production MEDIA extraction.
    @Test fun `white and black recover once centrally while off retains filtered palette fallback`() = runBlocking {
        for (color in listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK)) {
            var calls = 0
            val raw = analyzeArtworkColors(bitmap(color)) { image, unfiltered ->
                calls++
                Palette.from(image).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
            }
            assertEquals(2, calls)
            assertEquals(null, raw.display(false, false).palette)
            val recovered = requireNotNull(raw.display(false, false).dominant)
            if (color == android.graphics.Color.WHITE) assertTrue(recovered.luminance() > .9f)
            else assertTrue(recovered.luminance() < .01f)
            assertFalse(raw.display(false, false).forceDark)
            for (dark in listOf(false, true)) {
                val display = raw.display(dark, true)
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
            val key = ArtworkColorKey("default", "same-cover", "1", "HIGH", 0)
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
            assertEquals(raw.display(dark, false), raw.display(dark, true))
            assertEquals(coverPaletteFromSwatches(palette, dark)?.palette, raw.display(dark, false).palette)
        }
    }
}
