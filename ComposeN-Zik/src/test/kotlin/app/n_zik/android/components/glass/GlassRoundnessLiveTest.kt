package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.glass.backdrop.effects.lensCornerRadii
import app.n_zik.android.components.glass.backdrop.internal.ShapeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassRoundnessLiveTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `mounted backdrop updates clipping and lens corners when roundness changes`() {
        var roundness by mutableStateOf(0.dp)
        var outlineRadius: Float? = null
        var lensRadius: Float? = null
        val backdrop = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { drawRect(Color.Red) }
        }
        composeRule.setContent {
            val cornerShape = RoundedCornerShape(BoundedCornerSize(roundness, .4f))
            // Same shape-block lifetime as glassMaterial; the modifier node stays mounted.
            val shapeBlock = remember(cornerShape) { { cornerShape } }
            val outlineProvider = remember { ShapeProvider { RoundedCornerShape(BoundedCornerSize(roundness, .4f)) } }
            Box(Modifier.size(120.dp, 60.dp).drawPlainBackdrop(
                backdrop = backdrop,
                shape = shapeBlock,
                backdropScale = .5f,
                effects = {
                    if (size != Size.Unspecified && size.minDimension > 0f) {
                        lensRadius = lensCornerRadii(shape, size, contentScale, this, layoutDirection)?.first()
                    }
                },
                onDrawSurface = {
                    val outline = outlineProvider.shape.createOutline(size, layoutDirection, this)
                    outlineRadius = if (outline is Outline.Rounded) outline.roundRect.topLeftCornerRadius.x else 0f
                },
            ))
        }
        listOf(0.dp, 48.dp, 6.dp).forEach { value ->
            composeRule.runOnIdle { roundness = value }
            composeRule.waitForIdle()
            composeRule.onRoot().captureToImage()
            composeRule.runOnIdle {
                val density = composeRule.density.density
                val expected = minOf(value.value, 60f * .4f) * density
                assertNotNull("Backdrop effect must have been evaluated", lensRadius)
                assertNotNull("Backdrop outline must have been drawn", outlineRadius)
                assertEquals(expected, requireNotNull(outlineRadius), .001f)
                assertEquals(expected * .5f, requireNotNull(lensRadius), .001f)
            }
        }
    }
}
