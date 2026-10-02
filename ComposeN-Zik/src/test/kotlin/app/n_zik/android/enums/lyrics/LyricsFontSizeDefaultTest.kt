package app.n_zik.android.enums.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Pins the shared default for the lyrics font size preference.
 *
 * The lyrics screen (rendering) and the settings menu (display) both read the
 * same preference key (lyricsFontSizeKey) and fall back to [LyricsFontSize.DEFAULT]
 * when nothing is stored yet. A past bug made the menu default to Medium while
 * the screen rendered Large, so a fresh install showed one size in the menu
 * and another on screen.
 */
class LyricsFontSizeDefaultTest {

    @Test
    fun `the shared default is the extra large size`() {
        // Large is the size the lyrics screen already rendered with; the menu
        // default is aligned to it, not the other way around.
        assertEquals(LyricsFontSize.Large, LyricsFontSize.DEFAULT)
    }

    @Test
    fun `the shared default is a fixed preset, not custom`() {
        // Custom depends on a stored slider value; a fresh install has none,
        // so the fallback must be a fixed preset.
        assertNotEquals(LyricsFontSize.Custom, LyricsFontSize.DEFAULT)
    }
}
