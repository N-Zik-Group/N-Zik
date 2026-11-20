package app.n_zik.android.components.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.unit.IntSize
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.effectiveSurfaceColor
import app.n_zik.android.components.glass.measuredPlayGlyphForeground
import app.n_zik.android.components.glass.measuredPlayerTextForeground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerBackdropSamplingTest {
    @Test fun `contrast refresh requires a different shared or regional polarity`() {
        val previous = PlayerBackdropContrastDecision(Color.White, mapOf("controls" to Color.Black))
        assertFalse(previous.copy().changesPolarity(previous))
        assertTrue(previous.copy(regionalInk = mapOf("controls" to Color.White)).changesPolarity(previous))
        assertTrue(previous.copy(commonInk = Color.Black).changesPolarity(previous))
    }

    @Test fun `sharp artwork behind play can need different ink from surrounding controls`() {
        val state = PlayerBackdropSampling()
        val controls = Rect(0f, 0f, 320f, 640f)
        val playGlyph = Rect(140f, 300f, 180f, 340f)
        state.registerRegion("controls", controls)
        state.registerRegion("play", playGlyph)
        val pixels = IntArray(32 * 64) {
            if (it % 32 in 14..17 && it / 32 in 30..33) 0xFF000000.toInt() else -1
        }
        state.publish(pixels, Offset.Zero, IntSize(320, 640))
        val film = Color.White.copy(alpha = .12f)
        val wholePlate = effectiveSurfaceColor(requireNotNull(state.foregroundSurface), film)
        val playPlate = effectiveSurfaceColor(requireNotNull(state.colorAt(playGlyph)), film)
        assertEquals(Color.Black, measuredPlayGlyphForeground(Color.Black, wholePlate, 3f))
        assertEquals(Color.White, measuredPlayGlyphForeground(Color.Black, playPlate, 3f))
        assertEquals(Color.Yellow, measuredPlayGlyphForeground(Color.Yellow, playPlate, 3f))
        assertEquals(Color.White, state.contrastDecision()?.regionalInk?.get("play"))
    }

    @Test fun `play tint stays the same hue on dark and light artwork`() {
        val cases = listOf(Color(0xFF190C34) to Color(0xFF1F1F1F), Color(0xFFE0BCD0) to Color(0xFFF4F4F4))
        for ((accent, surface) in cases) {
            val glyph = measuredPlayGlyphForeground(accent, surface, 3f)
            val before = FloatArray(3).also { ColorUtils.colorToHSL(accent.toArgb(), it) }
            val after = FloatArray(3).also { ColorUtils.colorToHSL(glyph.toArgb(), it) }
            assertTrue(contrastAgainst(glyph, surface) >= 3f)
            assertEquals(before[0], after[0], 2f)
            assertEquals(before[1], after[1], .025f)
            assertEquals(accent.alpha, glyph.alpha, 0f)
        }
    }

    @Test fun `title and artist select ink from their own artwork stripe in Normal`() {
        val state = PlayerBackdropSampling()
        val title = Rect(90f, 250f, 230f, 280f)
        val artist = Rect(90f, 300f, 230f, 330f)
        state.registerRegion("title", title, Color.Transparent)
        state.registerRegion("artist", artist, Color.Transparent)
        val pixels = IntArray(32 * 64) { if (it / 32 < 29) 0xFF181818.toInt() else -1 }
        state.publish(pixels, Offset.Zero, IntSize(320, 640))
        val inactiveTitle = Color(0xFF6C6C6C)
        val titleInk = measuredPlayerTextForeground(inactiveTitle, requireNotNull(state.colorAt(title, Color.Transparent)))
        val artistInk = measuredPlayerTextForeground(Color.White, requireNotNull(state.colorAt(artist, Color.Transparent)))
        assertEquals(Color.White, titleInk)
        assertEquals(Color.Black, artistInk)
    }

    @Test fun `glass film can change regional polarity while the artwork stays dark`() {
        val bounds = Rect(0f, 0f, 100f, 200f)
        val film = Color.White.copy(alpha = .28f)
        fun decision(artwork: Int): PlayerBackdropContrastDecision {
            val state = PlayerBackdropSampling()
            state.registerRegion("play", bounds, film)
            state.publish(IntArray(32 * 64) { artwork }, Offset.Zero, IntSize(100, 200))
            return requireNotNull(state.contrastDecision())
        }
        val dark = decision(0xFF000000.toInt())
        val light = decision(0xFF464646.toInt())
        assertEquals(dark.commonInk, light.commonInk)
        assertTrue(light.changesPolarity(dark))
    }

    @Test fun `preview updates each region without freezing the intermediate frame`() {
        val state = PlayerBackdropSampling(previousSurface = Color.Gray)
        assertFalse(state.hasPreviewMeasurement)
        val top = Rect(0f, 0f, 100f, 100f)
        val bottom = Rect(0f, 100f, 100f, 200f)
        val size = IntSize(100, 200)
        state.registerRegion("controls", bottom)
        val pixels = IntArray(32 * 64) { if (it / 32 < 32) -1 else 0xFF000000.toInt() }
        state.publish(pixels, Offset.Zero, size, settled = false)
        assertEquals(Color.White, state.colorAt(top))
        assertEquals(Color.Black, state.foregroundSurface)
        assertTrue(state.hasPreviewMeasurement)
        assertFalse(state.finished)

        state.publish(IntArray(32 * 64) { -1 }, Offset.Zero, size)
        assertEquals(Color.White, state.colorAt(bottom))
        assertEquals(Color.White, state.commonSurface)
        assertTrue(state.finished)
    }
    @Test fun `text takes the darker part of a mixed band into account before choosing ink`() {
        val size = IntSize(8, 1)
        val region = Rect(0f, 0f, 1f, 1f)
        val pixels = intArrayOf(0xff686247.toInt(), 0xff686247.toInt(), 0xff686247.toInt(),
            0xff827a65.toInt(), 0xff827a65.toInt(), 0xff8b8167.toInt(), 0xff8b8167.toInt(), 0xffa09578.toInt())
        val bare = requireNotNull(averagePlayerBackdrop(pixels, size, region, textFilm = Color.Transparent))
        val glass = requireNotNull(averagePlayerBackdrop(pixels, size, region, textFilm = Color.White.copy(alpha = .7f)))
        assertEquals(Color.White, measuredPlayerTextForeground(Color.Black, bare))
        assertEquals(Color.Black, measuredPlayerTextForeground(Color.White, glass))
    }

    @Test fun `rounded transparent corners allow opaque background but sparse samples are rejected`() {
        val pixels = IntArray(100) { 0xFF900000.toInt() }
        pixels[0] = 0
        pixels[9] = 0
        pixels[90] = 0
        pixels[99] = 0
        assertEquals(Color(0xFF900000), averagePlayerBackdrop(pixels, IntSize(10, 10), Rect(0f, 0f, 1f, 1f)))
        for (index in 0 until 10) pixels[index] = 0
        assertNull(averagePlayerBackdrop(pixels, IntSize(10, 10), Rect(0f, 0f, 1f, 1f)))
        pixels[90] = 0xFF900000.toInt()
        pixels[99] = 0xFF900000.toInt()
        assertEquals(Color(0xFF900000), averagePlayerBackdrop(pixels, IntSize(10, 10), Rect(0f, 0f, 1f, 1f)))
    }

    @Test fun `all registered regions share one decision that survives animation and pause`() {
        val state = PlayerBackdropSampling()
        state.registerRegion("title", Rect(0f, 0f, 100f, 100f))
        state.registerRegion("transport", Rect(0f, 100f, 100f, 200f))
        val pixels = IntArray(32 * 64) { if (it / 32 < 32) 0xFF000000.toInt() else -1 }
        state.publish(pixels, Offset.Zero, IntSize(100, 200))
        assertTrue(state.hasDecision)
        assertEquals(Color(127, 127, 127), state.commonSurface)
        state.isActive = false
        state.publish(IntArray(32 * 64) { -1 }, Offset.Zero, IntSize(100, 200))
        assertEquals(Color(127, 127, 127), state.commonSurface)
    }

    @Test fun `transparent first snapshot retries until registered background is usable`() {
        val state = PlayerBackdropSampling()
        state.registerRegion("title", Rect(0f, 0f, 100f, 200f))
        state.publish(IntArray(32 * 64), Offset.Zero, IntSize(100, 200))
        assertFalse(state.hasDecision)
        state.publish(IntArray(32 * 64) { 0xFF900000.toInt() }, Offset.Zero, IntSize(100, 200))
        assertTrue(state.hasDecision)
        assertEquals(Color(0xFF900000), state.commonSurface)
    }

    @Test fun `foreground keeps the previous rendered surface until the next valid sample`() {
        val state = PlayerBackdropSampling(previousSurface = Color.Black)
        state.registerRegion("title", Rect(0f, 0f, 100f, 200f))
        assertFalse(state.hasDecision)
        assertEquals(Color.Black, state.foregroundSurface)

        state.publish(IntArray(32 * 64), Offset.Zero, IntSize(100, 200))
        assertFalse(state.hasDecision)
        assertEquals(Color.Black, state.foregroundSurface)

        state.publish(IntArray(32 * 64) { -1 }, Offset.Zero, IntSize(100, 200))
        assertTrue(state.hasDecision)
        assertEquals(Color.White, state.foregroundSurface)
    }

    @Test fun `foreground falls back to its theme after a failed background sample`() {
        val state = PlayerBackdropSampling(previousSurface = Color.Black)
        assertEquals(Color.Black, state.foregroundSurface)
        state.finished = true
        assertNull(state.foregroundSurface)
    }

    @Test fun `regions follow the rendered backdrop rather than the cover dominant`() {
        val red = 0xFF900000.toInt()
        val pale = 0xFFFFEEEE.toInt()
        val pixels = intArrayOf(pale, pale, red, red)
        assertEquals(Color(red), averagePlayerBackdrop(pixels, IntSize(2, 2), Rect(0f, .5f, 1f, 1f)))
        assertEquals(Color(pale), averagePlayerBackdrop(pixels, IntSize(2, 2), Rect(0f, 0f, 1f, .5f)))
    }

    @Test fun `window regions track animation frames and player offsets`() {
        val state = PlayerBackdropSampling()
        val pixels = IntArray(32 * 64) { index ->
            if (index / 32 < 32) 0xFF900000.toInt() else 0xFFFFEEEE.toInt()
        }
        state.publish(pixels, Offset(10f, 20f), IntSize(100, 200))
        val upper = Rect(10f, 20f, 110f, 120f)
        val lower = Rect(10f, 120f, 110f, 220f)
        assertEquals(Color(0xFF900000), state.colorAt(upper))
        assertEquals(Color(0xFFFFEEEE), state.colorAt(lower))
        state.publish(IntArray(32 * 64) { 0xFF123456.toInt() }, Offset(10f, 20f), IntSize(100, 200))
        assertEquals(Color(0xFF123456), state.colorAt(upper))
        state.isActive = false
        assertEquals(Color(0xFF123456), state.colorAt(lower))
    }

    @Test fun `empty outside and unrecorded regions have no invented color`() {
        assertNull(averagePlayerBackdrop(intArrayOf(0), IntSize(1, 1), Rect(0f, 0f, 1f, 1f)))
        assertNull(averagePlayerBackdrop(intArrayOf(-1), IntSize(1, 1), Rect(2f, 0f, 3f, 1f)))
        assertNull(averagePlayerBackdrop(intArrayOf(-1), IntSize(1, 1), Rect.Zero))
    }
}
