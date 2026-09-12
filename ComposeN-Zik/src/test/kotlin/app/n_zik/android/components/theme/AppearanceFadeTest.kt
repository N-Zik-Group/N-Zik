package app.n_zik.android.components.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppearanceFadeTest {

    private fun palette(background: Color, text: Color, isDark: Boolean = true): ColorPalette =
        ColorPalette(
            background0 = background,
            background1 = background,
            background2 = background,
            background3 = background,
            background4 = background,
            accent = background,
            onAccent = Color.White,
            text = text,
            textSecondary = text,
            textDisabled = text,
            isDark = isDark,
            iconButtonPlayer = text,
        )

    private fun textStyle(color: Color, sizeSp: Float): TextStyle =
        TextStyle(color = color, fontSize = sizeSp.sp)

    private fun typography(color: Color): Typography =
        Typography(
            xxxs = textStyle(color, 10f),
            xxs = textStyle(color, 12f),
            xs = textStyle(color, 14f),
            s = textStyle(color, 16f),
            m = textStyle(color, 18f),
            l = textStyle(color, 20f),
            xl = textStyle(color, 24f),
            xxl = textStyle(color, 28f),
            xxxl = textStyle(color, 36f),
            xlxl = textStyle(color, 34f),
        )

    private fun appearance(colorPalette: ColorPalette, typography: Typography): Appearance =
        Appearance(
            colorPalette = colorPalette,
            typography = typography,
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )

    private val fromAppearance = appearance(
        colorPalette = palette(Color.Red, Color.White),
        typography = typography(Color.White),
    )

    private val targetAppearance = appearance(
        colorPalette = palette(Color.Blue, Color.Black),
        typography = typography(Color.Black),
    )

    @Test
    fun `fadeAppearance at zero keeps the origin colors`() {
        val result = fadeAppearance(fromAppearance, targetAppearance, 0f)

        assertEquals(fromAppearance.colorPalette.background0, result.colorPalette.background0)
        assertEquals(fromAppearance.colorPalette.text, result.colorPalette.text)
        assertEquals(fromAppearance.typography.xxs.color, result.typography.xxs.color)
    }

    @Test
    fun `fadeAppearance at one uses the target colors`() {
        val result = fadeAppearance(fromAppearance, targetAppearance, 1f)

        assertEquals(targetAppearance.colorPalette.background0, result.colorPalette.background0)
        assertEquals(targetAppearance.colorPalette.text, result.colorPalette.text)
        assertEquals(targetAppearance.typography.xxs.color, result.typography.xxs.color)
    }

    @Test
    fun `fadeAppearance mid interpolates palette and typography`() {
        val result = fadeAppearance(fromAppearance, targetAppearance, 0.5f)

        assertEquals(
            lerp(fromAppearance.colorPalette.background0, targetAppearance.colorPalette.background0, 0.5f),
            result.colorPalette.background0
        )
        assertEquals(
            lerp(fromAppearance.colorPalette.text, targetAppearance.colorPalette.text, 0.5f),
            result.colorPalette.text
        )
        assertEquals(
            lerp(fromAppearance.colorPalette.text, targetAppearance.colorPalette.text, 0.5f),
            result.typography.xxs.color
        )
    }

    @Test
    fun `fadeAppearance mid does not match either endpoint`() {
        val result = fadeAppearance(fromAppearance, targetAppearance, 0.5f)

        assertTrue(result.colorPalette.background0 != fromAppearance.colorPalette.background0)
        assertTrue(result.colorPalette.background0 != targetAppearance.colorPalette.background0)
    }

    @Test
    fun `withColor recolors every typography style`() {
        val source = typography(Color.Red)
        val result = source.withColor(Color.Blue)

        assertEquals(Color.Blue, result.xxxs.color)
        assertEquals(Color.Blue, result.xxs.color)
        assertEquals(Color.Blue, result.xs.color)
        assertEquals(Color.Blue, result.s.color)
        assertEquals(Color.Blue, result.m.color)
        assertEquals(Color.Blue, result.l.color)
        assertEquals(Color.Blue, result.xl.color)
        assertEquals(Color.Blue, result.xxl.color)
        assertEquals(Color.Blue, result.xxxl.color)
        assertEquals(Color.Blue, result.xlxl.color)
    }

    @Test
    fun `withColor preserves every font size`() {
        val source = typography(Color.Red)
        val result = source.withColor(Color.Blue)

        assertEquals(10.sp, result.xxxs.fontSize)
        assertEquals(12.sp, result.xxs.fontSize)
        assertEquals(14.sp, result.xs.fontSize)
        assertEquals(16.sp, result.s.fontSize)
        assertEquals(18.sp, result.m.fontSize)
        assertEquals(20.sp, result.l.fontSize)
        assertEquals(24.sp, result.xl.fontSize)
        assertEquals(28.sp, result.xxl.fontSize)
        assertEquals(36.sp, result.xxxl.fontSize)
        assertEquals(34.sp, result.xlxl.fontSize)
    }

    @Test
    fun `appearanceRetarget applies target instantly on first target`() {
        val retarget = appearanceRetarget(current = null, fadeFrom = fromAppearance, target = targetAppearance)

        assertEquals(targetAppearance, retarget.displayed)
        assertNull(retarget.animateFrom)
    }

    @Test
    fun `appearanceRetarget applies target instantly when fadeFrom is null`() {
        val retarget = appearanceRetarget(current = fromAppearance, fadeFrom = null, target = targetAppearance)

        assertEquals(targetAppearance, retarget.displayed)
        assertNull(retarget.animateFrom)
    }

    @Test
    fun `appearanceRetarget skips animation when displayed already equals target`() {
        val retarget = appearanceRetarget(current = targetAppearance, fadeFrom = fromAppearance, target = targetAppearance)

        assertEquals(targetAppearance, retarget.displayed)
        assertNull(retarget.animateFrom)
    }

    @Test
    fun `appearanceRetarget restarts from the currently displayed appearance mid-fade`() {
        val displayed = fadeAppearance(fromAppearance, targetAppearance, 0.5f)

        val retarget = appearanceRetarget(current = displayed, fadeFrom = fromAppearance, target = targetAppearance)

        assertEquals(displayed, retarget.animateFrom)
        assertEquals(targetAppearance, retarget.displayed)
    }
}
