package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.glassMiniPlayerBackground
import app.n_zik.android.components.glass.glassMiniPlayerBodyVisibility
import app.n_zik.android.components.glass.glassExplicitBadgeBackground
import app.n_zik.android.components.glass.rememberMiniPlayerControlBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// API 32 exercises the optical capture without Robolectric's unsupported lens AGSL.
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMiniPlayerBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `mini controls retain rendered body film when legacy owner also supplies raw material source`() {
        var film by mutableStateOf(.35f)
        var pageColor by mutableStateOf(Color.Blue)
        var deployment by mutableStateOf(0f)
        var nativeBody by mutableStateOf(false)
        val clear = GlassSettings(enabled = true, film = 0f, blur = 0f, rim = 0f,
            depth = 0f, amount = 0f, vibrancy = 0f, tintStrength = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(10.dp)
            val raw = rememberCanvasBackdrop { drawRect(pageColor) }
            val deploy = rememberCanvasBackdrop { drawRect(Color.Cyan) }
            val body = rememberLayerBackdrop()
            val controls = rememberMiniPlayerControlBackdrop(
                if (deployment > 0f && !nativeBody) deploy else null, body) { deployment }
            val bodySettings = clear.copy(film = film, miniPlayer = !nativeBody)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalDensity provides Density(1f),
            ) {
                Box(Modifier.size(160.dp, 64.dp).background(Color.Cyan).testTag("mini.body.scene")) {
                    Box(Modifier.matchParentSize().glassMiniPlayerBodyVisibility(bodySettings) { deployment }
                        .glassMiniPlayerBackground(bodySettings, shape, raw, Color.Black,
                            progressColor = Color.Red.copy(alpha = .3f), progressFraction = { .5f },
                            exportedBackdrop = body))
                    // Same identity relationship as MiniPlayer's production provider. The body
                    // export is the actual child parent; raw is only the body's optical input.
                    CompositionLocalProvider(LocalGlassBackdrop provides controls,
                        LocalGlassContainerBackdrop provides controls,
                        LocalGlassMaterialSource provides if (nativeBody) controls else raw) {
                        Row(Modifier.align(Alignment.Center).background(Color.Magenta)) {
                            Controls(clear)
                            Box(Modifier.size(20.dp).testTag("mini.explicit")
                                .glassExplicitBadgeBackground(clear))
                        }
                    }
                }
            }
        }
        fun assertBodyPixels() {
            val scene = compose.onNodeWithTag("mini.body.scene").captureToImage().toPixelMap()
            val sceneNode = compose.onNodeWithTag("mini.body.scene").fetchSemanticsNode()
            for (name in listOf("Previous", "Play", "Output")) {
                val node = compose.onNodeWithContentDescription(name)
                val pixels = node.captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 4]
                val x = (node.fetchSemanticsNode().boundsInRoot.center.x - sceneNode.boundsInRoot.left).toInt()
                val expected = scene[x, 8]
                assertEquals("$name rendered parent red", expected.red, actual.red, .03f)
                assertEquals("$name rendered parent green", expected.green, actual.green, .03f)
                assertEquals("$name rendered parent blue", expected.blue, actual.blue, .03f)
            }
            val badge = compose.onNodeWithTag("mini.explicit")
            val pixels = badge.captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            val x = (badge.fetchSemanticsNode().boundsInRoot.center.x - sceneNode.boundsInRoot.left).toInt()
            val expected = scene[x, 8]
            assertEquals("Explicit badge rendered parent red", expected.red, actual.red, .03f)
            assertEquals("Explicit badge rendered parent green", expected.green, actual.green, .03f)
            assertEquals("Explicit badge rendered parent blue", expected.blue, actual.blue, .03f)
        }
        assertBodyPixels()
        compose.runOnIdle { film = .75f }
        assertBodyPixels()
        compose.runOnIdle { pageColor = Color.Green }
        assertBodyPixels()
        compose.runOnIdle { deployment = .06f }
        assertBodyPixels()
        compose.runOnIdle { deployment = .12f }
        assertBodyPixels()
        compose.runOnIdle { nativeBody = true; deployment = 0f }
        assertBodyPixels()
    }

    @Test fun `small controls preserve full optical resolution with the host fallback`() {
        val scales = mutableListOf<Float>()
        val opticalSizes = mutableListOf<Float>()
        val source = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                scales += (density as BackdropEffectScope).contentScale
                opticalSizes += density.size.minDimension
                drawRect(Color.Blue)
            }
        }
        val host = GlassMenuHostState().apply { backdrop = source }
        compose.setContent {
            val shape = RoundedCornerShape(10.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalGlassMenuHost provides host,
                LocalDensity provides Density(1f),
            ) { Controls(GlassSettings(enabled = true, blur = 0f, rim = 0f)) }
        }
        compose.onNodeWithContentDescription("Previous").captureToImage()
        compose.runOnIdle {
            assertTrue("the controls must sample the page backdrop", scales.size >= 3)
            scales.forEach { assertEquals(1f, it, 0f) }
            opticalSizes.forEach { assertEquals("36 px panes must clear the 24 px lens threshold", 36f, it, 0f) }
        }
    }

    @Test fun `local sibling capture keeps its live pattern beneath each themed control`() {
        var upper by mutableStateOf(Color.Blue)
        var mode by mutableStateOf(GlassAreaMode.GlassOnly)
        val host = GlassMenuHostState().apply {
            backdrop = object : Backdrop {
                override val isCoordinatesDependent = false
                override fun DrawScope.drawBackdrop(
                    density: Density,
                    coordinates: LayoutCoordinates?,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                ) { drawRect(Color.Magenta) }
            }
        }
        compose.setContent {
            val shape = RoundedCornerShape(10.dp)
            val source = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalGlassMenuHost provides host,
                LocalGlassBackdrop provides source,
            ) {
                Box(Modifier.size(160.dp, 64.dp).background(Color.Black)) {
                    Canvas(Modifier.size(160.dp, 64.dp).layerBackdrop(source)) {
                        drawRect(upper)
                        drawRect(Color.Red, Offset(0f, size.height / 2), Size(size.width, size.height / 2))
                    }
                    // An opaque parent makes passing the wrong source visibly fail.
                    Row(Modifier.align(Alignment.Center).background(Color.Black),
                        verticalAlignment = Alignment.CenterVertically) {
                        Controls(GlassSettings(enabled = true, film = .25f, blur = 0f, rim = .5f,
                            tintStrength = 0f, vibrancy = 0f,
                            areaModes = mapOf(GlassArea.MiniPlayer to mode)))
                    }
                }
            }
        }
        fun sample(description: String, y: Float): Color {
            val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
            return pixels[pixels.width / 2, (pixels.height * y).toInt()]
        }
        listOf(GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour).forEach { materialMode ->
            compose.runOnIdle { mode = materialMode; upper = Color.Blue }
            listOf("Previous", "Play", "Output").forEach { name ->
                val top = sample(name, .25f)
                val bottom = sample(name, .75f)
                assertTrue("$name must retain the upper blue source", top.blue > top.red + .4f)
                assertTrue("$name must retain the lower red source", bottom.red > bottom.blue + .4f)
            }
            compose.runOnIdle { upper = Color.Green }
            listOf("Previous", "Play", "Output").forEach { name ->
                val top = sample(name, .25f)
                assertTrue("$name must repaint the mounted source", top.green > top.blue + .4f)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Controls(settings: GlassSettings) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {}, "Previous", settings = settings)
            GlassMiniPlayerPlayPauseButton({}, Color.Black, "Play", settings = settings) {}
            GlassMiniPlayerIconButton(Icons.Filled.Computer, Color.Transparent, {}, "Output", settings = settings)
        }
    }
}
