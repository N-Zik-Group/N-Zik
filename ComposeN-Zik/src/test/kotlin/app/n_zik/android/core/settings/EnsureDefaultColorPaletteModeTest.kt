package app.n_zik.android.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.putEnum
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the theme-mode default seeding (spec-achromatic-ramp-luminance-cap, scope extension
 * 2026-09-28): a fresh profile's prefs (no `colorPaletteMode` key) must observe "Theme
 * mode" ([ColorPaletteMode.System]) after [ensureDefaultColorPaletteMode], an explicit
 * user choice (e.g. [ColorPaletteMode.Dark]) must never be overridden, and the seed must
 * be idempotent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnsureDefaultColorPaletteModeTest {

    private fun freshPrefs(): SharedPreferences {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("ensureDefaultColorPaletteModeTest", Context.MODE_PRIVATE)
        // Guard against a leaked value from a previous test environment.
        prefs.edit().remove(colorPaletteModeKey).commit()
        return prefs
    }

    private fun setMode(prefs: SharedPreferences, mode: ColorPaletteMode) {
        prefs.edit().putEnum(colorPaletteModeKey, mode).commit()
    }

    @Test
    fun `absent key is seeded to System (theme mode)`() {
        val prefs = freshPrefs()
        ensureDefaultColorPaletteMode(prefs)
        assertEquals(
            ColorPaletteMode.System,
            prefs.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
        )
    }

    @Test
    fun `an explicit Dark choice is never overridden`() {
        val prefs = freshPrefs()
        setMode(prefs, ColorPaletteMode.Dark)
        ensureDefaultColorPaletteMode(prefs)
        assertEquals(
            ColorPaletteMode.Dark,
            prefs.getEnum(colorPaletteModeKey, ColorPaletteMode.System)
        )
    }

    @Test
    fun `seed is idempotent - a second call keeps the value`() {
        val prefs = freshPrefs()
        ensureDefaultColorPaletteMode(prefs)
        ensureDefaultColorPaletteMode(prefs)
        assertEquals(
            ColorPaletteMode.System,
            prefs.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
        )
    }
}
