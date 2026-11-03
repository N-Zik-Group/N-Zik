package app.n_zik.android.components.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.components.player.extractCoverPalette
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DynamicThemeBrightnessTest {
    @Test
    fun `absent or disabled setting preserves the original neutral palette`() {
        assertFalse(DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
        for (coverIsDark in listOf(true, false)) {
            val palette = dynamicColorPaletteOf(Color.Gray, coverIsDark)
            assertSame(palette, palette.keepMonochromeColorsDark(false))
        }
    }

    @Test
    fun `bright and dark neutral covers stay canonically dark in both themes with all foreground roles`() = runBlocking {
        for (cover in listOf(Color(0xFFEBEBEB), Color(0xFF202020))) {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(cover.toArgb())
            }
            try {
                for (themeIsDark in listOf(true, false)) {
                    val extracted = requireNotNull(extractCoverPalette(bitmap, themeIsDark))
                    val originalSwatches = extracted.colors.copy()
                    val normalized = extracted.palette.keepMonochromeColorsDark(true)
                    assertEquals(dynamicColorPaletteOf(Color.Gray, true), normalized)
                    assertEquals(originalSwatches, extracted.colors)
                    assertEquals(cover.toArgb(), bitmap.getPixel(0, 0))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `colored and faintly colored palettes remain identical with either setting`() {
        for (accent in listOf(Color.Red, Color.Blue, Color(0xFF808081))) {
            for (isDark in listOf(true, false)) {
                val palette = dynamicColorPaletteOf(accent, isDark)
                for (enabled in listOf(true, false)) {
                    assertSame(palette, palette.keepMonochromeColorsDark(enabled))
                }
            }
        }
    }
}
