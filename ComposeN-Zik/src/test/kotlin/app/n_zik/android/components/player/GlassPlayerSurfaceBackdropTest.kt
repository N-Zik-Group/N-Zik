package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassMiniPlayerBackground
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassPlayerSurfaceBackdropTest {
    @get:Rule val compose = createComposeRule()
    private val clear = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
        tintStrength = 0f, vibrancy = 0f)

    @Test fun `player background capture follows draw phase animation updates`() {
        var animatedColor by mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val capture = rememberGlassPlayerBackground()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalDensity provides Density(1f),
                LocalGlassBackdrop provides capture.backdrop,
            ) {
                Box(Modifier.size(100.dp).background(Color.Red)) {
                    Box(Modifier.size(100.dp).then(capture.background)
                        .drawBehind { drawRect(animatedColor) }
                        .then(capture.content)) {
                        GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {},
                            "animated_player_action", settings = clear)
                    }
                }
            }
        }
        val initial = sample("animated_player_action")
        assertTrue("control should sample initial animation frame", initial.blue > initial.red + .6f)
        compose.runOnIdle { animatedColor = Color.Green }
        val nextFrame = sample("animated_player_action")
        assertTrue("control should sample the next draw-only animation frame",
            nextFrame.green > nextFrame.blue + .6f)
    }

    @Test fun `player container overrides inherited page source for nested action material`() {
        var body by mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val capture = rememberGlassPlayerBackground()
            val page = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalDensity provides Density(1f),
                LocalGlassContainerBackdrop provides page,
            ) {
                Box(Modifier.size(100.dp).background(Color.Red)) {
                    CompositionLocalProvider(
                        LocalGlassBackdrop provides capture.backdrop,
                        LocalGlassContainerBackdrop provides capture.backdrop,
                    ) {
                        Box(Modifier.size(100.dp).then(capture.background).background(body).then(capture.content)) {
                            Box(Modifier.size(100.dp).background(Color.White))
                            Box(Modifier.size(48.dp).testTag("nested_player_action")
                                .glassChipBackground(Color.Transparent, shape, clear))
                        }
                    }
                }
            }
        }
        fun sampleAction(): Color {
            val pixels = compose.onNodeWithTag("nested_player_action").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val initial = sampleAction()
        assertTrue("action must sample player, excluding red page and white descendants", initial.blue > initial.red + .6f)
        compose.runOnIdle { body = Color.Green }
        val updated = sampleAction()
        assertTrue("action must track the current player source", updated.green > updated.red + .6f)
    }

    @Test fun `mini controls sample the rendered body and progress instead of the outside page`() {
        var body by mutableStateOf(Color.Blue)
        var fraction by mutableStateOf(.5f)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val page = rememberLayerBackdrop()
            val renderedBody = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalDensity provides Density(1f),
            ) {
                Box(Modifier.size(160.dp, 64.dp)) {
                    Box(Modifier.size(160.dp, 64.dp).layerBackdrop(page).background(Color.Red))
                    Row(Modifier.align(Alignment.Center).size(160.dp, 64.dp).glassMiniPlayerBackground(
                        clear.copy(film = 1f), shape, page, body,
                        progressColor = Color.Green, progressFraction = { fraction },
                        exportedBackdrop = renderedBody,
                    ), verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalGlassBackdrop provides renderedBody) {
                            GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {}, "played", settings = clear)
                            Box(Modifier.size(48.dp))
                            GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {}, "body", settings = clear)
                        }
                    }
                }
            }
        }
        val played = sample("played")
        val initialBody = sample("body")
        assertTrue("played control must retain the green progress film", played.green > played.red + .6f)
        assertTrue("control must see blue mini body instead of red page", initialBody.blue > initialBody.red + .6f)
        compose.runOnIdle { body = Color.Yellow }
        val updated = sample("body")
        assertTrue("mounted controls must update with the body", updated.red > .7f && updated.green > .7f && updated.blue < .2f)
        compose.runOnIdle { fraction = 1f }
        val advanced = sample("body")
        assertTrue("draw-only progress updates must reach the control", advanced.green > advanced.red + .6f)
    }

    @Test fun `full player captures modifier background while excluding covers and controls`() {
        var body by mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val capture = rememberGlassPlayerBackground()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalDensity provides Density(1f),
                LocalGlassBackdrop provides capture.backdrop,
            ) {
                Box(Modifier.size(100.dp).background(Color.Red)) {
                    Box(Modifier.size(100.dp).then(capture.background).background(body).then(capture.content)) {
                        // A child cover must stay sharp and must never enter the sampled background.
                        Box(Modifier.size(100.dp).background(Color.White))
                        GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {}, "player", settings = clear)
                    }
                }
            }
        }
        val initial = sample("player")
        assertTrue("player control must see blue player background, excluding red page and white descendants",
            initial.blue > initial.red + .6f)
        compose.runOnIdle { body = Color.Green }
        val updated = sample("player")
        assertTrue("player background must update while mounted", updated.green > updated.blue + .6f)
    }

    @Test fun `clipped blurred cover player keeps foreground visible outside background recording`() {
        var background by mutableStateOf(Color.Blue)
        var foreground by mutableStateOf(Color.White)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val capture = rememberGlassPlayerBackground()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalDensity provides Density(1f),
                LocalGlassBackdrop provides capture.backdrop,
                LocalGlassContainerBackdrop provides capture.backdrop,
            ) {
                Box(Modifier.size(100.dp).testTag("clipped_player").background(Color.Red)) {
                    Box(Modifier.size(100.dp)
                        .then(capture.background)
                        .background(background)
                        .then(capture.content)
                        // Match the blurred-cover player: clip only after recording stops.
                        // A clip between background and content caches an empty foreground.
                        .clip(shape)) {
                        Box(Modifier.size(100.dp).background(foreground))
                        GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {},
                            "clipped_player_action", settings = clear)
                    }
                }
            }
        }
        fun foregroundPixel(): Color {
            val pixels = compose.onNodeWithTag("clipped_player").captureToImage().toPixelMap()
            return pixels[70, 70]
        }
        val cover = foregroundPixel()
        assertTrue("sharp foreground must remain visible above blurred background",
            cover.red > .9f && cover.green > .9f && cover.blue > .9f)
        val action = sample("clipped_player_action")
        assertTrue("action must sample blue background while excluding white foreground",
            action.blue > action.red + .6f)
        compose.runOnIdle {
            background = Color.Green
            foreground = Color.Yellow
        }
        val updatedCover = foregroundPixel()
        assertTrue("cached clip must retain live foreground updates",
            updatedCover.red > .9f && updatedCover.green > .9f && updatedCover.blue < .1f)
        val updatedAction = sample("clipped_player_action")
        assertTrue("action must sample the updated background while excluding yellow foreground",
            updatedAction.green > updatedAction.red + .6f)
    }

    private fun sample(description: String): Color {
        val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}
