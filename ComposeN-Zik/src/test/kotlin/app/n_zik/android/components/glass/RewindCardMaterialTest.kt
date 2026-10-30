package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
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
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.components.ui.screens.rewind.slides.rewindActionBackground
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCollageFrame
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCardBackground
import app.n_zik.android.components.ui.screens.rewind.slides.rewindOutlinedActionBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RewindCardMaterialTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `collage artwork fills its entire card when Glass toggles`() {
        val enabled = mutableStateOf(false)
        val shape = RoundedCornerShape(12.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(
                    Modifier.size(120.dp, 96.dp).testTag("collage")
                        .rewindCollageFrame(Color.Black, shape, GlassSettings(enabled = enabled.value))
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Cyan, shape).testTag("artwork"))
                }
            }
        }
        listOf(false, true, false).forEach { glassEnabled ->
            compose.runOnIdle { enabled.value = glassEnabled }
            assertEquals(
                "Glass must not inset the collage artwork inside a second frame",
                compose.onNodeWithTag("collage").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot,
            )
        }
    }

    @Test
    fun `cards and outlined actions retain native and capture pixels while Glass changes live material`() {
        val mode = mutableStateOf(0)
        val shape = RoundedCornerShape(12.dp)
        val fill = Color.Red.copy(alpha = .15f)
        val border = Color.White.copy(alpha = .45f)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val settings = GlassSettings(enabled = mode.value >= 2)
                Column(Modifier.background(Color.Black).testTag("surfaces")) {
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier.background(fill, shape)
                        else Modifier.rewindCardBackground(fill, shape, settings)))
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier.background(fill, shape).border(1.dp, border, shape)
                        else Modifier.rewindOutlinedActionBackground(fill, border, shape, settings)))
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier.background(fill, shape)
                        else Modifier.rewindActionBackground(fill, shape, settings)))
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier
                        else Modifier.rewindCollageFrame(fill, shape, settings))) {
                        Box(Modifier.fillMaxSize().background(Color.Cyan, shape))
                    }
                }
            }
        }
        val native = pixels()
        compose.runOnIdle { mode.value = 1 }
        assertSamePixels(native, pixels())
        compose.runOnIdle { mode.value = 2 }
        assertFalse("live Glass should change the card and action appearance", samePixels(native, pixels()))
        try {
            compose.runOnIdle { rewindCaptureMode.value = true }
            assertSamePixels(native, pixels())
            compose.runOnIdle {
                rewindCaptureMode.value = false
                rewindShareCaptureActive.value = true
            }
            assertSamePixels(native, pixels())
        } finally {
            compose.runOnIdle {
                rewindCaptureMode.value = false
                rewindShareCaptureActive.value = false
            }
        }
    }

    private fun pixels() = compose.onNodeWithTag("surfaces").captureToImage().toPixelMap()

    private fun samePixels(expected: PixelMap, actual: PixelMap): Boolean =
        expected.width == actual.width && expected.height == actual.height &&
            (0 until expected.height).all { y -> (0 until expected.width).all { x -> expected[x, y] == actual[x, y] } }

    private fun assertSamePixels(expected: PixelMap, actual: PixelMap) {
        assertEquals("native and captured fills must retain their original pixels", true, samePixels(expected, actual))
    }
}
