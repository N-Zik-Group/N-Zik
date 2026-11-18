package app.n_zik.android.components.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import app.n_zik.android.components.glass.measuredPlayerTextForeground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerBackdropSamplingTest {
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
