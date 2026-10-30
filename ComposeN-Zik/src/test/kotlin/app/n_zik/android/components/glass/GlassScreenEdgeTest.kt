package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassScreenEdgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `only the attached edge loses its rim without changing reported bounds`() {
        val edge = mutableStateOf<GlassScreenEdge?>(null)
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("pane")) {
                Box(Modifier.fillMaxSize()
                    .then(edge.value?.let { Modifier.extendGlassPastScreenEdge(it) } ?: Modifier)
                    .background(Color.Black).border(GlassRimWidth, Color.White, RectangleShape))
            }
        }
        val pane = compose.onNodeWithTag("pane")
        val bounds = pane.fetchSemanticsNode().boundsInRoot
        fun sample() = pane.captureToImage().toPixelMap()
        assertTrue(sample()[50, 0].red > .6f)
        assertTrue(sample()[50, 99].red > .6f)
        compose.runOnIdle { edge.value = GlassScreenEdge.Top }
        assertTrue(sample()[50, 0].red < .1f)
        assertTrue(sample()[50, 99].red > .6f)
        assertTrue(sample()[0, 50].red > .6f)
        assertEquals(bounds, pane.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { edge.value = GlassScreenEdge.Left }
        assertTrue(sample()[0, 50].red < .1f)
        assertTrue(sample()[99, 50].red > .6f)
        assertTrue(sample()[50, 0].red > .6f)
        assertEquals(bounds, pane.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { edge.value = GlassScreenEdge.Right }
        assertTrue(sample()[99, 50].red < .1f)
        assertTrue(sample()[0, 50].red > .6f)
        assertTrue(sample()[50, 99].red > .6f)
        assertEquals(bounds, pane.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { edge.value = GlassScreenEdge.Bottom }
        assertTrue(sample()[50, 0].red > .6f)
        assertTrue(sample()[50, 99].red < .1f)
        assertTrue(sample()[0, 50].red > .6f)
        assertEquals(bounds, pane.fetchSemanticsNode().boundsInRoot)
    }
}
