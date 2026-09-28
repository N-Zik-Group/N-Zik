package app.n_zik.android.legacyoffmain.player

import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.n_zik.android.components.player.durationOutlineColorOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pin of [durationOutlineColorOf] (spec-achromatic-ramp-luminance-cap, loopback 2, VG-3): the
 * kreate `DurationIndicator`'s outline decision was extracted into this pure helper so it can
 * be pinned — it is keyed on the effective palette tone instead of `colorPaletteMode`, and the
 * legacy light-mode quirk (the opt-out ignored, `White.copy(0.5f)` returned even with
 * `textOutline=false`) is fixed.
 *
 * Tested here in a plain JVM unit test; `internal` visibility is module-scoped in Kotlin, so it
 * resolves without sharing the legacy package (same convention as
 * `DurationIndicatorTimeRemainingOffMainTest`).
 */
class DurationIndicatorOutlineOffMainTest {

    @Test
    fun `the text outline opt-out is honored in both tones`() {
        // The legacy light-mode branch returned the white halo even when the user opted out —
        // the opt-out must now win in every tone.
        assertEquals(
            Color.Transparent,
            durationOutlineColorOf(textOutline = false, palette = DefaultLightColorPalette)
        )
        assertEquals(
            Color.Transparent,
            durationOutlineColorOf(textOutline = false, palette = DefaultDarkColorPalette)
        )
    }

    @Test
    fun `the outline halo follows the effective tone at half alpha`() {
        assertEquals(
            Color.White.copy(alpha = 0.5f),
            durationOutlineColorOf(textOutline = true, palette = DefaultLightColorPalette)
        )
        assertEquals(
            Color.Black.copy(alpha = 0.5f),
            durationOutlineColorOf(textOutline = true, palette = DefaultDarkColorPalette)
        )
    }
}
