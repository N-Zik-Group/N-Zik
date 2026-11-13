package app.n_zik.android.components.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import app.n_zik.android.ui.components.themed.DownloadStateIconButton
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import org.junit.Assert.assertEquals
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
class DownloadIconButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `twenty dp downloaded badge matches bare vector for each roundness in Material and Glass`() {
        val corner = mutableStateOf(4.dp)
        val glass = mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(corner.value)
            val settings = GlassSettings(enabled = glass.value)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
            ) {
                GlassRippleProvider(settings) {
                    Row {
                        Box(Modifier.size(40.dp).background(Color.Black).testTag("bare"), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.downloaded), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.Blue))
                        }
                        Box(Modifier.size(40.dp).background(Color.Black).testTag("overlay"), contentAlignment = Alignment.Center) {
                            IconButton({}, R.drawable.downloaded, Color.Blue, Modifier.size(20.dp))
                        }
                        Box(Modifier.size(40.dp).background(Color.Black).testTag("clipped"), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.downloaded), null, Modifier.size(20.dp).clip(shape), colorFilter = ColorFilter.tint(Color.Blue))
                        }
                        Box(Modifier.size(40.dp).background(Color.Black).testTag("state"), contentAlignment = Alignment.Center) {
                            DownloadStateIconButton({}, {}, R.drawable.downloaded, Color.Blue, Modifier.size(20.dp), downloadState = Download.STATE_COMPLETED)
                        }
                    }
                }
            }
        }
        for (glassEnabled in listOf(false, true)) for (radius in listOf(0.dp, 4.dp, 100.dp)) {
            compose.runOnIdle { glass.value = glassEnabled; corner.value = radius }
            val bare = compose.onNodeWithTag("bare").captureToImage().toPixelMap()
            val overlay = compose.onNodeWithTag("overlay").captureToImage().toPixelMap()
            val clipped = compose.onNodeWithTag("clipped").captureToImage().toPixelMap()
            val state = compose.onNodeWithTag("state").captureToImage().toPixelMap()
            assertEquals(bare.width, overlay.width)
            assertEquals(bare.height, overlay.height)
            var lostPixels = 0
            var inkPixels = 0
            for (y in 0 until bare.height) for (x in 0 until bare.width) {
                assertEquals("Complete downloaded vector at $x,$y, radius=$radius, glass=$glassEnabled", bare[x, y], overlay[x, y])
                assertEquals("Idle download-state vector at $x,$y, radius=$radius, glass=$glassEnabled", bare[x, y], state[x, y])
                if (bare[x, y] != clipped[x, y]) lostPixels++
                if (bare[x, y] != Color.Black) inkPixels++
            }
            assertTrue("The baseline must rasterize the real vector", inkPixels > 0)
            if (radius == 100.dp) assertTrue("This regression test must detect the old badge clipping", lostPixels > 0)
        }
    }

    @Test fun `feedback is clipped behind the glyph while the complete seat owns click long press cancellation and disabled state`() {
        val enabled = mutableStateOf(true)
        var clicks = 0
        var longClicks = 0
        var parentClicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(100.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                Box(Modifier.size(56.dp).background(Color.Black).testTag("host").clickable { parentClicks++ }, contentAlignment = Alignment.Center) {
                    DownloadIconButton(
                        onClick = { clicks++ }, icon = R.drawable.downloaded, color = Color.Blue,
                        modifier = Modifier.padding(8.dp).size(20.dp), enabled = enabled.value,
                        indication = SolidPressIndication, onLongClick = { longClicks++ },
                        placementModifier = Modifier.testTag("action"), contentDescription = "Downloaded",
                    )
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        val host = compose.onNodeWithTag("host")
        action.assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
        assertEquals("One child action plus its parent", 2, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        val before = host.captureToImage().toPixelMap()
        action.performTouchInput { down(Offset(1f, center.y)) }
        val held = host.captureToImage().toPixelMap()
        val hostBounds = host.fetchSemanticsNode().boundsInRoot
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        val left = (actionBounds.left - hostBounds.left).toInt()
        val top = (actionBounds.top - hostBounds.top).toInt()
        val midY = (actionBounds.center.y - hostBounds.top).toInt()
        assertEquals("Feedback paints the reserved padding", Color.Red, held[left + 2, midY])
        assertEquals("The user roundness clips the feedback corner", Color.Black, held[left, top])
        assertEquals("Feedback stays inside the reserved canvas", Color.Black, held[left - 1, midY])
        for (y in 0 until before.height) for (x in 0 until before.width) {
            assertEquals("Feedback cannot cover the blue glyph at $x,$y", before[x, y].blue, held[x, y].blue, 0.001f)
        }
        action.performTouchInput { cancel() }
        val cancelled = host.captureToImage().toPixelMap()
        for (y in 0 until before.height) for (x in 0 until before.width) assertEquals(before[x, y], cancelled[x, y])
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(0, parentClicks) }
        for (edge in 0..3) action.performTouchInput {
            click(when (edge) {
                0 -> Offset(1f, center.y)
                1 -> Offset(width - 1f, center.y)
                2 -> Offset(center.x, 1f)
                else -> Offset(center.x, height - 1f)
            })
        }
        action.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(4, clicks); assertEquals(1, longClicks); assertEquals(0, parentClicks); enabled.value = false }
        action.assertIsNotEnabled()
        action.performTouchInput { click(); longClick() }
        compose.runOnIdle { assertEquals(4, clicks); assertEquals(1, longClicks) }
    }

    // Deterministic feedback exposes clip/z-order regressions independently of platform ripple raster support.
    private object SolidPressIndication : IndicationNodeFactory {
        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = System.identityHashCode(this)
        override fun create(interactionSource: InteractionSource): DelegatableNode = SolidPressNode(interactionSource)
    }

    private class SolidPressNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private val presses = mutableSetOf<PressInteraction.Press>()

        override fun onAttach() {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses.add(interaction)
                        is PressInteraction.Release -> presses.remove(interaction.press)
                        is PressInteraction.Cancel -> presses.remove(interaction.press)
                    }
                    invalidateDraw()
                }
            }
        }

        override fun onDetach() {
            presses.clear()
        }

        override fun ContentDrawScope.draw() {
            if (presses.isNotEmpty()) drawRect(Color.Red)
            drawContent()
        }
    }
}
