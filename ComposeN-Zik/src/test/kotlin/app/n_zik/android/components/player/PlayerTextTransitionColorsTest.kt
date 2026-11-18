package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTextTransitionColorsTest {
    @Test
    fun `placeholder base keeps both native endpoints and follows displayed darkness`() {
        assertEquals(placeholderCompositeBase(false), placeholderCompositeBase(0f))
        assertEquals(placeholderCompositeBase(true), placeholderCompositeBase(1f))
        val before = placeholderCompositeBase(.49f)
        val after = placeholderCompositeBase(.51f)
        assertTrue(before.red > .1f && before.red < .9f)
        assertTrue(after.red > before.red && after.red - before.red < .03f)
    }

    @Test
    fun `queue halo follows intermediate measured ink without changing configured opacity`() {
        assertEquals(Color.White.copy(alpha = .65f), opposingTextOutlineColor(Color.Black, .65f))
        assertEquals(Color.Black.copy(alpha = .65f), opposingTextOutlineColor(Color.White, .65f))
        val before = opposingTextOutlineColor(Color(.49f, .49f, .49f), .65f)
        val after = opposingTextOutlineColor(Color(.51f, .51f, .51f), .65f)
        assertTrue(before.red - after.red > 0f && before.red - after.red < .03f)
        assertEquals(.65f, after.alpha, .005f)
    }

    @Test
    fun `reversing a displayed transition keeps its current placeholder and halo frame`() {
        val displayed = .37f
        val base = placeholderCompositeBase(displayed)
        val halo = opposingTextOutlineColor(base, .5f)
        val resumed = placeholderCompositeBase(displayed + .001f)
        val resumedHalo = opposingTextOutlineColor(resumed, .5f)
        assertEquals(base.red, resumed.red, .005f)
        assertEquals(halo.red, resumedHalo.red, .005f)
    }
}
