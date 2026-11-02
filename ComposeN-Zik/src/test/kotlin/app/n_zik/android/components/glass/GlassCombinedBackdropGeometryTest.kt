package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCombinedBackdropGeometryTest {
    @get:Rule(order = 0) val nativeTraversals = object : ExternalResource() {
        override fun before() = ShadowView.setUseRealDrawTraversals(true)
        override fun after() = ShadowView.setUseRealDrawTraversals(false)
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun `combined capture follows consumer and whole pane moves then settles at rest`() {
        val consumerX = mutableStateOf(0.dp)
        val paneX = mutableStateOf(0.dp)
        lateinit var sourceLayer: LayerBackdrop
        var captures = 0
        compose.setContent {
            val source = rememberLayerBackdrop()
            SideEffect { sourceLayer = source }
            val clear = rememberCanvasBackdrop { }
            val combined = rememberCombinedBackdrop(source, clear)
            val shape = remember { { RectangleShape } }
            val effects: BackdropEffectScope.() -> Unit = remember { {} }
            val record: DrawScope.(DrawScope.() -> Unit) -> Unit = remember {
                { draw -> captures++; draw() }
            }
            Box(Modifier.offset { IntOffset(paneX.value.roundToPx(), 0) }.size(260.dp, 120.dp)) {
                // Retain the static producer so moving its sibling cannot masquerade
                // as new source content and accidentally invalidate the consumer.
                Box(Modifier.size(240.dp, 120.dp).graphicsLayer().layerBackdrop(source).drawBehind {
                    drawRect(Color.Blue)
                    drawRect(Color.Red, topLeft = Offset(size.width / 2f, 0f),
                        size = Size(size.width / 2f, size.height))
                })
                Box(Modifier.offset { IntOffset(consumerX.value.roundToPx(), 0) }
                    .size(40.dp).testTag("consumer").drawPlainBackdrop(
                        backdrop = combined, shape = shape, effects = effects,
                        onDrawBackdrop = record,
                    ))
            }
        }
        fun assertColor(expected: Color) {
            val pixels = compose.onNodeWithTag("consumer").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals("retained combined source red", expected.red, actual.red, .03f)
            assertEquals("retained combined source blue", expected.blue, actual.blue, .03f)
        }
        assertColor(Color.Blue)
        val version = sourceLayer.contentVersion
        val originalCaptures = captures
        compose.runOnIdle { consumerX.value = 160.dp }
        assertColor(Color.Red)
        assertEquals("producer content must remain static", version, sourceLayer.contentVersion)
        assertTrue("consumer relocation must refresh its capture", captures > originalCaptures)

        val relocatedCaptures = captures
        compose.runOnIdle { paneX.value = 20.dp }
        assertColor(Color.Red)
        assertEquals("whole-pane movement does not redraw source content", version, sourceLayer.contentVersion)
        assertTrue("coordinate-dependent capture must notice the pane's move", captures > relocatedCaptures)
        val settledCaptures = captures
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertColor(Color.Red)
        }
        assertEquals("position invalidation must settle at rest", settledCaptures, captures)
    }
}
