package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.lerpTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaletteFadeModelTest {

    private fun palette(accent: Color, dark: Boolean = true): ColorPalette =
        ColorPalette(
            background0 = Color.Black,
            background1 = Color(0xFF101010),
            background2 = Color(0xFF181818),
            background3 = Color(0xFF202020),
            background4 = Color(0xFF282828),
            accent = accent,
            onAccent = Color.White,
            text = Color.White,
            textSecondary = Color(0xFFB0B0B0),
            textDisabled = Color(0xFF606060),
            isDark = dark,
            iconButtonPlayer = Color.Gray,
        )

    private val paletteA = palette(Color(0xFFFF0000))
    private val paletteB = palette(Color(0xFF00FF00))
    private val paletteC = palette(Color(0xFF0000FF))

    @Test
    fun `first retarget does not start a fade`() {
        val model = PaletteFadeModel()

        assertFalse(model.retarget(paletteA, 1f))

        assertFalse(model.isFading)
        assertEquals(paletteA, model.paletteAt(0f))
        assertEquals(paletteA, model.paletteAt(1f))
    }

    @Test
    fun `retarget to a different palette starts a fade from the previous target`() {
        val model = PaletteFadeModel()
        model.retarget(paletteA, 1f)

        assertTrue(model.retarget(paletteB, 1f))
        assertTrue(model.isFading)
        assertEquals(paletteA, model.paletteAt(0f))
        assertEquals(paletteB, model.paletteAt(1f))
    }

    @Test
    fun `mid-fade retarget restarts from the currently displayed palette`() {
        val model = PaletteFadeModel()
        model.retarget(paletteA, 1f)
        model.retarget(paletteB, 1f)

        // The animation is halfway through A -> B when the target changes
        assertTrue(model.retarget(paletteC, 0.5f))

        val expectedOrigin = paletteA.lerpTo(paletteB, 0.5f)
        assertEquals(expectedOrigin, model.paletteAt(0f))
        assertEquals(paletteC, model.paletteAt(1f))
    }

    @Test
    fun `mid-fade lerp interpolates the accent color`() {
        val model = PaletteFadeModel()
        model.retarget(paletteA, 1f)
        model.retarget(paletteB, 1f)

        val mid = model.paletteAt(0.5f)

        // Verify the model applies the same lerp Compose uses internally, with
        // the correct origin, target and fraction (not a hardcoded sRGB
        // midpoint, which depends on the color-space lerp implementation).
        assertEquals(lerp(paletteA.accent, paletteB.accent, 0.5f), mid.accent)
        // The midpoint must not be either endpoint
        assertTrue(mid.accent != paletteA.accent && mid.accent != paletteB.accent)
        // isDark follows the target, not the origin
        assertEquals(paletteB.isDark, mid.isDark)
    }

    @Test
    fun `retarget to the same palette is a no-op`() {
        val model = PaletteFadeModel()
        model.retarget(paletteA, 1f)

        assertFalse(model.retarget(paletteA, 1f))
        assertFalse(model.isFading)
    }

    @Test
    fun `endFade stops fading and returns the target`() {
        val model = PaletteFadeModel()
        model.retarget(paletteA, 1f)
        model.retarget(paletteB, 1f)

        model.endFade()

        assertFalse(model.isFading)
        assertEquals(paletteB, model.paletteAt(0f))
        assertEquals(paletteB, model.paletteAt(1f))
    }

    @Test
    fun `rapid alternating targets converge to the last target`() {
        val model = PaletteFadeModel()
        model.retarget(paletteA, 1f)
        model.retarget(paletteB, 0.3f)
        model.retarget(paletteC, 0.7f)

        model.endFade()

        assertEquals(paletteC, model.paletteAt(1f))
    }
}
