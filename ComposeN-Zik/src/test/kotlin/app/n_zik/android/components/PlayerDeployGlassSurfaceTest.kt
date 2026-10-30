package app.n_zik.android.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerDeployGlassSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `moving material samples live source and settled endpoints stop sampling until closing`() {
        var progress by mutableStateOf(0f)
        var sourceColor by mutableStateOf(Color.Blue)
        val captures = AtomicInteger()
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, shape, shape, shape,
            )) {
                val source = rememberCanvasBackdrop {
                    captures.incrementAndGet()
                    drawRect(sourceColor)
                }
                Box(Modifier.size(200.dp).background(Color.Red).testTag("endpoint_deploy")) {
                    PlayerDeployGlassSurface(
                        progress = { progress }, collapsedHeight = 60.dp,
                        startInset = 0.dp, endInset = 0.dp, baseShape = shape,
                        settings = GlassSettings(enabled = true, film = .5f, rim = 1f,
                            tintStrength = 0f, quality = GlassQuality.Balanced),
                        backdrop = source, filmColor = Color.Green,
                        exportedBackdrop = rememberLayerBackdrop(),
                    )
                }
            }
        }
        compose.onNodeWithTag("player_deploy_glass").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, captures.get()); progress = .2f }
        compose.onNodeWithTag("player_deploy_glass").assertExists()
        val opening = compose.onNodeWithTag("endpoint_deploy").captureToImage().toPixelMap()
        val initial = opening[opening.width / 2, opening.height / 4]
        assertTrue("The blue source must be visible through the green film", initial.blue > .3f && initial.red < .2f)
        val openingCaptures = captures.get()
        assertTrue(openingCaptures > 0)
        var previousCaptures = openingCaptures
        listOf(.35f, .6f, .85f).forEach { position ->
            compose.runOnIdle { progress = position }
            compose.onNodeWithTag("endpoint_deploy").captureToImage()
            compose.runOnIdle { assertTrue("Moving optics must follow the live source", captures.get() > previousCaptures) }
            previousCaptures = captures.get()
        }
        compose.runOnIdle { sourceColor = Color.Yellow; progress = .6f }
        val live = compose.onNodeWithTag("endpoint_deploy").captureToImage().toPixelMap()
        assertTrue("The new yellow source must reach the green film",
            live[live.width / 2, live.height / 4].red > initial.red + .3f)
        compose.runOnIdle { progress = 1f }
        compose.onNodeWithTag("player_deploy_glass").assertDoesNotExist()
        val settledCaptures = captures.get()
        repeat(3) { compose.onNodeWithTag("endpoint_deploy").captureToImage() }
        compose.runOnIdle { assertEquals(settledCaptures, captures.get()); progress = .8f }
        compose.onNodeWithTag("player_deploy_glass").assertExists()
        compose.onNodeWithTag("endpoint_deploy").captureToImage()
        val closingCaptures = captures.get()
        assertTrue(closingCaptures > settledCaptures)
        compose.runOnIdle { progress = .3f }
        compose.onNodeWithTag("endpoint_deploy").captureToImage()
        compose.runOnIdle { assertTrue(captures.get() > closingCaptures) }
    }

    @Test fun `early and middle deploy retain transparent film and rim and closing retraces opening`() {
        var progress by mutableStateOf(.1f)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(200.dp).background(Color.Red).testTag("deploy")) {
                    PlayerDeployGlassSurface(
                        progress = { progress }, collapsedHeight = 60.dp,
                        startInset = 0.dp, endInset = 0.dp, baseShape = shape,
                        settings = GlassSettings(enabled = true, film = .5f, rim = 1f,
                            tintStrength = 0f, quality = GlassQuality.Performance,
                            areaModes = mapOf(GlassArea.MiniPlayer to GlassAreaMode.GlassAndContour)),
                        backdrop = null, filmColor = Color.Green,
                        exportedBackdrop = rememberLayerBackdrop(),
                    )
                }
            }
        }
        val opening = mutableMapOf<Float, Color>()
        listOf(.1f, .3f, .7f, .3f, .1f).forEach { position ->
            compose.runOnIdle { progress = position }
            val pixels = compose.onNodeWithTag("deploy").captureToImage().toPixelMap()
            val scale = pixels.width / 200f
            val geometry = computeCardGeometry(position,
                Size(pixels.width.toFloat(), pixels.height.toFloat()),
                60f * scale, 16f * scale, 16f * scale, 28f * scale)
            val y = (geometry.height / 2).toInt()
            val center = pixels[pixels.width / 2, y]
            assertTrue("The page must remain visible through the film", center.red > .25f)
            assertTrue("The film must be drawn during the early deploy", center.green > .25f)
            val rimBlue = (geometry.left.toInt()..geometry.left.toInt() + 2)
                .maxOf { x -> pixels[x, y].blue }
            assertTrue("The rim must add white edge light independently of the green film", rimBlue > .02f)
            opening[position]?.let { assertEquals(it, center) } ?: run { opening[position] = center }
            assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height - 2])
        }
    }
}
