package app.n_zik.android.components.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.components.player.m3eRecapRestoredDynamicPalette
import app.n_zik.android.ui.saveable.TolerantAppearanceStateSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupAppearanceTest {
    private val original = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = Typography.fallback,
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )
    private val configured = original.copy(
        colorPalette = DefaultDarkColorPalette.copy(background0 = Color.Red, accent = Color.Green),
        thumbnailShape = RoundedCornerShape(9.dp),
        uiRoundnessShape = RoundedCornerShape(21.dp),
    )
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun roundTrip(
        saved: Appearance,
        name: ColorPaletteName,
        mode: ColorPaletteMode = ColorPaletteMode.Dark,
        systemIsDark: Boolean = true,
    ): Appearance {
        val saver = TolerantAppearanceStateSaver(
            fallback = { configured },
            normalizeRestored = { startupAppearance(it, configured, name, mode, systemIsDark) },
        )
        val payload = with(saver) { scope.save(mutableStateOf(saved)) }
        return saver.restore(payload).value
    }

    private fun restoredPalette(saved: Appearance): ColorPalette {
        val payload = with(ColorPalette.Companion) { scope.save(saved.colorPalette) }
        return ColorPalette.restore(payload)
    }

    @Test
    fun `recreation restores each non playback theme from current preferences immediately`() {
        for (name in ColorPaletteName.entries.filter { it != ColorPaletteName.Dynamic }) {
            assertEquals("Selected theme $name", configured, roundTrip(original, name))
        }
    }

    @Test
    fun `customized colors survive the lossy palette saver on every recreation`() {
        val first = roundTrip(configured, ColorPaletteName.Customized)
        assertEquals(configured, first)
        assertEquals(configured, roundTrip(first, ColorPaletteName.Customized))
    }

    @Test
    fun `dynamic startup keeps restored playback accent with current font and shape preferences`() {
        val saved = original.copy(colorPalette = dynamicColorPaletteOf(Color.Blue, true))
        val restored = roundTrip(saved, ColorPaletteName.Dynamic)
        assertEquals(restoredPalette(saved).accent, restored.colorPalette.accent)
        assertNotEquals(configured.colorPalette.accent, restored.colorPalette.accent)
        assertEquals(configured.thumbnailShape, restored.thumbnailShape)
        assertEquals(configured.uiRoundnessShape, restored.uiRoundnessShape)
        assertEquals(configured.typography.withColor(restored.colorPalette.text), restored.typography)
    }

    @Test
    fun `dynamic restoration applies pitch black before any playback callback`() {
        val saved = original.copy(colorPalette = dynamicColorPaletteOf(Color.Blue, true))
        val restored = roundTrip(saved, ColorPaletteName.Dynamic, ColorPaletteMode.PitchBlack)
        assertEquals(Color.Black, restored.colorPalette.background0)
        assertEquals(Color.Black, restored.colorPalette.background4)
        assertEquals(restoredPalette(saved).accent, restored.colorPalette.accent)
        assertEquals(Color.White, restored.typography.s.color)
    }

    @Test
    fun `restored achromatic ramp is capped synchronously for current system theme`() {
        val saved = original.copy(colorPalette = dynamicColorPaletteOf(Color.White, true))
        for (systemIsDark in listOf(true, false)) {
            val restored = roundTrip(saved, ColorPaletteName.Dynamic, ColorPaletteMode.System, systemIsDark)
            val expected = restoredPalette(saved).m3eRecapRestoredDynamicPalette(ColorPaletteName.Dynamic, systemIsDark)
            assertEquals(expected, restored.colorPalette)
            assertEquals(expected.text, restored.typography.s.color)
        }
    }
}
