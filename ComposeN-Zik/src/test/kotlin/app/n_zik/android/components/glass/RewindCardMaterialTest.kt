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

    @Test fun `outlined action modes use one shared rim and preserve native fill and export pixels`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val enabled = mutableStateOf(true)
        val shape = RoundedCornerShape(12.dp)
        val fill = Color.Red.copy(alpha = .15f)
        // Make accidental retention of the native border unmistakable in the full image.
        val border = Color.Magenta
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                    areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                Column(Modifier.background(Color.Black)) {
                    Box(Modifier.size(80.dp, 40.dp).testTag("outlined.native")
                        .background(fill, shape).border(1.dp, border, shape))
                    Box(Modifier.size(80.dp, 40.dp).testTag("outlined.actual")
                        .rewindOutlinedActionBackground(fill, border, shape, settings))
                    val expected = when {
                        !settings.usesGlassMaterial(GlassArea.ActionButtons) &&
                            !settings.usesGlassContour(GlassArea.ActionButtons) ->
                            Modifier.background(fill, shape).border(1.dp, border, shape)
                        !settings.usesGlassMaterial(GlassArea.ActionButtons) ->
                            Modifier.background(fill, shape).glassAreaContour(settings, GlassArea.ActionButtons, shape)
                        else -> Modifier.glassChipBackground(fill, shape, settings)
                    }
                    Box(Modifier.size(80.dp, 40.dp).testTag("outlined.expected").then(expected))
                }
            }
        }
        fun image(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val native = image("outlined.native")
        for (master in listOf(false, true)) {
            for (areaMode in GlassAreaMode.entries) {
                compose.runOnIdle { enabled.value = master; mode.value = areaMode }
                val actual = image("outlined.actual")
                assertEquals("$master/$areaMode must use exactly one native or shared rim",
                    true, samePixels(image("outlined.expected"), actual))
                if (!master || areaMode == GlassAreaMode.Disabled) assertSamePixels(native, actual)
                if (master && areaMode == GlassAreaMode.ContourOnly) {
                    assertFalse("ContourOnly replaces the native border with the common rim", samePixels(native, actual))
                    assertEquals("ContourOnly keeps the native fill", native[native.width / 2, native.height / 2],
                        actual[actual.width / 2, actual.height / 2])
                }
            }
        }
        try {
            for (areaMode in GlassAreaMode.entries) {
                compose.runOnIdle { enabled.value = true; mode.value = areaMode; rewindCaptureMode.value = true }
                assertSamePixels(native, image("outlined.actual"))
                compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = true }
                assertSamePixels(native, image("outlined.actual"))
                compose.runOnIdle { rewindShareCaptureActive.value = false }
            }
        } finally {
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = false }
        }
    }

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
        val native = compose.onNodeWithTag("collage").captureToImage().toPixelMap()
        listOf(false, true, false).forEach { glassEnabled ->
            compose.runOnIdle { enabled.value = glassEnabled }
            assertEquals(
                "Glass must not inset the collage artwork inside a second frame",
                compose.onNodeWithTag("collage").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot,
            )
            val pixels = compose.onNodeWithTag("collage").captureToImage().toPixelMap()
            assertEquals("The photograph stays sharp and untinted", Color.Cyan, pixels[pixels.width / 2, pixels.height / 2])
            if (glassEnabled) assertFalse("The shared Glass rim must draw above opaque collage photographs", samePixels(native, pixels))
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
