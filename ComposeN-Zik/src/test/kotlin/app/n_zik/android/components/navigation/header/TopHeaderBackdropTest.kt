package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.navigation.AppHeaderOverlay
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
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
class TopHeaderBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `unified top pane keeps lower corners rounded while folding and follows live roundness`() {
        val top = mutableIntStateOf(88)
        val radius = mutableStateOf(20.dp)
        compose.setContent {
            val shape = RoundedCornerShape(radius.value)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                val backdrop = rememberCanvasBackdrop { drawRect(Color.Blue) }
                Box(Modifier.size(100.dp, 200.dp).background(Color.White).testTag("corners")) {
                    Box(Modifier.offset { IntOffset(0, top.intValue) }) {
                        UnifiedTopNavigationGlass(
                            GlassSettings(film = 1f, blur = 0f, rim = 0f, tintStrength = 0f),
                            Color.Red, backdrop, { top.intValue },
                        ) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }
        for (navigationTop in listOf(88, 24)) {
            compose.runOnIdle { top.intValue = navigationTop }
            val pixels = compose.onNodeWithTag("corners").captureToImage().toPixelMap()
            val lowerEdge = navigationTop + 64 - 2
            assertEquals(Color.White, pixels[1, lowerEdge])
            assertEquals(Color.White, pixels[98, lowerEdge])
            assertEquals(Color.Red, pixels[50, lowerEdge])
            assertEquals(Color.Red, pixels[1, 24])
        }
        compose.runOnIdle { radius.value = 0.dp }
        val square = compose.onNodeWithTag("corners").captureToImage().toPixelMap()
        assertEquals(Color.Red, square[1, 86])
        assertEquals(Color.Red, square[98, 86])
        compose.runOnIdle { radius.value = 20.dp }
        val rounded = compose.onNodeWithTag("corners").captureToImage().toPixelMap()
        assertEquals(Color.White, rounded[1, 86])
        assertEquals(Color.White, rounded[98, 86])
    }

    @Test fun `unified top pane stays behind header controls while folding`() {
        val top = mutableIntStateOf(88)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                val backdrop = rememberCanvasBackdrop { drawRect(Color.Blue) }
                AppHeaderOverlay(
                    modifier = Modifier.size(100.dp, 200.dp).testTag("scene"),
                    header = {
                        Box(Modifier.offset { IntOffset(0, top.intValue - 64) }
                            .size(40.dp).background(Color.Green).clickable { clicks++ })
                    },
                    navigation = {
                        Box(Modifier.offset { IntOffset(0, top.intValue) }) {
                            UnifiedTopNavigationGlass(
                                GlassSettings(film = 1f, tintStrength = 0f),
                                Color.Red, backdrop, { top.intValue },
                            ) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                        }
                    },
                ) { Box(Modifier.size(100.dp, 200.dp).background(Color.White)) }
            }
        }
        for (navigationTop in listOf(88, 72)) {
            compose.runOnIdle { top.intValue = navigationTop }
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            assertEquals(Color.Green, pixels[20, navigationTop - 44])
            assertEquals(Color.Red, pixels[60, navigationTop + 20])
            compose.onNodeWithTag("scene").performTouchInput {
                click(Offset(20f, navigationTop - 44f))
            }
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun `unified material crosses header nav seam once and reports only foreground row height`() {
        val top = mutableIntStateOf(88)
        val source = mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val backdrop = rememberCanvasBackdrop { drawRect(source.value) }
                Box(Modifier.size(100.dp, 200.dp).background(Color.White).testTag("unified")) {
                    Box(Modifier.offset { IntOffset(0, top.intValue) }) {
                        UnifiedTopNavigationGlass(
                            GlassSettings(film = .4f, blur = 8f, rim = 1f, tintStrength = 0f),
                            Color.Red, backdrop, { top.intValue },
                        ) {
                            check(LocalUnifiedTopGlassSurface.current != null)
                            Box(Modifier.fillMaxWidth().height(64.dp).testTag("nav-foreground"))
                        }
                    }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("unified").captureToImage().toPixelMap()
        val first = pixels()
        // The outside rim intentionally adds light near y=0; the former internal seam must not.
        for (y in listOf(23, 60, 87, 89, 120)) {
            assertEquals(.4f, first[50, y].red, .03f)
            assertEquals(.6f, first[50, y].blue, .03f)
        }
        assertEquals(first[50, 87].red, first[50, 89].red, .01f)
        assertEquals(first[50, 87].blue, first[50, 89].blue, .01f)
        assertEquals(64f, compose.onNodeWithTag("nav-foreground").fetchSemanticsNode().boundsInRoot.height, .1f)
        assertEquals(Color.White, first[50, 160])
        compose.runOnIdle { top.intValue = 24; source.value = Color.Green }
        val folded = pixels()
        for (y in listOf(23, 25, 60)) {
            assertEquals(.4f, folded[50, y].red, .03f)
            assertEquals(.6f, folded[50, y].green, .03f)
        }
        assertEquals(Color.White, folded[50, 100])
        assertEquals(64f, compose.onNodeWithTag("nav-foreground").fetchSemanticsNode().boundsInRoot.height, .1f)
    }

    @Test fun `Top backing keeps live blur in status while its clip retracts without changing page size`() {
        val offset = mutableFloatStateOf(0f)
        val source = mutableStateOf(Color.Red)
        val glass = mutableStateOf(true)
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val backdrop = rememberCanvasBackdrop {
                    drawRect(Color.Blue)
                    drawRect(source.value, topLeft = Offset(size.width / 2, 0f),
                        size = Size(size.width / 2, size.height))
                }
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    Box(Modifier.size(100.dp).background(Color.White).testTag("page")) {
                        TopHeaderBackdrop(GlassSettings(enabled = glass.value, film = 0f, blur = 8f, rim = 0f),
                            Color.Yellow, 24f, 64f) { offset.floatValue }
                    }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        val initial = pixels()
        assertTrue(initial[50, 12].red > .1f && initial[50, 12].blue > .1f)
        assertTrue(initial[50, 60].red > .1f)
        compose.runOnIdle { offset.floatValue = -32f }
        assertEquals(Color.White, pixels()[50, 60])
        compose.runOnIdle { offset.floatValue = -64f; source.value = Color.Green }
        val hidden = pixels()
        assertEquals(initial.width, hidden.width)
        assertEquals(initial.height, hidden.height)
        assertTrue(hidden[50, 12].green > .1f && hidden[50, 12].blue > .1f)
        assertTrue(hidden[50, 12].red < .1f)
        assertEquals(Color.White, hidden[50, 30])
        compose.runOnIdle { glass.value = false }
        assertEquals(Color.Yellow, pixels()[50, 12])
        assertEquals(Color.White, pixels()[50, 30])
    }
}
