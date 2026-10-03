package app.n_zik.android.ui.saveable

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #881 (gh-881): the DYNAMIC branch of the `ColorPalette` tolerant restore (spec M5) — a
 * non-sentinel accent ARGB round-trips through `save` → `restore` to the exact same palette,
 * while the sentinels (0–3) still map to the static palettes. Runs on Robolectric because
 * `ColorUtils.colorToHSL` reads the channel bytes through `android.graphics.Color`, which a
 * plain JVM unit test cannot provide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorPaletteDynamicRoundTripTest {

    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <T, S : Any> Saver<T, S>.saveIn(sc: SaverScope, value: T): S? =
        with(sc) { save(value) }

    @Test
    fun `a dynamic palette round trips through the saver`() {
        val accentArgb = 0xFF804020.toInt()
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accentArgb, hsl)
        val palette = dynamicColorPaletteOf(hsl, isDark = false)

        val saved = requireNotNull(ColorPalette.Companion.saveIn(scope, palette))
        // The payload carries the palette's OWN accent ARGB — its saturation is clamped by
        // `dynamicColorPaletteOf` (coerceAtMost 0.5f), so it is NOT necessarily the input accent.
        assertEquals(listOf<Any>(palette.accent.toArgb(), false), saved)

        // Restore follows the dynamic branch from the SAVED accent — same deterministic
        // computation as `restore` itself (an HSL->RGB->HSL round trip is not bit-exact, so the
        // comparison is against the payload-derived palette, not the original).
        val expected = dynamicColorPaletteOf(
            FloatArray(3).apply { ColorUtils.colorToHSL(palette.accent.toArgb(), this) },
            isDark = false,
        )
        val restored = ColorPalette.Companion.restore(saved)
        assertEquals(expected, restored)
        assertTrue(restored != DefaultDarkColorPalette)
    }

    @Test
    fun `a dynamic payload restores to the palette derived from the accent, not the fallback`() {
        val accentArgb = 0xFF2060A0.toInt()
        val payload = listOf(accentArgb, true)
        val expected = dynamicColorPaletteOf(
            FloatArray(3).apply { ColorUtils.colorToHSL(accentArgb, this) },
            isDark = true,
        )

        val restored = ColorPalette.Companion.restore(payload)
        assertEquals(expected, restored)
        // The dynamic branch must NOT collapse to the static fallback for a non-sentinel accent.
        assertTrue(restored != DefaultDarkColorPalette)
        // The sentinel payload still maps to the static dark palette.
        assertEquals(DefaultDarkColorPalette, ColorPalette.Companion.restore(0))
    }
}
