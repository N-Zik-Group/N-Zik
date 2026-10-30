package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandaloneHeaderGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeMaterialStaysBetweenBothSystemInsets() {
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(240.dp, 120.dp).background(Color.Blue).testTag("safe-header")) {
                    StandaloneHeaderGlass(
                        GlassSettings(film = 1f, rim = 0f, tintStrength = 0f), Color.Red,
                        null, 24f, 64f, { 0f }, horizontalInsets = WindowInsets(20, 0, 30, 0),
                    ) { Box(Modifier.size(240.dp, 88.dp)) }
                }
            }
        }
        val pixels = compose.onNodeWithTag("safe-header").captureToImage().toPixelMap()
        for (y in listOf(4, 40, 80)) {
            assertEquals(Color.Blue, pixels[19, y])
            assertEquals(Color.Red, pixels[20, y])
            assertEquals(Color.Red, pixels[209, y])
            assertEquals(Color.Blue, pixels[210, y])
        }
    }

    @Test fun `standalone fallback exposes its lower glass rim without a rim across the status edge`() {
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(200.dp, 120.dp).background(Color.White).testTag("fallback")) {
                    StandaloneHeaderGlass(
                        settings = GlassSettings(film = 1f, rim = 1f, tintStrength = 0f),
                        filmColor = Color.Red,
                        pageBackdrop = null,
                        statusBarTopPx = 24f,
                        headerHeightPx = 64f,
                        headerOffsetPx = { 0f },
                    ) { Box(Modifier.size(200.dp, 88.dp)) }
                }
            }
        }
        val pixels = compose.onNodeWithTag("fallback").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[100, 0])
        assertEquals(Color.Red, pixels[100, 40])
        assertTrue(pixels[100, 87].green > .1f)
        assertEquals(Color.White, pixels[1, 86])
        assertEquals(Color.White, pixels[198, 86])
    }

    @Test fun `header body and controls sample live page while status and rounded bottom share the fade`() {
        val source = mutableStateOf(Color.Blue)
        val offset = mutableFloatStateOf(0f)
        val userShape = mutableStateOf<Shape>(RoundedCornerShape(20.dp))
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = userShape.value, artistThumbnailShape = shape,
            )) {
                val page = rememberCanvasBackdrop { drawRect(source.value) }
                Box(Modifier.size(200.dp, 120.dp).background(Color.White).testTag("scene")) {
                    StandaloneHeaderGlass(
                        GlassSettings(film = .4f, blur = 0f, rim = 0f, tintStrength = 0f),
                        Color.Red, page, 24f, 64f, { offset.floatValue },
                    ) { material ->
                        assertNotSame(page, material)
                        Box(Modifier.size(200.dp, 88.dp).testTag("foreground")) {
                            Box(Modifier.offset(90.dp, 42.dp).size(20.dp).testTag("control")
                                .drawPlainBackdrop(material, { RectangleShape }, effects = {}))
                        }
                    }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        fun assertMaterial(green: Boolean) {
            val image = pixels()
            for (y in listOf(12, 40, 70)) {
                assertEquals(.4f, image[40, y].red, .03f)
                assertEquals(.6f, if (green) image[40, y].green else image[40, y].blue, .03f)
            }
            val control = compose.onNodeWithTag("control").captureToImage().toPixelMap()[10, 10]
            assertEquals(.4f, control.red, .03f)
            assertEquals(.6f, if (green) control.green else control.blue, .03f)
        }
        assertMaterial(false)
        assertEquals(.4f, pixels()[1, 1].red, .03f)
        assertEquals(.4f, pixels()[198, 1].red, .03f)
        assertEquals(Color.White, pixels()[1, 86])
        assertEquals(Color.White, pixels()[198, 86])
        compose.runOnIdle { source.value = Color.Green; userShape.value = RectangleShape }
        assertMaterial(true)
        assertEquals(.4f, pixels()[1, 86].red, .03f)
        compose.runOnIdle { userShape.value = RoundedCornerShape(20.dp); offset.floatValue = -32f }
        assertEquals(Color.White, pixels()[1, 54])
        assertEquals(.4f, pixels()[100, 54].red, .03f)
        assertEquals(Color.White, pixels()[40, 70])
        assertEquals(.4f, pixels()[40, 12].red, .03f)
        compose.runOnIdle { offset.floatValue = -52f }
        assertEquals(.7f, pixels()[40, 12].red, .03f)
        assertEquals(.7f, pixels()[40, 28].red, .03f)
        assertEquals(88f, compose.onNodeWithTag("foreground").fetchSemanticsNode().boundsInRoot.height, .1f)
        compose.runOnIdle { offset.floatValue = -64f }
        assertEquals(Color.White, pixels()[40, 12])
        assertEquals(Color.White, pixels()[40, 28])
    }
}
